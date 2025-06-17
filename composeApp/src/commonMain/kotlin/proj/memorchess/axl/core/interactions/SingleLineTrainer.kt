package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.training.INextDateCalculator
import proj.memorchess.axl.core.util.IReloader
import proj.memorchess.axl.ui.util.DateUtil

/**
 * Trainer based on a node.
 *
 * @property node The node to train on.
 */
class SingleLineTrainer(private var node: StoredNode) :
  AInteractionsManager(Game(node.positionKey)) {

  private var isCorrect: Boolean = true

  override suspend fun afterPlayMove(move: String, reloader: IReloader) {
    val correspondingStoredMove = node.nextMoves.firstOrNull { it.move == move }
    this.isCorrect = correspondingStoredMove != null && correspondingStoredMove.isGood == true
    saveNode()
    reloader.reload()
  }

  private suspend fun saveNode() {
    val calculator =
      if (isCorrect) {
        INextDateCalculator.SUCCESS
      } else {
        INextDateCalculator.FAILURE
      }

    val nextTrainedDate = calculator.calculateNextDate(node.lastTrainedDate)

    StoredNode(
        positionKey = node.positionKey,
        previousMoves = node.previousMoves,
        nextMoves = node.nextMoves,
        lastTrainedDate = DateUtil.today(),
        nextTrainedDate = nextTrainedDate,
      )
      .save()
  }
}
