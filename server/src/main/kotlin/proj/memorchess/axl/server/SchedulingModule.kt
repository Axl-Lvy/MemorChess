package proj.memorchess.axl.server

import dev.inmo.krontab.doInfinityTz
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Nightly tombstone collection, at 05:00 UTC.
 *
 * Its own module rather than part of [syncModule], because every route test builds that one and
 * none of them should acquire a background job as a side effect.
 *
 * A missed run, from a restart across the hour, is harmless: nothing accumulates faster than a day
 * and the next run reclaims it all.
 */
internal fun Application.schedulingModule(store: SyncStore) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  scope.launch {
    // Seconds come first, then minutes and hours. This is not a crontab, where "0 4 * * *" would
    // mean minute 4 of every hour and fire 24 times a day. The trailing "0o" names UTC explicitly,
    // so the schedule does not follow whatever zone an unset container TZ picks.
    doInfinityTz("0 0 5 * * 0o") {
      // krontab does not catch: an exception escaping here ends this coroutine, and with it every
      // future run until the process restarts. Logging and returning keeps the schedule alive.
      try {
        store.collectTombstones()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.error("Nightly tombstone collection failed, retrying at the next run", e)
      }
    }
  }
}

private val logger = LoggerFactory.getLogger("proj.memorchess.axl.server.scheduling")
