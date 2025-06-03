package proj.ankichess.axl.core.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals

class TestBoardPrinter {

  private companion object {
    private const val EMPTY_BOARD =
      "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------"
    private const val STARTING_POSITION =
      "-----------------\n" +
        "|r|n|b|q|k|b|n|r|\n" +
        "-----------------\n" +
        "|p|p|p|p|p|p|p|p|\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "| | | | | | | | |\n" +
        "-----------------\n" +
        "|P|P|P|P|P|P|P|P|\n" +
        "-----------------\n" +
        "|R|N|B|Q|K|B|N|R|\n" +
        "-----------------"
  }

  @Test
  fun printEmptyBoard() {
    val board = Board()
    assertEquals(EMPTY_BOARD, board.toString())
  }

  @Test
  fun printStartingPosition() {
    val board = Board.createFromStartingPosition()
    board.startingPosition()
    assertEquals(STARTING_POSITION, board.toString())
  }

  @Test
  fun testReset() {
    val board = Board.createFromStartingPosition()
    board.reset()
    assertEquals(EMPTY_BOARD, board.toString())
  }
}
