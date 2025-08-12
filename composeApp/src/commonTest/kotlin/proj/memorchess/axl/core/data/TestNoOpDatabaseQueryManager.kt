package proj.memorchess.axl.core.data

import kotlin.test.*
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class TestNoOpDatabaseQueryManager {

    private val noOpDatabaseQueryManager = NoOpDatabaseQueryManager()

    @Test
    fun testGetAllNodesReturnsEmptyList() = runTest {
        val nodes = noOpDatabaseQueryManager.getAllNodes(false)
        assertTrue(nodes.isEmpty(), "Expected empty list of nodes")
    }

    @Test
    fun testGetPositionReturnsNull() = runTest {
        val position = noOpDatabaseQueryManager.getPosition(PositionIdentifier("test"))
        assertNull(position, "Expected null for non-existent position")
    }

    @Test
    fun testDeletePositionDoesNothing() = runTest {
        noOpDatabaseQueryManager.deletePosition(PositionIdentifier("test"))
        // No exception or state change expected
    }

    @Test
    fun testDeleteMoveDoesNothing() = runTest {
        noOpDatabaseQueryManager.deleteMove(PositionIdentifier("test"), "e4")
        // No exception or state change expected
    }

    @Test
    fun testDeleteAllDoesNothing() = runTest {
        noOpDatabaseQueryManager.deleteAll(LocalDateTime(2025, 8, 13, 0, 0))
        // No exception or state change expected
    }

    @Test
    fun testInsertNodesDoesNothing() = runTest {
        noOpDatabaseQueryManager.insertNodes(StoredNode(PositionIdentifier("test"),
          PreviousAndNextMoves(), PreviousAndNextDate.dummyToday()))
        // No exception or state change expected
    }

    @Test
    fun testGetLastUpdateReturnsNull() = runTest {
        val lastUpdate = noOpDatabaseQueryManager.getLastUpdate()
        assertNull(lastUpdate, "Expected null for last update")
    }

    @Test
    fun testIsActiveReturnsFalse() {
        val isActive = noOpDatabaseQueryManager.isActive()
        assertFalse(isActive, "Expected isActive to return false")
    }
}
