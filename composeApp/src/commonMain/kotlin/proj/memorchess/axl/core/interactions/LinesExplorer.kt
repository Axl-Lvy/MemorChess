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
 * Under demand paging every graph read is a suspend [TreeStore.node] / [TreeStore.computeState]
 * lookup, so [back], [forward], [reset] and [getNextMoves] are `suspend`. [state] starts at
 * [NodeState.UNKNOWN] (the same value an unresolved position computes to, so the first frame is
 * consistent) and is filled by [initState] from the page's loading phase, then recomputed after
 * every navigation step.
 *
 * [state]/[NodeState] stay unscoped even in a scoped session: the board's good/bad indicator still
 * aggregates every incoming edge regardless of tag. Making that scope pure would need an async, tag
 * filtered variant of [proj.memorchess.axl.core.graph.Node.computeState], evaluated per incoming
 * edge; deferred as a follow up, since the feature's actual gate is which moves [getNextMoves]
 * offers and a scoped trainer accepts, not the color indicator.
 *
 * Saving holds the line manually: every move from the starting position to the saved one gets the
 * user's own marking, so a move an installed repertoire brought in becomes the user's own.
 *
 * @param repertoireScope When not `null`, [getNextMoves] narrows to edges tagged with this
 *   repertoire, and saving a line tags the genuinely new moves this session played on it with this
 *   repertoire. `null` behaves exactly as before this parameter existed.
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

  var state by mutableStateOf(NodeState.UNKNOWN)
    protected set

  /**
   * Seeds the starting position into the cache and computes the initial [state] from it. Called
   * once from the page's loading phase because both halves suspend (they go through the bounded
   * cache, under its lock).
   *
   * The seed lives here rather than in an `init` block: the store's cache is shared across screens,
   * so by construction time a load of the starting position may already be in flight, and an
   * unguarded write would let that load's finalization drop the seeded node again.
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

  /**
   * Sorted list of classified outgoing moves at the current position, narrowed to [repertoireScope]
   * when this session is scoped (any [Edge.isGood] value passes, so a known trap the repertoire
   * teaches to counter still shows).
   */
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
          .addMove(from = origin, move = move, to = destination, isGood = null, fromDepth = originDepth)
          .also { if (repertoireScope != null) newMoves += origin to destination }
    navigation.push(edge, destination)
    state = treeStore.computeState(navigation.current, navigation.arrivedVia?.from)
    callCallBacks()
  }

  /**
   * Classifies the current line and holds it manually. The walk back alternates: the move reaching
   * the current position and every second move before it are good; the others keep the marking the
   * user sees, or turn bad when unclassified. Off line moves into a position on the line turn good
   * only when the user already holds them. The line above the session's root is held at the
   * markings it shows, and a scoped session then tags the new moves of the saved line.
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

  /**
   * Holds the line above [root] manually so the saved line stays connected to the starting
   * position through manual holds: while no move into the position is held, holds the one from the
   * shallowest origin at the marking it shows.
   */
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

  /**
   * Recursively deletes a subtree rooted at [position] when the edge being removed was its only
   * remaining incoming edge. Convergent positions reachable through other parents stay put.
   *
   * Each visited position is resolved through [TreeStore.node] so a paged out descendant is pulled
   * in on demand before being walked. Bounded by the subtree size, the same row count the delete
   * itself touches.
   */
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

  /**
   * Returns the count of positions that [delete] would remove starting at the current position,
   * bounded by the backend's cap. A descendant is counted only when it is not convergent (the
   * subtree being deleted is its sole set of parents), matching [delete]'s cascade rule. Delegates
   * to the DB side capped count so it never pages the whole subtree through the cache.
   */
  suspend fun calculateNumberOfNodeToDelete(): Int = treeStore.countDescendants(navigation.current)

  private enum class ClassifyMode {
    /** Mark every incoming edge as good. */
    GOOD,
    /** Mark currently unclassified incoming edges as bad. */
    BAD_IF_UNKNOWN,
  }

  /**
   * Holds the move into [position] that lies on the saved line, at the marking [mode] gives it. Other
   * moves into [position] change only when the user already holds them.
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
