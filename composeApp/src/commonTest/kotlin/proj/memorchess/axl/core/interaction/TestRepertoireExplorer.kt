package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.interactions.RepertoireExplorer
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnParser
import proj.memorchess.axl.test_util.TestWithKoin

/**
 * Exercises the read-only repertoire navigator: only PGN moves are playable, off-book moves are
 * rejected, navigation walks the transient graph, and transpositions are shared.
 */
class TestRepertoireExplorer : TestWithKoin() {

  private suspend fun explorerOf(vararg pgns: String): RepertoireExplorer {
    val games: List<PgnGame> = pgns.flatMap { PgnParser.parse(it) }
    return RepertoireExplorer.build(games)
  }

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  private suspend fun clickMove(explorer: RepertoireExplorer, from: String, to: String) {
    explorer.clickOnTile(Pair(from[1] - '1', from[0] - 'a'))
    explorer.clickOnTile(Pair(to[1] - '1', to[0] - 'a'))
  }

  @Test
  fun nextMovesAtRootAreTheBookFirstMoves() = test {
    val explorer = explorerOf("1. e4 e5 2. Nf3 *")
    assertEquals(listOf("e4"), explorer.getNextMoves())
  }

  @Test
  fun playingABookMoveAdvances() = test {
    val explorer = explorerOf("1. e4 e5 2. Nf3 *")
    explorer.playMove("e4")
    assertEquals(keyAfter("e4"), explorer.engine.toPositionKey())
    assertEquals(listOf("e5"), explorer.getNextMoves())
  }

  @Test
  fun playingAnOffBookMoveIsRejected() = test {
    val explorer = explorerOf("1. e4 e5 *")
    // d4 is a legal chess move but is not part of the repertoire.
    explorer.playMove("d4")
    assertEquals(PositionKey.START_POSITION, explorer.engine.toPositionKey())
    assertEquals(listOf("e4"), explorer.getNextMoves())
  }

  @Test
  fun clickingAnOffBookMoveIsRejected() = test {
    val explorer = explorerOf("1. e4 e5 *")
    // Drag the d-pawn d2-d4: legal, but off-book, so the board must snap back.
    clickMove(explorer, "d2", "d4")
    assertEquals(PositionKey.START_POSITION, explorer.engine.toPositionKey())
  }

  @Test
  fun clickingABookMoveAdvances() = test {
    val explorer = explorerOf("1. e4 e5 *")
    clickMove(explorer, "e2", "e4")
    assertEquals(keyAfter("e4"), explorer.engine.toPositionKey())
  }

  @Test
  fun backAndForwardWalkTheLine() = test {
    val explorer = explorerOf("1. e4 e5 2. Nf3 *")
    explorer.playMove("e4")
    explorer.playMove("e5")
    assertEquals(keyAfter("e4", "e5"), explorer.engine.toPositionKey())
    explorer.back()
    assertEquals(keyAfter("e4"), explorer.engine.toPositionKey())
    explorer.forward()
    assertEquals(keyAfter("e4", "e5"), explorer.engine.toPositionKey())
  }

  @Test
  fun resetReturnsToTheRoot() = test {
    val explorer = explorerOf("1. e4 e5 *")
    explorer.playMove("e4")
    explorer.reset()
    assertEquals(PositionKey.START_POSITION, explorer.engine.toPositionKey())
    assertEquals(listOf("e4"), explorer.getNextMoves())
  }

  @Test
  fun branchesOfferEveryBookContinuation() = test {
    // One position, two book replies.
    val explorer = explorerOf("1. e4 e5 2. Nf3 (2. Bc4) *")
    explorer.playMove("e4")
    explorer.playMove("e5")
    assertEquals(listOf("Bc4", "Nf3"), explorer.getNextMoves())
  }

  @Test
  fun transposedLinesShareTheSamePosition() = test {
    // Two move orders reaching the same position via different first moves.
    val explorer = explorerOf("1. d4 d5 2. Nf3 *", "1. Nf3 d5 2. d4 *")
    assertEquals(listOf("Nf3", "d4"), explorer.getNextMoves())

    explorer.playMove("d4")
    explorer.playMove("d5")
    explorer.playMove("Nf3")
    val viaDFirst = explorer.engine.toPositionKey()

    explorer.reset()
    explorer.playMove("Nf3")
    explorer.playMove("d5")
    explorer.playMove("d4")
    val viaKnightFirst = explorer.engine.toPositionKey()

    assertEquals(viaDFirst, viaKnightFirst)
    assertEquals(keyAfter("d4", "d5", "Nf3"), viaDFirst)
  }

  @Test
  fun emptyRepertoireHasNoMoves() = test {
    val explorer = explorerOf("*")
    assertEquals(PositionKey.START_POSITION, explorer.engine.toPositionKey())
    assertTrue(explorer.getNextMoves().isEmpty())
  }

  @Test
  fun singleMoveRepertoireEndsAfterThatMove() = test {
    val explorer = explorerOf("1. e4 *")
    assertEquals(listOf("e4"), explorer.getNextMoves())
    explorer.playMove("e4")
    assertTrue(explorer.getNextMoves().isEmpty())
  }
}
