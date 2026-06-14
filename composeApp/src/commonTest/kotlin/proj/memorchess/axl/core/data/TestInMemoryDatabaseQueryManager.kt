package proj.memorchess.axl.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Direct branch coverage for [InMemoryDatabaseQueryManager]: insert/query, hard and soft deletes of
 * both positions and moves, incident-move stripping, erase, and the last-update aggregate.
 */
class TestInMemoryDatabaseQueryManager {

  // A three position line start -e4-> key1 -e5-> key2, with the matching incident moves.
  private val key0 = keyAfter()
  private val key1 = keyAfter("e4")
  private val key2 = keyAfter("e4", "e5")
  private val moveE4 = DataMove(key0, key1, "e4", isGood = true)
  private val moveE5 = DataMove(key1, key2, "e5", isGood = true)

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  private fun node(
    key: PositionKey,
    previous: List<DataMove>,
    next: List<DataMove>,
    updatedAt: Instant = Instant.fromEpochSeconds(0),
  ) = DataNode(key, PreviousAndNextMoves(previous, next), CardStateFactory.new(), 0, updatedAt)

  private suspend fun seededLine(): InMemoryDatabaseQueryManager {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(moveE4)),
      node(key1, previous = listOf(moveE4), next = listOf(moveE5)),
      node(key2, previous = listOf(moveE5), next = listOf()),
    )
    return database
  }

  @Test
  fun insertsAndReadsBack() = runTest {
    val database = seededLine()
    assertEquals(3, database.getAllNodes().size)
    assertEquals(setOf("e5"), database.getPosition(key1)?.previousAndNextMoves?.nextMoves?.keys)
  }

  @Test
  fun getPositionReturnsNullForUnknownKey() = runTest {
    assertNull(seededLine().getPosition(keyAfter("d4")))
  }

  @Test
  fun hardDeletingAPositionStripsItsIncidentMoves() = runTest {
    val database = seededLine()
    database.deletePosition(key1, DeleteMode.HARD)
    assertNull(database.getPosition(key1))
    assertEquals(2, database.getAllNodes().size)
    // e4 pointed at key1, e5 came from key1: both are gone from the surviving neighbours.
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertTrue(database.getPosition(key2)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun hardDeletingALeafKeepsUnrelatedMovesOfOtherNodes() = runTest {
    val database = seededLine()
    database.deletePosition(key2, DeleteMode.HARD)
    assertNull(database.getPosition(key2))
    // key0 never referenced key2, so its e4 move (to key1) survives the cascade untouched.
    assertEquals(setOf("e4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
    // key1 keeps its incoming e4 but loses its outgoing e5, which pointed at the removed key2.
    assertEquals(setOf("e4"), database.getPosition(key1)!!.previousAndNextMoves.previousMoves.keys)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.isEmpty())
  }

  @Test
  fun softDeletingAPositionHidesItButKeepsTheRow() = runTest {
    val database = seededLine()
    database.deletePosition(key1, DeleteMode.SOFT)
    assertNull(database.getPosition(key1))
    assertEquals(2, database.getAllNodes().size)
    val withDeleted = database.getAllNodes(withDeletedOnes = true)
    assertEquals(3, withDeleted.size)
    assertTrue(withDeleted.single { it.positionKey == key1 }.isDeleted)
  }

  @Test
  fun deletingAMissingPositionIsANoOp() = runTest {
    val database = seededLine()
    database.deletePosition(keyAfter("d4"), DeleteMode.HARD)
    assertEquals(3, database.getAllNodes().size)
  }

  @Test
  fun hardDeletingAMoveRemovesItFromBothEnds() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", DeleteMode.HARD)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertTrue(database.getPosition(key2)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun softDeletingAMoveFlagsItOnBothEnds() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", DeleteMode.SOFT)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5").isDeleted)
    assertTrue(
      database.getPosition(key2)!!.previousAndNextMoves.previousMoves.getValue("e5").isDeleted
    )
  }

  @Test
  fun deletingAMissingMoveIsANoOp() = runTest {
    val database = seededLine()
    database.deleteMove(key0, "Qh5", DeleteMode.HARD)
    database.deleteMove(keyAfter("d4"), "d5", DeleteMode.HARD)
    assertEquals(setOf("e4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun deletingAMoveWhoseDestinationIsAbsentUpdatesOnlyTheOrigin() = runTest {
    val database = InMemoryDatabaseQueryManager()
    // Only the origin exists; its e4 edge points at a key1 node that was never inserted.
    database.insertNodes(node(key0, previous = listOf(), next = listOf(moveE4)))
    database.deleteMove(key0, "e4", DeleteMode.HARD)
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertNull(database.getPosition(key1))
  }

  @Test
  fun hardDeletingOneOfSeveralMovesKeepsTheOthers() = runTest {
    val keyD4 = keyAfter("d4")
    val moveD4 = DataMove(key0, keyD4, "d4", isGood = true)
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(moveE4, moveD4)),
      node(key1, previous = listOf(moveE4), next = listOf()),
      node(keyD4, previous = listOf(moveD4), next = listOf()),
    )
    database.deleteMove(key0, "e4", DeleteMode.HARD)
    // d4 is left in place; only the matching e4 is removed from the origin.
    assertEquals(setOf("d4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun eraseAllClearsEverything() = runTest {
    val database = seededLine()
    database.eraseAll()
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
  }

  @Test
  fun lastUpdateIsNullWhenEmpty() = runTest {
    assertNull(InMemoryDatabaseQueryManager().getLastUpdate())
  }

  @Test
  fun lastUpdateIsTheLatestOfNodesAndMoves() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val moveStamp = Instant.fromEpochSeconds(500)
    val nodeStamp = Instant.fromEpochSeconds(200)
    val recentMove = DataMove(key0, key1, "e4", isGood = true, updatedAt = moveStamp)
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(recentMove), updatedAt = nodeStamp)
    )
    // The move is newer than its node, so it drives the aggregate.
    assertEquals(moveStamp, database.getLastUpdate())
  }

  @Test
  fun lastUpdateFallsBackToNodeStampWhenItIsLatest() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val nodeStamp = Instant.fromEpochSeconds(900)
    database.insertNodes(node(key0, previous = listOf(), next = listOf(), updatedAt = nodeStamp))
    assertEquals(nodeStamp, database.getLastUpdate())
  }
}
