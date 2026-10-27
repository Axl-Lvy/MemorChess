package proj.memorchess.axl.core.graph

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.test_util.CountingDatabaseQueryManager
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.repertoireMovesOf
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

private val rootKey = GameEngine().toPositionKey()

class TestRepertoireMoveExporter {

  /** A [TreeStore] and a [RepertoireTagStore] over one shared in memory database. */
  private data class Fixture(val tree: TreeStore, val tagStore: RepertoireTagStore)

  private fun store(): Fixture {
    val database = InMemoryDatabaseQueryManager()
    return Fixture(testTreeStore(database), testRepertoireTagStore(database))
  }

  private fun exporter(fixture: Fixture) = RepertoireMoveExporter(fixture.tree, fixture.tagStore)

  /** A throwaway position key distinct per move sequence, not a real replayed FEN. */
  private fun after(from: PositionKey, vararg moves: String): PositionKey =
    PositionKey("${from.value}|${moves.joinToString("|")}")

  private fun move(from: PositionKey, san: String, to: PositionKey, isGood: Boolean = true) =
    RepertoireMove(from.value, san, to.value, isGood)

  @Test
  fun exportOfARepertoireWithNoTaggedEdgesIsEmpty() = runTest {
    val exporter = exporter(store())

    exporter.export("italian-game") shouldBe RepertoireExportResult.Empty
  }

  @Test
  fun exportsASingleTaggedLine() = runTest {
    val fixture = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    fixture.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterE4, "italian-game")
    fixture.tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4E5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Moves

    result.moves shouldContainExactlyInAnyOrder
      listOf(move(rootKey, "e4", afterE4), move(afterE4, "e5", afterE4E5))
    result.maxPlyDepth shouldBe 1
  }

  @Test
  fun exportCarriesEachMoveMarking() = runTest {
    val fixture = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    fixture.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterE4, "italian-game")
    fixture.tree.addMove(afterE4, "e5", afterE4E5, isGood = false, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4E5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Moves

    result.moves shouldContainExactlyInAnyOrder
      listOf(move(rootKey, "e4", afterE4), move(afterE4, "e5", afterE4E5, isGood = false))
  }

  @Test
  fun exportsBranchingVariations() = runTest {
    val fixture = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    val afterE4C5 = after(rootKey, "e4", "c5")
    fixture.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterE4, "italian-game")
    fixture.tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4E5, "italian-game")
    fixture.tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4C5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Moves

    result.moves.map { it.move }.toSet() shouldBe setOf("e4", "e5", "c5")
  }

  @Test
  fun includesAnUntaggedConnectiveEdgeOnTheWayToATaggedOne() = runTest {
    // e4 is common theory, played but never tagged; e4 e5 is the tagged repertoire line.
    val fixture = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    fixture.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    fixture.tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4E5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Moves

    result.moves shouldContainExactlyInAnyOrder
      listOf(move(rootKey, "e4", afterE4), move(afterE4, "e5", afterE4E5))
  }

  @Test
  fun excludesASoftDeletedEdgeEvenWhenItWasTagged() = runTest {
    val fixture = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    val afterE4C5 = after(rootKey, "e4", "c5")
    fixture.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterE4, "italian-game")
    fixture.tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4E5, "italian-game")
    fixture.tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterE4, afterE4C5, "italian-game")
    fixture.tree.deleteMove(afterE4, "c5")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Moves

    result.moves.map { it.move }.toSet() shouldBe setOf("e4", "e5")
  }

  @Test
  fun neverLoadsAnotherRepertoiresLineBelowTheSharedTheory() = runTest {
    // italian-game and sicilian share 1. e4; the sicilian continues 1... c5 2. Nf3 d6.
    val database = CountingDatabaseQueryManager(InMemoryDatabaseQueryManager())
    val writer = Fixture(testTreeStore(database), testRepertoireTagStore(database))
    val afterE4 = after(rootKey, "e4")
    val afterE5 = after(rootKey, "e4", "e5")
    val afterC5 = after(rootKey, "e4", "c5")
    val afterNf3 = after(rootKey, "e4", "c5", "Nf3")
    val afterD6 = after(rootKey, "e4", "c5", "Nf3", "d6")
    writer.tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    writer.tagStore.tag(rootKey, afterE4, "italian-game")
    writer.tree.addMove(afterE4, "e5", afterE5, isGood = true, fromDepth = 1)
    writer.tagStore.tag(afterE4, afterE5, "italian-game")
    writer.tree.addMove(afterE4, "c5", afterC5, isGood = true, fromDepth = 1)
    writer.tagStore.tag(afterE4, afterC5, "sicilian")
    writer.tree.addMove(afterC5, "Nf3", afterNf3, isGood = true, fromDepth = 2)
    writer.tagStore.tag(afterC5, afterNf3, "sicilian")
    writer.tree.addMove(afterNf3, "d6", afterD6, isGood = true, fromDepth = 3)
    writer.tagStore.tag(afterNf3, afterD6, "sicilian")
    database.getPositionCalls.clear()
    val coldExporter =
      RepertoireMoveExporter(testTreeStore(database), testRepertoireTagStore(database))

    val result = coldExporter.export("italian-game") as RepertoireExportResult.Moves

    result.moves.map { it.move }.toSet() shouldBe setOf("e4", "e5")
    // afterC5 is loaded anyway, by the one ply neighbour prefetch of afterE4; only below it counts.
    database.getPositionCalls[afterNf3] shouldBe null
    database.getPositionCalls[afterD6] shouldBe null
  }

  @Test
  fun exportsEveryMoveOrderThatTransposesIntoATaggedEdge() = runTest {
    // 1. d4 c4 and 1. c4 d4 reach the same position; only the e6 reply after it is tagged.
    val fixture = store()
    val afterD4 = after(rootKey, "d4")
    val afterC4 = after(rootKey, "c4")
    val converged = after(rootKey, "d4", "c4")
    val afterE6 = after(rootKey, "d4", "c4", "e6")
    fixture.tree.addMove(rootKey, "d4", afterD4, isGood = true, fromDepth = 0)
    fixture.tree.addMove(rootKey, "c4", afterC4, isGood = true, fromDepth = 0)
    fixture.tree.addMove(afterD4, "c4", converged, isGood = true, fromDepth = 1)
    fixture.tree.addMove(afterC4, "d4", converged, isGood = true, fromDepth = 1)
    fixture.tree.addMove(converged, "e6", afterE6, isGood = true, fromDepth = 2)
    fixture.tagStore.tag(converged, afterE6, "queens-gambit")

    val result = exporter(fixture).export("queens-gambit") as RepertoireExportResult.Moves

    result.moves.size shouldBe 5
    result.maxPlyDepth shouldBe 2
  }

  @Test
  fun exportsALegacyOverDisambiguatedMoveInCanonicalSan() = runTest {
    val fixture = store()
    val afterNf3 = GameEngine().apply { playSanMove("Nf3") }.toPositionKey()
    fixture.tree.addMove(rootKey, "Ngf3", afterNf3, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterNf3, "reti")

    val result = exporter(fixture).export("reti") as RepertoireExportResult.Moves

    result.moves shouldBe listOf(move(rootKey, "Nf3", afterNf3))
  }

  @Test
  fun exportsATaggedMoveBackToAPositionAlreadyOnTheLine() = runTest {
    val fixture = store()
    val afterNf3 = after(rootKey, "Nf3")
    val afterNf6 = after(rootKey, "Nf3", "Nf6")
    fixture.tree.addMove(rootKey, "Nf3", afterNf3, isGood = true, fromDepth = 0)
    fixture.tagStore.tag(rootKey, afterNf3, "reti")
    fixture.tree.addMove(afterNf3, "Nf6", afterNf6, isGood = true, fromDepth = 1)
    fixture.tagStore.tag(afterNf3, afterNf6, "reti")
    fixture.tree.addMove(afterNf6, "Ng1", afterNf3, isGood = true, fromDepth = 2)
    fixture.tagStore.tag(afterNf6, afterNf3, "reti")

    val result = exporter(fixture).export("reti") as RepertoireExportResult.Moves

    result.moves.map { it.move }.toSet() shouldBe setOf("Nf3", "Nf6", "Ng1")
  }

  @Test
  fun neverExportsAnUnsavedExplorationMove() = runTest {
    // 1. Nf3 Nf6 2. d4 is saved and tagged; 1. d4 Nf6 2. Nf3 is only explored but transposes into
    // it.
    val fixture = store()
    val afterNf3 = after(rootKey, "Nf3")
    val afterNf6 = after(rootKey, "Nf3", "Nf6")
    val afterD4 = after(rootKey, "Nf3", "Nf6", "d4")
    val afterD5 = after(rootKey, "Nf3", "Nf6", "d4", "d5")
    val exploredD4 = after(rootKey, "d4")
    val exploredNf6 = after(rootKey, "d4", "Nf6")
    fixture.tree.addMove(rootKey, "Nf3", afterNf3, isGood = true, fromDepth = 0)
    fixture.tree.addMove(afterNf3, "Nf6", afterNf6, isGood = false, fromDepth = 1)
    fixture.tree.addMove(afterNf6, "d4", afterD4, isGood = true, fromDepth = 2)
    fixture.tree.addMove(afterD4, "d5", afterD5, isGood = false, fromDepth = 3)
    fixture.tagStore.tag(afterD4, afterD5, "london")
    fixture.tree.addMove(rootKey, "d4", exploredD4, isGood = null, fromDepth = 0)
    fixture.tree.addMove(exploredD4, "Nf6", exploredNf6, isGood = null, fromDepth = 1)
    fixture.tree.addMove(exploredNf6, "Nf3", afterD4, isGood = null, fromDepth = 2)

    val result = exporter(fixture).export("london") as RepertoireExportResult.Moves

    result.moves.map { it.move } shouldBe listOf("Nf3", "Nf6", "d4", "d5")
  }

  @Test
  fun dropsAnUntaggedMoveBackToAPositionAlreadyOnTheLine() = runTest {
    // Ng1 leads back to afterNf3, a position already on the line: a move cycle.
    val fixture = store()
    val afterNf3 = after(rootKey, "Nf3")
    val afterNf6 = after(rootKey, "Nf3", "Nf6")
    val afterD4 = after(rootKey, "Nf3", "Nf6", "d4")
    fixture.tree.addMove(rootKey, "Nf3", afterNf3, isGood = true, fromDepth = 0)
    fixture.tree.addMove(afterNf3, "Nf6", afterNf6, isGood = true, fromDepth = 1)
    fixture.tree.addMove(afterNf6, "Ng1", afterNf3, isGood = true, fromDepth = 2)
    fixture.tree.addMove(afterNf6, "d4", afterD4, isGood = true, fromDepth = 2)
    fixture.tagStore.tag(afterNf6, afterD4, "reti")

    val result = exporter(fixture).export("reti") as RepertoireExportResult.Moves

    result.moves.map { it.move }.toSet() shouldBe setOf("Nf3", "Nf6", "d4")
  }

  @Test
  fun exportThenImportReproducesTheSameMovesAndMarkings() = runTest {
    val authored =
      repertoireMovesOf("1. e4 e5 (1... c5 2. Nf3 d6) 2. Nf3 Nc6 *").mapIndexed { index, move ->
        move.copy(isGood = index % 3 != 0)
      }
    val author = TestDatabases.empty()
    val authorTree = testTreeStore(author)
    val authorTags = testRepertoireTagStore(author)
    RepertoireMoveImporter(authorTree, authorTags).import(authored, repertoireId = "mine")
    val exported =
      RepertoireMoveExporter(authorTree, authorTags).export("mine") as RepertoireExportResult.Moves
    val reader = TestDatabases.empty()
    val readerTree = testTreeStore(reader)
    val readerTags = testRepertoireTagStore(reader)

    RepertoireMoveImporter(readerTree, readerTags).import(exported.moves, repertoireId = "theirs")

    val reExported =
      RepertoireMoveExporter(readerTree, readerTags).export("theirs")
        as RepertoireExportResult.Moves
    reExported.moves shouldContainExactlyInAnyOrder authored
  }
}
