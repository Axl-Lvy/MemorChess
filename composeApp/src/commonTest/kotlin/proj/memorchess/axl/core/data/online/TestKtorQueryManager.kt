package proj.memorchess.axl.core.data.online

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
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
    val nodes = TestDatabaseQueryManager.minimalNodePair()

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
    val nodes = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = nodes.first().positionIdentifier
    remoteDatabase.insertNodes(*nodes.toTypedArray())

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
    val nodes = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = nodes.first().positionIdentifier
    remoteDatabase.insertNodes(*nodes.toTypedArray())

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
    val move = DataMove(rootPosition, childPosition, "e4", true)

    val rootNode =
      DataNode(
        rootPosition,
        PreviousAndNextMoves(
          previousMoves = emptyList(),
          nextMoves = listOf(move),
        ),
        PreviousAndNextDate.dummyToday(),
      )

    val childNode =
      DataNode(
        childPosition,
        PreviousAndNextMoves(
          previousMoves = listOf(move),
          nextMoves = emptyList(),
        ),
        PreviousAndNextDate.dummyToday(),
      )

    remoteDatabase.insertNodes(rootNode, childNode)

    // Act
    remoteDatabase.deleteMove(rootPosition, "e4")

    // Assert
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
    val nodes = TestDatabaseQueryManager.minimalNodePair()
    remoteDatabase.insertNodes(*nodes.toTypedArray())

    // Act
    remoteDatabase.deleteAll(DateUtil.farInThePast())

    // Assert - nodes created should be deleted
    val remaining = remoteDatabase.getAllNodes(true)
    assertTrue(remaining.isEmpty(), "All nodes should be hard-deleted")
  }

  @Test
  fun testGetLastUpdate() = runTest {
    // Arrange
    val nodes = TestDatabaseQueryManager.minimalNodePair()

    // Act
    val beforeInsert = remoteDatabase.getLastUpdate()
    assertNull(beforeInsert)
    remoteDatabase.insertNodes(*nodes.toTypedArray())
    val afterInsert = remoteDatabase.getLastUpdate()
    remoteDatabase.deletePosition(nodes.first().positionIdentifier)
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
    val nodes = TestDatabaseQueryManager.minimalNodePair()
    remoteDatabase.insertNodes(*nodes.toTypedArray())
    remoteDatabase.deletePosition(nodes.first().positionIdentifier)

    // Act
    val nodesWithoutDeleted = remoteDatabase.getAllNodes(false)
    val nodesWithDeleted = remoteDatabase.getAllNodes(true)

    // Assert
    assertTrue(
      nodesWithoutDeleted.size < nodesWithDeleted.size,
      "Deleted nodes should be excluded by default",
    )
    assertTrue(nodesWithDeleted.isNotEmpty(), "Should include deleted nodes when requested")
  }

  @Test
  fun testOperationsRequireAuthentication() = runTest {
    // Arrange
    authManager.signOut()
    Awaitility.awaitUntilTrue { authManager.user == null }

    val nodes = TestDatabaseQueryManager.minimalNodePair()

    // Act & Assert
    assertFailsWith<IllegalStateException> { remoteDatabase.insertNodes(*nodes.toTypedArray()) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getAllNodes(false) }
    assertFailsWith<IllegalStateException> {
      remoteDatabase.getPosition(nodes.first().positionIdentifier)
    }
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

  @Test
  fun testThrowOnSignOut() = runTest {
    // Arrange
    authManager.signOut()
    Awaitility.awaitUntilTrue { authManager.user == null }

    val nodes = TestDatabaseQueryManager.minimalNodePair()
    val positionId = nodes.first().positionIdentifier

    // Act & Assert
    assertFailsWith<IllegalStateException> { remoteDatabase.deleteAll(null) }
    assertFailsWith<IllegalStateException> { remoteDatabase.deletePosition(positionId) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getAllNodes(false) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getPosition(positionId) }
    assertFailsWith<IllegalStateException> { remoteDatabase.getLastUpdate() }
    assertFailsWith<IllegalStateException> { remoteDatabase.deleteMove(positionId, "e4") }
    assertFailsWith<IllegalStateException> { remoteDatabase.insertNodes(*nodes.toTypedArray()) }
  }
}
