package proj.ankichess.axl.test_util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import proj.ankichess.axl.game.getLondon
import proj.ankichess.axl.game.getScandinavian
import proj.ankichess.axl.game.getVienna

/**
 * Test the [TestDataBase], uitr
 *
 * @constructor Create empty Test test database
 */
class TestTestDatabase {
    @Test
    fun testViennaDatabase() {
        val viennaDb = TestDataBase.vienna()
        val viennaMoves = getVienna()
        
        // Check that the database has the correct number of positions
        assertEquals(viennaMoves.size, viennaDb.storedNodes.size, 
            "Vienna database should have the same number of positions as moves")
        
        // Verify each move is stored in the database
        for (move in viennaMoves) {
            val found = viennaDb.storedNodes.values.any { node ->
                node.getAvailableMoveList().contains(move)
            }
            assertTrue(found, "Move $move should be in the Vienna database")
        }
    }
    
    @Test
    fun testLondonDatabase() {
        val londonDb = TestDataBase.london()
        val londonMoves = getLondon()
        
        // Check that the database has the correct number of positions
        assertEquals(londonMoves.size, londonDb.storedNodes.size, 
            "London database should have the same number of positions as moves")
        
        // Verify each move is stored in the database
        for (move in londonMoves) {
            val found = londonDb.storedNodes.values.any { node ->
                node.getAvailableMoveList().contains(move)
            }
            assertTrue(found, "Move $move should be in the London database")
        }
    }
    
    @Test
    fun testScandinavianDatabase() {
        val scandinavianDb = TestDataBase.scandinavian()
        val scandinavianMoves = getScandinavian()
        
        // Check that the database has the correct number of positions
        assertEquals(scandinavianMoves.size, scandinavianDb.storedNodes.size, 
            "Scandinavian database should have the same number of positions as moves")
        
        // Verify each move is stored in the database
        for (move in scandinavianMoves) {
            val found = scandinavianDb.storedNodes.values.any { node ->
                node.getAvailableMoveList().contains(move)
            }
            assertTrue(found, "Move $move should be in the Scandinavian database")
        }
    }
    
    @Test
    fun testMergeFunction() {
        val viennaDb = TestDataBase.vienna()
        val londonDb = TestDataBase.london()
        val scandinavianDb = TestDataBase.scandinavian()
        
        // Merge all databases
        val mergedDb = TestDataBase.merge(viennaDb, londonDb, scandinavianDb)
        
        // Check that the merged database contains all positions from individual databases
        val uniquePositions = (viennaDb.storedNodes.keys + londonDb.storedNodes.keys + scandinavianDb.storedNodes.keys).size
        
        assertEquals(uniquePositions, mergedDb.storedNodes.size, 
            "Merged database should contain all unique positions from individual databases")
        
        // Check that all moves from individual databases are in the merged database
        for (node in viennaDb.storedNodes.values) {
            val mergedNode = mergedDb.storedNodes[node.positionKey.fenRepresentation]
            assertTrue(mergedNode != null, "Position from Vienna database should be in merged database")
            for (move in node.getAvailableMoveList()) {
                assertTrue(mergedNode!!.getAvailableMoveList().contains(move), 
                    "Move $move from Vienna database should be in merged database")
            }
        }
        
        for (node in londonDb.storedNodes.values) {
            val mergedNode = mergedDb.storedNodes[node.positionKey.fenRepresentation]
            assertTrue(mergedNode != null, "Position from London database should be in merged database")
            for (move in node.getAvailableMoveList()) {
                assertTrue(mergedNode!!.getAvailableMoveList().contains(move), 
                    "Move $move from London database should be in merged database")
            }
        }
        
        for (node in scandinavianDb.storedNodes.values) {
            val mergedNode = mergedDb.storedNodes[node.positionKey.fenRepresentation]
            assertTrue(mergedNode != null, "Position from Scandinavian database should be in merged database")
            for (move in node.getAvailableMoveList()) {
                assertTrue(mergedNode!!.getAvailableMoveList().contains(move), 
                    "Move $move from Scandinavian database should be in merged database")
            }
        }
    }
}