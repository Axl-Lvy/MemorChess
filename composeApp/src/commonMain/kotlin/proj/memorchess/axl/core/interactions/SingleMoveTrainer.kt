package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.MutableState
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.INextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.util.IReloader

/**
 * Trainer based on a node.
 *
 * @property node The node to train on.
 */
class SingleMoveTrainer(private var node: StoredNode, val isCorrect: MutableState<Boolean?>) :
  AInteractionsManager(Game(node.positionKey)) {

  override suspend fun afterPlayMove(move: String, reloader: IReloader) {
    val correspondingStoredMove =
      node.previousAndNextMoves.nextMoves.values.firstOrNull { it.move == move }
    isCorrect.value = correspondingStoredMove != null && correspondingStoredMove.isGood == true
    saveNode()
    block()
    reloader.reload()
  }

  private suspend fun saveNode() {
    val isCorrectSnapshot = isCorrect.value
    checkNotNull(isCorrectSnapshot)
    val calculator =
      if (isCorrectSnapshot) {
        INextDateCalculator.SUCCESS
      } else {
        INextDateCalculator.FAILURE
      }

    val nextTrainingDate = calculator.calculateNextDate(node.previousAndNextTrainingDate)

    val storedNode =
      StoredNode(
        positionKey = node.positionKey,
        previousAndNextMoves = node.previousAndNextMoves,
        previousAndNextTrainingDate = PreviousAndNextDate(DateUtil.today(), nextTrainingDate),
      )
    storedNode.save()
    NodeManager.cacheNode(storedNode)
  }
}
