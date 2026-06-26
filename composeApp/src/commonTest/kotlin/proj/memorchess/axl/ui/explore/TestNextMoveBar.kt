package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.drainAllNodes
import proj.memorchess.axl.ui.assertNextMoveExist
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnBack
import proj.memorchess.axl.ui.clickOnReset
import proj.memorchess.axl.ui.clickOnSave
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove
import proj.memorchess.axl.ui.waitUntilSuspending

@OptIn(ExperimentalTestApi::class)
class TestNextMoveBar : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()

  private suspend fun ComposeUiTest.setUp() {
    database.eraseAll()
    setContent { InitializeApp { Explore() } }
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
    clickOnSave()
    waitUntilSuspending { drainAllNodes(database).size == 2 }
  }

  private fun runTestFromSetup(block: ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      setUp()
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testNextMoveAppears() = runTestFromSetup {
    clickOnBack()
    assertNextMoveExist("e4")
  }

  @Test
  fun testNextMoveWorks() = runTestFromSetup {
    clickOnBack()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }

  @Test
  fun testMultipleNextMoves() = runTestFromSetup {
    clickOnBack()
    playMove("e2", "e3")
    assertPieceMoved("e2", "e3", ChessPiece(PieceKind.PAWN, Player.WHITE))
    clickOnSave()
    clickOnBack()
    assertNextMoveExist("e4")
    assertNextMoveExist("e3").performClick()
    assertPieceMoved("e2", "e3", ChessPiece(PieceKind.PAWN, Player.WHITE))
    clickOnBack()
    assertNextMoveExist("e3")
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }

  @Test
  fun testManyNextMovesAreAllReachable() = runTestFromSetup {
    clickOnBack()
    for (col in listOf("a", "b", "c", "d", "e", "f", "g", "h")) {
      playMove("${col}2", "${col}3")
      clickOnSave()
      clickOnBack()
      playMove("${col}2", "${col}4")
      clickOnSave()
      clickOnBack()
    }
    // The continuations now reflow as a FlowRow of chips (no hard column count), so every saved
    // move — first and last alike — is laid out and reachable without a dedicated scroll container.
    assertNextMoveExist("a3")
    assertNextMoveExist("h4").performClick()
    assertPieceMoved("h2", "h4", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }

  @Test
  fun testNextMoveAfterReset() = runTestFromSetup {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
  }
}
