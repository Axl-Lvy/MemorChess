package proj.memorchess.axl.server.repertoire

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.data.repertoire.depthsFromStart
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException

/** Outcome of validating a repertoire's moves before they are accepted for publishing. */
internal sealed class MoveListValidation {

  /** Every move is legal, canonical and connected to the start, and every cap holds. */
  data class Valid(val moveCount: Int) : MoveListValidation()

  /** The list is empty, malformed, illegal, or not connected to the starting position. */
  data class Rejected(val reason: String) : MoveListValidation()

  /** The list exceeds the move count or ply depth cap. */
  data class TooLarge(val reason: String) : MoveListValidation()
}

/** Validates a published repertoire's moves, each one independently with [GameEngine]. */
internal object RepertoireMovesValidator {

  /**
   * Checks that [moves] is non empty, within [maxMoves], free of duplicates, made of legal
   * canonical moves landing on their destination, and reachable from the start within [maxPlyDepth]
   * plies.
   */
  fun validate(moves: List<RepertoireMove>, maxMoves: Int, maxPlyDepth: Int): MoveListValidation {
    if (moves.isEmpty()) return MoveListValidation.Rejected("the repertoire has no move")
    if (moves.size > maxMoves) {
      return MoveListValidation.TooLarge(
        "the repertoire has ${moves.size} moves, the cap is $maxMoves"
      )
    }
    if (moves.distinctBy { it.from to it.to }.size != moves.size) {
      return MoveListValidation.Rejected("two moves share the same origin and destination")
    }
    if (moves.distinctBy { it.from to it.move }.size != moves.size) {
      return MoveListValidation.Rejected("two moves share the same origin and move")
    }
    moves.firstNotNullOfOrNull(::problemOf)?.let {
      return MoveListValidation.Rejected(it)
    }
    val depths = moves.depthsFromStart()
    val deepest = moves.maxOf {
      depths[it.from] ?: return MoveListValidation.Rejected(detached(it))
    }
    if (deepest > maxPlyDepth) {
      return MoveListValidation.TooLarge("a move is $deepest plies deep, the cap is $maxPlyDepth")
    }
    return MoveListValidation.Valid(moves.size)
  }

  /** Describes why [move] is not legal, canonical and landing on its destination, or `null`. */
  private fun problemOf(move: RepertoireMove): String? {
    val from =
      PositionKey.validateAndCreateOrNull(move.from)
        ?: return "'${move.from}' is not a valid position"
    val engine =
      try {
        GameEngine(from)
      } catch (e: RuntimeException) {
        return "'${move.from}' is not a valid position"
      }
    val canonical =
      try {
        engine.playSanMove(move.move)
      } catch (e: IllegalMoveException) {
        return "'${move.move}' is not legal from '${move.from}'"
      } catch (e: IllegalArgumentException) {
        return "'${move.move}' is not legal from '${move.from}'"
      }
    return when {
      canonical != move.move -> "'${move.move}' must be written '$canonical'"
      engine.toPositionKey().value != move.to -> "'${move.move}' does not lead to '${move.to}'"
      else -> null
    }
  }

  private fun detached(move: RepertoireMove): String =
    "'${move.move}' from '${move.from}' is not connected to the starting position"
}
