package proj.memorchess.axl.core.interaction

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.moves.Move
import proj.memorchess.axl.core.engine.moves.factory.CheckChecker
import proj.memorchess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.memorchess.axl.core.engine.moves.factory.RealMoveFactory
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.getGames

class TestLinesExplorer : TestWithKoin {
  private lateinit var interactionsManager: LinesExplorer
  private lateinit var moveFactory: RealMoveFactory
  private lateinit var checkChecker: CheckChecker
  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()

  private fun initialize() {
    runTest {
      database.deleteAll(null)
      nodeManager.resetCacheFromDataBase()
    }
    interactionsManager = LinesExplorer()
    moveFactory = RealMoveFactory(interactionsManager.game.position)
    checkChecker = DummyCheckChecker(interactionsManager.game.position)
  }

  @BeforeTest
  override fun setUp() {
    super.setUp()
    initialize()
  }

  @Test
  fun testManyGames() = runTest {
    val gameList = getGames().shuffled().take(10)
    gameList.forEach { testGame(it) }
  }

  @Test fun testPrevious() = runTest { previousWorkflow() }

  @Test
  fun testForward() = runTest {
    previousWorkflow()
    interactionsManager.forward()
    assertPawnOnE4()
  }

  @Test
  fun testDelete() = runTest {
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
  fun testSave() = runTest {
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e4")
    interactionsManager.save()

    // Verify the move was saved as good
    val storedNode = database.getPosition(startPosition)
    val savedMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
    assertEquals(true, savedMove?.isGood, "Move should be saved as good")
  }

  @Test
  fun testSaveGoodThenBad() = runTest {
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e4")
    val secondPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e7")
    clickOnTile("e5")
    val thirdPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("b1")
    clickOnTile("c3")
    interactionsManager.save()

    // Verify the move was saved as bad
    verifyStoredNode(startPosition, true, "e4")
    verifyStoredNode(secondPosition, false, "e5")
    verifyStoredNode(thirdPosition, true, "Nc3")
  }

  @Test
  fun testSideMoveNotSaved() = runTest {
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e3")
    interactionsManager.back()
    clickOnTile("e2")
    clickOnTile("e4")
    interactionsManager.save()
    verifyStoredNode(startPosition, true, "e4")
    val storedNode: StoredNode? = database.getPosition(startPosition)
    val savedBadMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e3" }
    assertNull(savedBadMove)
  }

  private fun verifyStoredNode(
    positionIdentifier: PositionIdentifier,
    isGood: Boolean,
    move: String,
  ) {
    var storedNode: StoredNode? = null
    runTest { storedNode = database.getPosition(positionIdentifier) }
    val savedBadMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == move }
    assertEquals(isGood, savedBadMove?.isGood, "Move should be saved as bad")
  }

  private fun testGame(stringMoves: List<String>) = runTest {
    initialize()
    val refGame = Game()
    stringMoves.forEach {
      val move = createMove(it)
      clickOnTile(move.origin())
      clickOnTile(move.destination())
      if (it.contains("=")) {
        assertTrue { interactionsManager.game.needPromotion() }
        interactionsManager.applyPromotion(it.split("=")[1][0].lowercase())
      }
      refGame.playMove(it)
      validateGame(refGame)
    }
  }

  private fun assertPawnOnE4() {
    assertPawnOnTile("e4", "e2")
  }

  private fun assertPawnOnE2() {
    assertPawnOnTile("e2", "e4")
  }

  private fun assertPawnOnTile(pawnTile: String, emptyTile: String) {
    interactionsManager.game.position.board.getTile(pawnTile).getSafePiece()?.let {
      assertEquals("P", it.toString())
    } ?: error("No piece found on $pawnTile")
    assertNull(interactionsManager.game.position.board.getTile(emptyTile).getSafePiece())
  }

  private fun createMove(stringMove: String): Move {
    return moveFactory.parseMove(stringMove, checkChecker)
  }

  private fun validateGame(refGame: Game) {
    assertEquals(refGame.toString(), interactionsManager.game.toString())
  }

  private suspend fun clickOnTile(tile: String) {
    clickOnTile(IBoard.getCoords(tile))
  }

  private suspend fun clickOnTile(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  @Test
  fun testCalculateNumberOfNodeToDelete_SimpleLine() = runTest {
    interactionsManager.playMove("e4")
    interactionsManager.playMove("e5")
    interactionsManager.playMove("Nf3")
    interactionsManager.back()
    val count = interactionsManager.calculateNumberOfNodeToDelete()
    assertEquals(2, count)
  }

  @Test
  fun testCalculateNumberOfNodeToDelete_ConvergingNodes() = runTest {
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
}
