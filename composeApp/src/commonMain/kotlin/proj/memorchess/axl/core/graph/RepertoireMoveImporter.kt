package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.depthsFromStart
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary

/**
 * Installs a published repertoire's moves into the user's opening graph as holds of the
 * repertoire, each with the author's marking. The effective marking follows own > good > bad, so
 * an install never overrides the user's own marking. A move whose effective marking the install
 * would not change counts as already present.
 *
 * @property treeStore Mutation chokepoint of the opening graph that receives the moves.
 * @property tagStore Registry and tags the installed moves are tagged through.
 */
class RepertoireMoveImporter(
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  /**
   * Installs [moves] as holds of [repertoireId] in one batch. Reports `1f` through [onProgress] once
   * the write is done.
   *
   * @throws PgnImportException if a position key is malformed or a move is not connected to the
   *   starting position. Nothing is written in that case.
   */
  suspend fun import(
    moves: List<RepertoireMove>,
    repertoireId: String,
    onProgress: (Float) -> Unit = {},
  ): PgnImportSummary {
    val planned = plan(moves)
    treeStore.install(repertoireId, planned.map { it.insertion })
    onProgress(1f)
    val alreadyPresent = planned.count { it.unchanged }
    return PgnImportSummary(
      movesAdded = planned.size - alreadyPresent,
      movesAlreadyPresent = alreadyPresent,
    )
  }

  /**
   * Computes, without writing anything, how many of [moves] an [import] would leave unchanged.
   *
   * @throws PgnImportException under the same conditions as [import].
   */
  suspend fun preview(moves: List<RepertoireMove>): PgnImportPreview {
    val planned = plan(moves)
    return PgnImportPreview(
      totalMoves = planned.size,
      movesInCommon = planned.count { it.unchanged },
    )
  }

  private suspend fun plan(moves: List<RepertoireMove>): List<PlannedMove> {
    val depths = moves.depthsFromStart()
    return moves.map { move ->
      val from = keyOf(move.from)
      val to = keyOf(move.to)
      val depth =
        depths[move.from]
          ?: throw PgnImportException("Move ${move.move} from ${move.from} is not connected")
      val existing = treeStore.node(from)?.outgoing?.get(move.move)
      val markings = tagStore.markings(from, to).values.filterNotNull()
      val effective = effectiveMarking(existing?.ownIsGood, markings + move.isGood)
      PlannedMove(
        InstalledMove(from = from, move = move.move, to = to, isGood = move.isGood, fromDepth = depth),
        unchanged = existing?.isGood == effective,
      )
    }
  }

  private fun keyOf(value: String): PositionKey =
    PositionKey.validateAndCreateOrNull(value)
      ?: throw PgnImportException("'$value' is not a valid position")

  /** One move to install, and whether its effective marking would stay as it is. */
  private class PlannedMove(val insertion: InstalledMove, val unchanged: Boolean)
}
