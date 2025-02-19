package proj.ankichess.axl.core.game.moves.factory

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.board.ITile
import proj.ankichess.axl.core.game.moves.*
import proj.ankichess.axl.core.game.moves.description.ClassicMoveDescription
import proj.ankichess.axl.core.game.pieces.IPiece
import proj.ankichess.axl.core.game.pieces.Pawn

/**
 * Move factory.
 *
 * @property board The board.
 * @constructor Creates a Move factory from a board.
 */
abstract class AMoveFactory(val board: Board) {

  /**
   * Extracts moves from a piece which is not a pawn.
   *
   * @param originalMoves Move descriptions computed by the piece.
   * @return The available moves.
   */
  fun extractMoves(originalMoves: List<List<ClassicMoveDescription>>): List<IMove> {
    val moves = mutableListOf<IMove>()
    for (rawMoveList in originalMoves) {
      for (rawMove in rawMoveList) {
        val coords = rawMove.from
        val fromTile = getTileAtCoords(coords)
        val toTile = getTileAtCoords(rawMove.to)
        if (toTile.getSafePiece() != null) {
          if (toTile.getSafePiece()?.player != fromTile.getSafePiece()?.player) {
            moves.add(Capture(fromTile.getCoords(), toTile.getCoords()))
          }
          break
        } else {
          moves.add(ClassicMove(fromTile.getCoords(), toTile.getCoords()))
        }
      }
    }
    return moves
  }

  /**
   * Extracts pawn moves
   *
   * @param originalMoves Move descriptions computed by the pawn.
   * @param enPassantColumn En passant column.
   * @return The available moves.
   */
  fun extractPawnMoves(
    originalMoves: List<List<ClassicMoveDescription>>,
    enPassantColumn: Int,
  ): List<IMove> {
    val moves = mutableListOf<IMove>()
    for (rawMoveList in originalMoves) {
      for (rawMove in rawMoveList) {
        val fromTile = getTileAtCoords(rawMove.from)
        if (fromTile.getSafePiece() !is Pawn) {
          break
        }
        val toTile = getTileAtCoords(rawMove.to)
        if (rawMove.from.second != rawMove.to.second) {
          extractPawnCapture(toTile, fromTile, moves, rawMove, enPassantColumn)
          break
        }
        if (toTile.getSafePiece() != null) {
          break
        } else {
          moves.add(ClassicMove(fromTile.getCoords(), toTile.getCoords()))
        }
      }
    }
    return moves
  }

  /**
   * Extracts capture from a pawn move description.
   *
   * @param toTile Destination.
   * @param fromTile Origin.
   * @param moves Moves list where to put the result.
   * @param rawMove Move description.
   * @param enPassantColumn En passant column.
   */
  private fun extractPawnCapture(
    toTile: ITile,
    fromTile: ITile,
    moves: MutableList<IMove>,
    rawMove: ClassicMoveDescription,
    enPassantColumn: Int,
  ) {
    if (toTile.getSafePiece() != null) {
      moves.add(Capture(fromTile.getCoords(), toTile.getCoords()))
    } else if (
      rawMove.to.second == enPassantColumn &&
        rawMove.from.first == (if (fromTile.getSafePiece()?.player == Game.Player.WHITE) 4 else 3)
    ) {
      moves.add(
        EnPassant(
          fromTile.getCoords(),
          toTile.getCoords(),
          board
            .getTile(
              rawMove.to.first +
                if (fromTile.getSafePiece()?.player == Game.Player.WHITE) -1 else 1,
              rawMove.to.second,
            )
            .getCoords(),
        )
      )
    }
  }

  fun parseMove(stringMove: String, playerTurn: Game.Player, enPassantColumn: Int): IMove {
    val cleanMove =
      if (stringMove.endsWith('+') || stringMove.endsWith('#')) {
        stringMove.substring(0, stringMove.length - 1)
      } else {
        stringMove
      }
    if (cleanMove == Castle.LONG_CASTLE_STRING) {
      return if (playerTurn == Game.Player.WHITE) Castle.castles[1] else Castle.castles[3]
    }
    if (cleanMove == Castle.SHORT_CASTLE_STRING) {
      return if (playerTurn == Game.Player.WHITE) Castle.castles[0] else Castle.castles[2]
    }
    return if (cleanMove.length == 2 || cleanMove[0].isLowerCase()) {
      parsePawnMove(cleanMove, playerTurn, enPassantColumn)
    } else {
      parseGenericPieceMove(cleanMove, playerTurn)
    }
  }

  private fun parsePawnMove(
    stringMove: String,
    playerTurn: Game.Player,
    enPassantColumn: Int,
  ): IMove {
    val desc =
      if (!stringMove.contains('x')) {
        val destination = Board.getCoords(stringMove)
        val forward = if (playerTurn == Game.Player.WHITE) 1 else -1
        if (
          getTileAtCoords(Pair(destination.first - forward, destination.second)).getSafePiece() ==
            null
        ) {
          ClassicMoveDescription(
            Pair(destination.first - 2 * forward, destination.second),
            destination,
          )
        } else {
          ClassicMoveDescription(Pair(destination.first - forward, destination.second), destination)
        }
      } else {
        val originColumn = Board.getColumnNumber(stringMove[0].toString())
        val destination = Board.getCoords(stringMove.substring(2))
        val forward = if (playerTurn == Game.Player.WHITE) 1 else -1
        ClassicMoveDescription(Pair(destination.first - forward, originColumn), destination)
      }
    return extractPawnMoves(listOf(listOf(desc)), enPassantColumn).firstOrNull()
      ?: error("No valid pawn moves found.")
  }

  private fun parseGenericPieceMove(stringMove: String, playerTurn: Game.Player): IMove {
    val destination = Board.getCoords(stringMove.substring(stringMove.length - 2))
    val pieceString =
      stringMove[0].toString().let {
        if (playerTurn == Game.Player.WHITE) it.uppercase() else it.lowercase()
      }
    val (clueColumn: Int?, clueRow: Int?) = extractClues(stringMove)
    val candidatePositions = board.piecePositionsCache[pieceString]
    checkNotNull(candidatePositions) { "No piece " + pieceString + "left on the board." }
    val candidateMoves = mutableListOf<List<ClassicMoveDescription>>()
    for (position in candidatePositions) {
      if (clueRow != null && position.first != clueRow) {
        continue
      }
      if (clueColumn != null && position.second != clueRow) {
        continue
      }
      val candidate = ClassicMoveDescription(position, destination)
      if (getTileAtCoords(position).getSafePiece()?.isMovePossible(candidate) == true) {
        candidateMoves.add(listOf(candidate))
      }
    }
    val moves = extractMoves(candidateMoves)
    check(moves.size == 1) { "Found ${moves.size} possible moves with $stringMove" }
    return moves[0]
  }

  private fun extractClues(stringMove: String): Pair<Int?, Int?> {
    val clues = stringMove.substring(1, stringMove.length - 2).substringBefore('x')
    val clueColumn: Int?
    val clueRow: Int?
    if (clues.length == 2) {
      val clueTile = Board.getCoords(clues)
      clueRow = clueTile.first
      clueColumn = clueTile.second
    } else if (clues.length == 1) {
      if (clues.toIntOrNull() != null) {
        clueRow = clues.toInt() - 1
        clueColumn = null
      } else {
        clueRow = null
        clueColumn = Board.getColumnNumber(clues)
      }
    } else {
      clueRow = null
      clueColumn = null
    }
    return Pair(clueColumn, clueRow)
  }

  fun stringifyMove(move: IMove): String {
    (move as? Castle)?.let {
      return if (move.isLong()) Castle.LONG_CASTLE_STRING else Castle.SHORT_CASTLE_STRING
    }

    (move as? EnPassant)?.let {
      return Board.getColumnName(move.from.second) + "x" + Board.getTileName(move.to)
    }

    val stringMoveBuilder = StringBuilder()
    val movingPiece = getTileAtCoords(move.origin()).getSafePiece()
    checkNotNull(movingPiece) { "Can't create a move from an empty tile." }
    if (movingPiece is Pawn) {
      stringMoveBuilder.append(Board.getColumnName(move.origin().second))
    } else {
      stringMoveBuilder.append(movingPiece.toString().uppercase())
      stringMoveBuilder.append(ambiguityClue(movingPiece, move))
    }
    if (move is Capture) {
      stringMoveBuilder.append("x")
    }
    stringMoveBuilder.append(Board.getTileName(move.destination()))
    return stringMoveBuilder.toString()
  }

  abstract fun getTileAtCoords(coords: Pair<Int, Int>): ITile

  private fun ambiguityClue(movingPiece: IPiece, move: IMove): String {
    val samePiecePosition = board.piecePositionsCache[movingPiece.toString()]
    checkNotNull(samePiecePosition) {
      "Cache is not up to date. Couldn't find any position for $movingPiece"
    }
    var isSameColumn = false
    var isSameRow = false
    for (position in samePiecePosition) {
      if (
        position != move.origin() &&
          extractMoves(listOf(listOf(ClassicMoveDescription(position, move.destination()))))
            .isNotEmpty()
      ) {
        if (position.first == move.origin().first) {
          isSameRow = true
        } else {
          isSameColumn = true
        }
      }
    }
    val ambiguityBuilder = StringBuilder()
    if (isSameRow) {
      ambiguityBuilder.append(Board.getColumnName(move.origin().second))
    }
    if (isSameColumn) {
      ambiguityBuilder.append(move.origin().first)
    }
    return ambiguityBuilder.toString()
  }
}
