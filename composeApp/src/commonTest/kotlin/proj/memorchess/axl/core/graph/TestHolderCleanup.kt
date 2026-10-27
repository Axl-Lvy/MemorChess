package proj.memorchess.axl.core.graph

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore

/** Which moves and positions [HolderCleanup] plans to delete once holds are gone. */
class TestHolderCleanup {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

  private val e4 = TaggedEdge(start, posB, "e4")
  private val d4 = TaggedEdge(start, posA, "d4")
  private val nf3 = TaggedEdge(posA, posB, "Nf3")
  private val e5 = TaggedEdge(posB, posC, "e5")

  private fun TaggedEdge.installed(isGood: Boolean, fromDepth: Int) =
    InstalledMove(origin, move, destination, isGood, fromDepth)

  @Test
  fun aMoveOnlyTheRepertoireHoldsIsDeleted() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0)))
    val tag = database.getTags(start, posB).single()
    database.insertTag(tag.copy(isDeleted = true, deviceSeq = tag.deviceSeq + 1))

    val plan = HolderCleanup(database).plan(listOf(e4))

    plan.moves.map { it.move } shouldBe listOf("e4")
    plan.positions shouldBe listOf(posB)
  }

  @Test
  fun aManuallyHeldMoveIsKept() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.addMove(start, "e4", posB, isGood = true, fromDepth = 0)
    store.install("x", listOf(e4.installed(true, 0)))

    val plan = HolderCleanup(database).plan(listOf(e4), HoldOverlay("x", setOf(e4), emptySet()))

    plan shouldBe CleanupPlan(emptyList(), emptyList())
  }

  @Test
  fun aMoveAnotherInstallHoldsIsKept() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0)))
    store.install("y", listOf(e4.installed(false, 0)))

    val plan = HolderCleanup(database).plan(listOf(e4), HoldOverlay("x", setOf(e4), emptySet()))

    plan shouldBe CleanupPlan(emptyList(), emptyList())
  }

  @Test
  fun theCascadeDeletesEveryHolderBeyondAnOrphanedPosition() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0), e5.installed(true, 1)))
    store.addMove(posB, "e5", posC, isGood = true, fromDepth = 1)

    val plan =
      HolderCleanup(database).plan(listOf(e4, e5), HoldOverlay("x", setOf(e4, e5), emptySet()))

    plan.moves.map { it.move } shouldBe listOf("e4", "e5")
    plan.positions shouldBe listOf(posB, posC)
  }

  @Test
  fun aPositionStillReachedByAnotherMoveIsKept() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0), e5.installed(true, 1)))
    store.install("y", listOf(d4.installed(true, 0), nf3.installed(true, 1)))

    val plan = HolderCleanup(database).plan(listOf(e4), HoldOverlay("x", setOf(e4), emptySet()))

    plan.moves.map { it.move } shouldBe listOf("e4")
    plan.positions shouldBe emptyList()
  }

  @Test
  fun anAddedMoveKeepsItsDestinationAlive() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0), e5.installed(true, 1)))

    val plan =
      HolderCleanup(database).plan(listOf(e4), HoldOverlay("x", setOf(e4), setOf(d4, nf3, e5)))

    plan.moves.map { it.move } shouldBe listOf("e4")
    plan.positions shouldBe emptyList()
  }

  @Test
  fun theStartPositionIsNeverDeleted() = runTest {
    val database = TestDatabases.empty()
    val store = testTreeStore(database)
    store.install("x", listOf(e4.installed(true, 0)))

    val plan = HolderCleanup(database).plan(listOf(e4), HoldOverlay("x", setOf(e4), emptySet()))

    plan.positions shouldBe listOf(posB)
  }

  @Test
  fun anEmptyCandidateListPlansNothing() = runTest {
    val plan = HolderCleanup(TestDatabases.empty()).plan(emptyList())

    plan shouldBe CleanupPlan(emptyList(), emptyList())
  }
}
