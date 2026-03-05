package proj.memorchess.axl.core.data.online

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestAuthenticated
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestKtorQueryManager : TestAuthenticated() {

  private val remoteDatabase by inject<KtorQueryManager>()
  private val refDatabase = TestDatabaseQueryManager.vienna()

  override fun setUp() {
    super.setUp()
    runTest {
      // Clear remote database to start with clean state
      remoteDatabase.deleteAll(DateUtil.farInThePast())
    }
  }

  @Test
  fun testInsertAndRetrieveNodes() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    // Act
    remoteDatabase.insertMoves(moves, positions)
    val retrievedPositions = remoteDatabase.getAllPositions(false)

    // Assert
    assertEquals(positions.size, retrievedPositions.size)
    positions.forEach { originalPosition ->
      assertContains(retrievedPositions.map { it.positionIdentifier }, originalPosition.positionIdentifier)
    }
  }

  @Test
  fun testInsertMultipleNodes() = runTest {
    // Arrange
    val moves = refDatabase.dataMoves
    val positions = refDatabase.dataPositions.values.toList()
    assertTrue(positions.isNotEmpty(), "Reference database should have positions")

    // Act
    remoteDatabase.insertMoves(moves, positions)
    val retrievedPositions = remoteDatabase.getAllPositions(false)

    // Assert
    assertEquals(positions.size, retrievedPositions.size)
    positions.forEach { originalPosition ->
      assertContains(retrievedPositions.map { it.positionIdentifier }, originalPosition.positionIdentifier)
    }
  }

  @Test
  fun testGetPosition() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = positions.first().positionIdentifier
    remoteDatabase.insertMoves(moves, positions)

    // Act
    val retrievedPosition = remoteDatabase.getPosition(positionIdentifier)

    // Assert
    assertNotNull(retrievedPosition)
    assertEquals(positionIdentifier, retrievedPosition.positionIdentifier)
  }

  @Test
  fun testGetNonExistentPosition() = runTest {
    // Arrange
    val game = Game()
    game.playMove("e4") // Create a different position
    val nonExistentPosition = game.position.createIdentifier()

    // Act
    val retrievedPosition = remoteDatabase.getPosition(nonExistentPosition)

    // Assert
    assertNull(retrievedPosition)
  }

  @Test
  fun testDeletePosition() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = positions.first().positionIdentifier
    remoteDatabase.insertMoves(moves, positions)

    // Verify position exists
    val beforeDelete = remoteDatabase.getPosition(positionIdentifier)
    assertNotNull(beforeDelete)

    // Act
    remoteDatabase.deletePosition(positionIdentifier)

    // Assert
    val afterDelete = remoteDatabase.getPosition(positionIdentifier)
    assertNull(afterDelete, "Position should be null after deletion")
  }

  @Test
  fun testDeleteMove() = runTest {
    // Arrange
    val game = Game()
    val rootPosition = game.position.createIdentifier()
    game.playMove("e4")
    val childPosition = game.position.createIdentifier()
    val move = DataMove(rootPosition, childPosition, "e4", true)

    val rootDataPosition = DataPosition(
      rootPosition,
      0,
      PreviousAndNextDate.dummyToday(),
    )

    val childDataPosition = DataPosition(
      childPosition,
      1,
      PreviousAndNextDate.dummyToday(),
    )

    remoteDatabase.insertMoves(listOf(move), listOf(rootDataPosition, childDataPosition))

    // Act
    remoteDatabase.deleteMove(rootPosition, "e4")

    // Assert
    val retrievedRootPosition = remoteDatabase.getPosition(rootPosition)
    assertNotNull(retrievedRootPosition, "Root position should still exist after move deletion")
    val movesForRoot = remoteDatabase.getMovesForPosition(rootPosition)
    assertFalse { movesForRoot.any { it.move == "e4" && !it.isDeleted } }
  }

  @Test
  fun testDeleteAll() = runTest {
    // Arrange
    remoteDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Verify positions exist
    val beforeDelete = remoteDatabase.getAllPositions(false)
    assertTrue(beforeDelete.isNotEmpty())

    // Act
    remoteDatabase.deleteAll(null)

    // Assert
    val afterDelete = remoteDatabase.getAllPositions(false)
    assertTrue(afterDelete.isEmpty(), "All positions should be deleted")
  }

  @Test
  fun testDeleteAllWithHardFrom() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    remoteDatabase.insertMoves(moves, positions)

    // Act
    remoteDatabase.deleteAll(DateUtil.farInThePast())

    // Assert - positions created should be deleted
    val remaining = remoteDatabase.getAllPositions(true)
    assertTrue(remaining.isEmpty(), "All positions should be hard-deleted")
  }

  @Test
  fun testGetLastUpdate() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    // Act
    val beforeInsert = remoteDatabase.getLastUpdate()
    assertNull(beforeInsert)
    remoteDatabase.insertMoves(moves, positions)
    val afterInsert = remoteDatabase.getLastUpdate()
    remoteDatabase.deletePosition(positions.first().positionIdentifier)
    val afterDelete = remoteDatabase.getLastUpdate()

    // Assert
    assertNotNull(afterInsert, "Should have last update time after inserting nodes")
    assertNotNull(afterDelete, "Should have last update time after deleting nodes")
    assertTrue(
      "Last update should be updated after deletion. AfterDelete: $afterDelete AfterInsert: $afterInsert"
    ) {
      afterDelete != afterInsert
    }
  }

  @Test
  fun testGetAllNodesWithDeletedOnes() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    remoteDatabase.insertMoves(moves, positions)
    remoteDatabase.deletePosition(positions.first().positionIdentifier)

    // Act
    val positionsWithoutDeleted = remoteDatabase.getAllPositions(false)
    val positionsWithDeleted = remoteDatabase.getAllPositions(true)

    // Assert
    assertTrue(
      positionsWithoutDeleted.size < positionsWithDeleted.size,
      "Deleted positions should be excluded by default",
    )
    assertTrue(positionsWithDeleted.isNotEmpty(), "Should include deleted positions when requested")
  }

  @Test
  fun testOperationsRequireAuthentication() = runTest {
    // Arrange
    authManager.signOut()
    Awaitility.awaitUntilTrue { authManager.user == null }

    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    // Act & Assert
    assertFailsWith<IllegalStateException> { remoteDatabase.insertMoves(moves, positions) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getAllPositions(false) }
    assertFailsWith<IllegalStateException> {
      remoteDatabase.getPosition(positions.first().positionIdentifier)
    }
  }

  @Test
  fun testEmptyDatabaseOperations() = runTest {
    // Act
    val emptyPositions = remoteDatabase.getAllPositions(false)
    val nonExistentPosition =
      remoteDatabase.getPosition(
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      )
    val lastUpdate = remoteDatabase.getLastUpdate()

    // Assert
    assertTrue(emptyPositions.isEmpty(), "Empty database should return no positions")
    assertNull(nonExistentPosition, "Non-existent position should return null")
    assertNull(lastUpdate, "Empty database should have no last update")
  }

  @Test
  fun testThrowOnSignOut() = runTest {
    // Arrange
    authManager.signOut()
    Awaitility.awaitUntilTrue { authManager.user == null }

    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    val positionId = positions.first().positionIdentifier

    // Act & Assert
    assertFailsWith<IllegalStateException> { remoteDatabase.deleteAll(null) }
    assertFailsWith<IllegalStateException> { remoteDatabase.deletePosition(positionId) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getAllPositions(false) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getPosition(positionId) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getLastUpdate() }
    assertFailsWith<IllegalStateException> { remoteDatabase.deleteMove(positionId, "e4") }
    assertFailsWith<IllegalStateException> { remoteDatabase.insertMoves(moves, positions) }
  }
}
