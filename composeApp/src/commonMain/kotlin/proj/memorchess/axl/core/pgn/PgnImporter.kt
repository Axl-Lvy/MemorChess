package proj.memorchess.axl.core.pgn

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.MoveInsertion
import proj.memorchess.axl.core.graph.OpeningTree
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Merges parsed PGN games into the user's opening graph.
 *
 * Every variation of every game is replayed with [GameEngine] to compute the [PositionKey] and
 * depth of each position. The whole input is validated first: an illegal move anywhere aborts the
 * import with a [PgnImportException] before anything is written, so a failed import leaves the
 * graph exactly as it was.
 *
 * Each move is classified relative to the import's `perspective` (the side whose repertoire this
 * is): a move played by that side is a repertoire move (`isGood == true`), the opponent's replies
 * are stored as `isGood == false` so they thread the graph together without ever being drilled.
 * This mirrors the alternating good/bad classification the manual `LinesExplorer` save flow
 * produces, so installing a one sided opening (e.g. the black Scandinavian) only asks the user to
 * recall their own moves. A `null` perspective marks every move good, which suits two sided content
 * such as a Lichess study.
 *
 * Moves the user already trains with the same classification are skipped, which keeps the existing
 * card state of every position intact. As a consequence, importing the same repertoire twice is a
 * no op whose summary reports every move as already present.
 *
 * The caller is responsible for loading [treeStore] before importing so that the merge sees the
 * persisted graph.
 *
 * @property treeStore Mutation chokepoint of the opening graph that receives the imported moves.
 */
class PgnImporter(private val treeStore: TreeStore) {

  /**
   * Imports [games] into the opening graph.
   *
   * @param games Parsed games, typically obtained from [PgnParser.parse].
   * @param perspective Side whose repertoire is being imported: only its moves are marked `isGood`,
   *   the opponent's replies are stored as not good. `null` marks every move good, for two sided
   *   content such as a Lichess study.
   * @return How many moves were added and how many were already present.
   * @throws PgnImportException if any move of any variation is illegal. Nothing is written in that
   *   case.
   */
  suspend fun import(games: List<PgnGame>, perspective: Player? = null): PgnImportSummary {
    val plannedMoves = planMoves(games, perspective)
    val tree = treeStore.current()
    val (alreadyPresent, movesToInsert) = plannedMoves.partition { isAlreadyPresent(tree, it) }
    treeStore.addMoves(movesToInsert)
    return PgnImportSummary(
      movesAdded = movesToInsert.size,
      movesAlreadyPresent = alreadyPresent.size,
    )
  }

  /**
   * Computes how much of [games] the user already has, without writing anything. Reads the graph
   * currently held by [treeStore], so the caller must load it first, exactly as for [import].
   *
   * @param games Parsed games, typically obtained from [PgnParser.parse].
   * @param perspective Side whose repertoire this is, classified the same way as in [import] so the
   *   overlap matches what an install would skip. See [import] for the meaning of `null`.
   * @return The repertoire size and how many of its moves are already present.
   * @throws PgnImportException if any move of any variation is illegal.
   */
  fun preview(games: List<PgnGame>, perspective: Player? = null): PgnImportPreview {
    val plannedMoves = planMoves(games, perspective)
    val tree = treeStore.current()
    val movesInCommon = plannedMoves.count { isAlreadyPresent(tree, it) }
    return PgnImportPreview(totalMoves = plannedMoves.size, movesInCommon = movesInCommon)
  }

  /**
   * Whether [move] is already in [tree] with the same classification, in which case an import would
   * leave it untouched.
   */
  private fun isAlreadyPresent(tree: OpeningTree, move: MoveInsertion): Boolean {
    val existingEdge = tree[move.from]?.outgoing?.get(move.move)
    return existingEdge != null && existingEdge.isGood == move.isGood
  }

  /**
   * Replays every variation of every game and returns the distinct moves to merge, in discovery
   * order. Throws [PgnImportException] on the first illegal move, before any write happens.
   */
  private fun planMoves(games: List<PgnGame>, perspective: Player?): List<MoveInsertion> {
    val plannedMoves = mutableListOf<MoveInsertion>()
    val seenMoves = mutableSetOf<Pair<PositionKey, String>>()
    val rootKey = GameEngine().toPositionKey()
    for ((gameIndex, game) in games.withIndex()) {
      for (firstMove in game.moves) {
        walk(rootKey, 0, firstMove, gameIndex, perspective, plannedMoves, seenMoves)
      }
    }
    return plannedMoves
  }

  /**
   * Validates [moveNode] from the position [fromKey] at [depth] plies from the start, records the
   * resulting move classified for [perspective], then recurses into every continuation.
   */
  private fun walk(
    fromKey: PositionKey,
    depth: Int,
    moveNode: PgnMoveNode,
    gameIndex: Int,
    perspective: Player?,
    plannedMoves: MutableList<MoveInsertion>,
    seenMoves: MutableSet<Pair<PositionKey, String>>,
  ) {
    val engine = GameEngine(fromKey)
    // Whose move this is, captured before playing it flips the side to move.
    val mover = engine.playerTurn
    try {
      engine.playSanMove(moveNode.san)
    } catch (e: IllegalMoveException) {
      throw illegalMove(moveNode.san, depth, gameIndex, e)
    } catch (e: IllegalArgumentException) {
      // The underlying chess library reports some illegal SAN moves this way.
      throw illegalMove(moveNode.san, depth, gameIndex, e)
    }
    val toKey = engine.toPositionKey()
    if (seenMoves.add(fromKey to moveNode.san)) {
      plannedMoves +=
        MoveInsertion(
          from = fromKey,
          move = moveNode.san,
          to = toKey,
          isGood = perspective == null || mover == perspective,
          fromDepth = depth,
        )
    }
    for (child in moveNode.children) {
      walk(toKey, depth + 1, child, gameIndex, perspective, plannedMoves, seenMoves)
    }
  }

  /** Builds the exception reported for an illegal move found during validation. */
  private fun illegalMove(
    san: String,
    depth: Int,
    gameIndex: Int,
    cause: Throwable,
  ): PgnImportException =
    PgnImportException("Illegal move $san at ply ${depth + 1} in game ${gameIndex + 1}", cause)
}
