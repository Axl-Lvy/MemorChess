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
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.OutboxEntry
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.TreeStore

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

/**
 * Wires [DefaultSyncEngine] to the real push+pull cycle. `settings` outbox entries are read but not
 * yet pushed, and a pulled [SettingSyncRow] is not yet applied: [proj.memorchess.axl.core.config.SettingSyncMetadataStore]
 * has no generic "read/write this key's current value as a string" surface for an arbitrary
 * [proj.memorchess.axl.core.config.ConfigItem] to hang a remote-apply path off of, and guessing one
 * under time pressure risks silently corrupting a user's settings — a real follow-up, not something
 * this plan should paper over.
 */
fun SyncEngine(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  jobStore: SyncJobStore,
  cursorStore: SyncCursorStore,
  scope: CoroutineScope,
): SyncEngine =
  DefaultSyncEngine(jobStore, scope) {
    runSyncCycle(authProvider, database, treeStore, apiClient, cursorStore)
  }

/** Largest batch pushed in one request, matching `:server`'s own `MAX_PUSH_ROWS` cap. */
internal const val MAX_PUSH_ROWS: Int = 2_000

/** Largest page requested per pull. */
internal const val PULL_LIMIT: Int = 500

internal suspend fun runSyncCycle(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  cursorStore: SyncCursorStore,
): CycleOutcome {
  val token =
    when (val result = authProvider.accessToken()) {
      is TokenResult.Ok -> result.accessToken
      TokenResult.SignedOut -> return CycleOutcome.PausedNoAuth
      TokenResult.Failed.Terminal -> return CycleOutcome.PausedNoAuth
      TokenResult.Failed.Transient -> return CycleOutcome.Transient
    }

  pushOutbox(token, database, apiClient)?.let {
    return it
  }
  pullAll(token, treeStore, apiClient, cursorStore)?.let {
    return it
  }
  return CycleOutcome.Success
}

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pushOutbox(
  token: String,
  database: DatabaseQueryManager,
  apiClient: SyncApiClient,
): CycleOutcome? {
  val outbox = database.getOutbox()
  if (outbox.isEmpty()) return null
  for (batch in outbox.chunked(MAX_PUSH_ROWS)) {
    val request = buildPushRequest(database, batch)
    when (val outcome = apiClient.push(token, request)) {
      is SyncPushOutcome.Ok -> {
        // Every pushed entry is cleared, rejected ones included: a RejectedRow is a permanent
        // refusal per its own doc, so retrying it forever would spin the job indefinitely.
        database.clearDirty(batch)
      }
      SyncPushOutcome.Unauthorized -> return CycleOutcome.Transient
      SyncPushOutcome.TooLarge -> return CycleOutcome.Transient
      is SyncPushOutcome.Error -> {
        LOGGER.w { "Push batch failed: ${outcome.message}" }
        return CycleOutcome.Transient
      }
    }
  }
  return null
}

/** Builds one push batch's rows from the outbox entries' current local state. Skips
 * [DirtyKey.SettingKey] entries (see [SyncEngine]'s own doc) and any key whose row has since
 * disappeared from the outbox's own view of the world (nothing left to push). */
private suspend fun buildPushRequest(
  database: DatabaseQueryManager,
  batch: List<OutboxEntry>,
): SyncPushRequest {
  val nodes = mutableListOf<NodeSyncRow>()
  val edges = mutableListOf<EdgeSyncRow>()
  for (entry in batch) {
    when (val key = entry.key) {
      is DirtyKey.NodeKey -> {
        database.getPositionIncludingDeleted(key.positionKey)?.let { nodes += it.toNodeSyncRow() }
      }
      is DirtyKey.EdgeKey -> {
        localMove(database, key.origin, key.destination)?.let { edges += it.toEdgeSyncRow() }
      }
      is DirtyKey.SettingKey -> Unit
    }
  }
  return SyncPushRequest(nodes = nodes, edges = edges, settings = emptyList())
}

/** The move connecting [origin] to [destination], read off [origin]'s own denormalized map. */
private suspend fun localMove(
  database: DatabaseQueryManager,
  origin: PositionKey,
  destination: PositionKey,
): DataMove? =
  database.getPositionIncludingDeleted(origin)?.previousAndNextMoves?.nextMoves?.values?.firstOrNull {
    it.destination == destination
  }

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pullAll(
  token: String,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  cursorStore: SyncCursorStore,
): CycleOutcome? {
  var cursor = cursorStore.read()
  while (true) {
    when (val outcome = apiClient.pull(token, cursor, PULL_LIMIT)) {
      is SyncPullOutcome.Ok -> {
        val page = outcome.response
        for (node in page.nodes) treeStore.applySyncedNode(node)
        for (edge in page.edges) treeStore.applySyncedMove(edge)
        cursor = page.nextCursor
        cursorStore.write(cursor)
        if (cursor == null) return null
      }
      SyncPullOutcome.Unauthorized -> return CycleOutcome.Transient
      is SyncPullOutcome.Error -> {
        LOGGER.w { "Pull failed: ${outcome.message}" }
        return CycleOutcome.Transient
      }
    }
  }
}

private val LOGGER = Logger.withTag("SyncEngine")
