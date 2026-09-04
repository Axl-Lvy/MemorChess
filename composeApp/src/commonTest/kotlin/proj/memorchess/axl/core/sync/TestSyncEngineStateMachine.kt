package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.test_util.TestSettings

@OptIn(ExperimentalCoroutinesApi::class)
class TestSyncEngineStateMachine {

  private fun TestScope.engine(
    cycle: suspend () -> CycleOutcome = { CycleOutcome.Success },
    jobStore: SyncJobStore = SyncJobStore(TestSettings()),
  ) =
    DefaultSyncEngine(
      jobStore = jobStore,
      // backgroundScope: the driving loop is inherently long lived (it reschedules itself
      // indefinitely in production too), so its job must not be one runTest waits to complete.
      scope = backgroundScope,
      now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
      runCycle = cycle,
    )

  @Test fun startsIdle() = runTest { engine().status.value shouldBe SyncJobStatus.IDLE }

  @Test
  fun notifyDirtyMovesToScheduledThenRunsAfterTheDebounce() = runTest {
    val e = engine()
    e.start()

    e.notifyDirty()
    e.status.value shouldBe SyncJobStatus.SCHEDULED

    advanceTimeBy(2.seconds + 100.milliseconds)
    e.status.value shouldBe SyncJobStatus.IDLE // cycle ran and succeeded
  }

  @Test
  fun burstOfNotifyDirtyCoalescesIntoOneCycleCappedAtTenSeconds() = runTest {
    var cycles = 0
    val e =
      engine(
        cycle = {
          cycles++
          CycleOutcome.Success
        }
      )
    e.start()

    e.notifyDirty()
    advanceTimeBy(9.seconds)
    e.notifyDirty() // still inside the 10s cap from the first signal
    advanceTimeBy(2.seconds) // 11s from the first signal: must have fired by the 10s cap

    cycles shouldBe 1
  }

  @Test
  fun syncNowRunsImmediatelyRegardlessOfState() = runTest {
    var cycles = 0
    val e =
      engine(
        cycle = {
          cycles++
          CycleOutcome.Success
        }
      )
    e.start()

    e.syncNow()
    advanceTimeBy(100.milliseconds)

    cycles shouldBe 1
  }

  @Test
  fun onAppForegroundRunsImmediately() = runTest {
    var cycles = 0
    val e =
      engine(
        cycle = {
          cycles++
          CycleOutcome.Success
        }
      )
    e.start()

    e.onAppForeground()
    advanceTimeBy(100.milliseconds)

    cycles shouldBe 1
  }

  @Test
  fun transientFailureBacksOffWithGrowingDelay() = runTest {
    val e = engine(cycle = { CycleOutcome.Transient })
    e.start()

    e.syncNow()
    advanceTimeBy(100.milliseconds)
    e.status.value shouldBe SyncJobStatus.BACKING_OFF
  }

  @Test
  fun terminalAuthFailurePausesWithNoRetryTimer() = runTest {
    var cycles = 0
    val e =
      engine(
        cycle = {
          cycles++
          CycleOutcome.PausedNoAuth
        }
      )
    e.start()

    e.syncNow()
    advanceTimeBy(100.milliseconds)
    e.status.value shouldBe SyncJobStatus.PAUSED_NO_AUTH

    // No further cycle should fire on its own after a long wait.
    advanceTimeBy(600.seconds)
    cycles shouldBe 1
  }

  @Test
  fun aStoredRunningStateAtStartupIsTreatedAsCrashedAndRescheduled() = runTest {
    val jobStore = SyncJobStore(TestSettings())
    jobStore.write(SyncJobState(SyncJobStatus.RUNNING, nextAttemptAt = null, attempt = 0))
    var cycles = 0
    val e =
      engine(
        cycle = {
          cycles++
          CycleOutcome.Success
        },
        jobStore = jobStore,
      )

    e.start()
    advanceTimeBy(3.seconds)

    cycles shouldBe 1
  }

  @Test
  fun notifyDirtyDuringARunningCycleReschedulesInsteadOfDroppingTheSignal() = runTest {
    var cycles = 0
    lateinit var engineRef: DefaultSyncEngine
    val e =
      engine(
        cycle = {
          cycles++
          if (cycles == 1) engineRef.notifyDirty()
          CycleOutcome.Success
        }
      )
    engineRef = e
    e.start()

    e.syncNow()
    advanceTimeBy(100.milliseconds)
    cycles shouldBe 1
    e.status.value shouldBe SyncJobStatus.SCHEDULED

    advanceTimeBy(3.seconds)
    cycles shouldBe 2
  }
}
