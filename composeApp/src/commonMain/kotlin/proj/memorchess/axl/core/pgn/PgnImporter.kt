package proj.memorchess.axl.core.pgn

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.graph.MoveInsertion
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Merges parsed PGN games into the user's opening graph.
 *
 * Every variation of every game is replayed with [GameEngine] to compute the [PositionKey] and
 * depth of each position. The whole input is validated first: an illegal move anywhere aborts the
 * import with a [PgnImportException] before anything is written, so a failed import leaves the
 * graph exactly as it was.
 *
 * Valid moves are inserted through [TreeStore] as repertoire moves (`isGood == true`) in a single
 * batch. Moves the user already trains are skipped, which keeps the existing card state of every
 * position intact. As a consequence, importing the same repertoire twice is a no op whose summary
 * reports every move as already present.
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
   * @return How many moves were added and how many were already present.
   * @throws PgnImportException if any move of any variation is illegal. Nothing is written in that
   *   case.
   */
  suspend fun import(games: List<PgnGame>): PgnImportSummary {
    val plannedMoves = planMoves(games)
    val tree = treeStore.current()
    val movesToInsert = mutableListOf<MoveInsertion>()
    var alreadyPresent = 0
    for (plannedMove in plannedMoves) {
      val existingEdge = tree[plannedMove.from]?.outgoing?.get(plannedMove.move)
      if (existingEdge != null && existingEdge.isGood == true) {
        alreadyPresent++
      } else {
        movesToInsert += plannedMove
      }
    }
    treeStore.addMoves(movesToInsert)
    return PgnImportSummary(movesAdded = movesToInsert.size, movesAlreadyPresent = alreadyPresent)
  }

  /**
   * Replays every variation of every game and returns the distinct moves to merge, in discovery
   * order. Throws [PgnImportException] on the first illegal move, before any write happens.
   */
  private fun planMoves(games: List<PgnGame>): List<MoveInsertion> {
    val plannedMoves = mutableListOf<MoveInsertion>()
    val seenMoves = mutableSetOf<Pair<PositionKey, String>>()
    val rootKey = GameEngine().toPositionKey()
    for ((gameIndex, game) in games.withIndex()) {
      for (firstMove in game.moves) {
        walk(rootKey, 0, firstMove, gameIndex, plannedMoves, seenMoves)
      }
    }
    return plannedMoves
  }

  /**
   * Validates [moveNode] from the position [fromKey] at [depth] plies from the start, records the
   * resulting move, then recurses into every continuation.
   */
  private fun walk(
    fromKey: PositionKey,
    depth: Int,
    moveNode: PgnMoveNode,
    gameIndex: Int,
    plannedMoves: MutableList<MoveInsertion>,
    seenMoves: MutableSet<Pair<PositionKey, String>>,
  ) {
    val engine = GameEngine(fromKey)
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
          isGood = true,
          fromDepth = depth,
        )
    }
    for (child in moveNode.children) {
      walk(toKey, depth + 1, child, gameIndex, plannedMoves, seenMoves)
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
