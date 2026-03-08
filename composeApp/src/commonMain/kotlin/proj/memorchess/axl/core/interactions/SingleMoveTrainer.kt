package proj.memorchess.axl.core.interactions

import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.NextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.nodes.NodeManager

/**
 * Trainer based on a node.
 *
 * @property node The node to train on.
 * @property callBackOnCorrect Callback to call after the move is played. The input move is null
 *   when the played move is incorrect.
 */
class SingleMoveTrainer(private var node: DataNode, val callBackOnCorrect: (DataMove?) -> Unit) :
  InteractionsManager(GameEngine(node.positionKey)) {

  private val nodeManager: NodeManager by inject()

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

  fun updateNode(newNode: DataNode) {
    if (newNode.positionKey != node.positionKey || newNode.positionKey != engine.toPositionKey()) {
      node = newNode
      engine = GameEngine(node.positionKey)
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

    val dataNode =
      DataNode(
        positionKey = node.positionKey,
        previousAndNextMoves = node.previousAndNextMoves,
        previousAndNextTrainingDate = PreviousAndNextDate(DateUtil.today(), nextTrainingDate),
        depth = node.depth,
      )
    nodeManager.cacheNode(dataNode)
    nodeManager.saveNode(dataNode)
  }
}
