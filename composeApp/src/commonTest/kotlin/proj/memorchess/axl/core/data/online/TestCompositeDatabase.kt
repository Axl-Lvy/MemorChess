package proj.memorchess.axl.core.data.online

import kotlin.getValue
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.auth.KtorAuthManager
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.ensureDockerRunning

abstract class TestCompositeDatabase : TestWithKoin {

  val compositeDatabase by inject<DatabaseQueryManager>()
  val localDatabase by inject<DatabaseQueryManager>(named("local"))
  val remoteDatabase by inject<KtorQueryManager>()

  abstract class TestCompositeDatabaseAuthenticated : TestCompositeDatabase() {
    val authManager by inject<KtorAuthManager>()

    @BeforeTest
    override fun setUp() {
      super.setUp()
      ensureDockerRunning()
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
  fun testInsertAndRetrieveNodes() = runTest {
    // Arrange
    val nodes = TestDatabaseQueryManager.minimalNodePair()

    // Act
    compositeDatabase.insertNodes(*nodes.toTypedArray())
    val retrievedNodes = compositeDatabase.getAllNodes(false)

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
    compositeDatabase.insertNodes(*nodes.toTypedArray())

    // Act
    val retrievedNode = compositeDatabase.getPosition(positionIdentifier)

    // Assert
    assertNotNull(retrievedNode)
    assertEquals(positionIdentifier, retrievedNode.positionIdentifier)
  }

  @Test
  fun testDeletePosition() = runTest {
    // Arrange
    val nodes = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = nodes.first().positionIdentifier
    compositeDatabase.insertNodes(*nodes.toTypedArray())

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

    compositeDatabase.insertNodes(rootNode, childNode)

    // Act
    compositeDatabase.deleteMove(rootPosition, "e4")

    // Assert
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
    val nodes = TestDatabaseQueryManager.minimalNodePair()

    // Act
    val beforeInsert = compositeDatabase.getLastUpdate()
    assertNull(beforeInsert)
    compositeDatabase.insertNodes(*nodes.toTypedArray())
    val afterInsert = compositeDatabase.getLastUpdate()
    compositeDatabase.deletePosition(nodes.first().positionIdentifier)
    val afterDelete = compositeDatabase.getLastUpdate()

    // Assert
    assertNotNull(afterInsert, "Should have last update time after inserting nodes")
    assertNotNull(afterDelete, "Should have last update time after deleting nodes")
    assertTrue(
      "Last update should be updated after deletion. AfterDelete: $afterDelete AfterInsert: $afterInsert"
    ) {
      afterDelete != afterInsert
    }
  }
}
