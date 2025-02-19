package proj.ankichess.axl.core.game.parser

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.pieces.material.IPiece

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
    fenBuilder.append(parseBoard(game.board)).append(" ")
    fenBuilder.append(parsePlayer(game)).append(" ")
    fenBuilder.append(parsePossibleCastles(game)).append(" ")
    fenBuilder.append(parseEnPassant(game)).append(" ")
    fenBuilder.append(game.lastCaptureOrPawnHalfMove).append(" ")
    fenBuilder.append(game.moveCount)
    return fenBuilder.toString()
  }

  fun read(fen: String): Game {
    val splitFen = fen.split(" ")
    if (splitFen.size != 6) {
      throwOnInvalidFen(
        "This fen contains ${splitFen.size} parts but should contain exactly 6 parts: $fen"
      )
    }
    val game = Game(readBoard(splitFen[0]))
    game.playerTurn = readPlayer(splitFen[1])
    readCastle(splitFen[2], game.possibleCastle)
    game.enPassantColumn = readEnPassant(splitFen[3])
    game.lastCaptureOrPawnHalfMove = readSemiMoves(splitFen[4])
    game.moveCount = readMoves(splitFen[5])
    return game
  }

  private fun readBoard(boardFen: String): Board {
    val splitBoardFen = boardFen.split("/")
    if (splitBoardFen.size != 8) {
      throwOnInvalidFen("This fen is describing ${splitBoardFen.size} lines: $boardFen.")
    }
    val board = Board()
    for ((lineIndex, line) in splitBoardFen.reversed().withIndex()) {
      var offset = 0
      for ((columnIndex, character) in line.withIndex()) {
        if (columnIndex + offset > 7) {
          throwOnInvalidFen("Found a line representing more than 8 tiles: $line.")
        }
        if (character.isDigit()) {
          offset += character.digitToInt() - 1
        } else {
          board.placePiece(lineIndex, columnIndex + offset, character.toString())
          offset = 0
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

  private fun readCastle(castleString: String, resultArray: Array<Boolean>) {
    resultArray.fill(false)
    if (castleString == "-") {
      return
    }
    for (c in castleString) {
      when (c.toString()) {
        IPiece.KING.uppercase() -> resultArray[0] = true
        IPiece.QUEEN.uppercase() -> resultArray[1] = true
        IPiece.KING -> resultArray[2] = true
        IPiece.QUEEN -> resultArray[3] = true
      }
    }
  }

  private fun readEnPassant(enPassantString: String): Int {
    if (enPassantString == "-") {
      return -1
    }
    return Board.getColumnNumber(enPassantString[0].toString())
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
  private fun parseBoard(board: Board): String {
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

  private fun parsePlayer(game: Game): String {
    return when (game.playerTurn) {
      Game.Player.WHITE -> "w"
      Game.Player.BLACK -> "b"
    }
  }

  private fun parsePossibleCastles(game: Game): String {
    var isAllFalse = true
    val builder = StringBuilder()
    for ((index, isCastlePossible) in game.possibleCastle.withIndex()) {
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

  private fun parseEnPassant(game: Game): String {
    if (game.enPassantColumn == -1) {
      return "-"
    }
    val lineNumber =
      when (game.playerTurn) {
        Game.Player.WHITE -> 6
        Game.Player.BLACK -> 3
      }
    return Board.getColumnName(game.enPassantColumn) + lineNumber
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
