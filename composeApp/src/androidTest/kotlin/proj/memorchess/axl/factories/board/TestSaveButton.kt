package proj.memorchess.axl.factories.board

import kotlin.test.assertNull
import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.Awaitility

class TestSaveButton : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(::testSaveGood, ::testSaveBad, ::testPropagateSave)
  }

  override fun needsDatabaseReset(): Boolean = true

  private val afterH3Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/7P/PPPPPPP1/RNBQKBNR b KQkq")
  private val afterH6Position = PositionKey("rnbqkbnr/ppppppp1/7p/8/8/7P/PPPPPPP1/RNBQKBNR w KQkq")

  override fun beforeEach() {
    goToExplore()
    playMove("h2", "h3")
  }

  fun testSaveGood() {
    assertNull(getPosition(afterH3Position))
    clickOnSaveGood()
    var savedPosition: StoredNode? = null
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) {
      savedPosition = getPosition(afterH3Position)
      savedPosition != null
    }
    check(savedPosition!!.previousMoves.all { it.isGood == true })
  }

  fun testSaveBad() {
    assertNull(getPosition(afterH3Position))
    clickOnSaveBad()
    var savedPosition: StoredNode? = null
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) {
      savedPosition = getPosition(afterH3Position)
      savedPosition != null
    }
    check(savedPosition!!.previousMoves.all { it.isGood != true })
  }

  fun testPropagateSave() {
    playMove("h7", "h6")
    assertNull(getPosition(afterH3Position))
    assertNull(getPosition(afterH6Position))
    clickOnSaveBad()
    var savedLastPosition: StoredNode? = null
    var savedFirstPosition: StoredNode? = null
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) {
      savedLastPosition = getPosition(afterH6Position)
      savedFirstPosition = getPosition(afterH3Position)
      savedLastPosition != null && savedFirstPosition != null
    }
    check(savedLastPosition!!.previousMoves.all { it.isGood != true })
    check(savedFirstPosition!!.previousMoves.all { it.isGood == true })
  }
}
