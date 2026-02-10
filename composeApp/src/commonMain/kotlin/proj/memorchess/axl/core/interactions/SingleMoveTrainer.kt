package proj.memorchess.axl.core.interactions

import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.NextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.graph.nodes.PersonalNode
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/**
 * Trainer based on a position and its moves.
 *
 * @property position The position to train on.
 * @property moves The moves associated with this position.
 * @property callBackOnCorrect Callback to call after the move is played. The input move is null
 *   when the played move is incorrect.
 */
class SingleMoveTrainer(
  private var position: DataPosition,
  private var moves: PreviousAndNextMoves,
  val callBackOnCorrect: (DataMove?) -> Unit,
) : InteractionsManager(Game(position.positionIdentifier)) {

  private val nodeManager: NodeManager<PersonalNode> by inject()
  private val database: DatabaseQueryManager by inject()

  private var isCorrect: Boolean = true

  override suspend fun afterPlayMove(move: String) {
    val correspondingStoredMove =
      moves.nextMoves.values.firstOrNull { it.move == move }
    isCorrect = correspondingStoredMove != null && correspondingStoredMove.isGood == true
    block()
    callBackOnCorrect(if (isCorrect) correspondingStoredMove else null)
    savePosition()
    callCallBacks()
  }

  fun updatePosition(newPosition: DataPosition, newMoves: PreviousAndNextMoves) {
    if (
      newPosition.positionIdentifier != position.positionIdentifier ||
        newPosition.positionIdentifier != game.position.createIdentifier()
    ) {
      position = newPosition
      moves = newMoves
      game = Game(position.positionIdentifier)
      isCorrect = true
      unblock()
      callCallBacks()
    }
  }

  private suspend fun savePosition() {
    val calculator =
      if (isCorrect) {
        NextDateCalculator.SUCCESS
      } else {
        NextDateCalculator.FAILURE
      }

    val nextTrainingDate = calculator.calculateNextDate(position.previousAndNextTrainingDate)

    val updatedPosition =
      DataPosition(
        positionIdentifier = position.positionIdentifier,
        depth = position.depth,
        previousAndNextTrainingDate = PreviousAndNextDate(DateUtil.today(), nextTrainingDate),
      )
    val allMoves = (moves.previousMoves.values + moves.nextMoves.values).toList()
    nodeManager.cachePosition(updatedPosition)
    database.insertMoves(allMoves, listOf(updatedPosition))
  }
}
