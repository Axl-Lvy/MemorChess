package proj.memorchess.axl.core.data.online

import kotlin.getValue
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.auth.KtorAuthManager
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
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
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    // Act
    compositeDatabase.insertMoves(moves, positions)
    val retrievedPositions = compositeDatabase.getAllPositions(false)

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
    compositeDatabase.insertMoves(moves, positions)

    // Act
    val retrievedPosition = compositeDatabase.getPosition(positionIdentifier)

    // Assert
    assertNotNull(retrievedPosition)
    assertEquals(positionIdentifier, retrievedPosition.positionIdentifier)
  }

  @Test
  fun testDeletePosition() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()
    val positionIdentifier = positions.first().positionIdentifier
    compositeDatabase.insertMoves(moves, positions)

    // Verify position exists
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

    compositeDatabase.insertMoves(listOf(move), listOf(rootDataPosition, childDataPosition))

    // Act
    compositeDatabase.deleteMove(rootPosition, "e4")

    // Assert
    val retrievedRootPosition = compositeDatabase.getPosition(rootPosition)
    assertNotNull(retrievedRootPosition, "Root position should still exist after move deletion")
    val movesForRoot = compositeDatabase.getMovesForPosition(rootPosition)
    assertFalse { movesForRoot.any { it.move == "e4" && !it.isDeleted } }
  }

  @Test
  fun testDeleteAll() = runTest {
    // Arrange
    val refDatabase = TestDatabaseQueryManager.vienna()
    compositeDatabase.insertMoves(refDatabase.dataMoves, refDatabase.dataPositions.values.toList())

    // Verify positions exist
    val beforeDelete = compositeDatabase.getAllPositions(false)
    assertTrue(beforeDelete.isNotEmpty())

    // Act
    compositeDatabase.deleteAll(null)

    // Assert
    val afterDelete = compositeDatabase.getAllPositions(false)
    assertTrue(afterDelete.isEmpty(), "All positions should be deleted")
  }

  @Test
  fun testGetLastUpdate() = runTest {
    // Arrange
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    // Act
    val beforeInsert = compositeDatabase.getLastUpdate()
    assertNull(beforeInsert)
    compositeDatabase.insertMoves(moves, positions)
    val afterInsert = compositeDatabase.getLastUpdate()
    compositeDatabase.deletePosition(positions.first().positionIdentifier)
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
