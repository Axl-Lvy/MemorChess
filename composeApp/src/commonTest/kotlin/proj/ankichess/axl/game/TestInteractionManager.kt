package proj.ankichess.axl.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.factory.ACheckChecker
import proj.ankichess.axl.core.impl.engine.moves.factory.DummyCheckChecker
import proj.ankichess.axl.core.impl.engine.moves.factory.SimpleMoveFactory
import proj.ankichess.axl.core.impl.interactions.InteractionManager
import proj.ankichess.axl.core.intf.data.DatabaseHolder
import proj.ankichess.axl.core.intf.engine.board.IBoard
import proj.ankichess.axl.core.intf.engine.moves.IMove
import proj.ankichess.axl.test_util.NoOpReloader
import proj.ankichess.axl.test_util.TestDataBase
import proj.ankichess.axl.ui.popup.PopupRendererHolder

class TestInteractionManager {
  private lateinit var interactionManager: InteractionManager
  private lateinit var moveFactory: SimpleMoveFactory
  private lateinit var checkChecker: ACheckChecker

  private fun initialize() {
    DatabaseHolder.init(TestDataBase.empty())
    PopupRendererHolder.init { _, _ -> }
    interactionManager = InteractionManager()
    moveFactory = SimpleMoveFactory(interactionManager.game.position)
    checkChecker = DummyCheckChecker(interactionManager.game.position)
  }

  @Test
  fun testManyGames() {
    val gameList = getGames()
    gameList.forEach { testGame(it) }
  }

  @Test
  fun testPrevious() {
    initialize()
    interactionManager.clickOnTile(IBoard.getCoords("e2"))
    interactionManager.clickOnTile(IBoard.getCoords("e4"))
    interactionManager.back(NoOpReloader)
    assertPawnOnE2()
  }

  @Test
  fun testForward() {
    testPrevious()
    interactionManager.forward(NoOpReloader)
    assertPawnOnE4()
  }

  @Test
  fun testDelete() {
    testManyGames()
    testPrevious()
    runTest { interactionManager.delete(NoOpReloader) }
    interactionManager.forward(NoOpReloader)
    assertPawnOnE2()
  }

  private fun assertPawnOnE4() {
    assertPawnOnTile("e4", "e2")
  }

  private fun assertPawnOnE2() {
    assertPawnOnTile("e2", "e4")
  }

  private fun assertPawnOnTile(pawnTile: String, emptyTile: String) {
    interactionManager.game.position.board.getTile(pawnTile).getSafePiece()?.let {
      assertEquals("P", it.toString())
    } ?: error("No piece found on $pawnTile")
    assertNull(interactionManager.game.position.board.getTile(emptyTile).getSafePiece())
  }

  private fun testGame(stringMoves: List<String>) {
    initialize()
    val refGame = Game()
    stringMoves.forEach {
      val move = createMove(it)
      interactionManager.clickOnTile(move.origin())
      interactionManager.clickOnTile(move.destination())
      if (it.contains("=")) {
        interactionManager.game.applyPromotion(it.split("=")[1].substring(0, 1).lowercase())
      }
      refGame.playMove(it)
      validateGame(refGame)
    }
  }

  private fun createMove(stringMove: String): IMove {
    return moveFactory.parseMove(stringMove, checkChecker)
  }

  private fun validateGame(refGame: Game) {
    assertEquals(refGame.toString(), interactionManager.game.toString())
  }
}
