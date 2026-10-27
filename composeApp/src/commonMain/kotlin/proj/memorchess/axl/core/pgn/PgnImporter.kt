package proj.memorchess.axl.core.pgn

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.IllegalMoveException
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.MoveInsertion
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore

/**
 * Merges parsed PGN games into the user's opening graph. The whole input is validated before
 * anything is written, and moves already held with the same marking are skipped, keeping their card
 * state.
 */
class PgnImporter(
  private val treeStore: TreeStore,
  private val tagStore: RepertoireTagStore,
) {

  /**
   * Imports [games] into the opening graph.
   *
   * @param perspective Side whose repertoire this is: only its moves are marked good, the
   *   opponent's replies thread the graph without being drilled. `null` marks every move good, for
   *   two sided content such as a Lichess study.
   * @param repertoireId When not `null`, tags every planned move with it, including moves already
   *   present.
   * @param onProgress Reaches `1f` only once the database write and tagging have completed.
   * @throws PgnImportException if any move is illegal, or if [repertoireId] is registered with a
   *   [RepertoireColor] that conflicts with [perspective]. Nothing is written in either case.
   */
  suspend fun import(
    games: List<PgnGame>,
    perspective: Player? = null,
    repertoireId: String? = null,
    onProgress: (Float) -> Unit = {},
  ): PgnImportSummary {
    if (repertoireId != null) checkPerspectiveCompatible(repertoireId, perspective)
    val plannedMoves = planMoves(games, perspective, onProgress)
    val (alreadyPresent, movesToInsert) = plannedMoves.partition { isAlreadyPresent(it) }
    treeStore.addMoves(movesToInsert)
    if (repertoireId != null) {
      for (move in plannedMoves) tagStore.tag(move.from, move.to, repertoireId)
    }
    onProgress(1f)
    return PgnImportSummary(
      movesAdded = movesToInsert.size,
      movesAlreadyPresent = alreadyPresent.size,
    )
  }

  /** Throws [PgnImportException] when [perspective] conflicts with [repertoireId]'s color. */
  private suspend fun checkPerspectiveCompatible(repertoireId: String, perspective: Player?) {
    val existing = tagStore.repertoires().firstOrNull { it.id == repertoireId } ?: return
    val existingColor = existing.color ?: return
    val incomingColor = perspective?.toRepertoireColor() ?: return
    if (existingColor != incomingColor) {
      throw PgnImportException(
        "Repertoire '$repertoireId' is registered as $existingColor, cannot extend it with a " +
          "$incomingColor import"
      )
    }
  }

  /**
   * Computes how much of [games] the user already has, without writing anything.
   *
   * @param perspective Classifies moves as in [import], so the overlap matches what it would skip.
   * @throws PgnImportException if any move is illegal.
   */
  suspend fun preview(games: List<PgnGame>, perspective: Player? = null): PgnImportPreview {
    val plannedMoves = planMoves(games, perspective)
    val movesInCommon = plannedMoves.count { isAlreadyPresent(it) }
    return PgnImportPreview(totalMoves = plannedMoves.size, movesInCommon = movesInCommon)
  }

  /**
   * Whether the user already holds [move] manually with the same marking. A move only an installed
   * repertoire holds does not count: the import gives it a manual hold.
   */
  private suspend fun isAlreadyPresent(move: MoveInsertion): Boolean {
    val existingEdge = treeStore.node(move.from)?.outgoing?.get(move.move)
    return existingEdge != null && existingEdge.ownIsGood == move.isGood
  }

  /** Replays every variation of every game and returns the distinct moves to merge, in order. */
  private fun planMoves(
    games: List<PgnGame>,
    perspective: Player?,
    onProgress: (Float) -> Unit = {},
  ): List<MoveInsertion> {
    val plannedMoves = mutableListOf<MoveInsertion>()
    val seenMoves = mutableSetOf<Pair<PositionKey, String>>()
    val rootKey = GameEngine().toPositionKey()
    if (games.isEmpty()) {
      return plannedMoves
    }
    for ((gameIndex, game) in games.withIndex()) {
      for (firstMove in game.moves) {
        walk(rootKey, 0, firstMove, gameIndex, perspective, plannedMoves, seenMoves)
      }
      onProgress((gameIndex + 1).toFloat() / (games.size + 1))
    }
    return plannedMoves
  }

  /**
   * Validates and records [moveNode] played from [fromKey], then recurses into its continuations.
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
    // Captured before playing the move flips the side to move.
    val mover = engine.playerTurn
    // Stored as the board spells it, so a PGN's over-disambiguated "Ngf3" matches a played "Nf3".
    val san =
      try {
        engine.playSanMove(moveNode.san)
      } catch (e: IllegalMoveException) {
        throw illegalMove(moveNode.san, depth, gameIndex, e)
      } catch (e: IllegalArgumentException) {
        // The underlying chess library reports some illegal SAN moves this way.
        throw illegalMove(moveNode.san, depth, gameIndex, e)
      }
    val toKey = engine.toPositionKey()
    if (seenMoves.add(fromKey to san)) {
      plannedMoves +=
        MoveInsertion(
          from = fromKey,
          move = san,
          to = toKey,
          isGood = perspective == null || mover == perspective,
          fromDepth = depth,
        )
    }
    for (child in moveNode.children) {
      walk(toKey, depth + 1, child, gameIndex, perspective, plannedMoves, seenMoves)
    }
  }

  private fun illegalMove(
    san: String,
    depth: Int,
    gameIndex: Int,
    cause: Throwable,
  ): PgnImportException =
    PgnImportException("Illegal move $san at ply ${depth + 1} in game ${gameIndex + 1}", cause)
}

private fun Player.toRepertoireColor(): RepertoireColor =
  when (this) {
    Player.WHITE -> RepertoireColor.WHITE
    Player.BLACK -> RepertoireColor.BLACK
  }
