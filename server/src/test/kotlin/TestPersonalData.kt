package proj.memorchess.axl.server

import io.kotest.assertions.eq.EqMatcher
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import kotlin.test.Test
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.routes.DataRoutes

class TestPersonalData {
  @Test
  fun testGetMoves() = testAuthenticated {
    client.get(DataRoutes.Moves()).apply {
      status.value shouldBeExactly 200
      val moves = body<List<MoveFetched>>()
      moves shouldHaveSize 6
    }
  }

  @Test
  fun testGetPosition() {
    val testFen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq"
    testAuthenticated {
      client.get(DataRoutes.Node(fen = testFen)).apply {
        status.value shouldBeExactly 200
        val node = body<NodeFetched>()
        node.position.positionIdentifier shouldBe testFen
        node.moves shouldHaveSize 2
        for (move in node.moves) {
          move should
            {
              it.origin.positionIdentifier should
                EqMatcher(move.origin.positionIdentifier)
                  .or(EqMatcher(move.destination.positionIdentifier))
            }
        }
      }
    }
  }
}
