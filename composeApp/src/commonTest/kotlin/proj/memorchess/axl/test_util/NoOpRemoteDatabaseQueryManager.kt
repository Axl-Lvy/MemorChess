package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.data.NoOpDatabaseQueryManager
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.UnlinkedStoredNode
import proj.memorchess.axl.core.data.online.database.RemoteDatabaseQueryManager

class NoOpRemoteDatabaseQueryManager : NoOpDatabaseQueryManager(), RemoteDatabaseQueryManager {
  override suspend fun insertMoves(moves: List<StoredMove>) {}

  override suspend fun insertUnlinkedStoredNodes(nodes: List<UnlinkedStoredNode>) {
    // Nothing to do
  }
}
