package proj.memorchess.axl.test_util

import kotlinx.coroutines.CompletableDeferred
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey

/**
 * [DatabaseQueryManager] decorator that holds [getPosition] open for [gatedKey] until [gate]
 * completes, so a test can interleave a mutation with a load already in flight. Every other
 * operation delegates unchanged.
 *
 * The row is read **before** the gate, so the gated load carries the state the database held at the
 * moment it started rather than the state the interleaved mutation left behind.
 *
 * @property gate Released by the test to let the gated load finish.
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
