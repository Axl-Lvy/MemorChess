package proj.ankichess.axl.core.impl.engine.parser

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.board.Board
import proj.ankichess.axl.core.impl.engine.board.Position
import proj.ankichess.axl.core.intf.engine.board.IBoard
import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

/**
 * Fen parser to create or read [games][Game].
 *
 * See also [Wikipedia](https://fr.wikipedia.org/wiki/Notation_Forsyth-Edwards)
 */
object FenParser {

  /**
   * Reads a game.
   *
   * @param game The game.
   */
  fun parse(game: Game): String {
    val fenBuilder = StringBuilder()
    fenBuilder.append(parsePosition(game.position)).append(" ")
    fenBuilder.append(game.lastCaptureOrPawnHalfMove).append(" ")
    fenBuilder.append(game.moveCount)
    return fenBuilder.toString()
  }

  /**
   * Reads an [IPosition].
   *
   * @param position The position to read.
   * @return The string representation of the position.
   */
  fun parsePosition(position: IPosition): String {
    val fenBuilder = StringBuilder()
    fenBuilder.append(parseBoard(position.board)).append(" ")
    fenBuilder.append(parsePlayer(position.playerTurn)).append(" ")
    fenBuilder.append(parsePossibleCastles(position.possibleCastles)).append(" ")
    fenBuilder.append(parseEnPassant(position))
    return fenBuilder.toString()
  }

  /**
   * Creates an [Game] from a string.
   *
   * @param fen Fen representation of the game.
   * @return The created game.
   */
  fun read(fen: String): Game {
    val splitFen = fen.split(" ")
    if (splitFen.size != 6) {
      throwOnInvalidFen(
        "This fen contains ${splitFen.size} parts but should contain exactly 6 parts: $fen"
      )
    }
    val position = readPosition(fen)
    val game = Game(position)
    game.lastCaptureOrPawnHalfMove = readSemiMoves(splitFen[4])
    game.moveCount = readMoves(splitFen[5])
    return game
  }

  /**
   * Creates an [IPosition] from a string.
   *
   * @param fen Fen representation of the position.
   * @return The created position.
   */
  fun readPosition(fen: String): IPosition {
    val splitFen = fen.split(" ")
    if (splitFen.size < 3) {
      throwOnInvalidFen(
        "This fen contains ${splitFen.size} parts but should contain exactly 6 parts: $fen"
      )
    }
    val board = readBoard(splitFen[0])
    val playerTurn = readPlayer(splitFen[1])
    val possibleCastles = readCastle(splitFen[2])
    val enPassantColumn = if (splitFen.size > 3) readEnPassant(splitFen[3]) else -1
    return Position(board, playerTurn, possibleCastles, enPassantColumn)
  }

  private fun readBoard(boardFen: String): IBoard {
    val splitBoardFen = boardFen.split("/")
    if (splitBoardFen.size != 8) {
      throwOnInvalidFen("This fen is describing ${splitBoardFen.size} lines: $boardFen.")
    }
    val board = Board()
    for ((lineIndex, line) in splitBoardFen.reversed().withIndex()) {
      var columnIndex = 0
      for (character in line) {
        if (columnIndex > 7) {
          throwOnInvalidFen("Found a line representing more than 8 tiles: $line.")
        }
        if (character.isDigit()) {
          columnIndex += character.digitToInt()
        } else {
          board.placePiece(lineIndex, columnIndex, character.toString())
          columnIndex++
        }
      }
    }
    return board
  }

  private fun readPlayer(playerString: String): Game.Player {
    return when (playerString) {
      "w" -> Game.Player.WHITE
      "b" -> Game.Player.BLACK
      else -> throwOnInvalidFen("Invalid player $playerString")
    }
  }

  private fun readCastle(castleString: String): Array<Boolean> {
    val resultArray = arrayOf(false, false, false, false)
    if (castleString == "-") {
      return resultArray
    }
    for (c in castleString) {
      when (c.toString()) {
        IPiece.KING.uppercase() -> resultArray[0] = true
        IPiece.QUEEN.uppercase() -> resultArray[1] = true
        IPiece.KING -> resultArray[2] = true
        IPiece.QUEEN -> resultArray[3] = true
      }
    }
    return resultArray
  }

  private fun readEnPassant(enPassantString: String): Int {
    if (enPassantString == "-") {
      return -1
    }
    return IBoard.getColumnNumber(enPassantString[0].toString())
  }

  private fun readSemiMoves(semiMoveString: String): Int {
    val semiMoves =
      semiMoveString.toIntOrNull()
        ?: throwOnInvalidFen("Number of half moves should be an integer. Found $semiMoveString.")
    if (semiMoves < 0) {
      throwOnInvalidFen("Number of half moves should be positive. Found $semiMoves")
    }
    return semiMoves
  }

  private fun readMoves(semiMoveString: String): Int {
    val moves =
      semiMoveString.toIntOrNull()
        ?: throwOnInvalidFen("Number of moves should be an integer. Found $semiMoveString.")
    if (moves < 0) {
      throwOnInvalidFen("Number of moves should be positive. Found $moves")
    }
    return moves
  }

  /**
   * Reads a board.
   *
   * @param board The board.
   * @return The string representing the board.
   */
  private fun parseBoard(board: IBoard): String {
    val sequence = StringBuilder()
    for (rowIndex in 7 downTo 0) {
      if (rowIndex < 7) {
        sequence.append("/")
      }
      var hole = 0
      for (colIndex in 0..7) {
        val piece = board.getTile(rowIndex, colIndex).getSafePiece()
        if (piece == null) {
          hole++
        } else {
          if (hole > 0) {
            sequence.append(hole)
            hole = 0
          }
          sequence.append(piece.toString())
        }
      }
      if (hole > 0) {
        sequence.append(hole)
      }
    }
    return sequence.toString()
  }

  private fun parsePlayer(player: Game.Player): String {
    return when (player) {
      Game.Player.WHITE -> "w"
      Game.Player.BLACK -> "b"
    }
  }

  private fun parsePossibleCastles(possibleCastle: Array<Boolean>): String {
    var isAllFalse = true
    val builder = StringBuilder()
    for ((index, isCastlePossible) in possibleCastle.withIndex()) {
      if (isCastlePossible) {
        builder.append(
          when (index) {
            0 -> IPiece.KING.uppercase()
            1 -> IPiece.QUEEN.uppercase()
            2 -> IPiece.KING
            3 -> IPiece.QUEEN
            else -> error("Array should be of size 4.")
          }
        )
        isAllFalse = false
      }
    }
    if (isAllFalse) {
      return "-"
    }
    return builder.toString()
  }

  private fun parseEnPassant(position: IPosition): String {
    if (position.enPassantColumn == -1) {
      return "-"
    }
    val lineNumber =
      when (position.playerTurn) {
        Game.Player.WHITE -> 6
        Game.Player.BLACK -> 3
      }
    return IBoard.getColumnName(position.enPassantColumn) + lineNumber
  }

  /**
   * Throws on invalid fen with appropriate message.
   *
   * @param message Message.
   */
  private fun throwOnInvalidFen(message: String): Nothing {
    val throwMessageBuilder = StringBuilder()
    throwMessageBuilder
      .append("Invalid fen. ")
      .append(message)
      .append(" See https://fr.wikipedia.org/wiki/Notation_Forsyth-Edwards")
    throw IllegalArgumentException(throwMessageBuilder.toString())
  }
}
