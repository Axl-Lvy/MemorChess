package proj.memorchess.axl.core.interactions

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
class SingleMoveTrainer(private var node: StoredNode, val setIsCorrect: (Boolean) -> Unit) :
  AInteractionsManager(Game(node.positionKey)) {

  private var isCorrect: Boolean = true

  override suspend fun afterPlayMove(move: String, reloader: IReloader) {
    val correspondingStoredMove =
      node.previousAndNextMoves.nextMoves.values.firstOrNull { it.move == move }
    isCorrect = correspondingStoredMove != null && correspondingStoredMove.isGood == true
    saveNode()
    block()
    setIsCorrect(isCorrect)
    reloader.reload()
  }

  private suspend fun saveNode() {
    val calculator =
      if (isCorrect) {
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
    NodeManager.cacheNode(storedNode)
    storedNode.save()
  }
}
