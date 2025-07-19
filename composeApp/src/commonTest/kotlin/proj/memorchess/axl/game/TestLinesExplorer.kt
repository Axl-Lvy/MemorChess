package proj.memorchess.axl.game

import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.LocalDatabaseHolder
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.moves.IMove
import proj.memorchess.axl.core.engine.moves.factory.ACheckChecker
import proj.memorchess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.memorchess.axl.core.engine.moves.factory.SimpleMoveFactory
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.NoOpReloader
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.components.popup.ToastRendererHolder

class TestLinesExplorer : TestWithKoin() {
  private lateinit var interactionsManager: LinesExplorer
  private lateinit var moveFactory: SimpleMoveFactory
  private lateinit var checkChecker: ACheckChecker
  private val database by inject<DatabaseQueryManager>()

  private fun initialize() {
    runTest {
      database.deleteAll()
      NodeManager.resetCacheFromDataBase()
    }
    ToastRendererHolder.init { _, _ -> }
    interactionsManager = LinesExplorer()
    moveFactory = SimpleMoveFactory(interactionsManager.game.position)
    checkChecker = DummyCheckChecker(interactionsManager.game.position)
  }

  @AfterTest
  fun tearDown() {
    LocalDatabaseHolder.reset()
  }

  @Test
  @Ignore
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
  @Ignore
  fun testDelete() {
    testManyGames()
    testPrevious()
    runTest { interactionsManager.delete(NoOpReloader) }
    interactionsManager.forward(NoOpReloader)
    assertPawnOnE2()
  }

  @Test
  fun testSave() {
    initialize()
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e4")
    runTest {
      interactionsManager.save()

      // Verify the move was saved as good
      val storedNode = database.getPosition(startPosition)
      val savedMove = storedNode?.previousAndNextMoves?.nextMoves?.values?.find { it.move == "e4" }
      assertEquals(true, savedMove?.isGood, "Move should be saved as good")
    }
  }

  @Test
  fun testSaveGoodThenBad() {
    initialize()
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e4")
    val secondPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e7")
    clickOnTile("e5")
    val thirdPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("b1")
    clickOnTile("c3")
    runTest { interactionsManager.save() }

    // Verify the move was saved as bad
    verifyStoredNode(startPosition, true, "e4")
    verifyStoredNode(secondPosition, false, "e5")
    verifyStoredNode(thirdPosition, true, "Nc3")
  }

  @Test
  fun testSideMoveNotSaved() {
    initialize()
    val startPosition = interactionsManager.game.position.createIdentifier()
    clickOnTile("e2")
    clickOnTile("e3")
    interactionsManager.back(NoOpReloader)
    clickOnTile("e2")
    clickOnTile("e4")
    runTest { interactionsManager.save() }
    verifyStoredNode(startPosition, true, "e4")
    var storedNode: StoredNode? = null
    runTest { storedNode = database.getPosition(startPosition) }
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

  private fun testGame(stringMoves: List<String>) {
    initialize()
    val refGame = Game()
    stringMoves.forEach {
      val move = createMove(it)
      clickOnTile(move.origin())
      clickOnTile(move.destination())
      if (it.contains("=")) {
        assertTrue { interactionsManager.game.needPromotion() }
        runTest {
          interactionsManager.applyPromotion(
            it.split("=")[1].substring(0, 1).lowercase(),
            NoOpReloader,
          )
        }
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
