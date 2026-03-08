package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.NavigationHistory
import proj.memorchess.axl.core.graph.TreeRepository
import proj.memorchess.axl.core.graph.nodes.NodeManager

/** LinesExplorer is an interaction manager that allows exploring the stored lines. */
open class LinesExplorer(
  position: PositionKey? = null,
  protected val nodeManager: NodeManager,
  protected val treeRepository: TreeRepository,
) : InteractionsManager(if (position == null) GameEngine() else GameEngine(position)) {

  private val startPosition = position ?: PositionKey.START_POSITION

  init {
    nodeManager.openingTree.getOrCreate(startPosition, 0)
  }

  protected val navigation = NavigationHistory(startPosition)

  var state by mutableStateOf(nodeManager.openingTree.computeState(startPosition, null))

  /** Moves back in the exploration tree to the previous node. */
  fun back() {
    val result = navigation.back()
    if (result != null) {
      engine = GameEngine(navigation.current)
    } else {
      val moves = nodeManager.openingTree.get(navigation.current)
      val previousMove = moves?.previousMoves?.values?.firstOrNull()
      if (previousMove == null) {
        toastRenderer.info("No previous move.")
        return
      }
      navigation.reset(previousMove.origin)
      engine = GameEngine(navigation.current)
    }
    state = nodeManager.openingTree.computeState(navigation.current, navigation.arrivedVia?.origin)
    callCallBacks(false)
  }

  /** Moves forward in the exploration tree to the next child node. */
  fun forward() {
    val result = navigation.forward()
    if (result != null) {
      val (move, _) = result
      engine.playSanMove(move.move)
      state =
        nodeManager.openingTree.computeState(navigation.current, navigation.arrivedVia?.origin)
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
    val moves = nodeManager.openingTree.get(navigation.current) ?: return emptyList()
    return moves.nextMoves.filter { it.value.isGood != null }.keys.sorted()
  }

  /** Resets the LinesExplorer to the root node. */
  fun reset() {
    val resetPosition = PositionKey.START_POSITION
    nodeManager.openingTree.getOrCreate(resetPosition, 0)
    navigation.reset(resetPosition)
    state = nodeManager.openingTree.computeState(resetPosition, null)
    super.reset(resetPosition)
  }

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val destination = engine.toPositionKey()
    val dataMove = nodeManager.registerMove(origin, destination, move, navigation.depth)
    navigation.push(dataMove, destination)
    state = nodeManager.openingTree.computeState(navigation.current, navigation.arrivedVia?.origin)
    callCallBacks()
  }

  /** Saves the current node as coming from a good move. */
  suspend fun save() {
    val currentMoves = nodeManager.openingTree.get(navigation.current) ?: return
    // Mark current position's previous moves as good
    currentMoves.setPreviousMovesAsGood()
    treeRepository.saveNode(
      DataNode(
        navigation.current,
        currentMoves.filterValidMoves(),
        PreviousAndNextDate.dummyToday(),
      )
    )

    // Walk back through navigation path alternating bad/good
    val path = navigation.getBackPath().reversed()
    for ((index, entry) in path.withIndex()) {
      val (position, _) = entry
      val moves = nodeManager.openingTree.get(position) ?: continue
      if (index % 2 == 0) {
        moves.setPreviousMovesAsBadIfNotMarked()
      } else {
        moves.setPreviousMovesAsGood()
      }
      treeRepository.saveNode(
        DataNode(position, moves.filterValidMoves(), PreviousAndNextDate.dummyToday())
      )
    }

    state = nodeManager.openingTree.computeState(navigation.current, navigation.arrivedVia?.origin)
    toastRenderer.info("Saved")
  }

  /** Deletes the current node's children and reloads the explorer. */
  suspend fun delete() {
    val currentMoves = nodeManager.openingTree.get(navigation.current)
    // Delete children recursively
    currentMoves?.nextMoves?.values?.toList()?.forEach { move ->
      deleteFromPrevious(move.destination, move)
    }
    // Clear this node's next moves
    nodeManager.clearNextMoves(navigation.current)
    treeRepository.deletePosition(navigation.current)

    navigation.clearForward()
    state = nodeManager.openingTree.computeState(navigation.current, navigation.arrivedVia?.origin)
    toastRenderer.info("Deleted")
    callCallBacks()
  }

  private suspend fun deleteFromPrevious(positionKey: PositionKey, viaMove: DataMove) {
    nodeManager.clearPreviousMove(positionKey, viaMove)
    val moves = nodeManager.openingTree.get(positionKey)
    if (moves != null && moves.previousMoves.isEmpty()) {
      moves.nextMoves.values.toList().forEach { move -> deleteFromPrevious(move.destination, move) }
      nodeManager.clearNextMoves(positionKey)
      moves.nextMoves.clear()
      treeRepository.deletePosition(positionKey)
    }
  }

  /**
   * Calculates the number of nodes that would be deleted if the current node is deleted.
   *
   * @return The number of nodes that would be deleted.
   */
  fun calculateNumberOfNodeToDelete(): Int {
    return nodeManager.openingTree.countDescendants(navigation.current)
  }
}
