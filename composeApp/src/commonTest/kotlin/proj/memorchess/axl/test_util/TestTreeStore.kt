package proj.memorchess.axl.test_util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.SyncApplier

/**
 * Builds a [NodeCache] over [database] on [scope].
 *
 * The default scope is unconfined, so a load and its neighbour prefetch run inline inside the call
 * that triggered them and a test observes their effect as soon as it returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testNodeCache(
  database: DatabaseQueryManager,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
): NodeCache = NodeCache({ database.getPosition(it) }, scope)

/** Builds a [TreeStore] over [database], defaulting to a fresh ephemeral [DeviceIdentity]. */
@OptIn(ExperimentalCoroutinesApi::class)
fun testTreeStore(
  database: DatabaseQueryManager,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
  deviceIdentity: DeviceIdentity = DeviceIdentity.ephemeral(),
): TreeStore {
  val cache = testNodeCache(database, scope)
  val trainable = TrainableProjection(database, cache)
  return TreeStore(
    database,
    cache,
    Prefetcher(cache, scope),
    trainable,
    RepertoireTagStore(database, deviceIdentity, trainable),
    deviceIdentity,
  )
}

/**
 * Builds a [RepertoireTagStore] over [database], defaulting to a fresh ephemeral [DeviceIdentity].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testRepertoireTagStore(
  database: DatabaseQueryManager,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
  deviceIdentity: DeviceIdentity = DeviceIdentity.ephemeral(),
): RepertoireTagStore =
  RepertoireTagStore(
    database,
    deviceIdentity,
    TrainableProjection(database, testNodeCache(database, scope)),
  )

/** Builds a [SyncApplier] over [database]. */
@OptIn(ExperimentalCoroutinesApi::class)
fun testSyncApplier(
  database: DatabaseQueryManager,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
): SyncApplier {
  val cache = testNodeCache(database, scope)
  return SyncApplier(database, cache, TrainableProjection(database, cache))
}
