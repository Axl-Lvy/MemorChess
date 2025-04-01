package proj.ankichess.axl.core.impl.engine.moves

class EnPassant(from: Pair<Int, Int>, to: Pair<Int, Int>, private val captured: Pair<Int, Int>) :
  ClassicMove(from, to) {

  override fun generateChanges(): Map<Pair<Int, Int>, Pair<Int, Int>?> {
    return super.generateChanges() + linkedMapOf(captured to null)
  }
}
