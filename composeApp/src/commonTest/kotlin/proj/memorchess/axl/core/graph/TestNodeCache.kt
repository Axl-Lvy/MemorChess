package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Unit tests for [NodeCache]: bounded LRU residency, single flight, and the stale read contract
 * documented on [NodeCache.resolve].
 *
 * Every case drives the cache against a plain [NodeLoader] lambda, so a gate is a
 * [kotlinx.coroutines.CompletableDeferred] the lambda awaits rather than a database decorator. The
 * load scope is an [UnconfinedTestDispatcher], so a released gate runs finalization and any retry
 * inline: after `gate.complete(Unit)` returns there is nothing left pending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestNodeCache {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun key(i: Int) = PositionKey("pos$i w K")

  private fun isolatedRow(i: Int) =
    DataNode(
      positionKey = key(i),
      previousAndNextMoves = PreviousAndNextMoves(),
      cardState = CardStateFactory.new(),
      depth = i,
    )

  private fun move(from: PositionKey, san: String, to: PositionKey) =
    DataMove(origin = from, destination = to, move = san, isGood = true)

  private fun edge(from: PositionKey, san: String, to: PositionKey) =
    Edge(from = from, move = san, to = to, isGood = true, createdAt = now, updatedAt = now)

  private fun row(
    positionKey: PositionKey,
    incoming: List<DataMove> = emptyList(),
    outgoing: List<DataMove> = emptyList(),
    depth: Int = 0,
  ) = DataNode(positionKey, PreviousAndNextMoves(incoming, outgoing), CardStateFactory.new(), depth)

  @Test
  fun aMissLoadsOnceThenServesHits() = runTest {
    var calls = 0
    val cache =
      NodeCache(
        { positionKey ->
          calls++
          if (positionKey == key(0)) isolatedRow(0) else null
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    val first = cache.resolve(key(0))
    assertNotNull(first.node, "a persisted position must resolve")
    first.hit shouldBe false
    calls shouldBe 1

    val second = cache.resolve(key(0))
    assertNotNull(second.node)
    second.hit shouldBe true
    calls shouldBe 1
  }

  @Test
  fun anAbsentPositionResolvesNullAndCachesNothing() = runTest {
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          null
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    // Zero resident: a miss must insert nothing, so the second resolve looks up again.
    assertNull(cache.resolve(key(0)).node)
    cache.residentCount() shouldBe 0
    assertNull(cache.resolve(key(0)).node)
    calls shouldBe 2
  }

  @Test
  fun oneResidentEntryStaysAHit() = runTest {
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          isolatedRow(0)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    cache.resolve(key(0))
    cache.resolve(key(0))
    cache.resolve(key(0))
    calls shouldBe 1
    cache.residentCount() shouldBe 1
  }

  @Test
  fun atCapEveryEntryStaysResident() = runTest {
    val cap = OpeningTree.MAX_CACHE_NODES
    val calls = mutableMapOf<PositionKey, Int>()
    val cache =
      NodeCache(
        { positionKey ->
          calls[positionKey] = (calls[positionKey] ?: 0) + 1
          row(positionKey)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    for (i in 0 until cap) cache.resolve(key(i))
    cache.residentCount() shouldBe cap

    cache.resolve(key(0))
    calls[key(0)] shouldBe 1
  }

  @Test
  fun pastCapTheLeastRecentlyUsedEntryIsEvicted() = runTest {
    val cap = OpeningTree.MAX_CACHE_NODES
    val calls = mutableMapOf<PositionKey, Int>()
    val cache =
      NodeCache(
        { positionKey ->
          calls[positionKey] = (calls[positionKey] ?: 0) + 1
          row(positionKey)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    for (i in 0..cap) cache.resolve(key(i))
    cache.residentCount() shouldBe cap

    cache.resolve(key(1))
    calls[key(1)] shouldBe 1

    cache.resolve(key(0))
    calls[key(0)] shouldBe 2
  }

  @Test
  fun wellPastCapResidencyStaysAtTheCap() = runTest {
    val cap = OpeningTree.MAX_CACHE_NODES
    val cache =
      NodeCache(
        { positionKey -> row(positionKey) },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    for (i in 0 until cap * 3) cache.resolve(key(i))
    cache.residentCount() shouldBe cap
  }

  @Test
  fun aSyncApplyDuringALoadForcesARetryAndWinsIt() = runTest {
    // Spec test 1. The first load returns the pre sync row; the retry returns the applied one.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val preSync = row(key(0), outgoing = listOf(move(key(0), "e4", key(1))))
    val applied = row(key(0), outgoing = listOf(move(key(0), "d4", key(2))))
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) {
            gate.await()
            preSync
          } else {
            applied
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    var resolved: Node? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      resolved = cache.resolve(key(0)).node
    }
    cache.invalidate(key(0))
    gate.complete(Unit)

    calls shouldBe 2
    resolved?.outgoing?.keys shouldBe setOf("d4")
    cache.peek(key(0))?.outgoing?.keys shouldBe setOf("d4")
  }

  @Test
  fun anEdgeUpsertedDuringALoadSurvivesTheLoad() = runTest {
    // Spec test 2, cache half. The loaded row carries to -> child and a real card state; the edge
    // under test is a genuinely new incoming one.
    val to = key(0)
    val child = key(1)
    val from = key(2)
    val gate = CompletableDeferred<Unit>()
    val loadedRow = row(to, outgoing = listOf(move(to, "e5", child)), depth = 3)
    val cache =
      NodeCache(
        {
          gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    var resolved: Node? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      resolved = cache.resolve(to).node
    }
    cache.upsertEdge(edge(from, "e4", to), fromDepth = 2)
    gate.complete(Unit)

    val node = assertNotNull(resolved)
    node.incoming.keys shouldBe setOf("e4")
    node.outgoing.keys shouldBe setOf("e5")
    node.cardState shouldBe loadedRow.cardState
    cache.peek(to)?.incoming?.keys shouldBe setOf("e4")
  }

  @Test
  fun twoConcurrentResolvesShareOneLoad() = runTest {
    // Spec test 3.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          gate.await()
          isolatedRow(0)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    var first: Node? = null
    var second: Node? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      first = cache.resolve(key(0)).node
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      second = cache.resolve(key(0)).node
    }
    gate.complete(Unit)

    calls shouldBe 1
    assertNotNull(first)
    first shouldBe second
  }

  @Test
  fun aFailedLoadLeavesNoClaimBehind() = runTest {
    // Spec test 4, first part.
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) throw IllegalStateException("boom") else isolatedRow(0)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    assertFailsWith<IllegalStateException> { cache.resolve(key(0)) }
    // No dead Deferred left in flight: the next resolve issues a fresh read rather than joining it.
    assertNotNull(cache.resolve(key(0)).node)
    calls shouldBe 2
  }

  @Test
  fun aCancelledCallerDoesNotStopTheLoad() = runTest {
    // Spec test 4, second part.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          gate.await()
          isolatedRow(0)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    val job =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(key(0)) }
    job.cancel()
    gate.complete(Unit)

    assertNotNull(cache.peek(key(0)), "the load still finished and still populated the cache")
    calls shouldBe 1
  }

  @Test
  fun aFailingRetryLeavesTheKeyNotResident() = runTest {
    // Spec test 4, third part: the superseded first round candidate must not survive.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) {
            gate.await()
            isolatedRow(0)
          } else {
            throw IllegalStateException("boom")
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      runCatching { cache.resolve(key(0)) }
    }
    cache.invalidate(key(0))
    gate.complete(Unit)

    calls shouldBe 2
    assertNull(cache.peek(key(0)))
  }

  @Test
  fun ensureDuringALoadDoesNotLoseTheLoadedNode() = runTest {
    // Spec test 5, first part: a create style ensure on a non resident key.
    val gate = CompletableDeferred<Unit>()
    val loadedRow = row(key(0), outgoing = listOf(move(key(0), "e4", key(1))), depth = 3)
    val cache =
      NodeCache(
        {
          gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    var resolved: Node? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      resolved = cache.resolve(key(0)).node
    }
    cache.ensure(key(0), depth = 7)
    gate.complete(Unit)

    val node = assertNotNull(resolved)
    node.outgoing.keys shouldBe setOf("e4")
    node.cardState shouldBe loadedRow.cardState
    node.depth shouldBe 3
  }

  @Test
  fun ensureDuringALoadLowersTheDepthAndKeepsTheEdges() = runTest {
    // Spec test 5, second part: the mark fires and the depth drops.
    val gate = CompletableDeferred<Unit>()
    val loadedRow = row(key(0), outgoing = listOf(move(key(0), "e4", key(1))), depth = 9)
    val cache =
      NodeCache(
        {
          gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    var resolved: Node? = null
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      resolved = cache.resolve(key(0)).node
    }
    cache.ensure(key(0), depth = 4)
    gate.complete(Unit)

    val node = assertNotNull(resolved)
    node.depth shouldBe 4
    node.outgoing.keys shouldBe setOf("e4")
  }

  @Test
  fun aRacedLoadDoesNotResurrectARemovedEdge() = runTest {
    // Spec test 6, and the claim marked twice edge case.
    val to = key(0)
    val originA = key(1)
    val originB = key(2)
    val originAPrime = key(3)
    val gate = CompletableDeferred<Unit>()
    val loadedRow =
      row(to, incoming = listOf(move(originA, "mA", to), move(originB, "mB", to)), depth = 1)
    val cache =
      NodeCache(
        {
          gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.upsertEdge(edge(originAPrime, "mC", to), fromDepth = 0)
    // originA is not resident, so the tree no ops. The mark must still be set.
    cache.removeEdge(originA, "mA", to)
    gate.complete(Unit)

    cache.peek(to)?.incoming?.keys shouldBe setOf("mB", "mC")
  }

  @Test
  fun aRemovalIsReplayedWithNoCacheEntryAtAll() = runTest {
    // Spec test 7: the plain deleteMove path, neither endpoint resident.
    val to = key(0)
    val originA = key(1)
    val originB = key(2)
    val gate = CompletableDeferred<Unit>()
    val loadedRow =
      row(to, incoming = listOf(move(originA, "mA", to), move(originB, "mB", to)), depth = 1)
    val cache =
      NodeCache(
        {
          gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.removeEdge(originA, "mA", to)
    gate.complete(Unit)

    cache.peek(to)?.incoming?.keys shouldBe setOf("mB")
  }

  @Test
  fun anInvalidationDoesNotSwallowAConcurrentRemoval() = runTest {
    // Spec test 8, and the stale then edited merge order. Both reads return the same rows, which is
    // what a database write that has not yet committed looks like.
    val to = key(0)
    val originA = key(1)
    val originB = key(2)
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val loadedRow =
      row(to, incoming = listOf(move(originA, "mA", to), move(originB, "mB", to)), depth = 1)
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) gate.await()
          loadedRow
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.invalidate(to)
    cache.removeEdge(originA, "mA", to)
    gate.complete(Unit)

    calls shouldBe 2
    cache.peek(to)?.incoming?.keys shouldBe setOf("mB")
  }

  @Test
  fun anInvalidationDoesNotPinAShell() = runTest {
    // Spec test 9, and the edited then stale merge order.
    val to = key(0)
    val child = key(1)
    val from = key(2)
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val fresh = row(to, outgoing = listOf(move(to, "e5", child)), depth = 1)
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) {
            gate.await()
            row(to, depth = 1)
          } else {
            fresh
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.invalidate(to)
    cache.upsertEdge(edge(from, "e4", to), fromDepth = 0)
    gate.complete(Unit)

    val node = assertNotNull(cache.peek(to))
    node.outgoing.keys shouldBe setOf("e5")
    node.incoming.keys shouldBe setOf("e4")
    node.cardState shouldBe fresh.cardState
  }

  @Test
  fun aMarkedDepthNeverRaisesTheLoadedDepth() = runTest {
    // Spec test 10, plus the equal and below boundaries of the depth merge.
    val to = key(0)
    val from = key(1)
    suspend fun cachedDepthAfterUpsert(loadedDepth: Int, fromDepth: Int): Int {
      val gate = CompletableDeferred<Unit>()
      val cache =
        NodeCache(
          {
            gate.await()
            row(to, depth = loadedDepth)
          },
          CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
      cache.upsertEdge(edge(from, "e4", to), fromDepth = fromDepth)
      gate.complete(Unit)
      return assertNotNull(cache.peek(to)).depth
    }

    // Mark above the loaded depth: the loaded depth wins.
    cachedDepthAfterUpsert(loadedDepth = 3, fromDepth = 6) shouldBe 3
    // Mark equal to the loaded depth.
    cachedDepthAfterUpsert(loadedDepth = 3, fromDepth = 2) shouldBe 3
    // Mark below the loaded depth: the mark wins.
    cachedDepthAfterUpsert(loadedDepth = 3, fromDepth = 1) shouldBe 2
  }

  @Test
  fun theRetrySurvivesACancelledCaller() = runTest {
    // Spec test 11, first part.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val applied = row(key(0), outgoing = listOf(move(key(0), "d4", key(2))))
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) {
            gate.await()
            isolatedRow(0)
          } else {
            applied
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    val job =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(key(0)) }
    job.cancel()
    cache.invalidate(key(0))
    gate.complete(Unit)

    calls shouldBe 2
    cache.peek(key(0))?.outgoing?.keys shouldBe setOf("d4")
  }

  @Test
  fun clearDuringALoadLeavesTheKeyNotResident() = runTest {
    // Spec test 11, second part: the eraseAll trace. The first attempt puts its pre erase
    // candidate,
    // the retry's null load removes it.
    val gate = CompletableDeferred<Unit>()
    var calls = 0
    val cache =
      NodeCache(
        {
          calls++
          if (calls == 1) {
            gate.await()
            isolatedRow(0)
          } else {
            null
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    val job =
      backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(key(0)) }
    job.cancel()
    cache.clear()
    gate.complete(Unit)

    calls shouldBe 2
    assertNull(cache.peek(key(0)))
  }

  @Test
  fun aMarkWithNoClaimInFlightIsIgnored() = runTest {
    // Edge case: no load in flight, so nothing is recorded and the next load is unreconciled.
    val cache =
      NodeCache({ isolatedRow(0) }, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    cache.removeEdge(key(1), "mA", key(0))
    cache.invalidate(key(0))

    val node = assertNotNull(cache.resolve(key(0)).node)
    node.depth shouldBe 0
  }

  @Test
  fun theLaterOfTwoEditsOnOneKeyWins() = runTest {
    // Edge case: edits union with the later winning per key.
    val to = key(0)
    val from = key(1)
    val gate = CompletableDeferred<Unit>()
    val cache =
      NodeCache(
        {
          gate.await()
          row(to, depth = 1)
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.upsertEdge(edge(from, "e4", to), fromDepth = 0)
    cache.removeEdge(from, "e4", to)
    gate.complete(Unit)

    cache.peek(to)?.incoming?.keys shouldBe emptySet()
  }

  @Test
  fun removeEdgeMarksWithTheOriginResidentToo() = runTest {
    // Edge case: the origin resident path, the mirror of the evicted one in test 7.
    val to = key(0)
    val originA = key(1)
    val gate = CompletableDeferred<Unit>()
    val cache =
      NodeCache(
        { positionKey ->
          if (positionKey == originA) {
            row(originA, outgoing = listOf(move(originA, "mA", to)))
          } else {
            gate.await()
            row(to, incoming = listOf(move(originA, "mA", to)), depth = 1)
          }
        },
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      )

    cache.resolve(originA)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(to) }
    cache.removeEdge(originA, "mA", to)
    gate.complete(Unit)

    cache.peek(originA)?.outgoing?.keys shouldBe emptySet()
    cache.peek(to)?.incoming?.keys shouldBe emptySet()
  }
}
