package proj.memorchess.axl.core.interactions

import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.training.NextDateCalculatorOnFailure
import proj.memorchess.axl.core.training.NextDateCalculatorOnSuccess

class SingleLineTrainer(private var node: Node) : AInteractionsManager(node.createGame()) {

  var isCorrect: Boolean = true
    private set

  override fun afterPlayMove(move: String) {
    node = NodeManager.createNode(game, node, move)
    val playedMove = node.linkedMoves.previousMoves[move]
    checkNotNull(playedMove) {
      "$move has not been registered in the node $node 's previous moves."
    }
    isCorrect = playedMove.isGood == true
    val nextDateCalculator =
      if (isCorrect) NextDateCalculatorOnSuccess else NextDateCalculatorOnFailure
    playedMove.nextTrainedDate = nextDateCalculator.calculateNextDate(playedMove.lastTrainedDate)
    playedMove.lastTrainedDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    kotlinx.coroutines.GlobalScope.launch { playedMove.save() }
  }
}
