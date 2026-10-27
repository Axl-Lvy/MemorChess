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
 * Runs tombstone collection nightly at 05:00 UTC. Kept out of [syncModule] so that route tests,
 * which all build it, start no background job.
 */
internal fun Application.schedulingModule(store: SyncStore) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  scope.launch {
    // Not a crontab: seconds come first. The trailing "0o" pins UTC whatever the container TZ.
    doInfinityTz("0 0 5 * * 0o") {
      // krontab does not catch: an escaping exception would end every future run.
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
