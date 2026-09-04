package proj.memorchess.axl.core.sync

import com.russhwolf.settings.Settings

/**
 * Persists the last applied `/v1/sync` pull cursor, so a restart resumes the pull loop instead of
 * redownloading history. Backed by [Settings], same mechanism as [SyncJobStore].
 */
class SyncCursorStore(private val settings: Settings) {

  /** The last applied cursor, or `null` before the first successful pull. */
  fun read(): Long? = settings.getLongOrNull(KEY_CURSOR)

  /** Persists [cursor]. A `null` value clears it. */
  fun write(cursor: Long?) {
    if (cursor != null) settings.putLong(KEY_CURSOR, cursor) else settings.remove(KEY_CURSOR)
  }

  private companion object {
    const val KEY_CURSOR = "sync.cursor"
  }
}
