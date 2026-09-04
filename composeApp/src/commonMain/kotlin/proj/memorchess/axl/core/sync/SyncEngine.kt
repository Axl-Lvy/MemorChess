package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the sync push+pull cycle: debounces local writes into one attempt, retries a transient
 * failure with backoff, and pauses entirely when the session has no valid auth. See the sync design
 * doc section 4 for the full state machine.
 */
interface SyncEngine {
  /** Current job status, for the UI. */
  val status: StateFlow<SyncJobStatus>

  /** Starts the driving loop. Call once at app startup. */
  fun start()

  /** A local write happened. Non-suspending and safe to call from any thread/scope. */
  fun notifyDirty()

  /** The app came to the foreground; runs a cycle immediately. */
  fun onAppForeground()

  /** User requested an immediate sync; runs a cycle immediately. */
  fun syncNow()
}

/**
 * Outcome of one push+pull cycle, decoupled from HTTP/token specifics so the state machine can be
 * tested without a real [proj.memorchess.axl.core.auth.AuthProvider] or [SyncApiClient].
 */
internal sealed class CycleOutcome {
  data object Success : CycleOutcome()

  data object Transient : CycleOutcome()

  data object PausedNoAuth : CycleOutcome()
}

/**
 * @param jobStore Persists state across restarts.
 * @param scope Coroutine scope the driving loop runs on.
 * @param now Clock, injectable for tests.
 * @param runCycle The actual push+pull cycle; internal so only this module wires it.
 */
internal class DefaultSyncEngine(
  private val jobStore: SyncJobStore,
  private val scope: CoroutineScope,
  private val now: () -> Instant = { Clock.System.now() },
  private val runCycle: suspend () -> CycleOutcome,
) : SyncEngine {

  private val _status = MutableStateFlow(SyncJobStatus.IDLE)
  override val status: StateFlow<SyncJobStatus> = _status.asStateFlow()

  private var burstStartedAt: Instant? = null
  private var timerJob: Job? = null
  private var pendingRetriggerDuringRun = false

  override fun start() {
    val stored = jobStore.read()
    val recovered =
      if (stored.status == SyncJobStatus.RUNNING) {
        // Nothing can have been mid cycle across a process restart: a stored RUNNING is a crash,
        // not a resumable state.
        SyncJobState(SyncJobStatus.SCHEDULED, now(), attempt = stored.attempt)
      } else {
        stored
      }
    jobStore.write(recovered)
    _status.value = recovered.status
    if (recovered.status == SyncJobStatus.SCHEDULED) {
      scheduleTimer(recovered.nextAttemptAt ?: now())
    }
  }

  override fun notifyDirty() {
    when (_status.value) {
      SyncJobStatus.IDLE -> {
        burstStartedAt = now()
        schedule(DEBOUNCE)
      }
      SyncJobStatus.SCHEDULED -> {
        val cap = (burstStartedAt ?: now()) + BURST_CAP
        val proposed = now() + DEBOUNCE
        schedule(if (proposed < cap) DEBOUNCE else cap - now())
      }
      SyncJobStatus.RUNNING -> {
        // Handled when the in-flight cycle finishes: onCycleFinished re-checks this flag and
        // reschedules a fresh burst instead of dropping the signal.
        pendingRetriggerDuringRun = true
      }
      SyncJobStatus.BACKING_OFF,
      SyncJobStatus.PAUSED_NO_AUTH -> Unit // own timer/pause governs
    }
  }

  override fun onAppForeground() = runNow()

  override fun syncNow() = runNow()

  private fun runNow() {
    timerJob?.cancel()
    launchCycle()
  }

  private fun schedule(delayFromNow: Duration) {
    val at = now() + delayFromNow
    setState(SyncJobState(SyncJobStatus.SCHEDULED, at, attempt = jobStore.read().attempt))
    scheduleTimer(at)
  }

  private fun scheduleTimer(at: Instant) {
    timerJob?.cancel()
    timerJob =
      scope.launch {
        val wait = at - now()
        if (wait > Duration.ZERO) delay(wait)
        launchCycle()
      }
  }

  private fun launchCycle() {
    setState(SyncJobState(SyncJobStatus.RUNNING, null, attempt = jobStore.read().attempt))
    scope.launch {
      pendingRetriggerDuringRun = false
      val outcome =
        try {
          runCycle()
        } catch (e: Exception) {
          LOGGER.w(e) { "Sync cycle threw" }
          CycleOutcome.Transient
        }
      onCycleFinished(outcome)
    }
  }

  private fun onCycleFinished(outcome: CycleOutcome) {
    when (outcome) {
      CycleOutcome.Success -> {
        if (pendingRetriggerDuringRun) {
          burstStartedAt = now()
          schedule(DEBOUNCE)
        } else {
          burstStartedAt = null
          setState(SyncJobState.IDLE)
        }
      }
      CycleOutcome.Transient -> {
        val attempt = jobStore.read().attempt + 1
        val backoff = minOf(2.0.pow(attempt).seconds, MAX_BACKOFF)
        val at = now() + backoff
        setState(SyncJobState(SyncJobStatus.BACKING_OFF, at, attempt))
        scheduleTimer(at)
      }
      CycleOutcome.PausedNoAuth -> setState(SyncJobState(SyncJobStatus.PAUSED_NO_AUTH, null, 0))
    }
  }

  private fun setState(state: SyncJobState) {
    jobStore.write(state)
    _status.value = state.status
  }

  private companion object {
    val DEBOUNCE = 2.seconds
    val BURST_CAP = 10.seconds
    val MAX_BACKOFF = 5.minutes
  }
}

private fun Double.pow(exp: Int): Double {
  var result = 1.0
  repeat(exp) { result *= this }
  return result
}

private val LOGGER = Logger.withTag("SyncEngine")
