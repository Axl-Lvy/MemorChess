package proj.memorchess.axl.core.data.online

import kotlin.getValue
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

abstract class TestCompositeDatabase : TestWithKoin {

  val compositeDatabase by inject<DatabaseQueryManager>()
  val localDatabase by inject<DatabaseQueryManager>(named("local"))
  val remoteDatabase by inject<SupabaseQueryManager>()

  abstract class TestCompositeDatabaseAuthenticated : TestCompositeDatabase() {
    val authManager by inject<AuthManager>()

    @BeforeTest
    override fun setUp() {
      super.setUp()
      ensureSignedOut()
      runTest { authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword) }
      Awaitility.awaitUntilTrue { authManager.user != null }
    }

    @AfterTest
    override fun tearDown() {
      ensureSignedOut()
      super.tearDown()
    }

    fun ensureSignedOut() {
      runTest {
        if (authManager.user != null) {
          authManager.signOut()
          Awaitility.awaitUntilTrue { authManager.user == null }
        }
      }
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
    compositeDatabase.insertNodes(node)
    val retrievedNodes = compositeDatabase.getAllNodes(false)

    // Assert
    assertEquals(1, retrievedNodes.size)
    assertEquals(node.positionIdentifier, retrievedNodes.first().positionIdentifier)
  }

  @Test
  fun testGetPosition() = runTest {
    // Arrange
    val game = Game()
    val positionIdentifier = game.position.createIdentifier()
    val node =
      StoredNode(positionIdentifier, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    compositeDatabase.insertNodes(node)

    // Act
    val retrievedNode = compositeDatabase.getPosition(positionIdentifier)

    // Assert
    assertNotNull(retrievedNode)
    assertEquals(positionIdentifier, retrievedNode.positionIdentifier)
  }

  @Test
  fun testDeletePosition() = runTest {
    // Arrange
    val game = Game()
    val positionIdentifier = game.position.createIdentifier()
    val node =
      StoredNode(positionIdentifier, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    compositeDatabase.insertNodes(node)

    // Verify node exists
    val beforeDelete = compositeDatabase.getPosition(positionIdentifier)
    assertNotNull(beforeDelete)

    // Act
    compositeDatabase.deletePosition(positionIdentifier)

    // Assert
    val afterDelete = compositeDatabase.getPosition(positionIdentifier)
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
          nextMoves = listOf(StoredMove(rootPosition, childPosition, "e4", true)),
        ),
        PreviousAndNextDate.dummyToday(),
      )

    val childNode =
      StoredNode(childPosition, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    compositeDatabase.insertNodes(rootNode, childNode)

    // Act
    compositeDatabase.deleteMove(rootPosition, "e4")

    // Assert - This test depends on implementation details
    // The move should be marked as deleted in the database
    val retrievedRootNode = compositeDatabase.getPosition(rootPosition)
    val retrieveChildNode = compositeDatabase.getPosition(childPosition)
    assertNotNull(retrievedRootNode, "Root node should still exist after move deletion")
    assertFalse { retrievedRootNode.previousAndNextMoves.nextMoves.any { it.key == "e4" } }
    assertNull(retrieveChildNode, "Child node should be deleted after move deletion")
  }

  @Test
  fun testDeleteAll() = runTest {
    // Arrange
    val nodes = TestDatabaseQueryManager.vienna().getAllNodes()
    compositeDatabase.insertNodes(*nodes.toTypedArray())

    // Verify nodes exist
    val beforeDelete = compositeDatabase.getAllNodes(false)
    assertTrue(beforeDelete.isNotEmpty())

    // Act
    compositeDatabase.deleteAll(null)

    // Assert
    val afterDelete = compositeDatabase.getAllNodes(false)
    assertTrue(afterDelete.isEmpty(), "All nodes should be deleted")
  }

  @Test
  fun testGetLastUpdate() = runTest {
    // Arrange
    val game = Game()
    val node =
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
        DateUtil.farInThePast(),
      )

    // Act
    val beforeInsert = compositeDatabase.getLastUpdate()
    assertNull(beforeInsert)
    compositeDatabase.insertNodes(node)
    val afterInsert = compositeDatabase.getLastUpdate()
    compositeDatabase.deletePosition(node.positionIdentifier)
    val afterDelete = compositeDatabase.getLastUpdate()

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
}
