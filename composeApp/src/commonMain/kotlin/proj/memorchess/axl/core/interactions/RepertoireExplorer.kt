package proj.memorchess.axl.core.interactions

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.toast_not_in_repertoire
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.RepertoireMoveImporter
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * Read only navigator over one repertoire's lines in a throwaway [TreeStore] made by [build]. An
 * off book move is rejected and the board snaps back.
 */
class RepertoireExplorer private constructor(treeStore: TreeStore, tagStore: RepertoireTagStore) :
  LinesExplorer(position = null, treeStore = treeStore, tagStore = tagStore) {

  override suspend fun afterPlayMove(move: String) {
    val origin = navigation.current
    val edge = treeStore.node(origin)?.outgoing?.get(move)
    if (edge == null) {
      // Legal but not in this repertoire: undo it.
      engine = GameEngine(origin)
      toastRenderer.info(Res.string.toast_not_in_repertoire)
      callCallBacks(false)
      return
    }
    navigation.push(edge, edge.to)
    // navigation.arrivedVia is now this edge, so edge.from is the parent.
    state = treeStore.computeState(navigation.current, edge.from)
    callCallBacks()
  }

  companion object {
    /**
     * Builds an explorer over [moves], written into a fresh in memory graph.
     *
     * @throws proj.memorchess.axl.core.pgn.PgnImportException if a position is malformed or a move
     *   is not connected to the starting position.
     */
    suspend fun build(moves: List<RepertoireMove>): RepertoireExplorer {
      // Parentless: NodeCache gives the handed Job a permanent child, which would keep the caller
      // from completing. Nothing leaks since the store is in memory.
      val scope = CoroutineScope(coroutineContext + SupervisorJob())
      val database = InMemoryDatabaseQueryManager()
      val deviceIdentity = DeviceIdentity.ephemeral()
      val cache = NodeCache({ database.getPosition(it) }, scope)
      val trainable = TrainableProjection(database, cache)
      val tagStore = RepertoireTagStore(database, deviceIdentity, trainable)
      val treeStore =
        TreeStore(
          database,
          cache,
          Prefetcher(cache, scope),
          trainable,
          tagStore,
          deviceIdentity,
        )
      // The store holds only this repertoire, so its holds give every move the author's marking.
      RepertoireMoveImporter(treeStore, tagStore).import(moves, repertoireId = VIEWED_REPERTOIRE)
      return RepertoireExplorer(treeStore, tagStore)
    }
  }
}

/** Repertoire id the viewed moves are installed under in the throwaway store. */
private const val VIEWED_REPERTOIRE = "viewed"
