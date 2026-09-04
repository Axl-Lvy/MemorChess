package proj.memorchess.axl.server.repertoire

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.pgn.PgnMoveNode
import proj.memorchess.axl.core.pgn.PgnParseException
import proj.memorchess.axl.core.pgn.PgnParser

/**
 * Deepest line accepted, in plies from the starting position.
 *
 * A real opening repertoire never needs more than a few dozen plies down any single line. The cap
 * exists to bound recursion depth: without it, a payload well under the byte cap can still walk a
 * line thousands of plies deep (for example a short sequence of legal moves repeated many times),
 * which overflows the call stack before [MAX_REPERTOIRE_MOVES] is ever reached, because a repeated
 * line keeps revisiting the same small set of distinct moves.
 */
private const val MAX_PLY_DEPTH = 200

/**
 * Stack size given to the worker thread [RepertoirePgnValidator.validate] runs on, generous
 * headroom over a JVM's default thread stack (roughly one megabyte).
 *
 * The parser this validates with (`:shared`'s `PgnParser`) builds its move tree recursively, one
 * frame per move across the whole document, not only per line, so a still size capped payload
 * packed with short moves can recurse deeply enough to overflow a default stack during parsing
 * itself, before this validator's own [MAX_PLY_DEPTH] check ever runs. Running on a dedicated,
 * generously sized stack is the containable fix on this side of that boundary. A parser rewritten
 * to build its tree iteratively would remove the need for this outright, tracked as a follow up.
 */
private const val VALIDATOR_STACK_BYTES = 64L * 1024 * 1024

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
    return runOnDeepStack { parseAndWalk(pgn, maxMoves) }
  }

  private fun parseAndWalk(pgn: String, maxMoves: Int): RepertoireValidation {
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
        val outcome = walk(rootKey, firstMove, depth = 1, maxMoves, seen)
        if (outcome != null) return outcome
      }
    }
    return RepertoireValidation.Valid(seen.size)
  }

  /**
   * Runs [block] on a dedicated thread with a [VALIDATOR_STACK_BYTES] stack, so a document deep
   * enough to overflow a default stack while parsing is reported as [RepertoireValidation.TooLarge]
   * rather than propagated as a crash. Blocks the caller until [block] finishes. Callers on a
   * coroutine dispatcher should wrap this in a dispatcher meant for blocking work.
   */
  private fun runOnDeepStack(block: () -> RepertoireValidation): RepertoireValidation {
    var outcome: RepertoireValidation? = null
    val worker =
      Thread(
        null,
        {
          outcome =
            try {
              block()
            } catch (e: StackOverflowError) {
              RepertoireValidation.TooLarge("the document is too deeply nested to parse")
            }
        },
        "repertoire-pgn-validator",
        VALIDATOR_STACK_BYTES,
      )
    worker.start()
    worker.join()
    return outcome ?: RepertoireValidation.Rejected("validation failed unexpectedly")
  }

  /**
   * Replays [node] from [fromKey] at [depth] plies from the start, recording it and recursing into
   * every continuation.
   *
   * @return the first [RepertoireValidation.Rejected] or [RepertoireValidation.TooLarge] found, or
   *   `null` when [node] and every descendant are legal and under both [maxMoves] and
   *   [MAX_PLY_DEPTH].
   */
  private fun walk(
    fromKey: PositionKey,
    node: PgnMoveNode,
    depth: Int,
    maxMoves: Int,
    seen: MutableSet<Pair<PositionKey, String>>,
  ): RepertoireValidation? {
    if (depth > MAX_PLY_DEPTH) {
      return RepertoireValidation.TooLarge("a line goes past $MAX_PLY_DEPTH plies deep")
    }
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
      val outcome = walk(toKey, child, depth + 1, maxMoves, seen)
      if (outcome != null) return outcome
    }
    return null
  }
}
