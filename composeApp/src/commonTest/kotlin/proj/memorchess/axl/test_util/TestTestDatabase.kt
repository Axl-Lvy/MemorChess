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

    // Each move string should produce one DataMove
    assertEquals(
      viennaMoves.size,
      viennaDb.dataMoves.size,
      "Vienna database should have the same number of DataMoves as input moves",
    )

    // Verify each move is stored in the database
    for (move in viennaMoves) {
      val found = viennaDb.dataMoves.any { it.move == move }
      assertTrue(found, "Move $move should be in the Vienna database")
    }
  }

  @Test
  fun testLondonDatabase() {
    val londonDb = TestDatabaseQueryManager.london()
    val londonMoves = getLondon()

    assertEquals(
      londonMoves.size,
      londonDb.dataMoves.size,
      "London database should have the same number of DataMoves as input moves",
    )

    for (move in londonMoves) {
      val found = londonDb.dataMoves.any { it.move == move }
      assertTrue(found, "Move $move should be in the London database")
    }
  }

  @Test
  fun testScandinavianDatabase() {
    val scandinavianDb = TestDatabaseQueryManager.scandinavian()
    val scandinavianMoves = getScandinavian()

    assertEquals(
      scandinavianMoves.size,
      scandinavianDb.dataMoves.size,
      "Scandinavian database should have the same number of DataMoves as input moves",
    )

    for (move in scandinavianMoves) {
      val found = scandinavianDb.dataMoves.any { it.move == move }
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

    // Check that the merged database contains all unique positions
    val uniquePositions =
      (viennaDb.dataPositions.keys + londonDb.dataPositions.keys + scandinavianDb.dataPositions.keys).size

    assertEquals(
      uniquePositions,
      mergedDb.dataPositions.size,
      "Merged database should contain all unique positions from individual databases",
    )

    // Check that all moves from individual databases are in the merged database
    for (move in viennaDb.dataMoves) {
      val found = mergedDb.dataMoves.any {
        it.origin == move.origin && it.destination == move.destination && it.move == move.move
      }
      assertTrue(found, "Move ${move.move} from Vienna database should be in merged database")
    }

    for (move in londonDb.dataMoves) {
      val found = mergedDb.dataMoves.any {
        it.origin == move.origin && it.destination == move.destination && it.move == move.move
      }
      assertTrue(found, "Move ${move.move} from London database should be in merged database")
    }

    for (move in scandinavianDb.dataMoves) {
      val found = mergedDb.dataMoves.any {
        it.origin == move.origin && it.destination == move.destination && it.move == move.move
      }
      assertTrue(found, "Move ${move.move} from Scandinavian database should be in merged database")
    }
  }
}
