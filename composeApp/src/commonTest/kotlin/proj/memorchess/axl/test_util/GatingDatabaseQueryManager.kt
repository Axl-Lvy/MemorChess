package proj.memorchess.axl.test_util

import kotlinx.coroutines.CompletableDeferred
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey

/**
 * [DatabaseQueryManager] holding [getPosition] for [gatedKey] open until [gate] completes. The row
 * is read before the gate, so the load carries the state from before any interleaved mutation.
 */
class GatingDatabaseQueryManager(
  private val delegate: DatabaseQueryManager,
  private val gatedKey: PositionKey,
) : DatabaseQueryManager by delegate {

  val gate: CompletableDeferred<Unit> = CompletableDeferred()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    val row = delegate.getPosition(positionKey)
    if (positionKey == gatedKey) gate.await()
    return row
  }
}
