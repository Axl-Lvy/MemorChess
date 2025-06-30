package proj.memorchess.axl.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.moves.IMove
import proj.memorchess.axl.core.engine.moves.factory.ACheckChecker
import proj.memorchess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.memorchess.axl.core.engine.moves.factory.SimpleMoveFactory
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.NoOpReloader
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.ui.components.popup.ToastRendererHolder

class TestLinesExplorer {
  private lateinit var interactionsManager: LinesExplorer
  private lateinit var moveFactory: SimpleMoveFactory
  private lateinit var checkChecker: ACheckChecker
  private lateinit var database: TestDatabase

  private fun initialize() {
    database = TestDatabase.empty()
    DatabaseHolder.init(database)
    runTest { NodeManager.resetCacheFromDataBase() }
    ToastRendererHolder.init { _, _ -> }
    interactionsManager = LinesExplorer()
    moveFactory = SimpleMoveFactory(interactionsManager.game.position)
    checkChecker = DummyCheckChecker(interactionsManager.game.position)
  }

  @Test
  fun testManyGames() {
    val gameList = getGames()
    gameList.forEach { testGame(it) }
  }

  @Test
  fun testPrevious() {
    initialize()
    clickOnTile("e2")
    clickOnTile("e4")
    interactionsManager.back(NoOpReloader)
    assertPawnOnE2()
  }

  @Test
  fun testForward() {
    testPrevious()
    interactionsManager.forward(NoOpReloader)
    assertPawnOnE4()
  }

  @Test
  fun testDelete() {
    testManyGames()
    testPrevious()
    runTest { interactionsManager.delete(NoOpReloader) }
    interactionsManager.forward(NoOpReloader)
    assertPawnOnE2()
  }

  @Test
  fun testSaveGood() {
    initialize()
    val startPosition = interactionsManager.game.position.toImmutablePosition()
    clickOnTile("e2")
    clickOnTile("e4")
    runTest { interactionsManager.saveGood() }

    // Verify the move was saved as good
    val storedNode = database.storedNodes[startPosition.fenRepresentation]
    val savedMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
    assertEquals(true, savedMove?.isGood, "Move should be saved as good")
  }

  @Test
  fun testSaveBad() {
    initialize()
    val startPosition = interactionsManager.game.position.toImmutablePosition()
    clickOnTile("e2")
    clickOnTile("e4")
    runTest { interactionsManager.saveBad() }

    // Verify the move was saved as bad
    val storedNode = database.storedNodes[startPosition.fenRepresentation]
    val savedMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
    assertEquals(false, savedMove?.isGood, "Move should be saved as bad")
  }

  @Test
  fun testSaveGoodThenBad() {
    initialize()
    val startPosition = interactionsManager.game.position.toImmutablePosition()
    clickOnTile("e2")
    clickOnTile("e4")
    runTest { interactionsManager.saveBad() }
    val secondPosition = interactionsManager.game.position.toImmutablePosition()
    clickOnTile("e7")
    clickOnTile("e5")
    runTest { interactionsManager.saveGood() }

    // Verify the move was saved as bad
    val storedRootNode = database.storedNodes[startPosition.fenRepresentation]
    val savedBadMove =
      storedRootNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
    assertEquals(false, savedBadMove?.isGood, "Move should be saved as bad")
    val storedSecondNode = database.storedNodes[secondPosition.fenRepresentation]
    val savedGoodMove =
      storedSecondNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e5" }
    assertEquals(true, savedGoodMove?.isGood, "Move should be saved as good")
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

  private fun testGame(stringMoves: List<String>) {
    initialize()
    val refGame = Game()
    stringMoves.forEach {
      val move = createMove(it)
      clickOnTile(move.origin())
      clickOnTile(move.destination())
      if (it.contains("=")) {
        interactionsManager.game.applyPromotion(it.split("=")[1].substring(0, 1).lowercase())
      }
      refGame.playMove(it)
      validateGame(refGame)
    }
  }

  private fun createMove(stringMove: String): IMove {
    return moveFactory.parseMove(stringMove, checkChecker)
  }

  private fun validateGame(refGame: Game) {
    assertEquals(refGame.toString(), interactionsManager.game.toString())
  }

  private fun clickOnTile(tile: String) {
    clickOnTile(IBoard.getCoords(tile))
  }

  private fun clickOnTile(coords: Pair<Int, Int>) {
    runTest { interactionsManager.clickOnTile(coords, NoOpReloader) }
  }
}
