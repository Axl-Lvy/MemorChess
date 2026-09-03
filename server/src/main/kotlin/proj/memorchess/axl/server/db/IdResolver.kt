package proj.memorchess.axl.server.db

import java.sql.Connection

/**
 * The identity of a move edge as it arrives on the wire.
 *
 * @property origin Cropped FEN of the origin position.
 * @property destination Cropped FEN of the destination position.
 * @property move The move in standard algebraic notation. A pure function of the two endpoints, and
 *   therefore stored once globally rather than once per user.
 */
internal data class EdgeIdentity(val origin: String, val destination: String, val move: String)

/**
 * Interns [keys] into the shared `position` table and returns their ids.
 *
 * Runs inside the caller's transaction, so a push can resolve ids and write user rows atomically.
 * The batch is **sorted** before insertion: two concurrent transactions inserting overlapping sets
 * in different orders deadlock, and a single lock order removes the possibility. Duplicates within
 * [keys] collapse, and keys that already exist are left untouched.
 */
internal fun Connection.resolvePositionIds(keys: Collection<String>): Map<String, Long> {
  val distinct = keys.toSortedSet()
  if (distinct.isEmpty()) return emptyMap()

  prepareStatement("INSERT INTO position (position_key) VALUES (?) ON CONFLICT DO NOTHING").use {
    statement ->
    for (key in distinct) {
      statement.setString(1, key)
      statement.addBatch()
    }
    statement.executeBatch()
  }

  val ids = HashMap<String, Long>(distinct.size)
  prepareStatement("SELECT position_key, id FROM position WHERE position_key = ANY (?)").use {
    statement ->
    statement.setArray(1, createArrayOf("text", distinct.toTypedArray()))
    statement.executeQuery().use { rows ->
      while (rows.next()) ids[rows.getString(1)] = rows.getLong(2)
    }
  }
  return ids
}

/**
 * Interns [edges] into the shared `move_edge` table and returns their ids, resolving both endpoints
 * through [resolvePositionIds] first.
 *
 * Sorted for the same lock ordering reason as [resolvePositionIds]. `move` is written only on
 * insert: it is derivable from the endpoints, so an existing row's value is authoritative and is
 * never updated.
 */
internal fun Connection.resolveEdgeIds(edges: Collection<EdgeIdentity>): Map<EdgeIdentity, Long> {
  val distinct = edges.toSortedSet(compareBy({ it.origin }, { it.destination }))
  if (distinct.isEmpty()) return emptyMap()

  val positionIds = resolvePositionIds(distinct.flatMap { listOf(it.origin, it.destination) })

  prepareStatement(
      "INSERT INTO move_edge (origin_id, destination_id, move) VALUES (?, ?, ?) " +
        "ON CONFLICT DO NOTHING"
    )
    .use { statement ->
      for (edge in distinct) {
        statement.setLong(1, positionIds.getValue(edge.origin))
        statement.setLong(2, positionIds.getValue(edge.destination))
        statement.setString(3, edge.move)
        statement.addBatch()
      }
      statement.executeBatch()
    }

  val ids = HashMap<EdgeIdentity, Long>(distinct.size)
  prepareStatement("SELECT id FROM move_edge WHERE origin_id = ? AND destination_id = ?").use {
    statement ->
    for (edge in distinct) {
      statement.setLong(1, positionIds.getValue(edge.origin))
      statement.setLong(2, positionIds.getValue(edge.destination))
      statement.executeQuery().use { rows -> if (rows.next()) ids[edge] = rows.getLong(1) }
    }
  }
  return ids
}
