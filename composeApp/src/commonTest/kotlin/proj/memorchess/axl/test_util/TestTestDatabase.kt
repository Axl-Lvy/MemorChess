package proj.memorchess.axl.test_util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Test the [TestDatabases] factories, most particularly the utility methods to create prefilled
 * test databases.
 */
class TestTestDatabase {
  @Test
  fun testViennaDatabase() = runTest {
    val viennaDb = TestDatabases.vienna()
    val viennaMoves = getVienna()
    val nodes = drainAllNodes(viennaDb)

    // Check that the database has the correct number of positions
    assertEquals(
      viennaMoves.size,
      nodes.size,
      "Vienna database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in viennaMoves) {
      val found = nodes.any { node ->
        node.previousAndNextMoves.nextMoves.values.any { it.move == move }
      }
      assertTrue(found, "Move $move should be in the Vienna database")
    }
  }

  @Test
  fun testLondonDatabase() = runTest {
    val londonDb = TestDatabases.london()
    val londonMoves = getLondon()
    val nodes = drainAllNodes(londonDb)

    // Check that the database has the correct number of positions
    assertEquals(
      londonMoves.size,
      nodes.size,
      "London database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in londonMoves) {
      val found = nodes.any { node ->
        node.previousAndNextMoves.nextMoves.values.any { it.move == move }
      }
      assertTrue(found, "Move $move should be in the London database")
    }
  }

  @Test
  fun testScandinavianDatabase() = runTest {
    val scandinavianDb = TestDatabases.scandinavian()
    val scandinavianMoves = getScandinavian()
    val nodes = drainAllNodes(scandinavianDb)

    // Check that the database has the correct number of positions
    assertEquals(
      scandinavianMoves.size,
      nodes.size,
      "Scandinavian database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in scandinavianMoves) {
      val found = nodes.any { node ->
        node.previousAndNextMoves.nextMoves.values.any { it.move == move }
      }
      assertTrue(found, "Move $move should be in the Scandinavian database")
    }
  }

  @Test
  fun testMergeFunction() = runTest {
    val viennaDb = TestDatabases.vienna()
    val londonDb = TestDatabases.london()
    val scandinavianDb = TestDatabases.scandinavian()

    // Merge all databases
    val mergedDb = TestDatabases.merge(viennaDb, londonDb, scandinavianDb)

    val viennaNodes = drainAllNodes(viennaDb)
    val londonNodes = drainAllNodes(londonDb)
    val scandinavianNodes = drainAllNodes(scandinavianDb)

    // Check that the merged database contains all positions from individual databases
    val uniquePositions =
      (viennaNodes.map { it.positionKey } +
          londonNodes.map { it.positionKey } +
          scandinavianNodes.map { it.positionKey })
        .toSet()
        .size

    assertEquals(
      uniquePositions,
      drainAllNodes(mergedDb).size,
      "Merged database should contain all unique positions from individual databases",
    )

    // Check that all moves from individual databases are in the merged database
    for (sourceNodes in listOf(viennaNodes, londonNodes, scandinavianNodes)) {
      for (node in sourceNodes) {
        val mergedNode = assertNotNull(mergedDb.getPosition(node.positionKey))
        for (move in node.previousAndNextMoves.nextMoves.values) {
          assertTrue(
            mergedNode.previousAndNextMoves.nextMoves.values.contains(move),
            "Move $move from a source database should be in merged database",
          )
        }
      }
    }
  }
}
