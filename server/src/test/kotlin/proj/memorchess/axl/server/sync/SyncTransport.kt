package proj.memorchess.axl.server.sync

import kotlin.time.Instant
import proj.memorchess.axl.core.sync.SyncPullResponse
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse

/**
 * How a [TestDevice] reaches the server.
 *
 * The identity is the transport's, never the device's, which mirrors production: the caller cannot
 * name a user, so neither can a test device.
 */
internal interface SyncTransport {

  suspend fun push(request: SyncPushRequest, serverNow: Instant): SyncPushResponse

  suspend fun pull(since: Long, limit: Int, serverNow: Instant): SyncPullResponse
}

/** Calls the store directly, skipping HTTP. */
internal class StoreTransport(private val store: SyncStore, private val userId: String) :
  SyncTransport {

  override suspend fun push(request: SyncPushRequest, serverNow: Instant) =
    store.push(userId, request, serverNow)

  override suspend fun pull(since: Long, limit: Int, serverNow: Instant) =
    store.pull(userId, since, limit, serverNow)
}
