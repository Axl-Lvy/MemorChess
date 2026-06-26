package proj.memorchess.axl.core.data

/**
 * One bounded page of stored nodes, ordered by [DataNode.positionKey] ascending.
 *
 * Returned by [DatabaseQueryManager.getNodesPage], the only multi row read left on the persistence
 * seam. The page never exceeds the requested limit, so the cost is constant regardless of
 * repertoire size. A consumer drains the whole store by following [nextCursor] until it is `null`.
 *
 * @property nodes The nodes in this page, each carrying its edges, ordered by position key
 *   ascending. Soft deleted rows are excluded.
 * @property nextCursor The last node's position key when a full limit sized page was returned and
 *   more rows may remain, used as the `cursor` of the next [DatabaseQueryManager.getNodesPage]
 *   call. `null` when this is the last page (fewer than limit rows were returned), which terminates
 *   the paging loop.
 */
data class NodesPage(val nodes: List<DataNode>, val nextCursor: String?)
