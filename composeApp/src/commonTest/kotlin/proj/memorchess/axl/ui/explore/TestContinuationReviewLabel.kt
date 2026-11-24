package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.drainAllNodes
import proj.memorchess.axl.ui.assertPieceMoved
import proj.memorchess.axl.ui.clickOnBack
import proj.memorchess.axl.ui.clickOnSave
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.playMove
import proj.memorchess.axl.ui.setTestContent
import proj.memorchess.axl.ui.waitUntilNodeExists
import proj.memorchess.axl.ui.waitUntilSuspending

@OptIn(ExperimentalTestApi::class)
class TestContinuationReviewLabel : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()
  private val treeStore: TreeStore by inject()

  /** Saves 1. e4 from the Explore page and leaves the board on the position after it. */
  private suspend fun ComposeUiTest.saveE4() {
    database.eraseAll()
    setTestContent { InitializeApp { Explore() } }
    playMove("e2", "e4")
    assertPieceMoved("e2", "e4", ChessPiece(PieceKind.PAWN, Player.WHITE))
    clickOnSave()
    waitUntilSuspending { drainAllNodes(database).size == 2 }
  }

  private fun runTest(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      saveE4()
      block()
    } finally {
      koinTearDown()
    }
  }

  private suspend fun setStartCardDueIn(daysFromNow: Int) {
    val now = DateUtil.now()
    val card =
      CardStateFactory.new(now)
        .copy(lastReview = now, stability = 3.0, dueDate = now + daysFromNow.days)
    treeStore.updateCardState(PositionKey.START_POSITION, card)
  }

  @Test
  fun aNeverReviewedContinuationShowsNew() = runTest {
    clickOnBack()

    waitUntilNodeExists(hasTestTag("Play e4").and(hasText("NEW"))).assertExists()
  }

  @Test
  fun aReviewedContinuationShowsItsDueDay() = runTest {
    setStartCardDueIn(3)

    clickOnBack()

    waitUntilNodeExists(hasTestTag("Play e4").and(hasText("due in 3d"))).assertExists()
  }

  @Test
  fun anOverdueContinuationShowsDueToday() = runTest {
    setStartCardDueIn(-2)

    clickOnBack()

    waitUntilNodeExists(hasTestTag("Play e4").and(hasText("due today"))).assertExists()
  }
}
