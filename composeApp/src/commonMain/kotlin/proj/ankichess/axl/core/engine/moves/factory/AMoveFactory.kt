package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.moves.*
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.engine.pieces.IPiece
import proj.ankichess.axl.core.engine.pieces.Pawn
import proj.ankichess.axl.core.engine.pieces.vectors.King
import proj.ankichess.axl.core.engine.pieces.vectors.Rook
import proj.ankichess.axl.core.engine.pieces.vectors.VectorUtils

/**
 * Move factory.
 *
 * @property position The position.
 * @constructor Creates a Move factory from a board.
 */
abstract class AMoveFactory(val position: Position) {

  /**
   * Creates one move.
   *
   * @param moveDescription The move we want to check
   * @return
   */
  fun createMoveFrom(moveDescription: MoveDescription): IMove? {
    if (
      position.board.getTile(moveDescription.from).getSafePiece() is King &&
        position.board.getTile(moveDescription.to).getSafePiece() is Rook &&
        position.board.getTile(moveDescription.from).getSafePiece()!!.player ==
          position.board.getTile(moveDescription.to).getSafePiece()!!.player
    ) {
      return extractCastle(moveDescription)
    }
    val subMoves = mutableListOf<MoveDescription>()
    val subVector = moveDescription.getSubVector()
    var currentCoordinates: Pair<Int, Int>? = VectorUtils.addVector(moveDescription.from, subVector)
    while (currentCoordinates != null && currentCoordinates != moveDescription.to) {
      subMoves.add(MoveDescription(moveDescription.from, currentCoordinates))
      currentCoordinates = VectorUtils.addVector(subVector, currentCoordinates.copy())
    }
    subMoves.add(moveDescription)
    val extractedMoves = extractMoves(listOf(subMoves))
    return if (extractedMoves.size != subMoves.size) {
      null
    } else {
      extractedMoves.last()
    }
  }

  private fun extractCastle(moveDescription: MoveDescription): IMove? {
    val index =
      if (moveDescription.to.second == 0) {
        if (position.playerTurn == Game.Player.WHITE) {
          1
        } else {
          3
        }
      } else {
        if (position.playerTurn == Game.Player.WHITE) {
          0
        } else {
          2
        }
      }
    check(position.possibleCastles[index])
    val castle = Castle.castles[index]
    check(castle.isPossible(position.board))
    return castle
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
      if (movingPiece is Pawn) {
        stringMoveBuilder.append(Board.getTileName(move.destination()))
      }
    } else if (movingPiece is Pawn) {
      stringMoveBuilder.append(Board.getTileName(move.destination()).last())
    }
    if (movingPiece !is Pawn) {
      stringMoveBuilder.append(Board.getTileName(move.destination()))
    }
    return stringMoveBuilder.toString()
  }

  /**
   * Extracts moves from [move descriptions][MoveDescription].
   *
   * @param originalMoves
   * @return
   */
  fun extractMoves(originalMoves: List<List<MoveDescription>>): List<IMove> {
    if (originalMoves.isEmpty() || originalMoves[0].isEmpty()) {
      return listOf()
    }
    return if (
      (getTileAtCoords(originalMoves[0][0].from).getSafePiece()?.toString()?.lowercase() ?: "") ==
        IPiece.PAWN.lowercase()
    ) {
      extractPawnMoves(originalMoves)
    } else {
      extractPieceMoves(originalMoves)
    }
  }

  /**
   * Parses a move.
   *
   * @param stringMove Move notation.
   * @return The move.
   * @throws IllegalMoveException if the move is not possible.
   */
  fun parseMove(stringMove: String, checkChecker: ACheckChecker): IMove {
    val cleanMove =
      if (stringMove.endsWith('+') || stringMove.endsWith('#')) {
        stringMove.substring(0, stringMove.length - 1).split("=")[0]
      } else {
        stringMove.split("=")[0]
      }
    if (cleanMove == Castle.LONG_CASTLE_STRING || cleanMove == Castle.SHORT_CASTLE_STRING) {
      val index =
        if (cleanMove == Castle.LONG_CASTLE_STRING) {
          if (position.playerTurn == Game.Player.WHITE) {
            1
          } else {
            3
          }
        } else {
          if (position.playerTurn == Game.Player.WHITE) {
            0
          } else {
            2
          }
        }
      check(position.possibleCastles[index])
      val castle = Castle.castles[index]
      check(castle.isPossible(position.board))
      return castle
    }
    try {
      return if (cleanMove.length == 2 || cleanMove[0].isLowerCase()) {
        parsePawnMove(cleanMove)
      } else {
        parseGenericPieceMove(cleanMove, checkChecker)
      }
    } catch (e: IllegalStateException) {
      throw IllegalMoveException(
        "$stringMove is not a valid move. $position.playerTurn to play, of the board: \n ${position.board}",
        e,
      )
    }
  }

  /**
   * Extracts moves from a piece which is not a pawn.
   *
   * @param originalMoves Move descriptions computed by the piece.
   * @return The available moves.
   */
  private fun extractPieceMoves(originalMoves: List<List<MoveDescription>>): List<IMove> {
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
   * @return The available moves.
   */
  private fun extractPawnMoves(originalMoves: List<List<MoveDescription>>): List<IMove> {
    val moves = mutableListOf<IMove>()
    for (rawMoveList in originalMoves) {
      for (rawMove in rawMoveList) {
        val fromTile = getTileAtCoords(rawMove.from)
        if (fromTile.getSafePiece() !is Pawn) {
          break
        }
        val toTile = getTileAtCoords(rawMove.to)
        if (rawMove.from.second != rawMove.to.second) {
          extractPawnCapture(toTile, fromTile, moves, rawMove)
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
    rawMove: MoveDescription,
  ) {
    if (toTile.getSafePiece() != null) {
      moves.add(Capture(fromTile.getCoords(), toTile.getCoords()))
    } else if (
      rawMove.to.second == position.enPassantColumn &&
        rawMove.from.first == (if (fromTile.getSafePiece()?.player == Game.Player.WHITE) 4 else 3)
    ) {
      moves.add(
        EnPassant(
          fromTile.getCoords(),
          toTile.getCoords(),
          position.board
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

  private fun parsePawnMove(stringMove: String): IMove {
    val destination: Pair<Int, Int>
    val desc =
      if (!stringMove.contains('x')) {
        destination = Board.getCoords(stringMove)
        val forward = if (position.playerTurn == Game.Player.WHITE) 1 else -1
        if (
          getTileAtCoords(Pair(destination.first - forward, destination.second)).getSafePiece() ==
            null
        ) {
          MoveDescription(Pair(destination.first - 2 * forward, destination.second), destination)
        } else {
          MoveDescription(Pair(destination.first - forward, destination.second), destination)
        }
      } else {
        val originColumn = Board.getColumnNumber(stringMove[0].toString())
        destination = Board.getCoords(stringMove.substring(2))
        val forward = if (position.playerTurn == Game.Player.WHITE) 1 else -1
        MoveDescription(Pair(destination.first - forward, originColumn), destination)
      }
    return extractPawnMoves(listOf(listOf(desc))).firstOrNull()
      ?: throw IllegalMoveException("No pawn can go to " + Board.getTileName(destination) + ".")
  }

  private fun parseGenericPieceMove(stringMove: String, checkChecker: ACheckChecker): IMove {
    val destination = Board.getCoords(stringMove.substring(stringMove.length - 2))
    val pieceString =
      stringMove[0].toString().let {
        if (position.playerTurn == Game.Player.WHITE) it.uppercase() else it.lowercase()
      }
    val (clueColumn: Int?, clueRow: Int?) = extractClues(stringMove)
    val candidatePositions = position.board.piecePositionsCache[pieceString]
    checkNotNull(candidatePositions) { "No piece " + pieceString + "left on the board." }
    val candidateMoves = mutableListOf<MoveDescription>()
    for (position in candidatePositions) {
      if (clueRow != null && position.first != clueRow) {
        continue
      }
      if (clueColumn != null && position.second != clueColumn) {
        continue
      }
      val candidate = MoveDescription(position, destination)
      if (getTileAtCoords(position).getSafePiece()?.isMovePossible(candidate) == true) {
        candidateMoves.add(candidate)
      }
    }
    val moves =
      candidateMoves.mapNotNull { createMoveFrom(it) }.filter { checkChecker.isPossible(it) }
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

  abstract fun getTileAtCoords(coords: Pair<Int, Int>): ITile

  private fun ambiguityClue(movingPiece: IPiece, move: IMove): String {
    val samePiecePosition = position.board.piecePositionsCache[movingPiece.toString()]
    checkNotNull(samePiecePosition) {
      "Cache is not up to date. Couldn't find any position for $movingPiece"
    }
    var isSameColumn = false
    var isSameRow = false
    for (position in samePiecePosition) {
      val candidateMove = MoveDescription(position, move.destination())
      if (
        position != move.origin() &&
          movingPiece.isMovePossible(candidateMove) &&
          createMoveFrom(candidateMove) != null
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
