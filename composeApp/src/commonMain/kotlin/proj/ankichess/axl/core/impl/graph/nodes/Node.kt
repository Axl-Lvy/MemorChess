package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.data.PositionKey
import proj.ankichess.axl.core.impl.data.StoredNode
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.data.getCommonDataBase

class Node(
  val position: PositionKey,
  val moves: MutableSet<String> = mutableSetOf(),
  var previous: Node? = null,
  var next: Node? = null,
) {
  fun createGame(): Game {
    return Game(position)
  }

  fun addChild(move: String, child: Node) {
    moves.add(move)
    next = child
  }

  suspend fun save() {
    getCommonDataBase().insertPosition(StoredNode(position, moves.sorted()))
    previous?.save()
  }
}
