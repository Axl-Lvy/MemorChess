package proj.memorchess.axl.core.interactions

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.toast_not_in_repertoire
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * Read only navigator over the lines of a single repertoire.
 *
 * Unlike [LinesExplorer], which mutates the user's persisted opening graph, this explorer walks a
 * throwaway [TreeStore] built from a downloaded PGN (see [build]) and never writes anything back.
 * Only moves that exist in that PGN can be played: an off book move is rejected and the board snaps
 * back to the position it came from. Back / forward / reset and the next move list are inherited
 * from [LinesExplorer] and operate on the transient graph, which contains exactly the repertoire's
 * moves.
 *
 * @constructor Wraps an already populated transient [treeStore]. Use [build] to create one from
 *   PGN.
 */
class RepertoireExplorer private constructor(treeStore: TreeStore) :
  LinesExplorer(position = null, treeStore = treeStore) {

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val edge = treeStore.node(origin)?.outgoing?.get(move)
    if (edge == null) {
      // Legal chess move but not part of this repertoire: undo it and stay put.
      engine = GameEngine(origin)
      toastRenderer.info(Res.string.toast_not_in_repertoire)
      callCallBacks(false)
      return
    }
    navigation.push(edge, edge.to)
    // After the push, navigation.arrivedVia is exactly this edge, so edge.from is the parent.
    state = treeStore.computeState(navigation.current, edge.from)
    callCallBacks()
  }

  companion object {
    /**
     * Builds an explorer over the lines of [games], replayed into a fresh in memory graph.
     *
     * @param games Parsed repertoire games, normally from
     *   [proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient.fetchPgn].
     * @throws proj.memorchess.axl.core.pgn.PgnImportException if any move of any variation is
     *   illegal.
     */
    suspend fun build(games: List<PgnGame>): RepertoireExplorer {
      // A parentless supervisor rather than the caller's Job. NodeCache attaches a permanent child
      // supervisor to whatever Job it is handed, so tying it to the caller would leave that caller
      // unable to complete. Nothing can leak here: the store is in memory, prefetch is one ply, and
      // a load over a HashMap finishes in microseconds.
      val scope = CoroutineScope(coroutineContext + SupervisorJob())
      val database = InMemoryDatabaseQueryManager()
      val cache = NodeCache({ database.getPosition(it) }, scope)
      val treeStore =
        TreeStore(database, cache, Prefetcher(cache, scope), DeviceIdentity.ephemeral())
      PgnImporter(treeStore).import(games)
      return RepertoireExplorer(treeStore)
    }
  }
}
