package proj.memorchess.axl.ui.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.ui.assertNextMoveExist
import proj.memorchess.axl.ui.assertNodeWithTagExists
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnBack
import proj.memorchess.axl.ui.clickOnReset
import proj.memorchess.axl.ui.clickOnSave
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove

@OptIn(ExperimentalTestApi::class)
class TestNextMoveBar : TestWithKoin {

  private val database: DatabaseQueryManager by inject()

  private fun ComposeUiTest.setUp() {
    runTest { database.deleteAll(DateUtil.farInThePast()) }
    setContent { initializeApp { Explore() } }
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", Pawn.white())
    clickOnSave()
    Awaitility.awaitUntilTrue {
      var size = 0
      runTest { size = database.getAllNodes(false).size }
      size == 2
    }
  }

  fun runTestFromSetup(block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
      setUp()
      block()
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
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testMultipleNextMoves() = runTestFromSetup {
    clickOnBack()
    playMove("e2", "e3")
    assertPieceMoved("e2", "e3", Pawn.white())
    clickOnSave()
    clickOnBack()
    assertNextMoveExist("e4")
    assertNextMoveExist("e3").performClick()
    assertPieceMoved("e2", "e3", Pawn.white())
    clickOnBack()
    assertNextMoveExist("e3")
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }

  @Test
  fun testScrollToLastNextMove() = runTestFromSetup {
    clickOnBack()
    for (col in listOf("a", "b", "c", "d", "e", "f", "g", "h")) {
      playMove("${col}2", "${col}3")
      clickOnSave()
      clickOnBack()
      playMove("${col}2", "${col}4")
      clickOnSave()
      clickOnBack()
    }
    assertNextMoveExist("a3")
    assertNodeWithTagExists("NextMovesBar")
      .performScrollToNode(hasTestTag(getNextMoveDescription("h4")))
    assertNextMoveExist("h4").performClick()
    assertPieceMoved("h2", "h4", Pawn.white())
  }

  @Test
  fun testNextMoveAfterReset() = runTestFromSetup {
    clickOnReset()
    assertNextMoveExist("e4").performClick()
    assertPieceMoved("e2", "e4", Pawn.white())
  }
}
