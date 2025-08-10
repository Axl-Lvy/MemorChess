package proj.memorchess.axl.online

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.utils.Awaitility
import proj.memorchess.axl.utils.TestWithAuthentication

class TestSupabaseQueryManager : TestWithAuthentication() {

  private val remoteDatabase by inject<SupabaseQueryManager>()
  private val refDatabase = TestDatabaseQueryManager.vienna()

  override fun setUp() {
    super.setUp()
    runTest {
      authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword)
      Awaitility.awaitUntilTrue { authManager.user != null }
      // Clear remote database to start with clean state
      remoteDatabase.deleteAll(DateUtil.farInThePast())
    }
  }

  @Test
  fun testInsertAndRetrieveSingleNode() = runTest {
    // Arrange
    val game = Game()
    val node =
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )

    // Act
    remoteDatabase.insertNodes(node)
    val retrievedNodes = remoteDatabase.getAllNodes(false)

    // Assert
    assertEquals(1, retrievedNodes.size)
    assertEquals(node.positionIdentifier, retrievedNodes.first().positionIdentifier)
  }

  @Test
  fun testInsertMultipleNodes() = runTest {
    // Arrange
    val nodes = refDatabase.getAllNodes()
    assertTrue(nodes.isNotEmpty(), "Reference database should have nodes")

    // Act
    remoteDatabase.insertNodes(*nodes.toTypedArray())
    val retrievedNodes = remoteDatabase.getAllNodes(false)

    // Assert
    assertEquals(nodes.size, retrievedNodes.size)
    nodes.forEach { originalNode ->
      assertContains(retrievedNodes.map { it.positionIdentifier }, originalNode.positionIdentifier)
    }
  }

  @Test
  fun testGetPosition() = runTest {
    // Arrange
    val game = Game()
    val positionIdentifier = game.position.createIdentifier()
    val node =
      StoredNode(positionIdentifier, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    remoteDatabase.insertNodes(node)

    // Act
    val retrievedNode = remoteDatabase.getPosition(positionIdentifier)

    // Assert
    assertNotNull(retrievedNode)
    assertEquals(positionIdentifier, retrievedNode.positionIdentifier)
  }

  @Test
  fun testGetNonExistentPosition() = runTest {
    // Arrange
    val game = Game()
    game.playMove("e4") // Create a different position
    val nonExistentPosition = game.position.createIdentifier()

    // Act
    val retrievedNode = remoteDatabase.getPosition(nonExistentPosition)

    // Assert
    assertNull(retrievedNode)
  }

  @Test
  fun testDeletePosition() = runTest {
    // Arrange
    val game = Game()
    val positionIdentifier = game.position.createIdentifier()
    val node =
      StoredNode(positionIdentifier, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    remoteDatabase.insertNodes(node)

    // Verify node exists
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

    val rootNode =
      StoredNode(
        rootPosition,
        PreviousAndNextMoves(
          previousMoves = emptyList(),
          nextMoves =
            listOf(
              proj.memorchess.axl.core.data.StoredMove(rootPosition, childPosition, "e4", true)
            ),
        ),
        PreviousAndNextDate.dummyToday(),
      )

    val childNode =
      StoredNode(childPosition, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    remoteDatabase.insertNodes(rootNode, childNode)

    // Act
    remoteDatabase.deleteMove(rootPosition, "e4")

    // Assert - This test depends on implementation details
    // The move should be marked as deleted in the database
    val retrievedRootNode = remoteDatabase.getPosition(rootPosition)
    val retrieveChildNode = remoteDatabase.getPosition(childPosition)
    assertNotNull(retrievedRootNode, "Root node should still exist after move deletion")
    assertFalse { retrievedRootNode.previousAndNextMoves.nextMoves.any { it.key == "e4" } }
    assertNull(retrieveChildNode, "Child node should be deleted after move deletion")
  }

  @Test
  fun testDeleteAll() = runTest {
    // Arrange
    val nodes = refDatabase.getAllNodes()
    remoteDatabase.insertNodes(*nodes.toTypedArray())

    // Verify nodes exist
    val beforeDelete = remoteDatabase.getAllNodes(false)
    assertTrue(beforeDelete.isNotEmpty())

    // Act
    remoteDatabase.deleteAll(null)

    // Assert
    val afterDelete = remoteDatabase.getAllNodes(false)
    assertTrue(afterDelete.isEmpty(), "All nodes should be deleted")
  }

  @Test
  fun testDeleteAllWithHardFrom() = runTest {
    // Arrange
    val game = Game()
    val updatedAt = DateUtil.now()
    val positionIdentifier = game.position.createIdentifier()
    val node =
      StoredNode(
        positionIdentifier,
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
        updatedAt,
      )
    remoteDatabase.insertNodes(node)

    // Act
    remoteDatabase.deleteAll(DateUtil.farInThePast())

    // Assert - node created should be deleted
    assertFalse {
      remoteDatabase.getAllNodes(true).any { it.positionIdentifier == positionIdentifier }
    }
  }

  @Test
  fun testGetLastUpdate() = runBlocking {
    // Arrange
    val game = Game()
    val node =
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
        DateUtil.now(),
      )

    // Act
    val beforeInsert = remoteDatabase.getLastUpdate()
    assertNull(beforeInsert)
    remoteDatabase.insertNodes(node)
    val afterInsert = remoteDatabase.getLastUpdate()
    remoteDatabase.deletePosition(node.positionIdentifier)
    val afterDelete = remoteDatabase.getLastUpdate()

    // Assert
    assertNotNull(afterInsert, "Should have last update time after inserting nodes")
    assertNotNull(afterDelete, "Should have last update time after deleting nodes")

    // It seems like the clocks from the test and the remote database might not be perfectly
    // synchronized. So afterDelete might be more recent than afterInsert.
    assertTrue(
      "Last update should be updated after deletion. AfterDelete: $afterDelete AfterInsert: $afterInsert"
    ) {
      afterDelete != afterInsert
    }
  }

  @Test
  fun testGetAllNodesWithDeletedOnes() = runTest {
    // Arrange
    val game = Game()
    val node =
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )
    remoteDatabase.insertNodes(node)
    remoteDatabase.deletePosition(node.positionIdentifier)

    // Act
    val nodesWithoutDeleted = remoteDatabase.getAllNodes(false)
    val nodesWithDeleted = remoteDatabase.getAllNodes(true)

    // Assert
    assertTrue(nodesWithoutDeleted.isEmpty(), "Should not include deleted nodes")
    assertTrue(nodesWithDeleted.isNotEmpty(), "Should include deleted nodes when requested")
  }

  @Test
  fun testOperationsRequireAuthentication() = runTest {
    // Arrange
    authManager.signOut()
    Awaitility.awaitUntilTrue { authManager.user == null }

    val game = Game()
    val node =
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )

    // Act & Assert
    assertFailsWith<IllegalStateException> { remoteDatabase.insertNodes(node) }

    assertFailsWith<IllegalStateException> { remoteDatabase.getAllNodes(false) }

    assertFailsWith<IllegalStateException> { remoteDatabase.getPosition(node.positionIdentifier) }
  }

  @Test
  fun testEmptyDatabaseOperations() = runTest {
    // Act
    val emptyNodes = remoteDatabase.getAllNodes(false)
    val nonExistentPosition =
      remoteDatabase.getPosition(
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      )
    val lastUpdate = remoteDatabase.getLastUpdate()

    // Assert
    assertTrue(emptyNodes.isEmpty(), "Empty database should return no nodes")
    assertNull(nonExistentPosition, "Non-existent position should return null")
    assertNull(lastUpdate, "Empty database should have no last update")
  }
}
