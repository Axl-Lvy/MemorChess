package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.CountingDatabaseQueryManager

/**
 * Unit tests for the bounded LRU cache and demand paged resolution in [TreeStore.node]. They run
 * against a [CountingDatabaseQueryManager] so cache hits, misses, eviction and one ply prefetch are
 * asserted by counting point lookups, never by reaching into the cache.
 *
 * Prefetch is made deterministic by passing an [UnconfinedTestDispatcher] scope: a launched warm
 * runs eagerly to completion within the resolving call, so its effect is observable immediately
 * after [TreeStore.node] returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestTreeStoreCache {

  private fun key(i: Int) = PositionKey("pos$i w K")

  /** A persisted, edgeless node so resolving it triggers no neighbour prefetch. */
  private fun isolatedNode(i: Int) =
    DataNode(
      positionKey = key(i),
      previousAndNextMoves = PreviousAndNextMoves(),
      cardState = CardStateFactory.new(),
      depth = i,
    )

  private fun goodMove(from: PositionKey, san: String, to: PositionKey) =
    DataMove(origin = from, destination = to, move = san, isGood = true)

  @Test
  fun resolvingAMissCallsGetPositionExactlyOnceThenHits() = runTest {
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(isolatedNode(0))
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    val first = store.node(key(0))
    assertNotNull(first, "a persisted position must resolve")
    assertEquals(1, database.getPositionCalls[key(0)], "the first resolve is a single point lookup")

    val second = store.node(key(0))
    assertNotNull(second)
    assertEquals(1, database.getPositionCalls[key(0)], "a second resolve is a cache hit, no lookup")
  }

  @Test
  fun resolvingAnAbsentPositionReturnsNullAndCachesNothing() = runTest {
    val database = CountingDatabaseQueryManager(InMemoryDatabaseQueryManager())
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    // 0 entries resident: a miss returns null and inserts nothing, so a second resolve looks up
    // again rather than serving a phantom hit.
    assertNull(store.node(key(0)))
    assertEquals(1, database.getPositionCalls[key(0)])
    assertNull(store.node(key(0)))
    assertEquals(2, database.getPositionCalls[key(0)], "a null result must not be cached")
  }

  @Test
  fun singleResidentEntryStaysAHit() = runTest {
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(isolatedNode(0))
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    store.node(key(0))
    store.node(key(0))
    store.node(key(0))
    assertEquals(1, database.getPositionCalls[key(0)], "one entry below the cap never re-fetches")
  }

  @Test
  fun atCapEveryEntryStaysResident() = runTest {
    val cap = OpeningTree.MAX_CACHE_NODES
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(*(0 until cap).map { isolatedNode(it) }.toTypedArray())
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    // Resolve exactly cap distinct positions: each is one miss.
    for (i in 0 until cap) store.node(key(i))
    assertEquals(cap, database.totalGetPositionCalls)

    // Nothing was evicted, so re-resolving the very first is still a hit.
    store.node(key(0))
    assertEquals(
      1,
      database.getPositionCalls[key(0)],
      "with exactly cap entries the oldest is still resident",
    )
  }

  @Test
  fun pastCapTheLeastRecentlyUsedEntryIsEvicted() = runTest {
    val cap = OpeningTree.MAX_CACHE_NODES
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(*(0..cap).map { isolatedNode(it) }.toTypedArray())
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    // Resolve cap + 1 distinct positions in order. key(0) is the least recently used and must be
    // evicted when key(cap) is inserted.
    for (i in 0..cap) store.node(key(i))
    assertEquals(cap + 1, database.totalGetPositionCalls)

    // key(1) was touched after key(0) and is still resident: a hit, no new lookup.
    store.node(key(1))
    assertEquals(1, database.getPositionCalls[key(1)], "a more recent entry survives eviction")

    // key(0) was evicted: re-resolving it is a fresh miss.
    store.node(key(0))
    assertEquals(2, database.getPositionCalls[key(0)], "the least recently used entry was evicted")
  }

  @Test
  fun resolvingAMissWarmsItsNeighbors() = runTest {
    // start --e4--> child, both persisted with the edge in both directions.
    val start = PositionKey.START_POSITION
    val child = PositionKey("child b K")
    val edge = goodMove(start, "e4", child)
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(
      DataNode(start, PreviousAndNextMoves(emptyList(), listOf(edge)), CardStateFactory.new(), 0),
      DataNode(child, PreviousAndNextMoves(listOf(edge), emptyList()), CardStateFactory.new(), 1),
    )
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    store.node(start)
    // The miss on start fired a one ply prefetch that warmed child.
    assertEquals(1, database.getPositionCalls[child], "resolving a miss warms its neighbour")

    // child is now resident: resolving it is a hit, not a second lookup.
    val resolvedChild = store.node(child)
    assertNotNull(resolvedChild)
    assertEquals(1, database.getPositionCalls[child], "the warmed neighbour resolves from cache")
  }

  @Test
  fun prefetchWarmsEachNeighborOnlyOnce() = runTest {
    val start = PositionKey.START_POSITION
    val child = PositionKey("child b K")
    val edge = goodMove(start, "e4", child)
    val backing = InMemoryDatabaseQueryManager()
    backing.insertNodes(
      DataNode(start, PreviousAndNextMoves(emptyList(), listOf(edge)), CardStateFactory.new(), 0),
      DataNode(child, PreviousAndNextMoves(listOf(edge), emptyList()), CardStateFactory.new(), 1),
    )
    val database = CountingDatabaseQueryManager(backing)
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    // Resolve start twice; the second is a hit and must not re-prefetch the already resident child.
    store.node(start)
    store.node(start)
    assertEquals(
      1,
      database.getPositionCalls[child],
      "an already resident neighbour is not re-warmed",
    )
  }

  @Test
  fun mutationPatchesTheCacheWithoutReload() = runTest {
    val start = PositionKey.START_POSITION
    val child = PositionKey("child b K")
    val database = CountingDatabaseQueryManager(InMemoryDatabaseQueryManager())
    val store = TreeStore(database, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    store.addMove(from = start, move = "e4", to = child, isGood = true, fromDepth = 0)
    val lookupsAfterMutation = database.totalGetPositionCalls

    // The write-through upsert left both endpoints resident, so resolving them is a hit: no extra
    // point lookup happens after the mutation, and the new edge is visible immediately.
    val origin = store.node(start)
    assertNotNull(origin)
    assertTrue(origin.outgoing.containsKey("e4"), "the new edge is patched into the cache")
    assertEquals(child, origin.outgoing["e4"]?.to)
    store.node(child)
    assertEquals(
      lookupsAfterMutation,
      database.totalGetPositionCalls,
      "resolving the mutated endpoints from cache must not trigger a reload",
    )
  }
}
