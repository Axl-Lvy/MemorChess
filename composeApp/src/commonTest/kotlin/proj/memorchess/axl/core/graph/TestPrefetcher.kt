package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Unit tests for [Prefetcher]'s fan out policy. An [UnconfinedTestDispatcher] scope makes each warm
 * run to completion inside [Prefetcher.warmNeighbors], so the loads it fired are observable as soon
 * as it returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestPrefetcher {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun key(name: String) = PositionKey("$name w K")

  private fun edge(from: PositionKey, san: String, to: PositionKey) =
    Edge(from = from, move = san, to = to, isGood = true, createdAt = now, updatedAt = now)

  private fun emptyRow(positionKey: PositionKey) =
    DataNode(positionKey, PreviousAndNextMoves(), CardStateFactory.new(), 0)

  @Test
  fun everyDistinctNeighborIsWarmedExactlyOnce() = runTest {
    val requested = mutableListOf<PositionKey>()
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    val cache =
      NodeCache(
        { positionKey ->
          requested += positionKey
          emptyRow(positionKey)
        },
        scope,
      )
    val self = key("self")
    val child = key("child")
    val parent = key("parent")
    val node =
      Node(
        positionKey = self,
        outgoing = mapOf("e4" to edge(self, "e4", child), "d4" to edge(self, "d4", child)),
        incoming = mapOf("c4" to edge(parent, "c4", self)),
        depth = 1,
      )

    Prefetcher(cache, scope).warmNeighbors(node)

    // child appears on two outgoing edges but is warmed once, and the node itself is never warmed.
    requested.map { it.value }.sorted() shouldBe listOf(child.value, parent.value).sorted()
  }

  @Test
  fun theNodeItselfIsExcluded() = runTest {
    val requested = mutableListOf<PositionKey>()
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    val cache =
      NodeCache(
        { positionKey ->
          requested += positionKey
          emptyRow(positionKey)
        },
        scope,
      )
    val self = key("self")
    val node = Node(positionKey = self, outgoing = mapOf("e4" to edge(self, "e4", self)), depth = 0)

    Prefetcher(cache, scope).warmNeighbors(node)

    requested shouldBe emptyList()
  }

  @Test
  fun prefetchNeverRecurses() = runTest {
    val requested = mutableListOf<PositionKey>()
    val self = key("self")
    val child = key("child")
    val grandchild = key("grandchild")
    val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    val cache =
      NodeCache(
        { positionKey ->
          requested += positionKey
          if (positionKey == child) {
            DataNode(
              child,
              PreviousAndNextMoves(
                emptyList(),
                listOf(DataMove(child, grandchild, "e5", isGood = true)),
              ),
              CardStateFactory.new(),
              1,
            )
          } else {
            emptyRow(positionKey)
          }
        },
        scope,
      )
    val node = Node(positionKey = self, outgoing = mapOf("e4" to edge(self, "e4", child)), depth = 0)

    Prefetcher(cache, scope).warmNeighbors(node)

    // One ply only: the child was warmed, its own child was not.
    requested shouldBe listOf(child)
  }
}
