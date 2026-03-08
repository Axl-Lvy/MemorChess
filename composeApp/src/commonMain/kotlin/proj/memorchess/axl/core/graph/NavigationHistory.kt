package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionKey

/**
 * Tracks navigation through positions with full back/forward history.
 *
 * @param startPosition The initial position to start navigating from.
 */
class NavigationHistory(startPosition: PositionKey) {

  /** The current position. */
  var current: PositionKey = startPosition
    private set

  /** The [DataMove] that was played to arrive at [current]. Null at root. */
  var arrivedVia: DataMove? = null
    private set

  /** Back-history: stack of (positionWeWereAt, moveWePlayedToLeave). */
  private val backStack = ArrayDeque<Pair<PositionKey, DataMove>>()

  /** Forward-history: stack of (moveToReplay, positionItLeadsTo). Set by [back]. */
  private val forwardStack = ArrayDeque<Pair<DataMove, PositionKey>>()

  /** Navigate forward to a new position by playing a move. */
  fun push(move: DataMove, destination: PositionKey) {
    backStack.addLast(current to move)
    forwardStack.clear()
    current = destination
    arrivedVia = move
  }

  /** Go back. Returns the (position we went back to, move that was undone) or null. */
  fun back(): Pair<PositionKey, DataMove>? {
    val (previousPosition, movePlayedFromThere) = backStack.removeLastOrNull() ?: return null
    forwardStack.addLast(movePlayedFromThere to current)
    current = previousPosition
    arrivedVia = backStack.lastOrNull()?.second
    return previousPosition to movePlayedFromThere
  }

  /** Go forward. Returns the (move to replay, destination) or null. */
  fun forward(): Pair<DataMove, PositionKey>? {
    val (move, destination) = forwardStack.removeLastOrNull() ?: return null
    backStack.addLast(current to move)
    current = destination
    arrivedVia = move
    return move to destination
  }

  /** Full back-stack depth (number of moves from root to current). */
  val depth: Int
    get() = backStack.size

  /** Returns the back-stack as a list from root to current (not including current). */
  fun getBackPath(): List<Pair<PositionKey, DataMove>> = backStack.toList()

  /** Clear forward history (e.g. after a delete invalidates descendants). */
  fun clearForward() {
    forwardStack.clear()
  }

  /** Reset to a new position, clearing all history. */
  fun reset(position: PositionKey) {
    backStack.clear()
    forwardStack.clear()
    current = position
    arrivedVia = null
  }
}
