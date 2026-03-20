package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

/** Tests for [DatabaseQueryManager] operations against the local database. */
class TestLocalDatabase : TestWithKoin() {

  private val database by inject<DatabaseQueryManager>()

  override suspend fun setUp() {
    assertTrue { database.isActive() }
    database.deleteAll(null)
  }

  @Test
  fun testInsertAndRetrieveSingleNode() = test {
    // Arrange
    val engine = GameEngine()
    val node =
      DataNode(engine.toPositionKey(), PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    // Act
    database.insertNodes(node)
    val retrievedNodes = database.getAllNodes(false)

    // Assert
    assertEquals(1, retrievedNodes.size)
    assertEquals(node.positionKey, retrievedNodes.first().positionKey)
  }

  @Test
  fun testGetPosition() = test {
    // Arrange
    val engine = GameEngine()
    val positionKey = engine.toPositionKey()
    val node = DataNode(positionKey, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    database.insertNodes(node)

    // Act
    val retrievedNode = database.getPosition(positionKey)

    // Assert
    assertNotNull(retrievedNode)
    assertEquals(positionKey, retrievedNode.positionKey)
  }

  @Test
  fun testDeletePosition() = test {
    // Arrange
    val engine = GameEngine()
    val positionKey = engine.toPositionKey()
    val node = DataNode(positionKey, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())
    database.insertNodes(node)

    // Verify node exists
    val beforeDelete = database.getPosition(positionKey)
    assertNotNull(beforeDelete)

    // Act
    database.deletePosition(positionKey)

    // Assert
    val afterDelete = database.getPosition(positionKey)
    assertNull(afterDelete, "Position should be null after deletion")
  }

  @Test
  fun testDeleteMove() = test {
    // Arrange
    val engine = GameEngine()
    val rootPosition = engine.toPositionKey()
    engine.playSanMove("e4")
    val childPosition = engine.toPositionKey()

    val rootNode =
      DataNode(
        rootPosition,
        PreviousAndNextMoves(
          previousMoves = emptyList(),
          nextMoves = listOf(DataMove(rootPosition, childPosition, "e4", true)),
        ),
        PreviousAndNextDate.dummyToday(),
      )

    val childNode =
      DataNode(childPosition, PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    database.insertNodes(rootNode, childNode)

    // Act
    database.deleteMove(rootPosition, "e4")

    // Assert
    val retrievedRootNode = database.getPosition(rootPosition)
    val retrieveChildNode = database.getPosition(childPosition)
    assertNotNull(retrievedRootNode, "Root node should still exist after move deletion")
    assertFalse { retrievedRootNode.previousAndNextMoves.nextMoves.any { it.key == "e4" } }
    assertNull(retrieveChildNode, "Child node should be deleted after move deletion")
  }

  @Test
  fun testDeleteAll() = test {
    // Arrange
    val nodes = TestDatabaseQueryManager.vienna().getAllNodes()
    database.insertNodes(*nodes.toTypedArray())

    // Verify nodes exist
    val beforeDelete = database.getAllNodes(false)
    assertTrue(beforeDelete.isNotEmpty())

    // Act
    database.deleteAll(null)

    // Assert
    val afterDelete = database.getAllNodes(false)
    assertTrue(afterDelete.isEmpty(), "All nodes should be deleted")
  }

  @Test
  fun testGetLastUpdate() = test {
    // Arrange
    val engine = GameEngine()
    val node =
      DataNode(
        engine.toPositionKey(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
        updatedAt = DateUtil.farInThePast(),
      )

    // Act
    val beforeInsert = database.getLastUpdate()
    assertNull(beforeInsert)
    database.insertNodes(node)
    val afterInsert = database.getLastUpdate()
    database.deletePosition(node.positionKey)
    val afterDelete = database.getLastUpdate()

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
