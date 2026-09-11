package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testNodeCache
import proj.memorchess.axl.test_util.testTreeStore

/** Unit tests for [TrainableProjection]'s recompute and clear. */
class TestTrainableProjection {

  private val start = PositionKey.START_POSITION
  private val destination = PositionKey("after-e4 b K")

  /** Trainable positions counted for `italian-game`. */
  private suspend fun trainableCount(database: DatabaseQueryManager): Int =
    database.getRepertoireMasterySnapshots(listOf("italian-game"))["italian-game"]?.totalCount ?: 0

  /** A persisted graph whose single good edge is tagged with `italian-game`. */
  private suspend fun taggedGraph(database: DatabaseQueryManager) {
    val store = testTreeStore(database)
    store.registerRepertoire("italian-game", "Italian Game", RepertoireColor.WHITE)
    store.addMove(from = start, move = "e4", to = destination, isGood = true, fromDepth = 0)
    store.tagEdge(start, destination, "italian-game")
  }

  @Test
  fun recomputeCollectsTheRepertoiresOfLiveGoodTaggedEdges() = runTest {
    val database = TestDatabases.empty()
    taggedGraph(database)
    val projection = TrainableProjection(database, testNodeCache(database))
    projection.clear(start)
    trainableCount(database) shouldBe 0

    projection.recompute(start)

    trainableCount(database) shouldBe 1
  }

  @Test
  fun recomputeIsANoOpForAnUnresolvableOrigin() = runTest {
    val database = TestDatabases.empty()
    taggedGraph(database)
    val projection = TrainableProjection(database, testNodeCache(database))

    projection.recompute(PositionKey("never-persisted w K"))

    trainableCount(database) shouldBe 1
  }

  @Test
  fun clearEmptiesTheProjectionForAPosition() = runTest {
    val database = TestDatabases.empty()
    taggedGraph(database)
    val projection = TrainableProjection(database, testNodeCache(database))

    projection.clear(start)

    trainableCount(database) shouldBe 0
  }
}
