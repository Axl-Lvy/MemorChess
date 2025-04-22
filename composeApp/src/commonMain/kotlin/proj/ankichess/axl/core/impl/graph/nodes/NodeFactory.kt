package proj.ankichess.axl.core.impl.graph.nodes

import com.diamondedge.logging.logging
import proj.ankichess.axl.core.impl.data.PositionKey
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.data.IStoredNode
import proj.ankichess.axl.core.intf.data.getCommonDataBase

/** Node factory singleton. */
object NodeFactory {

  /**
   * Cache to prevent creating a node twice.
   *
   * TODO: handle many index
   */
  private val movesCache = mutableMapOf<PositionKey, MutableSet<String>>()

  fun createRootNode(): Node {
    val position = Game().position.toImmutablePosition()
    val rootNode = Node(position)
    val newNodeMoves = movesCache.getOrPut(position) { mutableSetOf() }
    rootNode.moves.addAll(newNodeMoves)
    return rootNode
  }

  /**
   * Creates a node.
   *
   * @param game The game at the position we want to store.
   * @return The node.
   */
  fun createNode(game: Game, previous: Node, move: String): Node {
    val newNodeMoves = movesCache.getOrPut(game.position.toImmutablePosition()) { mutableSetOf() }
    val newNode =
      Node(game.position.toImmutablePosition(), previous = previous, moves = newNodeMoves)
    previous.addChild(move, newNode)
    return newNode
  }

  suspend fun retrieveGraphFromDatabase() {
    val allPosition: List<IStoredNode> = getCommonDataBase().getAllPositions()
    allPosition.forEach {
      movesCache.getOrPut(it.positionKey, { mutableSetOf() }).addAll(it.getAvailableMoveList())
      LOGGER.i { "Retrieved node: ${it.positionKey}" }
    }
  }

  private val LOGGER = logging()
}
