package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey

/**
 * Tracks navigation through positions with full back and forward history.
 *
 * Holds [PositionKey]s and [Edge]s only. No live references to a tree; consumers ask
 * [TreeStore.current] when they need graph context.
 *
 * @param startPosition The initial position to start navigating from.
 */
class NavigationHistory(startPosition: PositionKey) {

  /** The current position. */
  var current: PositionKey = startPosition
    private set

  /** The [Edge] that led to [current]. `null` at root. */
  var arrivedVia: Edge? = null
    private set

  /** Back history: stack of (positionWeWereAt, edgeWePlayedToLeave). */
  private val backStack = ArrayDeque<Pair<PositionKey, Edge>>()

  /** Forward history: stack of (edgeToReplay, positionItLeadsTo). Filled by [back]. */
  private val forwardStack = ArrayDeque<Pair<Edge, PositionKey>>()

  /** Walks forward to a new position by playing [edge]. */
  fun push(edge: Edge, destination: PositionKey) {
    backStack.addLast(current to edge)
    forwardStack.clear()
    current = destination
    arrivedVia = edge
  }

  /** Walks one step backward. Returns (positionWeWentBackTo, edgeWeUndid) or `null` at root. */
  fun back(): Pair<PositionKey, Edge>? {
    val (previousPosition, edgePlayedFromThere) = backStack.removeLastOrNull() ?: return null
    forwardStack.addLast(edgePlayedFromThere to current)
    current = previousPosition
    arrivedVia = backStack.lastOrNull()?.second
    return previousPosition to edgePlayedFromThere
  }

  /** Walks one step forward. Returns (edgeToReplay, destination) or `null`. */
  fun forward(): Pair<Edge, PositionKey>? {
    val (edge, destination) = forwardStack.removeLastOrNull() ?: return null
    backStack.addLast(current to edge)
    current = destination
    arrivedVia = edge
    return edge to destination
  }

  /** Plies played from root to [current]. */
  val depth: Int
    get() = backStack.size

  /** Returns the back stack as a list from root to current, excluding current. */
  fun getBackPath(): List<Pair<PositionKey, Edge>> = backStack.toList()

  /** Clears forward history. Used after a delete invalidates descendants. */
  fun clearForward() {
    forwardStack.clear()
  }

  /** Resets to a fresh [position], clearing all history. */
  fun reset(position: PositionKey) {
    backStack.clear()
    forwardStack.clear()
    current = position
    arrivedVia = null
  }
}
