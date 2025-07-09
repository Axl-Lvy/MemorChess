package proj.memorchess.axl.explore

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.AUiTestFromMainActivity
import proj.memorchess.axl.utils.Awaitility

class TestSaveButton : AUiTestFromMainActivity() {

  private val afterH3Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/7P/PPPPPPP1/RNBQKBNR b KQkq")
  private val afterH6Position = PositionKey("rnbqkbnr/ppppppp1/7p/8/8/7P/PPPPPPP1/RNBQKBNR w KQkq")

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToExplore()
    playMove("h2", "h3")
  }

  @Test
  fun testSaveGood() {
    assertNull(getPosition(afterH3Position))
    var savedPosition: StoredNode? = null
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testSaveGood: Position not saved") {
      clickOnSave()
      savedPosition = getPosition(afterH3Position)
      savedPosition != null
    }
    check(savedPosition!!.previousAndNextMoves.previousMoves.values.all { it.isGood == true })
  }

  @Test
  fun testPropagateSave() {
    playMove("h7", "h6")
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
}
