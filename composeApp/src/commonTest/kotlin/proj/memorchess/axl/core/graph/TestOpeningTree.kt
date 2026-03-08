package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionKey

class TestOpeningTree {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

  @Test
  fun computeStateWithEmptyPreviousMoves() {
    val tree = OpeningTree()
    tree.put(startPos, PreviousAndNextMoves())
    assertEquals(NodeState.FIRST, tree.computeState(startPos, null))
  }

  @Test
  fun computeStateUnknownPosition() {
    val tree = OpeningTree()
    assertEquals(NodeState.UNKNOWN, tree.computeState(startPos, null))
  }

  @Test
  fun computeStateSavedGood() {
    val tree = OpeningTree()
    val moves = PreviousAndNextMoves()
    moves.addPreviousMove(DataMove(startPos, posA, "e4", isGood = true))
    tree.put(posA, moves)
    assertEquals(NodeState.SAVED_GOOD, tree.computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedBad() {
    val tree = OpeningTree()
    val moves = PreviousAndNextMoves()
    moves.addPreviousMove(DataMove(startPos, posA, "e4", isGood = false))
    tree.put(posA, moves)
    assertEquals(NodeState.SAVED_BAD, tree.computeState(posA, startPos))
  }

  @Test
  fun computeStateMixedGoodBadReturnsBadState() {
    val tree = OpeningTree()
    val moves = PreviousAndNextMoves()
    moves.addPreviousMove(DataMove(startPos, posA, "e4", isGood = true))
    moves.addPreviousMove(DataMove(posB, posA, "d4", isGood = false))
    tree.put(posA, moves)
    assertEquals(NodeState.BAD_STATE, tree.computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedGoodButUnknownMove() {
    val tree = OpeningTree()
    val moves = PreviousAndNextMoves()
    moves.addPreviousMove(DataMove(startPos, posA, "e4", isGood = true))
    tree.put(posA, moves)
    // arrivedFrom is different from the move's origin
    assertEquals(NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE, tree.computeState(posA, posB))
  }

  @Test
  fun countDescendantsLinearChain() {
    val tree = OpeningTree()
    val moveAB = DataMove(posA, posB, "e4")
    val moveBC = DataMove(posB, posC, "e5")

    val movesA = PreviousAndNextMoves()
    movesA.addNextMove(moveAB)
    tree.put(posA, movesA)

    val movesB = PreviousAndNextMoves()
    movesB.addPreviousMove(moveAB)
    movesB.addNextMove(moveBC)
    tree.put(posB, movesB)

    val movesC = PreviousAndNextMoves()
    movesC.addPreviousMove(moveBC)
    tree.put(posC, movesC)

    assertEquals(3, tree.countDescendants(posA))
  }

  @Test
  fun countDescendantsStopsAtConvergence() {
    val tree = OpeningTree()
    val posD = PositionKey("posD b K")
    val moveAB = DataMove(posA, posB, "e4")
    val moveBC = DataMove(posB, posC, "e5")
    val moveDC = DataMove(posD, posC, "d4")

    val movesA = PreviousAndNextMoves()
    movesA.addNextMove(moveAB)
    tree.put(posA, movesA)

    val movesB = PreviousAndNextMoves()
    movesB.addPreviousMove(moveAB)
    movesB.addNextMove(moveBC)
    tree.put(posB, movesB)

    // posC has two parents
    val movesC = PreviousAndNextMoves()
    movesC.addPreviousMove(moveBC)
    movesC.addPreviousMove(moveDC)
    tree.put(posC, movesC)

    // When counting from posB, posC has another parent so should not be counted
    assertEquals(1, tree.countDescendants(posB, moveAB))
  }

  @Test
  fun getOrCreateUpdatesDepthToMinimum() {
    val tree = OpeningTree()
    val first = tree.getOrCreate(posA, 5)
    assertEquals(5, first.depth)
    val second = tree.getOrCreate(posA, 2)
    assertEquals(2, second.depth)
    // Higher depth should not update
    tree.getOrCreate(posA, 10)
    assertEquals(2, second.depth)
  }

  @Test
  fun clearRemovesAllPositions() {
    val tree = OpeningTree()
    tree.put(posA, PreviousAndNextMoves())
    tree.clear()
    assertNull(tree.get(posA))
  }
}
