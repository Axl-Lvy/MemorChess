package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.training.NextDateCalculatorOnFailure
import proj.memorchess.axl.core.training.NextDateCalculatorOnSuccess
import proj.memorchess.axl.core.util.IReloader
import proj.memorchess.axl.ui.util.DateUtil

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
        NextDateCalculatorOnSuccess
      } else {
        NextDateCalculatorOnFailure
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
