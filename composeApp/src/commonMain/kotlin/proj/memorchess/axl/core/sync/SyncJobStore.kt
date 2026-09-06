package proj.memorchess.axl.core.sync

import com.russhwolf.settings.Settings
import kotlin.time.Instant

/**
 * State of the [SyncEngine]'s driving job, persisted so an app killed mid backoff resumes the
 * correct wait instead of retrying immediately or losing the schedule.
 */
enum class SyncJobStatus {
  /** Nothing scheduled. */
  IDLE,

  /** A cycle is scheduled to run at [SyncJobState.nextAttemptAt]. */
  SCHEDULED,

  /**
   * A cycle is in progress. A value read back as this at startup is a crash, not a resumable state:
   * see [SyncEngine]'s startup recovery.
   */
  RUNNING,

  /**
   * The previous cycle failed transiently; retrying at [SyncJobState.nextAttemptAt] with
   * exponential backoff.
   */
  BACKING_OFF,

  /**
   * The last token refresh was rejected by the issuer. No retry timer; resumes only on a fresh sign
   * in.
   */
  PAUSED_NO_AUTH,

  /**
   * The last push would have exceeded a per user storage quota. No retry timer, since retrying the
   * same outbox can never succeed on its own. A dirty write does not resume it either, same as
   * every other paused or backing off state, and doubly so here: a further local write only makes
   * the quota worse, never better. Resumes only on an explicit [SyncEngine.syncNow] or
   * [SyncEngine.onAppForeground].
   */
  PAUSED_QUOTA_EXCEEDED,
}

/** One persisted snapshot of the sync job's state. */
data class SyncJobState(val status: SyncJobStatus, val nextAttemptAt: Instant?, val attempt: Int) {
  companion object {
    /** The state a fresh install (or a just-cleared store) reads as. */
    val IDLE = SyncJobState(SyncJobStatus.IDLE, null, 0)
  }
}

/**
 * Persists [SyncEngine]'s job state across process restarts. Backed by [Settings], same mechanism
 * as [proj.memorchess.axl.core.auth.OidcTokenStore].
 */
class SyncJobStore(private val settings: Settings) {

  /** The current state, or [SyncJobState.IDLE] if nothing has ever been written. */
  fun read(): SyncJobState {
    val statusName = settings.getStringOrNull(KEY_STATUS) ?: return SyncJobState.IDLE
    val status = runCatching { SyncJobStatus.valueOf(statusName) }.getOrDefault(SyncJobStatus.IDLE)
    val nextAttemptAt =
      settings.getLongOrNull(KEY_NEXT_ATTEMPT_AT)?.let(Instant::fromEpochMilliseconds)
    val attempt = settings.getInt(KEY_ATTEMPT, 0)
    return SyncJobState(status, nextAttemptAt, attempt)
  }

  /** Persists [state], replacing whatever was stored before. */
  fun write(state: SyncJobState) {
    settings.putString(KEY_STATUS, state.status.name)
    if (state.nextAttemptAt != null) {
      settings.putLong(KEY_NEXT_ATTEMPT_AT, state.nextAttemptAt.toEpochMilliseconds())
    } else {
      settings.remove(KEY_NEXT_ATTEMPT_AT)
    }
    settings.putInt(KEY_ATTEMPT, state.attempt)
  }

  private companion object {
    const val KEY_STATUS = "sync.job.status"
    const val KEY_NEXT_ATTEMPT_AT = "sync.job.next_attempt_at"
    const val KEY_ATTEMPT = "sync.job.attempt"
  }
}
