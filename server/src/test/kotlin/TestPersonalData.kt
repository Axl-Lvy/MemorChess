package proj.memorchess.axl.server

import io.kotest.assertions.eq.EqMatcher
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPath
import kotlin.test.Test
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched

class TestPersonalData {
  @Test
  fun testGetMoves() = testAuthenticated {
    client.get("/data/moves").apply {
      assert(status.value == 200)
      val moves = body<List<MoveFetched>>()
      assert(moves.size == 6)
    }
  }

  @Test
  fun testGetPosition() {
    val testFen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq"
    testAuthenticated {
      client.get("/data/node/" + testFen.encodeURLPath(encodeSlash = true)).apply {
        assert(status.value == 200) {
          "Expected 200 OK but got ${status.value}. Response:" + "$this"
        }
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
