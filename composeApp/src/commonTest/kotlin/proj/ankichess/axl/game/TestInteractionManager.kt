package proj.ankichess.axl.game

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.board.InteractionManager
import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.moves.IMove
import proj.ankichess.axl.core.engine.moves.factory.ACheckChecker
import proj.ankichess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.ankichess.axl.core.engine.moves.factory.SimpleMoveFactory

class TestInteractionManager {
  private lateinit var interactionManager: InteractionManager
  private lateinit var moveFactory: SimpleMoveFactory
  private lateinit var checkChecker: ACheckChecker

  private fun initialize() {
    interactionManager = InteractionManager()
    moveFactory = SimpleMoveFactory(interactionManager.game.position)
    checkChecker = DummyCheckChecker(interactionManager.game.position)
  }

  @Test
  fun testManyGames() {
    val gameList = getGames()
    gameList.forEach { testGame(it) }
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
