package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.NextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager

/**
 * Trainer based on a node.
 *
 * @property node The node to train on.
 * @property callBackOnCorrect Callback to call after the move is played. The input move is null
 *   when the played move is incorrect.
 */
class SingleMoveTrainer(
  private var node: StoredNode,
  val callBackOnCorrect: (StoredMove?) -> Unit,
) : InteractionsManager(Game(node.positionIdentifier)) {

  private var isCorrect: Boolean = true

  override suspend fun afterPlayMove(move: String) {
    val correspondingStoredMove =
      node.previousAndNextMoves.nextMoves.values.firstOrNull { it.move == move }
    isCorrect = correspondingStoredMove != null && correspondingStoredMove.isGood == true
    block()
    callBackOnCorrect(if (isCorrect) correspondingStoredMove else null)
    saveNode()
    callCallBacks()
  }

  fun updateNode(newNode: StoredNode) {
    if (newNode.positionIdentifier != node.positionIdentifier) {
      node = newNode
      game = Game(node.positionIdentifier)
      isCorrect = true
      unblock()
      callCallBacks()
    }
  }

  private suspend fun saveNode() {
    val calculator =
      if (isCorrect) {
        NextDateCalculator.SUCCESS
      } else {
        NextDateCalculator.FAILURE
      }

    val nextTrainingDate = calculator.calculateNextDate(node.previousAndNextTrainingDate)

    val storedNode =
      StoredNode(
        positionIdentifier = node.positionIdentifier,
        previousAndNextMoves = node.previousAndNextMoves,
        previousAndNextTrainingDate = PreviousAndNextDate(DateUtil.today(), nextTrainingDate),
      )
    NodeManager.cacheNode(storedNode)
    storedNode.save()
  }
}
