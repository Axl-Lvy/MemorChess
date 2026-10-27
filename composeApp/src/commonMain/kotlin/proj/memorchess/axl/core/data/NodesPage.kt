package proj.memorchess.axl.core.data

/**
 * One page of live nodes, with their edges, ordered by [DataNode.positionKey].
 *
 * @property nextCursor Cursor for the next [DatabaseQueryManager.getNodesPage] call, or `null` once
 *   a page comes back shorter than the limit.
 */
data class NodesPage(val nodes: List<DataNode>, val nextCursor: String?)
