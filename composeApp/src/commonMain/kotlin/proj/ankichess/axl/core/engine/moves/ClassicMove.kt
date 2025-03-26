package proj.ankichess.axl.core.engine.moves

open class ClassicMove(val from: Pair<Int, Int>, val to: Pair<Int, Int>) : IMove {

  override fun destination(): Pair<Int, Int> {
    return to
  }

  override fun origin(): Pair<Int, Int> {
    return from
  }

  override fun generateChanges(): Map<Pair<Int, Int>, Pair<Int, Int>?> {
    return linkedMapOf(to to from, from to null)
  }
}
