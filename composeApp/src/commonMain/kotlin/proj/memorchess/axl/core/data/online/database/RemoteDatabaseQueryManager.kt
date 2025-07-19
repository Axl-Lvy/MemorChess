package proj.memorchess.axl.core.data.online.database

import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.UnlinkedStoredNode

/** Remote database query manager. */
interface RemoteDatabaseQueryManager : DatabaseQueryManager {

  /**
   * Insert multiple moves
   *
   * @param moves moves to insert
   */
  suspend fun insertMoves(moves: List<StoredMove>)

  /**
   * Insert multiple unlinked stored nodes.
   *
   * @param nodes nodes to insert
   */
  suspend fun insertUnlinkedStoredNodes(nodes: List<UnlinkedStoredNode>)
}
