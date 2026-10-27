package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.depthsFromStart
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary

/**
 * Merges a published repertoire's moves into the user's opening graph.
 *
 * Each move keeps the author's marking, except that an install never turns a move the user already
 * has as good into a bad one. A move the install would not change counts as already present.
 *
 * @property treeStore Mutation chokepoint of the opening graph that receives the moves.
 * @property tagStore Registry and tags the installed moves are tagged through.
 */
class RepertoireMoveImporter(
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  /**
   * Writes [moves] into the graph in one batch, tagging every one of them with [repertoireId] when
   * it is not `null`. Reports `1f` through [onProgress] once the write and tagging are done.
   *
   * @throws PgnImportException if a position key is malformed or a move is not connected to the
   *   starting position. Nothing is written in that case.
   */
  suspend fun import(
    moves: List<RepertoireMove>,
    repertoireId: String?,
    onProgress: (Float) -> Unit = {},
  ): PgnImportSummary {
    val planned = plan(moves)
    val (alreadyPresent, toInsert) = planned.partition { it.unchanged }
    treeStore.addMoves(toInsert.map { it.insertion })
    if (repertoireId != null) {
      for (move in planned) tagStore.tag(move.insertion.from, move.insertion.to, repertoireId)
    }
    onProgress(1f)
    return PgnImportSummary(movesAdded = toInsert.size, movesAlreadyPresent = alreadyPresent.size)
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
      val existing = treeStore.node(from)?.outgoing?.get(move.move)?.isGood
      val written = existing == true || move.isGood
      PlannedMove(
        MoveInsertion(from = from, move = move.move, to = to, isGood = written, fromDepth = depth),
        unchanged = existing == written,
      )
    }
  }

  private fun keyOf(value: String): PositionKey =
    PositionKey.validateAndCreateOrNull(value)
      ?: throw PgnImportException("'$value' is not a valid position")

  /** One move to write, and whether the graph already holds it exactly as it would be written. */
  private class PlannedMove(val insertion: MoveInsertion, val unchanged: Boolean)
}
