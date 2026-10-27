package proj.memorchess.axl.core.sync

import proj.memorchess.axl.core.auth.AccessToken
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.TokenResult
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.OutboxEntry
import proj.memorchess.axl.core.data.PositionKey

/**
 * Drives the sync push+pull cycle: debounces local writes into one attempt, retries a transient
 * failure with backoff, and pauses entirely when the session has no valid auth.
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

  /**
   * Erases synced data on the server then on this device (this device only when signed out), then
   * syncs. Never deletes locally if the server erase failed; waits for a running cycle.
   */
  suspend fun eraseEverywhere(): EraseOutcome
}

/** Outcome of one push+pull cycle, decoupled from HTTP and token specifics. */
internal sealed class CycleOutcome {
  data object Success : CycleOutcome()

  data object Transient : CycleOutcome()

  data object PausedNoAuth : CycleOutcome()

  /** A push would exceed the storage quota; retrying cannot help until the user frees space. */
  data object QuotaExceeded : CycleOutcome()

  /**
   * The server refused this device until it wipes its synced state. Never escapes the cycle, which
   * wipes and reports [Transient].
   */
  data object ResyncRequired : CycleOutcome()
}

internal class DefaultSyncEngine(
  private val jobStore: SyncJobStore,
  private val scope: CoroutineScope,
  private val now: () -> Instant = { Clock.System.now() },
  private val runErase: suspend () -> EraseOutcome = { EraseOutcome.Failed },
  private val runCycle: suspend () -> CycleOutcome,
) : SyncEngine {

  private val _status = MutableStateFlow(SyncJobStatus.IDLE)
  override val status: StateFlow<SyncJobStatus> = _status.asStateFlow()

  private var burstStartedAt: Instant? = null
  private var timerJob: Job? = null
  private var pendingRetriggerDuringRun = false

  // Held by a running cycle and by an erase, so an erase never interleaves with a pull or a push.
  private val cycleMutex = Mutex()

  override fun start() {
    // A resume effect can start a cycle before this runs; recovering over it would run a second
    // one. A RUNNING stored by a dead process is still recovered, since a fresh engine starts IDLE.
    if (_status.value == SyncJobStatus.RUNNING) return
    val stored = jobStore.read()
    val recovered =
      if (stored.status == SyncJobStatus.RUNNING) {
        // A stored RUNNING means the previous process crashed mid cycle.
        SyncJobState(SyncJobStatus.SCHEDULED, now(), attempt = stored.attempt)
      } else {
        stored
      }
    jobStore.write(recovered)
    _status.value = recovered.status
    when (recovered.status) {
      // BACKING_OFF's timer died with the old process, and notifyDirty ignores dirt in that state.
      SyncJobStatus.SCHEDULED,
      SyncJobStatus.BACKING_OFF -> scheduleTimer(recovered.nextAttemptAt ?: now())
      // A device that never pulls holds its user's garbage collection watermark down forever.
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
        // onCycleFinished reschedules a fresh burst instead of dropping the signal.
        pendingRetriggerDuringRun = true
      }
      SyncJobStatus.BACKING_OFF,
      SyncJobStatus.PAUSED_NO_AUTH,
      SyncJobStatus.PAUSED_QUOTA_EXCEEDED -> Unit // own timer/pause governs
    }
  }

  override fun onAppForeground() = runNow()

  override fun syncNow() = runNow()

  override suspend fun eraseEverywhere(): EraseOutcome {
    val outcome = cycleMutex.withLock { runErase() }
    // Pulls the erase's own tombstones right away: garbage collection purges them, and frees the
    // quota they hold, only once every device has acknowledged them, this one included.
    if (outcome == EraseOutcome.Erased) syncNow()
    return outcome
  }

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
    // Guarded here, not in runNow: scheduleTimer calls this right after its delay with no
    // suspension point, so a cancel landing then would not stop it.
    if (_status.value == SyncJobStatus.RUNNING) {
      pendingRetriggerDuringRun = true
      return
    }
    setState(SyncJobState(SyncJobStatus.RUNNING, null, attempt = jobStore.read().attempt))
    // Cleared here, not in the launched body, so a signal arriving before dispatch is kept.
    pendingRetriggerDuringRun = false
    scope.launch {
      val outcome =
        try {
          cycleMutex.withLock { runCycle() }
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
      // Unreachable: runSyncCycle wipes and reports Transient itself.
      CycleOutcome.ResyncRequired -> onCycleFinished(CycleOutcome.Transient)
    }
  }

  /** Schedules the next heartbeat cycle, which only fires out of `IDLE`. */
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
 * Creates a [SyncEngine] running the real push+pull cycle. Settings are neither pushed nor applied
 * from pulls.
 */
fun SyncEngine(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  applier: SyncApplier,
  apiClient: SyncApiClient,
  jobStore: SyncJobStore,
  deviceIdentity: DeviceIdentity,
  scope: CoroutineScope,
): SyncEngine =
  DefaultSyncEngine(
    jobStore,
    scope,
    runErase = { runErase(authProvider, applier, apiClient, deviceIdentity, jobStore) },
  ) {
    runSyncCycle(authProvider, database, applier, apiClient, deviceIdentity, jobStore)
  }

/** How often an idle app runs a cycle; must stay well below the server's collection interval. */
internal val HEARTBEAT = 30.minutes

/** Largest batch pushed in one request, matching `:server`'s own `MAX_PUSH_ROWS` cap. */
internal const val MAX_PUSH_ROWS: Int = 2_000

/** Largest page requested per pull. */
internal const val PULL_LIMIT: Int = 500

internal suspend fun runSyncCycle(
  authProvider: AuthProvider,
  database: DatabaseQueryManager,
  applier: SyncApplier,
  apiClient: SyncApiClient,
  deviceIdentity: DeviceIdentity,
  jobStore: SyncJobStore,
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
    registerDevice(token, deviceId, apiClient, jobStore)?.let {
      if (it != CycleOutcome.ResyncRequired) return it
      true
    } ?: false

  var quotaExceeded = false
  if (!resyncRequired) {
    pushOutbox(token, deviceId, database, apiClient)?.let {
      when (it) {
        CycleOutcome.ResyncRequired -> resyncRequired = true
        // Still pulls: a pull never raises quota use, and garbage collection frees quota only
        // once every device has acknowledged the tombstones.
        CycleOutcome.QuotaExceeded -> quotaExceeded = true
        else -> return it
      }
    }
  }

  if (!resyncRequired) {
    pullAll(token, applier, apiClient, deviceId)?.let {
      if (it != CycleOutcome.ResyncRequired) return it
      resyncRequired = true
    }
  }

  if (resyncRequired) {
    return resync(token, deviceId, database, applier, apiClient)
  }
  return if (quotaExceeded) CycleOutcome.QuotaExceeded else CycleOutcome.Success
}

/**
 * Registers this device, reporting and then clearing [SyncJobStore.resetPending]. `null` on
 * success, else a [CycleOutcome] to stop the cycle.
 */
private suspend fun registerDevice(
  token: AccessToken,
  deviceId: DeviceId,
  apiClient: SyncApiClient,
  jobStore: SyncJobStore,
): CycleOutcome? {
  val afterReset = jobStore.resetPending
  return when (
    val outcome = apiClient.registerDevice(token, deviceId, currentPlatform(), afterReset)
  ) {
    SyncRegisterOutcome.Ok -> {
      if (afterReset) jobStore.resetPending = false
      null
    }
    SyncRegisterOutcome.Unauthorized -> CycleOutcome.Transient
    SyncRegisterOutcome.RateLimited -> CycleOutcome.Transient
    SyncRegisterOutcome.ResyncRequired -> CycleOutcome.ResyncRequired
    is SyncRegisterOutcome.Error -> {
      LOGGER.w { "Register failed: ${outcome.message}" }
      CycleOutcome.Transient
    }
  }
}

/**
 * Wipes everything this device holds from sync and reports the wipe rather than letting the server
 * assume it, so a device dying before the wipe never merges a full pull into stale rows.
 */
private suspend fun resync(
  token: AccessToken,
  deviceId: DeviceId,
  database: DatabaseQueryManager,
  applier: SyncApplier,
  apiClient: SyncApiClient,
): CycleOutcome {
  LOGGER.w { "Server asked this device to resync from scratch" }
  applier.eraseAll()
  database.clearDirty(database.getOutbox())
  val reported = apiClient.registerDevice(token, deviceId, currentPlatform(), afterReset = true)
  if (reported != SyncRegisterOutcome.Ok) {
    LOGGER.w { "Reporting the wipe failed with $reported" }
  }
  // Transient either way: an unreported wipe is refused again next cycle and reported then.
  return CycleOutcome.Transient
}

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pushOutbox(
  token: AccessToken,
  deviceId: DeviceId,
  database: DatabaseQueryManager,
  apiClient: SyncApiClient,
): CycleOutcome? {
  val outbox = database.getOutbox()
  if (outbox.isEmpty()) return null
  for (batch in outbox.chunked(MAX_PUSH_ROWS)) {
    val request = buildPushRequest(deviceId, database, batch)
    when (val outcome = apiClient.push(token, request)) {
      is SyncPushOutcome.Ok -> {
        // Rejected rows are cleared too: a RejectedRow is permanent, so a retry would spin forever.
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
 * Builds one push batch from the outbox entries' current local state, skipping settings and rows
 * that no longer exist.
 */
private suspend fun buildPushRequest(
  deviceId: DeviceId,
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
        localMove(database, key.origin, key.destination)?.toEdgeSyncRow()?.let { edges += it }
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

private suspend fun applyPulledPage(page: SyncPullResponse, applier: SyncApplier) {
  for (node in page.nodes) applier.applyNode(node)
  for (edge in page.edges) applier.applyMove(edge)
  for (repertoire in page.repertoires) applier.applyRepertoire(repertoire)
  for (tag in page.tags) applier.applyTag(tag)
}

/** `null` on success; a [CycleOutcome] to stop the whole cycle on failure. */
private suspend fun pullAll(
  token: AccessToken,
  applier: SyncApplier,
  apiClient: SyncApiClient,
  deviceId: DeviceId,
): CycleOutcome? {
  // Token of the last page written locally, for the server to confirm; null until one is.
  var ack: String? = null
  while (true) {
    when (val outcome = apiClient.pull(token, deviceId, ack, PULL_LIMIT)) {
      is SyncPullOutcome.Ok -> {
        val page = outcome.response
        // Only the empty response confirms the last page with rows, so the loop cannot stop sooner.
        if (page.isEmpty()) return null
        applyPulledPage(page, applier)
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

private fun SyncPullResponse.isEmpty(): Boolean =
  nodes.isEmpty() &&
    edges.isEmpty() &&
    settings.isEmpty() &&
    repertoires.isEmpty() &&
    tags.isEmpty()

private val LOGGER = Logger.withTag("SyncEngine")
