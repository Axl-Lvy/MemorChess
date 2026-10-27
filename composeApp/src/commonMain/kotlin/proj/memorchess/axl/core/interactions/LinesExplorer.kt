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
import proj.memorchess.axl.core.graph.NodeState
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Free exploration of stored openings backed by a [TreeStore].
 *
 * @param repertoireScope When not `null`, [getNextMoves] narrows to this repertoire and saving tags
 *   the session's new moves with it.
 */
open class LinesExplorer(
  position: PositionKey? = null,
  protected val treeStore: TreeStore,
  protected val tagStore: RepertoireTagStore,
  private val repertoireScope: String? = null,
) : InteractionsManager(if (position == null) GameEngine() else GameEngine(position)) {

  private val startPosition = position ?: PositionKey.START_POSITION

  protected val navigation = NavigationHistory(startPosition)

  /** Moves this scoped session created, tagged with [repertoireScope] once a save holds them. */
  private val newMoves = mutableSetOf<Pair<PositionKey, PositionKey>>()

  // Unscoped even in a scoped session: the indicator aggregates every incoming edge.
  var state by mutableStateOf(NodeState.UNKNOWN)
    protected set

  /**
   * Seeds the starting position and computes the initial [state]. Not done in `init`: the shared
   * cache may already be loading that position, and an unguarded write would be dropped.
   */
  suspend fun initState() {
    treeStore.ensurePosition(startPosition, 0)
    state = treeStore.computeState(startPosition, arrivedFrom = null)
  }

  /** Walk one move backward, falling back to the parent of [navigation.current] when at root. */
  suspend fun back() {
    val popped = navigation.back()
    if (popped == null) {
      val previousEdge = treeStore.node(navigation.current)?.incoming?.values?.firstOrNull()
      if (previousEdge == null) {
        toastRenderer.info(Res.string.toast_no_previous_move)
        return
      }
      navigation.reset(previousEdge.from)
      engine = GameEngine(navigation.current)
    } else {
      engine = GameEngine(navigation.current)
    }
    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks(false)
  }

  /** Walk one move forward through the forward history stack. */
  suspend fun forward() {
    val popped = navigation.forward()
    if (popped == null) {
      toastRenderer.info(Res.string.toast_no_next_move)
      return
    }
    val (edge, _) = popped
    engine.playSanMove(edge.move)
    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks(false)
  }

  /** Sorted classified moves from the current position, narrowed to [repertoireScope] if set. */
  suspend fun getNextMoves(): List<String> {
    val node = treeStore.node(navigation.current) ?: return emptyList()
    val classified = node.outgoing.values.filter { it.isGood != null }
    val scoped =
      repertoireScope?.let { scope ->
        classified.filter { scope in tagStore.tagsFor(it.from, it.to) }
      } ?: classified
    return scoped.map { it.move }.sorted()
  }

  /** SAN moves played from the root position to [navigation.current], in playing order. */
  fun playedMoves(): List<String> = navigation.getBackPath().map { it.second.move }

  /** Plies played from the root position to [navigation.current]. */
  val depth: Int
    get() = navigation.depth

  /** Resets the explorer to the initial chess position. */
  suspend fun reset() {
    val resetPosition = PositionKey.START_POSITION
    treeStore.ensurePosition(resetPosition, 0)
    navigation.reset(resetPosition)
    state = treeStore.computeState(resetPosition, arrivedFrom = null)
    super.reset(resetPosition)
  }

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val destination = engine.toPositionKey()
    val originDepth = navigation.depth
    // Replaying a known move writes nothing: re-upserting it would turn a move an installed
    // repertoire holds into a manual hold.
    val edge =
      treeStore.node(origin)?.outgoing?.get(move)
        ?: treeStore
          .addMove(
            from = origin,
            move = move,
            to = destination,
            isGood = null,
            fromDepth = originDepth,
          )
          .also { if (repertoireScope != null) newMoves += origin to destination }
    navigation.push(edge, destination)
    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks()
  }

  /**
   * Holds the current line manually: the last move and every second one before it turn good, the
   * others keep their shown marking or turn bad when unclassified.
   */
  suspend fun save() {
    val path = navigation.getBackPath()
    val root = path.firstOrNull()?.first ?: navigation.current
    holdLineAbove(root)
    val reached = listOf(root) + path.map { it.second.to }
    val pathEdges = path.map { it.second.from to it.second.to }.toSet()
    for ((index, position) in reached.withIndex()) {
      val fromCurrent = reached.lastIndex - index
      val mode = if (fromCurrent % 2 == 0) ClassifyMode.GOOD else ClassifyMode.BAD_IF_UNKNOWN
      classifyIncoming(position, treeStore.getDepth(position), mode, pathEdges)
    }
    if (repertoireScope != null) {
      for (edge in path.map { it.second }) {
        if (newMoves.remove(edge.from to edge.to)) tagStore.tag(edge.from, edge.to, repertoireScope)
      }
    }

    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    toastRenderer.info(Res.string.toast_saved)
  }

  /** Holds the shallowest move into each position above [root] until one is already held. */
  private suspend fun holdLineAbove(root: PositionKey) {
    var position = root
    val visited = mutableSetOf<PositionKey>()
    while (position != PositionKey.START_POSITION && visited.add(position)) {
      val incoming =
        treeStore.node(position)?.incoming?.values?.filter { !it.isDeleted && it.isGood != null }
      if (incoming.isNullOrEmpty() || incoming.any { it.ownIsGood != null }) return
      val parent = incoming.minBy { treeStore.getDepth(it.from) }
      treeStore.addMove(
        parent.from,
        parent.move,
        parent.to,
        parent.isGood,
        treeStore.getDepth(parent.from),
      )
      position = parent.from
    }
  }

  /** Deletes the current position's children and the position itself. */
  suspend fun delete() {
    val current = navigation.current
    val node = treeStore.node(current)
    if (node != null) {
      for (edge in node.outgoing.values.toList()) {
        cascadeDeleteFromPrevious(edge.to, edge)
      }
    }
    treeStore.deleteNode(current)
    navigation.clearForward()
    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    toastRenderer.info(Res.string.toast_deleted)
    callCallBacks()
  }

  /** Deletes [viaEdge], then the subtree at [position] if that was its last incoming edge. */
  private suspend fun cascadeDeleteFromPrevious(position: PositionKey, viaEdge: Edge) {
    treeStore.deleteMove(viaEdge.from, viaEdge.move)
    val node = treeStore.node(position)
    if (node != null && node.incoming.isEmpty()) {
      for (edge in node.outgoing.values.toList()) {
        cascadeDeleteFromPrevious(edge.to, edge)
      }
      treeStore.deleteNode(position)
    }
  }

  /** Number of positions [delete] would remove, capped by [TreeStore.countDescendants]. */
  suspend fun calculateNumberOfNodeToDelete(): Int = treeStore.countDescendants(navigation.current)

  private enum class ClassifyMode {
    /** Mark every incoming edge as good. */
    GOOD,
    /** Mark currently unclassified incoming edges as bad. */
    BAD_IF_UNKNOWN,
  }

  /**
   * Holds the move into [position] that lies on the saved line, at the marking [mode] gives it.
   * Other moves into [position] change only when the user already holds them.
   */
  private suspend fun classifyIncoming(
    position: PositionKey,
    depth: Int,
    mode: ClassifyMode,
    pathEdges: Set<Pair<PositionKey, PositionKey>>,
  ) {
    val incoming = treeStore.node(position)?.incoming?.values?.toList().orEmpty()
    for (edge in incoming) {
      val onPath = (edge.from to edge.to) in pathEdges
      if (!onPath && edge.ownIsGood == null) continue
      val newIsGood =
        when (mode) {
          ClassifyMode.GOOD -> true
          ClassifyMode.BAD_IF_UNKNOWN -> edge.ownIsGood ?: edge.isGood ?: false
        }
      if (edge.ownIsGood == newIsGood) continue
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
