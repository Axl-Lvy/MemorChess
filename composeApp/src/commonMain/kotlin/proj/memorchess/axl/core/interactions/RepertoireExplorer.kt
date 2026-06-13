package proj.memorchess.axl.core.interactions

import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.toast_not_in_repertoire
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnGame
import proj.memorchess.axl.core.pgn.PgnImporter

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
    val edge = treeStore.current().get(origin)?.outgoing?.get(move)
    if (edge == null) {
      // Legal chess move but not part of this repertoire: undo it and stay put.
      engine = GameEngine(origin)
      toastRenderer.info(Res.string.toast_not_in_repertoire)
      callCallBacks(false)
      return
    }
    navigation.push(edge, edge.to)
    state = treeStore.current().computeState(navigation.current, navigation.arrivedVia?.from)
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
      val treeStore = TreeStore(InMemoryDatabaseQueryManager())
      PgnImporter(treeStore).import(games)
      return RepertoireExplorer(treeStore)
    }
  }
}
