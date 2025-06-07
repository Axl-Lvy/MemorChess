package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.graph.nodes.NodeManager

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
  }
}
