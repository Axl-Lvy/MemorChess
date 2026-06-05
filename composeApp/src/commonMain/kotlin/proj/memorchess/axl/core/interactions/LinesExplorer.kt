package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.toast_deleted
import memorchess.composeapp.generated.resources.toast_no_next_move
import memorchess.composeapp.generated.resources.toast_no_previous_move
import memorchess.composeapp.generated.resources.toast_saved
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.NavigationHistory
import proj.memorchess.axl.core.graph.TreeStore

/** Free exploration of stored openings backed by a [TreeStore]. */
open class LinesExplorer(position: PositionKey? = null, protected val treeStore: TreeStore) :
  InteractionsManager(if (position == null) GameEngine() else GameEngine(position)) {

  private val startPosition = position ?: PositionKey.START_POSITION

  init {
    treeStore.ensurePosition(startPosition, 0)
  }

  protected val navigation = NavigationHistory(startPosition)

  var state by mutableStateOf(treeStore.current().computeState(startPosition, arrivedFrom = null))

  /** Walk one move backward, falling back to the parent of [navigation.current] when at root. */
  fun back() {
    val popped = navigation.back()
    if (popped == null) {
      val previousEdge =
        treeStore.current().get(navigation.current)?.incoming?.values?.firstOrNull()
      if (previousEdge == null) {
        toastRenderer.info(Res.string.toast_no_previous_move)
        return
      }
      navigation.reset(previousEdge.from)
      engine = GameEngine(navigation.current)
    } else {
      engine = GameEngine(navigation.current)
    }
    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks(false)
  }

  /** Walk one move forward through the forward history stack. */
  fun forward() {
    val popped = navigation.forward()
    if (popped == null) {
      toastRenderer.info(Res.string.toast_no_next_move)
      return
    }
    val (edge, _) = popped
    engine.playSanMove(edge.move)
    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks(false)
  }

  /** Sorted list of classified outgoing moves at the current position. */
  fun getNextMoves(): List<String> {
    val node = treeStore.current().get(navigation.current) ?: return emptyList()
    return node.outgoing.values.filter { it.isGood != null }.map { it.move }.sorted()
  }

  /** Resets the explorer to the initial chess position. */
  fun reset() {
    val resetPosition = PositionKey.START_POSITION
    treeStore.ensurePosition(resetPosition, 0)
    navigation.reset(resetPosition)
    state = treeStore.current().computeState(resetPosition, arrivedFrom = null)
    super.reset(resetPosition)
  }

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val destination = engine.toPositionKey()
    val originDepth = navigation.depth
    val edge =
      treeStore.addMove(
        from = origin,
        move = move,
        to = destination,
        isGood = treeStore.current().get(origin)?.outgoing?.get(move)?.isGood,
        fromDepth = originDepth,
      )
    navigation.push(edge, destination)
    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks()
  }

  /**
   * Classifies the current line as good and saves it. The walk back alternates: even index (the
   * destination itself and its grandparent) is good; odd index (its parent and great grandparent)
   * is bad if currently unclassified, otherwise left as is.
   */
  suspend fun save() {
    val current = navigation.current
    val currentDepth = treeStore.current().getDepth(current)
    classifyIncoming(current, currentDepth, mode = ClassifyMode.GOOD)

    val path = navigation.getBackPath().reversed()
    for ((index, entry) in path.withIndex()) {
      val (position, _) = entry
      val mode = if (index % 2 == 0) ClassifyMode.BAD_IF_UNKNOWN else ClassifyMode.GOOD
      val depth = treeStore.current().getDepth(position)
      classifyIncoming(position, depth, mode)
    }

    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
    toastRenderer.info(Res.string.toast_saved)
  }

  /** Deletes the current position's children and the position itself. */
  suspend fun delete() {
    val current = navigation.current
    val node = treeStore.current().get(current)
    if (node != null) {
      for (edge in node.outgoing.values.toList()) {
        cascadeDeleteFromPrevious(edge.to, edge)
      }
    }
    treeStore.deleteNode(current)
    navigation.clearForward()
    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
    toastRenderer.info(Res.string.toast_deleted)
    callCallBacks()
  }

  /**
   * Recursively deletes a subtree rooted at [position] when the edge being removed was its only
   * remaining incoming edge. Convergent positions reachable through other parents stay put.
   */
  private suspend fun cascadeDeleteFromPrevious(position: PositionKey, viaEdge: Edge) {
    treeStore.deleteMove(viaEdge.from, viaEdge.move)
    val node = treeStore.current().get(position)
    if (node != null && node.incoming.isEmpty()) {
      for (edge in node.outgoing.values.toList()) {
        cascadeDeleteFromPrevious(edge.to, edge)
      }
      treeStore.deleteNode(position)
    }
  }

  /** Returns the count of positions that would be removed by [delete] starting at the current. */
  fun calculateNumberOfNodeToDelete(): Int =
    treeStore.current().countDescendants(navigation.current)

  private enum class ClassifyMode {
    /** Mark every incoming edge as good. */
    GOOD,
    /** Mark currently unclassified incoming edges as bad. */
    BAD_IF_UNKNOWN,
  }

  private suspend fun classifyIncoming(position: PositionKey, depth: Int, mode: ClassifyMode) {
    val incoming = treeStore.current().get(position)?.incoming?.values?.toList().orEmpty()
    for (edge in incoming) {
      val newIsGood =
        when (mode) {
          ClassifyMode.GOOD -> true
          ClassifyMode.BAD_IF_UNKNOWN -> edge.isGood ?: false
        }
      if (edge.isGood == newIsGood) continue
      val originDepth = (depth - 1).coerceAtLeast(0)
      treeStore.addMove(
        from = edge.from,
        move = edge.move,
        to = edge.to,
        isGood = newIsGood,
        fromDepth = originDepth,
      )
    }
  }
}
