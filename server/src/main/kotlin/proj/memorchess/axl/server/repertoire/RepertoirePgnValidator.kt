package proj.memorchess.axl.server.repertoire

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.pgn.PgnMoveNode
import proj.memorchess.axl.core.pgn.PgnParseException
import proj.memorchess.axl.core.pgn.PgnParser

/** Outcome of validating a repertoire payload before it is accepted for publishing. */
internal sealed class RepertoireValidation {

  /** The payload parses, has at least one legal move, and stays under every cap. */
  data class Valid(val moveCount: Int) : RepertoireValidation()

  /** The payload does not parse, has no playable move, or plays an illegal move. */
  data class Rejected(val reason: String) : RepertoireValidation()

  /** The payload or its distinct move count exceeds a server side cap. */
  data class TooLarge(val reason: String) : RepertoireValidation()
}

/**
 * Validates a repertoire PGN payload before it is stored.
 *
 * Uses the same parser and engine the client uses to validate a download: [PgnParser] must parse
 * the document, it must contain at least one playable move, and every move of every variation must
 * be legal from its origin according to [GameEngine]. This mirrors `PgnImporter`'s validation walk
 * in `composeApp`, minus the tree store write path and the good/bad move classification, neither of
 * which the server has a use for.
 */
internal object RepertoirePgnValidator {

  /**
   * @param maxPayloadBytes Cap on the payload's UTF-8 byte size, checked before parsing.
   * @param maxMoves Cap on the number of distinct `(position, move)` edges across every variation.
   */
  fun validate(pgn: String, maxPayloadBytes: Int, maxMoves: Int): RepertoireValidation {
    val payloadBytes = pgn.encodeToByteArray().size
    if (payloadBytes > maxPayloadBytes) {
      return RepertoireValidation.TooLarge(
        "the payload is $payloadBytes bytes, the cap is $maxPayloadBytes"
      )
    }

    val games =
      try {
        PgnParser.parse(pgn)
      } catch (e: PgnParseException) {
        return RepertoireValidation.Rejected(e.message ?: "invalid PGN")
      }
    if (games.all { it.moves.isEmpty() }) {
      return RepertoireValidation.Rejected("the PGN contains no playable move")
    }

    val seen = mutableSetOf<Pair<PositionKey, String>>()
    val rootKey = GameEngine().toPositionKey()
    for (game in games) {
      for (firstMove in game.moves) {
        val outcome = walk(rootKey, firstMove, maxMoves, seen)
        if (outcome != null) return outcome
      }
    }
    return RepertoireValidation.Valid(seen.size)
  }

  /**
   * Replays [node] from [fromKey], recording it and recursing into every continuation.
   *
   * @return the first [RepertoireValidation.Rejected] or [RepertoireValidation.TooLarge] found, or
   *   `null` when [node] and every descendant are legal and under [maxMoves].
   */
  private fun walk(
    fromKey: PositionKey,
    node: PgnMoveNode,
    maxMoves: Int,
    seen: MutableSet<Pair<PositionKey, String>>,
  ): RepertoireValidation? {
    val engine = GameEngine(fromKey)
    try {
      engine.playSanMove(node.san)
    } catch (e: IllegalMoveException) {
      return RepertoireValidation.Rejected("illegal move ${node.san}: ${e.message}")
    } catch (e: IllegalArgumentException) {
      // The underlying chess library reports some illegal SAN moves this way.
      return RepertoireValidation.Rejected("illegal move ${node.san}: ${e.message}")
    }
    val toKey = engine.toPositionKey()
    if (seen.add(fromKey to node.san) && seen.size > maxMoves) {
      return RepertoireValidation.TooLarge("the repertoire has more than $maxMoves distinct moves")
    }
    for (child in node.children) {
      val outcome = walk(toKey, child, maxMoves, seen)
      if (outcome != null) return outcome
    }
    return null
  }
}
