package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

/** Manual holds and install holds written through [TreeStore]. */
class TestTreeStoreHolders {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")

  private suspend fun InMemoryDatabaseQueryManager.move(from: PositionKey, san: String): DataMove =
    getPositionIncludingDeleted(from)!!.previousAndNextMoves.nextMoves.getValue(san)

  @Test
  fun installWritesMovesWithNoManualHold() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)

    store.install("a", listOf(InstalledMove(start, "e4", posA, isGood = true, fromDepth = 0)))

    database.move(start, "e4").isGood shouldBe true
    database.move(start, "e4").ownIsGood shouldBe null
  }

  @Test
  fun twoInstallsFollowGoodOverBad() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)

    store.install("a", listOf(InstalledMove(start, "Nf3", posA, isGood = true, fromDepth = 0)))
    store.install("b", listOf(InstalledMove(start, "Nf3", posA, isGood = false, fromDepth = 0)))

    database.move(start, "Nf3").isGood shouldBe true
  }

  @Test
  fun installNeverOverridesAnOwnMarking() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "e4", posA, isGood = false, fromDepth = 0)

    store.install("a", listOf(InstalledMove(start, "e4", posA, isGood = true, fromDepth = 0)))

    database.move(start, "e4").isGood shouldBe false
    database.move(start, "e4").ownIsGood shouldBe false
  }

  @Test
  fun aSavedMoveIsAManualHold() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)

    store.addMove(start, "e4", posA, isGood = true, fromDepth = 0)

    database.move(start, "e4").ownIsGood shouldBe true
  }

  @Test
  fun holdManuallyPinsTheEffectiveMarking() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    val tagStore = testRepertoireTagStore(database)
    store.install("a", listOf(InstalledMove(start, "e4", posA, isGood = false, fromDepth = 0)))

    store.holdManually(tagStore.edgesTaggedWith("a"))

    database.move(start, "e4").ownIsGood shouldBe false
  }

  @Test
  fun forkHoldsEverySourceMoveAndTagsTheForkWithoutMarking() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install(
      "a",
      listOf(
        InstalledMove(start, "e4", posA, isGood = true, fromDepth = 0),
        InstalledMove(posA, "e5", posB, isGood = false, fromDepth = 1),
      ),
    )

    store.fork("a", "b", "B", color = null)

    database.move(start, "e4").ownIsGood shouldBe true
    database.move(posA, "e5").ownIsGood shouldBe false
    database.getTags(start, posA).single { it.repertoireId == "b" }.isGood shouldBe null
  }

  @Test
  fun reinstallingADeletedMoveKeepsTheManualHoldTombstone() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "e4", posA, isGood = true, fromDepth = 0)
    store.deleteMove(start, "e4")
    val tombstone = database.move(start, "e4")

    store.install("a", listOf(InstalledMove(start, "e4", posA, isGood = true, fromDepth = 0)))

    val reinstalled = database.move(start, "e4")
    reinstalled.isDeleted shouldBe false
    reinstalled.ownIsGood shouldBe null
    reinstalled.deviceSeq shouldBe tombstone.deviceSeq
    reinstalled.originDevice shouldBe tombstone.originDevice
  }
}
