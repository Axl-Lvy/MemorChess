package proj.memorchess.axl.core.pgn

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.test_util.testTreeStore

private val rootKey = GameEngine().toPositionKey()

class TestRepertoirePgnExporter {

  private fun store() = testTreeStore(InMemoryDatabaseQueryManager())

  private fun exporter(tree: TreeStore) = RepertoirePgnExporter(tree)

  /** A throwaway position key distinct per move sequence, not a real replayed FEN. */
  private fun after(from: PositionKey, vararg moves: String): PositionKey =
    PositionKey("${from.value}|${moves.joinToString("|")}")

  @Test
  fun exportOfARepertoireWithNoTaggedEdgesIsEmpty() = runTest {
    val exporter = RepertoirePgnExporter(store())

    exporter.export("italian-game") shouldBe RepertoireExportResult.Empty
  }

  @Test
  fun exportsASingleTaggedLine() = runTest {
    val tree = store()
    tree.addMove(rootKey, "e4", after(rootKey, "e4"), isGood = true, fromDepth = 0)
    tree.tagEdge(rootKey, after(rootKey, "e4"), "italian-game")
    tree.addMove(
      after(rootKey, "e4"),
      "e5",
      after(rootKey, "e4", "e5"),
      isGood = true,
      fromDepth = 1,
    )
    tree.tagEdge(after(rootKey, "e4"), after(rootKey, "e4", "e5"), "italian-game")

    val result = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn

    result.moveCount shouldBe 2
    result.maxPlyDepth shouldBe 2
    PgnParser.parse(result.text).single().moves.single().san shouldBe "e4"
    PgnParser.parse(result.text).single().moves.single().children.single().san shouldBe "e5"
  }

  @Test
  fun exportsBranchingVariationsAsRav() = runTest {
    val tree = store()
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tree.tagEdge(rootKey, afterE4, "italian-game")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4E5, "italian-game")
    val afterE4C5 = after(rootKey, "e4", "c5")
    tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4C5, "italian-game")

    val result = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.map { it.san }.toSet() shouldBe setOf("e5", "c5")
  }

  @Test
  fun includesAnUntaggedConnectiveEdgeOnTheWayToATaggedOne() = runTest {
    // e4 is common theory, played but never tagged; e4 e5 is the tagged repertoire line.
    val tree = store()
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4E5, "italian-game")

    val result = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.single().san shouldBe "e5"
  }

  @Test
  fun roundTripsThroughPgnParserPreservingTreeShape() = runTest {
    val tree = store()
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tree.tagEdge(rootKey, afterE4, "italian-game")

    val exported = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn
    val reparsed = PgnParser.parse(exported.text)

    reparsed.single().moves.map { it.san } shouldBe listOf("e4")
  }

  @Test
  fun countsOnlyDistinctEdgesNotEveryTimeATranspositionRevisitsOne() = runTest {
    // Two move orders converge on the same afterE4E5 position, then both continue into the same
    // tagged c3 continuation: the continuation is one distinct edge, even though it appears in the
    // exported text under two different parents.
    val tree = store()
    val afterE4 = after(rootKey, "e4")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tree.tagEdge(rootKey, afterE4, "italian-game")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4E5, "italian-game")
    val afterC3 = after(rootKey, "e4", "e5", "c3")
    tree.addMove(afterE4E5, "c3", afterC3, isGood = true, fromDepth = 2)
    tree.tagEdge(afterE4E5, afterC3, "italian-game")

    val result = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn

    result.moveCount shouldBe 3
    result.maxPlyDepth shouldBe 3
  }

  @Test
  fun excludesASoftDeletedEdgeEvenWhenItWasTagged() = runTest {
    val tree = store()
    val afterE4 = after(rootKey, "e4")
    tree.addMove(rootKey, "e4", afterE4, isGood = true, fromDepth = 0)
    tree.tagEdge(rootKey, afterE4, "italian-game")
    val afterE4E5 = after(rootKey, "e4", "e5")
    tree.addMove(afterE4, "e5", afterE4E5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4E5, "italian-game")
    val afterE4C5 = after(rootKey, "e4", "c5")
    tree.addMove(afterE4, "c5", afterE4C5, isGood = true, fromDepth = 1)
    tree.tagEdge(afterE4, afterE4C5, "italian-game")
    tree.deleteMove(afterE4, "c5", DeleteMode.SOFT)

    val result = exporter(tree).export("italian-game") as RepertoireExportResult.Pgn

    val parsed = PgnParser.parse(result.text).single().moves.single()
    parsed.san shouldBe "e4"
    parsed.children.map { it.san } shouldBe listOf("e5")
  }
}
