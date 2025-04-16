package proj.ankichess.axl.core.impl.graph.nodes

import com.diamondedge.logging.logging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.parser.FenParser
import proj.ankichess.axl.core.intf.data.IStoredPosition
import proj.ankichess.axl.core.intf.data.getCommonDataBase
import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.graph.INode

/** Node factory singleton. */
object NodeFactory {

  /**
   * Cache to prevent creating a node twice.
   *
   * TODO: handle many index
   */
  private val cache = createCache()

  private fun createCache(): MutableMap<IPosition, INode> {
    val rootNode = RootNode()

    return mutableMapOf(rootNode.getGame().position to RootNode())
  }

  /**
   * Creates a node.
   *
   * @param game The game at the position we want to store.
   * @return The node.
   */
  fun createNode(game: Game, parent: INode, move: String): INode {
    val key = game.position
    val newNode = Node(parent, key)
    parent.addChild(move, newNode)
    return newNode
  }

  private fun getOrCreateNode(game: Game, parent: INode?, move: String?): INode {
    val key = game.position
    return cache.getOrPut(key) {
      if (parent == null) {
        return OrphanNode(key)
      }
      val newNode = Node(parent, key)
      if (move != null) {
        parent.addChild(move, newNode)
      }
      return newNode
    }
  }

  fun getNode(game: Game): INode? {
    val key = game.position
    return cache[key]
  }

  fun cacheNode(node: INode): INode? {
    return cache.put(node.getGame().position, node)
  }

  fun retrieveGraphFromDatabase(): INode {
    val allPosition: Flow<IStoredPosition> = getCommonDataBase().getAllPositions()
    allPosition.onEach {
      getOrCreateNode(
        OrphanNode(FenParser.readPosition(it.fenRepresentation)).getGame(),
        null,
        null,
      )
      LOGGER.i { "Retrieved node: ${it.fenRepresentation}" }
    }
    return getOrCreateNode(Game(), null, null)
  }

  private val LOGGER = logging()
}
