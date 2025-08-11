package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnSave
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove

@OptIn(ExperimentalTestApi::class)
class TestSaveButton : TestWithKoin {

  private val afterH3Position =
    PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/7P/PPPPPPP1/RNBQKBNR b KQkq")
  private val afterH6Position =
    PositionIdentifier("rnbqkbnr/ppppppp1/7p/8/8/7P/PPPPPPP1/RNBQKBNR w KQkq")
  private val database: DatabaseQueryManager by inject()

  fun runTestFromSetup(block: ComposeUiTest.() -> Unit) {
    runTest { database.deleteAll(DateUtil.farInThePast()) }
    runComposeUiTest {
      setContent { initializeApp { Explore() } }
      playMove("h2", "h3")
      assertPieceMoved("h2", "h3", Pawn.white())
      block()
    }
  }

  @Test
  fun testSaveGood() = runTestFromSetup {
    runTest {
      assertNull(getPosition(afterH3Position))
      var savedPosition: StoredNode? = null
      Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testSaveGood: Position not saved") {
        clickOnSave()
        savedPosition = getPosition(afterH3Position)
        savedPosition != null
      }
      check(savedPosition!!.previousAndNextMoves.previousMoves.values.all { it.isGood == true })
    }
  }

  @Test
  fun testPropagateSave() = runTestFromSetup {
    playMove("h7", "h6")
    assertPieceMoved("h7", "h6", Pawn.black())
    assertNull(getPosition(afterH3Position))
    assertNull(getPosition(afterH6Position))
    var savedLastPosition: StoredNode? = null
    var savedFirstPosition: StoredNode? = null
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testPropagateSave: Positions not saved") {
      clickOnSave()
      savedLastPosition = getPosition(afterH6Position)
      savedFirstPosition = getPosition(afterH3Position)
      savedLastPosition != null && savedFirstPosition != null
    }
    check(savedLastPosition!!.previousAndNextMoves.previousMoves.values.all { it.isGood == true })
    check(savedFirstPosition!!.previousAndNextMoves.previousMoves.values.all { it.isGood == false })
  }

  private fun getPosition(p: PositionIdentifier): StoredNode? {
    var result: StoredNode? = null
    runTest { result = database.getPosition(p) }
    return result
  }
}
