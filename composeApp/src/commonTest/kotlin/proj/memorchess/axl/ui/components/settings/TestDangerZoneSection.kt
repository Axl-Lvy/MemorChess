package proj.memorchess.axl.ui.components.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_erase_failed
import memorchess.composeapp.generated.resources.settings_erase_sign_in_required
import org.jetbrains.compose.resources.getString
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.core.sync.EraseOutcome
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.core.sync.SyncJobStatus
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.components.settings.sections.DangerZoneSection

@OptIn(ExperimentalTestApi::class)
class TestDangerZoneSection : TestWithKoin() {

  private class ScriptedSyncEngine(private val outcome: EraseOutcome) : SyncEngine {
    var eraseCalls = 0
      private set

    override val status: StateFlow<SyncJobStatus> = MutableStateFlow(SyncJobStatus.IDLE)

    override fun start() {}

    override fun notifyDirty() {}

    override fun onAppForeground() {}

    override fun syncNow() {}

    override suspend fun eraseEverywhere(): EraseOutcome {
      eraseCalls++
      return outcome
    }
  }

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      block()
    } finally {
      koinTearDown()
    }
  }

  private fun ComposeUiTest.eraseWith(
    outcome: EraseOutcome,
    streakTracker: StreakTracker = StreakTracker(InMemoryDailyActivityStore()),
  ): ScriptedSyncEngine {
    val engine = ScriptedSyncEngine(outcome)
    setContent {
      InitializeApp {
        DangerZoneSection(syncEngine = engine, streakTracker = streakTracker, onReset = {})
      }
    }
    onNodeWithTag("eraseAllDataButton").performClick()
    onNodeWithTag("confirmDialogOkButton").performClick()
    waitForIdle()
    return engine
  }

  @Test
  fun confirmingEraseCallsTheEngine() = runTestFromSetup {
    val engine = eraseWith(EraseOutcome.Erased)

    engine.eraseCalls shouldBe 1
  }

  @Test
  fun aFailedEraseShowsTheErrorAndKeepsTheStreak() = runTestFromSetup {
    val streakTracker = StreakTracker(InMemoryDailyActivityStore())
    streakTracker.recordReview()

    eraseWith(EraseOutcome.Failed, streakTracker)

    onNodeWithTag("erase_error").assertIsDisplayed()
    onNodeWithTag("erase_error").assertTextEquals(getString(Res.string.settings_erase_failed))
    streakTracker.cardsCompletedToday() shouldBe 1
  }

  @Test
  fun aSignInRequiredEraseShowsTheSignInMessageAndKeepsTheStreak() = runTestFromSetup {
    val streakTracker = StreakTracker(InMemoryDailyActivityStore())
    streakTracker.recordReview()

    eraseWith(EraseOutcome.SignInRequired, streakTracker)

    onNodeWithTag("erase_error")
      .assertTextEquals(getString(Res.string.settings_erase_sign_in_required))
    streakTracker.cardsCompletedToday() shouldBe 1
  }

  @Test
  fun aSuccessfulEraseWipesTheStreakAndShowsNoError() = runTestFromSetup {
    val streakTracker = StreakTracker(InMemoryDailyActivityStore())
    streakTracker.recordReview()

    eraseWith(EraseOutcome.Erased, streakTracker)

    streakTracker.cardsCompletedToday() shouldBe 0
    onNodeWithTag("erase_error").assertDoesNotExist()
  }
}
