package proj.memorchess.axl.core.interaction

import kotlin.test.*
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.TestWithKoin

class TestLinesExplorer : TestWithKoin() {
  private lateinit var interactionsManager: LinesExplorer
  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()

  private suspend fun initialize() {
    database.deleteAll(null)
    nodeManager.resetCacheFromSource()
    interactionsManager = LinesExplorer(nodeManager = nodeManager)
  }

  override suspend fun setUp() {
    initialize()
  }

  @Test fun testPrevious() = test { previousWorkflow() }

  @Test
  fun testForward() = test {
    previousWorkflow()
    interactionsManager.forward()
    assertPawnOnE4()
  }

  @Test
  fun testDelete() = test {
    previousWorkflow()
    interactionsManager.delete()
    interactionsManager.forward()
    assertPawnOnE2()
  }

  private suspend fun previousWorkflow() {
    clickOnTile("e2")
    clickOnTile("e4")
    assertPawnOnE4()
    interactionsManager.back()
    assertPawnOnE2()
  }

  @Test
  fun testSave() = test {
    val startPosition = interactionsManager.engine.toPositionKey()
    clickOnTile("e2")
    clickOnTile("e4")
    interactionsManager.save()

    // Verify the move was saved as good
    val storedNode = database.getPosition(startPosition)
    val savedMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
    assertEquals(true, savedMove?.isGood, "Move should be saved as good")
  }

  @Test
  fun testSaveGoodThenBad() = test {
    val startPosition = interactionsManager.engine.toPositionKey()
    clickOnTile("e2")
    clickOnTile("e4")
    val secondPosition = interactionsManager.engine.toPositionKey()
    clickOnTile("e7")
    clickOnTile("e5")
    val thirdPosition = interactionsManager.engine.toPositionKey()
    clickOnTile("b1")
    clickOnTile("c3")
    interactionsManager.save()

    // Verify the move was saved as bad
    verifyStoredNode(startPosition, true, "e4")
    verifyStoredNode(secondPosition, false, "e5")
    verifyStoredNode(thirdPosition, true, "Nc3")
  }

  @Test
  fun testSideMoveNotSaved() = test {
    val startPosition = interactionsManager.engine.toPositionKey()
    clickOnTile("e2")
    clickOnTile("e3")
    interactionsManager.back()
    clickOnTile("e2")
    clickOnTile("e4")
    interactionsManager.save()
    verifyStoredNode(startPosition, true, "e4")
    val dataNode: DataNode? = database.getPosition(startPosition)
    val savedBadMove = dataNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e3" }
    assertNull(savedBadMove)
  }

  @Test
  fun testSelectTile() = test {
    clickOnTile("e3")
    assertNull(interactionsManager.selectedTile, "No piece should be selected on an empty tile.")
    clickOnTile("e2")
    assertEquals(interactionsManager.selectedTile, Pair(1, 4))
    clickOnTile("d2")
    assertEquals(
      Pair(1, 3),
      interactionsManager.selectedTile,
      "Piece from the same player has been selected. Selected tile should be updated.",
    )
    clickOnTile("d4")
    assertNull(interactionsManager.selectedTile, "After a move, the selected tile should be null.")
    clickOnTile("d2")
    assertNull(interactionsManager.selectedTile, "Selected a tile from the wrong player.")
    clickOnTile("e7")
    assertEquals(Pair(6, 4), interactionsManager.selectedTile)
    clickOnTile("d5")
    assertNull(interactionsManager.selectedTile, "Invalid move should reset selected tile.")
  }

  private suspend fun verifyStoredNode(positionKey: PositionKey, isGood: Boolean, move: String) {
    val dataNode = database.getPosition(positionKey)
    val savedBadMove = dataNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == move }
    assertEquals(isGood, savedBadMove?.isGood, "Move should be saved as bad")
  }

  private fun assertPawnOnE4() {
    assertPawnOnTile("e4", "e2")
  }

  private fun assertPawnOnE2() {
    assertPawnOnTile("e2", "e4")
  }

  private fun assertPawnOnTile(pawnTile: String, emptyTile: String) {
    val pawnRow = pawnTile[1] - '1'
    val pawnCol = pawnTile[0] - 'a'
    val emptyRow = emptyTile[1] - '1'
    val emptyCol = emptyTile[0] - 'a'
    interactionsManager.engine.pieceAt(pawnRow, pawnCol)?.let { assertEquals("P", it.toString()) }
      ?: error("No piece found on $pawnTile")
    assertNull(interactionsManager.engine.pieceAt(emptyRow, emptyCol))
  }

  private suspend fun clickOnTile(tile: String) {
    val col = tile[0] - 'a'
    val row = tile[1] - '1'
    clickOnTile(Pair(row, col))
  }

  private suspend fun clickOnTile(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  @Test
  fun testCalculateNumberOfNodeToDelete_SimpleLine() = test {
    interactionsManager.playMove("e4")
    interactionsManager.playMove("e5")
    interactionsManager.playMove("Nf3")
    interactionsManager.back()
    val count = interactionsManager.calculateNumberOfNodeToDelete()
    assertEquals(2, count)
  }

  @Test
  fun testCalculateNumberOfNodeToDelete_ConvergingNodes() = test {
    // Line 1
    interactionsManager.playMove("e4")
    interactionsManager.playMove("e5")
    interactionsManager.playMove("Nf3")
    interactionsManager.reset()
    // Line 2
    interactionsManager.playMove("Nf3")
    interactionsManager.playMove("e5")
    interactionsManager.playMove("e4")
    interactionsManager.back()
    // Now, both lines should converge to the same position
    // Deleting here should not delete the converged node if still reachable from the other path
    val count1 = interactionsManager.calculateNumberOfNodeToDelete()
    assertEquals(1, count1, "Should only delete the current node, the next one is still reachable")
  }

  @Test
  fun testCreateLinesExplorerFromCustomPosition() = test {
    // Arrange: create a custom position by playing some moves
    interactionsManager.playMove("e4")
    interactionsManager.playMove("e5")
    interactionsManager.save()
    val customPosition = interactionsManager.engine.toPositionKey()

    // Act: create a new LinesExplorer from this position
    val explorerFromCustom = LinesExplorer(customPosition, nodeManager)
    // Assert: explorer's game should be at the custom position
    assertEquals(customPosition, explorerFromCustom.engine.toPositionKey())
  }

  @Test
  fun testGoBackFromStartPosition() = test {
    // Arrange: create a custom position by playing some moves
    interactionsManager.playMove("e4")
    interactionsManager.playMove("e5")
    interactionsManager.save()
    val customPosition = interactionsManager.engine.toPositionKey()

    // Act: create a new LinesExplorer from this position
    val explorerFromCustom = LinesExplorer(customPosition, nodeManager)
    // Assert: explorer's game should be at the custom position
    assertEquals(customPosition, explorerFromCustom.engine.toPositionKey())
    explorerFromCustom.back()
    interactionsManager.back()
    // After going back, the position should be the one before "e5"
    assertEquals(
      explorerFromCustom.engine.toPositionKey(),
      interactionsManager.engine.toPositionKey(),
    )
  }

  @Test
  fun testNoPreviousMove() = test {
    interactionsManager.back()
    assertEquals(PositionKey.START_POSITION, interactionsManager.engine.toPositionKey())
  }

  @Test
  fun testRootNodeWithPreviousMoves() = test {
    interactionsManager.playMove("Nf3")
    interactionsManager.save()
    interactionsManager.playMove("Nf6")
    interactionsManager.save()
    interactionsManager.playMove("Ng1")
    interactionsManager.save()
    interactionsManager.playMove("Ng8")
    interactionsManager.save()

    assertEquals(PositionKey.START_POSITION, interactionsManager.engine.toPositionKey())
    interactionsManager.reset()
    assertEquals(PositionKey.START_POSITION, interactionsManager.engine.toPositionKey())
  }

  @Test
  fun testBlocked() = test {
    interactionsManager.block()
    clickOnTile("e2")
    clickOnTile("e4")
    assertEquals(interactionsManager.engine.toPositionKey(), PositionKey.START_POSITION)
    interactionsManager.unblock()
    clickOnTile("e2")
    clickOnTile("e4")
    assertNotEquals(interactionsManager.engine.toPositionKey(), PositionKey.START_POSITION)
  }
}
