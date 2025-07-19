package proj.memorchess.axl.core.data.online.database

import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.UnlinkedStoredNode

/** Remote database query manager. */
interface RemoteDatabaseQueryManager : DatabaseQueryManager {

  suspend fun insertMoves(moves: List<StoredMove>)

  suspend fun insertUnlinkedStoredNodes(nodes: List<UnlinkedStoredNode>)
}
