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

  /**
   * A push would exceed the server's per user storage quota. Distinct from [Transient]: retrying
   * the same outbox can never succeed until the user frees space, so [DefaultSyncEngine] pauses
   * instead of backing off.
   */
  data object QuotaExceeded : CycleOutcome()

  /**
   * The server refused this device until it wipes its synced local state. Internal to the cycle:
   * [DefaultSyncEngine] never sees it, because the cycle performs the wipe itself and reports
   * [Transient] so the next attempt starts clean.
   */
  data object ResyncRequired : CycleOutcome()
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
    // A foreground hook can begin a cycle before this runs, because a resume effect fires during
    // composition while this call is dispatched. That cycle is in flight, not a crashed one, and
    // recovering over it would leave a second cycle running beside it. A stored RUNNING from a
    // previous process is still recovered below, since a fresh engine starts IDLE.
    if (_status.value == SyncJobStatus.RUNNING) return
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
    when (recovered.status) {
      // BACKING_OFF too: its timer lived only in the dead process, and notifyDirty deliberately
      // ignores dirt in that state, so without this the device would run no cycle at all.
      SyncJobStatus.SCHEDULED,
      SyncJobStatus.BACKING_OFF -> scheduleTimer(recovered.nextAttemptAt ?: now())
      // An app relaunched into IDLE with a clean outbox would otherwise run no cycle at all, and a
      // device that never pulls holds its user's garbage collection watermark down forever.
      SyncJobStatus.IDLE -> armHeartbeat()
      else -> Unit
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
      SyncJobStatus.PAUSED_NO_AUTH,
      SyncJobStatus.PAUSED_QUOTA_EXCEEDED -> Unit // own timer/pause governs
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
    timerJob = scope.launch {
      val wait = at - now()
      if (wait > Duration.ZERO) delay(wait)
      launchCycle()
    }
  }

  private fun launchCycle() {
    // The single place a cycle starts, which is why the guard lives here rather than in runNow:
    // scheduleTimer calls this straight after its delay with no suspension point in between, so a
    // cancel landing after that delay returns would not stop it.
    if (_status.value == SyncJobStatus.RUNNING) {
      pendingRetriggerDuringRun = true
      return
    }
    setState(SyncJobState(SyncJobStatus.RUNNING, null, attempt = jobStore.read().attempt))
    // Cleared here rather than inside the launched body: the engine is RUNNING from the line
    // above, so a signal arriving before that body is dispatched sets the flag, and clearing it
    // in there would drop it.
    pendingRetriggerDuringRun = false
    scope.launch {
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
          armHeartbeat()
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
      CycleOutcome.QuotaExceeded ->
        setState(SyncJobState(SyncJobStatus.PAUSED_QUOTA_EXCEEDED, null, 0))
      // Never reaches here: runSyncCycle performs the wipe itself and reports Transient. Handled
      // rather than merged into Transient so the compiler flags a future path that does escape.
      CycleOutcome.ResyncRequired -> onCycleFinished(CycleOutcome.Transient)
    }
  }

  /**
   * Schedules the next heartbeat cycle.
   *
   * Only ever fires out of `IDLE`. `BACKING_OFF` keeps its own timer, and the two paused states
   * have none by design: a device parked in either one is legitimately frozen and only `syncNow` or
   * `onAppForeground` leaves them.
   */
  private fun armHeartbeat() {
    timerJob?.cancel()
    timerJob = scope.launch {
      delay(HEARTBEAT)
      if (_status.value == SyncJobStatus.IDLE) launchCycle()
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
 * yet pushed, and a pulled [SettingSyncRow] is not yet applied:
 * [proj.memorchess.axl.core.config.SettingSyncMetadataStore] has no generic "read/write this key's
 * current value as a string" surface for an arbitrary [proj.memorchess.axl.core.config.ConfigItem]
 * to hang a remote-apply path off of, and guessing one under time pressure risks silently
 * corrupting a user's settings — a real follow-up, not something this plan should paper over.
 */
fun SyncEngine(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  jobStore: SyncJobStore,
  deviceIdentity: DeviceIdentity,
  scope: CoroutineScope,
): SyncEngine =
  DefaultSyncEngine(jobStore, scope) {
    runSyncCycle(authProvider, database, treeStore, apiClient, deviceIdentity)
  }

/**
 * How often an otherwise idle app runs a cycle.
 *
 * Its only constraint is staying comfortably shorter than the interval between the server's
 * collection runs, since it sets the floor on how stale a watermark can be when collection reads
 * it.
 */
internal val HEARTBEAT = 30.minutes

/** Largest batch pushed in one request, matching `:server`'s own `MAX_PUSH_ROWS` cap. */
internal const val MAX_PUSH_ROWS: Int = 2_000

/** Largest page requested per pull. */
internal const val PULL_LIMIT: Int = 500

internal suspend fun runSyncCycle(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  deviceIdentity: DeviceIdentity,
): CycleOutcome {
  val token =
    when (val result = authProvider.accessToken()) {
      is TokenResult.Ok -> result.accessToken
      TokenResult.SignedOut -> return CycleOutcome.PausedNoAuth
      TokenResult.Failed.Terminal -> return CycleOutcome.PausedNoAuth
      TokenResult.Failed.Transient -> return CycleOutcome.Transient
    }

  val deviceId = deviceIdentity.originDevice
  var resyncRequired =
    registerDevice(token, deviceId, apiClient)?.let {
      if (it != CycleOutcome.ResyncRequired) return it
      true
    } ?: false

  if (!resyncRequired) {
    pushOutbox(token, deviceId, database, apiClient)?.let {
      if (it != CycleOutcome.ResyncRequired) return it
      resyncRequired = true
    }
  }

  if (!resyncRequired) {
    pullAll(token, treeStore, apiClient, deviceId)?.let {
      if (it != CycleOutcome.ResyncRequired) return it
      resyncRequired = true
    }
  }

  if (resyncRequired) {
    return resync(token, deviceId, database, treeStore, apiClient)
  }
  return CycleOutcome.Success
}

/**
 * Registers this device, which gates both push and pull.
 *
 * `null` on success, a [CycleOutcome] to stop the cycle otherwise.
 */
private suspend fun registerDevice(
  token: String,
  deviceId: String,
  apiClient: SyncApiClient,
): CycleOutcome? =
  when (val outcome = apiClient.registerDevice(token, deviceId, currentPlatform(), false)) {
    SyncRegisterOutcome.Ok -> null
    SyncRegisterOutcome.Unauthorized -> CycleOutcome.Transient
    SyncRegisterOutcome.RateLimited -> CycleOutcome.Transient
    SyncRegisterOutcome.ResyncRequired -> CycleOutcome.ResyncRequired
    is SyncRegisterOutcome.Error -> {
      LOGGER.w { "Register failed: ${outcome.message}" }
      CycleOutcome.Transient
    }
  }

/**
 * Throws away everything this device holds from sync and starts it over at nothing seen.
 *
 * The wipe is reported to the server rather than assumed by it: a device that died between the
 * refusal and the wipe would otherwise pull the whole dataset on top of stale rows, and a local row
 * whose tombstone is already gone would survive that merge and could be pushed back.
 */
private suspend fun resync(
  token: String,
  deviceId: String,
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
): CycleOutcome {
  LOGGER.w { "Server asked this device to resync from scratch" }
  treeStore.eraseAll()
  database.clearDirty(database.getOutbox())
  val reported = apiClient.registerDevice(token, deviceId, currentPlatform(), afterReset = true)
  if (reported != SyncRegisterOutcome.Ok) {
    LOGGER.w { "Reporting the wipe failed with $reported" }
  }
  // Transient either way. The wipe is already done locally, and a cycle that could not report it
  // is refused again next time and reports it then.
  return CycleOutcome.Transient
}

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pushOutbox(
  token: String,
  deviceId: String,
  database: DatabaseQueryManager,
  apiClient: SyncApiClient,
): CycleOutcome? {
  val outbox = database.getOutbox()
  if (outbox.isEmpty()) return null
  for (batch in outbox.chunked(MAX_PUSH_ROWS)) {
    val request = buildPushRequest(deviceId, database, batch)
    when (val outcome = apiClient.push(token, request)) {
      is SyncPushOutcome.Ok -> {
        // Every pushed entry is cleared, rejected ones included: a RejectedRow is a permanent
        // refusal per its own doc, so retrying it forever would spin the job indefinitely.
        database.clearDirty(batch)
      }
      SyncPushOutcome.Unauthorized -> return CycleOutcome.Transient
      SyncPushOutcome.TooLarge -> return CycleOutcome.Transient
      SyncPushOutcome.RateLimited -> return CycleOutcome.Transient
      SyncPushOutcome.ResyncRequired -> return CycleOutcome.ResyncRequired
      SyncPushOutcome.QuotaExceeded -> {
        LOGGER.w { "Push refused: per user quota exceeded" }
        return CycleOutcome.QuotaExceeded
      }
      is SyncPushOutcome.Error -> {
        LOGGER.w { "Push batch failed: ${outcome.message}" }
        return CycleOutcome.Transient
      }
    }
  }
  return null
}

/**
 * Builds one push batch's rows from the outbox entries' current local state. Skips
 * [DirtyKey.SettingKey] entries (see [SyncEngine]'s own doc) and any key whose row has since
 * disappeared from the outbox's own view of the world (nothing left to push).
 */
private suspend fun buildPushRequest(
  deviceId: String,
  database: DatabaseQueryManager,
  batch: List<OutboxEntry>,
): SyncPushRequest {
  val nodes = mutableListOf<NodeSyncRow>()
  val edges = mutableListOf<EdgeSyncRow>()
  val repertoires = mutableListOf<RepertoireSyncRow>()
  val tags = mutableListOf<EdgeRepertoireTagSyncRow>()
  for (entry in batch) {
    when (val key = entry.key) {
      is DirtyKey.NodeKey -> {
        database.getPositionIncludingDeleted(key.positionKey)?.let { nodes += it.toNodeSyncRow() }
      }
      is DirtyKey.EdgeKey -> {
        localMove(database, key.origin, key.destination)?.let { edges += it.toEdgeSyncRow() }
      }
      is DirtyKey.SettingKey -> Unit
      is DirtyKey.RepertoireKey -> {
        database.getRepertoireIncludingDeleted(key.repertoireId)?.let {
          repertoires += it.toRepertoireSyncRow()
        }
      }
      is DirtyKey.TagKey -> {
        database.getTagIncludingDeleted(key.origin, key.destination, key.repertoireId)?.let {
          tags += it.toEdgeRepertoireTagSyncRow()
        }
      }
    }
  }
  return SyncPushRequest(
    nodes = nodes,
    edges = edges,
    settings = emptyList(),
    repertoires = repertoires,
    tags = tags,
    device = deviceId,
  )
}

/** The move connecting [origin] to [destination], read off [origin]'s own denormalized map. */
private suspend fun localMove(
  database: DatabaseQueryManager,
  origin: PositionKey,
  destination: PositionKey,
): DataMove? =
  database
    .getPositionIncludingDeleted(origin)
    ?.previousAndNextMoves
    ?.nextMoves
    ?.values
    ?.firstOrNull { it.destination == destination }

/** Applies every row of one pulled [page] to [treeStore]. */
private suspend fun applyPulledPage(page: SyncPullResponse, treeStore: TreeStore) {
  for (node in page.nodes) treeStore.applySyncedNode(node)
  for (edge in page.edges) treeStore.applySyncedMove(edge)
  for (repertoire in page.repertoires) treeStore.applySyncedRepertoire(repertoire)
  for (tag in page.tags) treeStore.applySyncedTag(tag)
}

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pullAll(
  token: String,
  treeStore: TreeStore,
  apiClient: SyncApiClient,
  deviceId: String,
): CycleOutcome? {
  // The token of the page this device has written, sent back so the server may confirm it. Null on
  // the first request of a cycle, because nothing has been committed in it yet.
  var ack: String? = null
  while (true) {
    when (val outcome = apiClient.pull(token, deviceId, ack, PULL_LIMIT)) {
      is SyncPullOutcome.Ok -> {
        val page = outcome.response
        // The request that comes back empty is the one confirming the last page carrying rows,
        // which is why the loop cannot stop on the page that carried them.
        if (page.isEmpty()) return null
        applyPulledPage(page, treeStore)
        ack = page.pageToken
      }
      SyncPullOutcome.Unauthorized -> return CycleOutcome.Transient
      SyncPullOutcome.RateLimited -> return CycleOutcome.Transient
      SyncPullOutcome.ResyncRequired -> return CycleOutcome.ResyncRequired
      is SyncPullOutcome.Error -> {
        LOGGER.w { "Pull failed: ${outcome.message}" }
        return CycleOutcome.Transient
      }
    }
  }
}

/** Whether this page carried no rows at all, which is what terminates the pull loop. */
private fun SyncPullResponse.isEmpty(): Boolean =
  nodes.isEmpty() &&
    edges.isEmpty() &&
    settings.isEmpty() &&
    repertoires.isEmpty() &&
    tags.isEmpty()

private val LOGGER = Logger.withTag("SyncEngine")
