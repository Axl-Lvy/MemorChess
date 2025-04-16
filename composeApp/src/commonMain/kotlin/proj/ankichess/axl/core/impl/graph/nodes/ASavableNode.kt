package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.parser.FenParser
import proj.ankichess.axl.core.intf.data.IStoredPosition
import proj.ankichess.axl.core.intf.data.getCommonDataBase
import proj.ankichess.axl.core.intf.graph.INode

abstract class ASavableNode(protected val position: String) : INode {
  private var isSaved = false

  override fun isSaved(): Boolean {
    return isSaved
  }

  override suspend fun save() {
    getCommonDataBase().insertPosition(getPosition())
    isSaved = true
  }

  override fun getGame(): Game {
    return Game(FenParser.readPosition(position))
  }

  abstract fun getPosition(): IStoredPosition
}
