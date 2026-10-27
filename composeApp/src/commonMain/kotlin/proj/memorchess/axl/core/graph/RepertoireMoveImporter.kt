package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.depthsFromStart
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary

/**
 * Installs a published repertoire's moves into the opening graph as holds of that repertoire. The
 * effective marking follows own > good > bad, so an install never overrides the user's own marking.
 */
class RepertoireMoveImporter(
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  /**
   * Installs [moves] as holds of [repertoireId] in one batch, reporting `1f` once written.
   *
   * @throws PgnImportException if a position key is malformed or a move is not connected to the
   *   starting position. Nothing is written in that case.
   */
  suspend fun import(
    moves: List<RepertoireMove>,
    repertoireId: String,
    onProgress: (Float) -> Unit = {},
  ): PgnImportSummary {
    val planned = plan(moves, repertoireId)
    treeStore.install(repertoireId, planned.map { it.insertion })
    onProgress(1f)
    val alreadyPresent = planned.count { it.unchanged }
    return PgnImportSummary(
      movesAdded = planned.size - alreadyPresent,
      movesAlreadyPresent = alreadyPresent,
    )
  }

  /**
   * Computes, without writing anything, how many of [moves] an [import] as [repertoireId] would
   * leave unchanged, and how many moves it would delete.
   *
   * @throws PgnImportException under the same conditions as [import].
   */
  suspend fun preview(moves: List<RepertoireMove>, repertoireId: String): PgnImportPreview {
    val planned = plan(moves, repertoireId)
    return PgnImportPreview(
      totalMoves = planned.size,
      movesInCommon = planned.count { it.unchanged },
      movesToDelete =
        treeStore.previewInstall(
          repertoireId,
          planned.map { TaggedEdge(it.insertion.from, it.insertion.to, it.insertion.move) },
        ),
    )
  }

  private suspend fun plan(moves: List<RepertoireMove>, repertoireId: String): List<PlannedMove> {
    val depths = moves.depthsFromStart()
    return moves.map { move ->
      val from = keyOf(move.from)
      val to = keyOf(move.to)
      val depth =
        depths[move.from]
          ?: throw PgnImportException("Move ${move.move} from ${move.from} is not connected")
      val existing = treeStore.node(from)?.outgoing?.get(move.move)
      val markings = (tagStore.markings(from, to) - repertoireId).values.filterNotNull()
      val effective = effectiveMarking(existing?.ownIsGood, markings + move.isGood)
      PlannedMove(
        InstalledMove(
          from = from,
          move = move.move,
          to = to,
          isGood = move.isGood,
          fromDepth = depth,
        ),
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
