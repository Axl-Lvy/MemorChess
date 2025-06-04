package proj.memorchess.axl.core.engine.moves

interface IMove {

  fun destination(): Pair<Int, Int>

  fun origin(): Pair<Int, Int>

  fun generateChanges(): Map<Pair<Int, Int>, Pair<Int, Int>?>
}
