package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.MutablePreviousAndNextMoves
import proj.memorchess.axl.core.graph.NavigationHistory
import proj.memorchess.axl.core.graph.nodes.NodeManager

/** LinesExplorer is an interaction manager that allows exploring the stored lines. */
open class LinesExplorer(position: PositionKey? = null, protected val nodeManager: NodeManager) :
  InteractionsManager(if (position == null) GameEngine() else GameEngine(position)) {

  private val startPosition = position ?: PositionKey.START_POSITION

  init {
    nodeManager.ensurePosition(startPosition, 0)
  }

  protected val navigation = NavigationHistory(startPosition)

  var state by mutableStateOf(nodeManager.computeState(startPosition, null))

  /** Moves back in the exploration tree to the previous node. */
  fun back() {
    val result = navigation.back()
    if (result != null) {
      engine = GameEngine(navigation.current)
    } else {
      val moves = nodeManager.getMoves(navigation.current)
      val previousMove = moves?.previousMoves?.values?.firstOrNull()
      if (previousMove == null) {
        toastRenderer.info("No previous move.")
        return
      }
      navigation.reset(previousMove.origin)
      engine = GameEngine(navigation.current)
    }
    state = nodeManager.computeState(navigation.current, navigation.arrivedVia?.origin)
    callCallBacks(false)
  }

  /** Moves forward in the exploration tree to the next child node. */
  fun forward() {
    val result = navigation.forward()
    if (result != null) {
      val (move, _) = result
      engine.playSanMove(move.move)
      state = nodeManager.computeState(navigation.current, navigation.arrivedVia?.origin)
      callCallBacks(false)
    } else {
      toastRenderer.info("No next move.")
    }
  }

  /**
   * Get the list of moves that can be played from the current position.
   *
   * @return A list of moves that can be played from the current position.
   */
  fun getNextMoves(): List<String> {
    val moves = nodeManager.getMoves(navigation.current) ?: return emptyList()
    return moves.nextMoves.filter { it.value.isGood != null }.keys.sorted()
  }

  /** Resets the LinesExplorer to the root node. */
  fun reset() {
    val resetPosition = PositionKey.START_POSITION
    nodeManager.ensurePosition(resetPosition, 0)
    navigation.reset(resetPosition)
    state = nodeManager.computeState(resetPosition, null)
    super.reset(resetPosition)
  }

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val destination = engine.toPositionKey()
    val dataMove = nodeManager.registerMove(origin, destination, move, navigation.depth)
    navigation.push(dataMove, destination)
    state = nodeManager.computeState(navigation.current, navigation.arrivedVia?.origin)
    callCallBacks()
  }

  /** Saves the current node as coming from a good move. */
  suspend fun save() {
    val currentMoves = nodeManager.getMoves(navigation.current) ?: return
    // Mark current position's previous moves as good
    currentMoves.setPreviousMovesAsGood()
    propagateIsGoodToOrigins(currentMoves)
    nodeManager.saveNode(
      DataNode(
        navigation.current,
        currentMoves.filterValidMoves(),
        PreviousAndNextDate.dummyToday(),
        nodeManager.getDepth(navigation.current),
      )
    )

    // Walk back through navigation path alternating bad/good
    val path = navigation.getBackPath().reversed()
    for ((index, entry) in path.withIndex()) {
      val (position, _) = entry
      val moves = nodeManager.getMoves(position) ?: continue
      if (index % 2 == 0) {
        moves.setPreviousMovesAsBadIfNotMarked()
      } else {
        moves.setPreviousMovesAsGood()
      }
      propagateIsGoodToOrigins(moves)
      nodeManager.saveNode(
        DataNode(
          position,
          moves.filterValidMoves(),
          PreviousAndNextDate.dummyToday(),
          nodeManager.getDepth(position),
        )
      )
    }

    state = nodeManager.computeState(navigation.current, navigation.arrivedVia?.origin)
    toastRenderer.info("Saved")
  }

  /**
   * After updating isGood on previous moves, propagate the change to the corresponding next move
   * entries in origin positions.
   */
  private fun propagateIsGoodToOrigins(moves: MutablePreviousAndNextMoves) {
    moves.previousMoves.values.forEach { move ->
      nodeManager.getMoves(move.origin)?.updateNextMove(move)
    }
  }

  /** Deletes the current node's children and reloads the explorer. */
  suspend fun delete() {
    val currentMoves = nodeManager.getMoves(navigation.current)
    // Delete children recursively
    currentMoves?.nextMoves?.values?.toList()?.forEach { move ->
      deleteFromPrevious(move.destination, move)
    }
    // Clear this node's next moves
    nodeManager.clearNextMoves(navigation.current)
    nodeManager.deletePosition(navigation.current)

    navigation.clearForward()
    state = nodeManager.computeState(navigation.current, navigation.arrivedVia?.origin)
    toastRenderer.info("Deleted")
    callCallBacks()
  }

  private suspend fun deleteFromPrevious(positionKey: PositionKey, viaMove: DataMove) {
    nodeManager.clearPreviousMove(positionKey, viaMove)
    val moves = nodeManager.getMoves(positionKey)
    if (moves != null && moves.previousMoves.isEmpty()) {
      moves.nextMoves.values.toList().forEach { move -> deleteFromPrevious(move.destination, move) }
      nodeManager.clearNextMoves(positionKey)
      moves.clearNextMoves()
      nodeManager.deletePosition(positionKey)
    }
  }

  /**
   * Calculates the number of nodes that would be deleted if the current node is deleted.
   *
   * @return The number of nodes that would be deleted.
   */
  fun calculateNumberOfNodeToDelete(): Int {
    return nodeManager.countDescendants(navigation.current)
  }
}
