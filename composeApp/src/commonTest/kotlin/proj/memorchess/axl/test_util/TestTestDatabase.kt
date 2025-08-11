package proj.memorchess.axl.test_util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test the [TestDatabaseQueryManager], most particularly the utility methods to create prefilled
 * test databases.
 */
class TestTestDatabase {
  @Test
  fun testViennaDatabase() {
    val viennaDb = TestDatabaseQueryManager.vienna()
    val viennaMoves = getVienna()

    // Check that the database has the correct number of positions
    assertEquals(
      viennaMoves.size,
      viennaDb.storedNodes.size,
      "Vienna database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in viennaMoves) {
      val found =
        viennaDb.storedNodes.values.any { node ->
          node.previousAndNextMoves.nextMoves.values.any { it.move == move }
        }
      assertTrue(found, "Move $move should be in the Vienna database")
    }
  }

  @Test
  fun testLondonDatabase() {
    val londonDb = TestDatabaseQueryManager.london()
    val londonMoves = getLondon()

    // Check that the database has the correct number of positions
    assertEquals(
      londonMoves.size,
      londonDb.storedNodes.size,
      "London database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in londonMoves) {
      val found =
        londonDb.storedNodes.values.any { node ->
          node.previousAndNextMoves.nextMoves.values.any { it.move == move }
        }
      assertTrue(found, "Move $move should be in the London database")
    }
  }

  @Test
  fun testScandinavianDatabase() {
    val scandinavianDb = TestDatabaseQueryManager.scandinavian()
    val scandinavianMoves = getScandinavian()

    // Check that the database has the correct number of positions
    assertEquals(
      scandinavianMoves.size,
      scandinavianDb.storedNodes.size,
      "Scandinavian database should have the same number of positions as moves",
    )

    // Verify each move is stored in the database
    for (move in scandinavianMoves) {
      val found =
        scandinavianDb.storedNodes.values.any { node ->
          node.previousAndNextMoves.nextMoves.values.any { it.move == move }
        }
      assertTrue(found, "Move $move should be in the Scandinavian database")
    }
  }

  @Test
  fun testMergeFunction() {
    val viennaDb = TestDatabaseQueryManager.vienna()
    val londonDb = TestDatabaseQueryManager.london()
    val scandinavianDb = TestDatabaseQueryManager.scandinavian()

    // Merge all databases
    val mergedDb = TestDatabaseQueryManager.merge(viennaDb, londonDb, scandinavianDb)

    // Check that the merged database contains all positions from individual databases
    val uniquePositions =
      (viennaDb.storedNodes.keys + londonDb.storedNodes.keys + scandinavianDb.storedNodes.keys).size

    assertEquals(
      uniquePositions,
      mergedDb.storedNodes.size,
      "Merged database should contain all unique positions from individual databases",
    )

    // Check that all moves from individual databases are in the merged database
    for (node in viennaDb.storedNodes.values) {
      val mergedNode = mergedDb.storedNodes[node.positionIdentifier.fenRepresentation]
      assertTrue(mergedNode != null, "Position from Vienna database should be in merged database")
      for (move in node.previousAndNextMoves.nextMoves.values) {
        assertTrue(
          mergedNode.previousAndNextMoves.nextMoves.values.contains(move),
          "Move $move from Vienna database should be in merged database",
        )
      }
    }

    for (node in londonDb.storedNodes.values) {
      val mergedNode = mergedDb.storedNodes[node.positionIdentifier.fenRepresentation]
      assertTrue(mergedNode != null, "Position from London database should be in merged database")
      for (move in node.previousAndNextMoves.nextMoves.values) {
        assertTrue(
          mergedNode.previousAndNextMoves.nextMoves.values.contains(move),
          "Move $move from London database should be in merged database",
        )
      }
    }

    for (node in scandinavianDb.storedNodes.values) {
      val mergedNode = mergedDb.storedNodes[node.positionIdentifier.fenRepresentation]
      assertTrue(
        mergedNode != null,
        "Position from Scandinavian database should be in merged database",
      )
      for (move in node.previousAndNextMoves.nextMoves.values) {
        assertTrue(
          mergedNode.previousAndNextMoves.nextMoves.values.contains(move),
          "Move $move from Scandinavian database should be in merged database",
        )
      }
    }
  }
}
