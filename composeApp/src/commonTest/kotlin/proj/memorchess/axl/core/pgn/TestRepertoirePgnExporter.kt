package proj.memorchess.axl.core.pgn

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.test_util.CountingDatabaseQueryManager
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

private val rootKey = GameEngine().toPositionKey()

class TestRepertoirePgnExporter {

  /** A [TreeStore] and a [RepertoireTagStore] over one shared in memory database. */
  private data class Fixture(val tree: TreeStore, val tagStore: RepertoireTagStore)

  private fun store(): Fixture {
    val database = InMemoryDatabaseQueryManager()
    return Fixture(testTreeStore(database), testRepertoireTagStore(database))
  }

  private fun exporter(fixture: Fixture) = RepertoirePgnExporter(fixture.tree, fixture.tagStore)

  /** A throwaway position key distinct per move sequence, not a real replayed FEN. */
  private fun after(from: PositionKey, vararg moves: String): PositionKey =
    PositionKey("${from.value}|${moves.joinToString("|")}")

  @Test
  fun exportOfARepertoireWithNoTaggedEdgesIsEmpty() = runTest {
    val exporter = exporter(store())

    exporter.export("italian-game") shouldBe RepertoireExportResult.Empty
  }

  @Test
  fun exportsASingleTaggedLine() = runTest {
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    tree.addMove(rootKey, "e4", after(rootKey, "e4"), isGood = true, fromDepth = 0)
    tagStore.tag(rootKey, after(rootKey, "e4"), "italian-game")
    tree.addMove(
      after(rootKey, "e4"),
      "e5",
      after(rootKey, "e4", "e5"),
      isGood = true,
      fromDepth = 1,
    )
    tagStore.tag(after(rootKey, "e4"), after(rootKey, "e4", "e5"), "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn

    result.moveCount shouldBe 2
    result.maxPlyDepth shouldBe 2
    PgnParser.parse(result.text).single().moves.single().san shouldBe "e4"
    PgnParser.parse(result.text).single().moves.single().children.single().san shouldBe "e5"
  }

  @Test
  fun exportsBranchingVariationsAsRav() = runTest {
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tagStore.tag(rootKey, afterE4, "italian-game")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4E5, "italian-game")
    val afterE4C5 = after(rootKey, "e4", "c5")
    tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4C5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.map { it.san }.toSet() shouldBe setOf("e5", "c5")
  }

  @Test
  fun includesAnUntaggedConnectiveEdgeOnTheWayToATaggedOne() = runTest {
    // e4 is common theory, played but never tagged; e4 e5 is the tagged repertoire line.
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4E5, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.single().san shouldBe "e5"
  }

  @Test
  fun roundTripsThroughPgnParserPreservingTreeShape() = runTest {
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tagStore.tag(rootKey, afterE4, "italian-game")

    val exported = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn
    val reparsed = PgnParser.parse(exported.text)

    reparsed.single().moves.map { it.san } shouldBe listOf("e4")
  }

  @Test
  fun countsOnlyDistinctEdgesNotEveryTimeATranspositionRevisitsOne() = runTest {
    // Two move orders converge on the same afterE4E5 position, then both continue into the same
    // tagged c3 continuation: the continuation is one distinct edge, even though it appears in the
    // exported text under two different parents.
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tagStore.tag(rootKey, afterE4, "italian-game")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4E5, "italian-game")
    val afterC3 = after(rootKey, "e4", "e5", "c3")
    tree.addMove(afterE4E5, "c3", afterC3, isGood = true, fromDepth = 2)
    tagStore.tag(afterE4E5, afterC3, "italian-game")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn

    result.moveCount shouldBe 3
    result.maxPlyDepth shouldBe 3
  }

  @Test
  fun excludesASoftDeletedEdgeEvenWhenItWasTagged() = runTest {
    val fixture = store()
    val tree = fixture.tree
    val tagStore = fixture.tagStore
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tagStore.tag(rootKey, afterE4, "italian-game")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4E5, "italian-game")
    val afterE4C5 = after(rootKey, "e4", "c5")
    tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    tagStore.tag(afterE4, afterE4C5, "italian-game")
    tree.deleteMove(afterE4, "c5")

    val result = exporter(fixture).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.map { it.san } shouldBe listOf("e5")
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
      RepertoirePgnExporter(testTreeStore(database), testRepertoireTagStore(database))

    val result = coldExporter.export("italian-game") as RepertoireExportResult.Pgn

    PgnParser.parse(result.text).single().moves.single().children.map { it.san } shouldBe
      listOf("e5")
    // afterC5 is loaded anyway, by the one ply neighbour prefetch of afterE4; only below it counts.
    database.getPositionCalls[afterNf3] shouldBe null
    database.getPositionCalls[afterD6] shouldBe null
  }

  @Test
  fun exportsEveryMoveOrderThatTransposesIntoATaggedEdge() = runTest {
    // 1. d4 c4 and 1. c4 d4 reach the same position; only the e6 reply after it is tagged.
    val fixture = store()
    val tree = fixture.tree
    val afterD4 = after(rootKey, "d4")
    val afterC4 = after(rootKey, "c4")
    val converged = after(rootKey, "d4", "c4")
    val afterE6 = after(rootKey, "d4", "c4", "e6")
    tree.addMove(rootKey, "d4", afterD4, isGood = true, fromDepth = 0)
    tree.addMove(rootKey, "c4", afterC4, isGood = true, fromDepth = 0)
    tree.addMove(afterD4, "c4", converged, isGood = true, fromDepth = 1)
    tree.addMove(afterC4, "d4", converged, isGood = true, fromDepth = 1)
    tree.addMove(converged, "e6", afterE6, isGood = true, fromDepth = 2)
    fixture.tagStore.tag(converged, afterE6, "queens-gambit")

    val result = exporter(fixture).export("queens-gambit") as RepertoireExportResult.Pgn

    val firstMoves = PgnParser.parse(result.text).single().moves
    firstMoves.map { it.san }.toSet() shouldBe setOf("d4", "c4")
    firstMoves.map { it.children.single().children.single().san } shouldBe listOf("e6", "e6")
    result.moveCount shouldBe 5
    result.maxPlyDepth shouldBe 3
  }

  @Test
  fun stopsAtAMoveThatReturnsToAPositionAlreadyOnTheLine() = runTest {
    // Ng1 leads back to afterNf3, a position already on the line: a move cycle.
    val fixture = store()
    val tree = fixture.tree
    val afterNf3 = after(rootKey, "Nf3")
    val afterNf6 = after(rootKey, "Nf3", "Nf6")
    val afterD4 = after(rootKey, "Nf3", "Nf6", "d4")
    tree.addMove(rootKey, "Nf3", afterNf3, isGood = true, fromDepth = 0)
    tree.addMove(afterNf3, "Nf6", afterNf6, isGood = true, fromDepth = 1)
    tree.addMove(afterNf6, "Ng1", afterNf3, isGood = true, fromDepth = 2)
    tree.addMove(afterNf6, "d4", afterD4, isGood = true, fromDepth = 2)
    fixture.tagStore.tag(afterNf6, afterD4, "reti")

    val result = exporter(fixture).export("reti") as RepertoireExportResult.Pgn

    val nf6 = PgnParser.parse(result.text).single().moves.single().children.single()
    nf6.children.map { it.san } shouldBe listOf("d4")
    result.moveCount shouldBe 3
  }
}
