package proj.memorchess.axl.server

import io.kotest.assertions.eq.EqMatcher
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.data.PositionFetched
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

  @Test
  fun testGetPositionNotFound() = testAuthenticated {
    val nonExistentFen = "nonexistent/position/fen"
    client.get(DataRoutes.Node(fen = nonExistentFen)).apply { status.value shouldBeExactly 404 }
  }

  @Test
  fun testAddMoves() = testAuthenticated {
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    val newOriginPosition =
      PositionFetched(
        positionIdentifier = "rnbqkbnr/pppppppp/8/8/8/2N5/PPPPPPPP/R1BQKBNR b KQkq",
        depth = 1,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val newDestinationPosition =
      PositionFetched(
        positionIdentifier = "rnbqkb1r/pppppppp/5n2/8/8/2N5/PPPPPPPP/R1BQKBNR w KQkq",
        depth = 2,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val newMove =
      MoveFetched(
        origin = newOriginPosition,
        destination = newDestinationPosition,
        move = "Nf6",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
      )

    client
      .post(DataRoutes.Moves()) {
        contentType(ContentType.Application.Json)
        setBody(listOf(newMove))
      }
      .apply { status.value shouldBeExactly 201 }

    // Verify the move was added
    client.get(DataRoutes.Moves()).apply {
      status.value shouldBeExactly 200
      val moves = body<List<MoveFetched>>()
      moves shouldHaveSize 7
    }
  }

  @Test
  fun testAddMovesInvalidRequest() = testAuthenticated {
    client
      .post(DataRoutes.Moves()) {
        contentType(ContentType.Application.Json)
        setBody("invalid json")
      }
      .apply { status.value shouldBeExactly 400 }
  }

  @Test
  fun testDeletePosition() = testAuthenticated {
    val fenToDelete = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq"

    // Verify position exists
    client.get(DataRoutes.Node(fen = fenToDelete)).apply { status.value shouldBeExactly 200 }

    // Delete the position
    client.delete(DataRoutes.Node(fen = fenToDelete)).apply { status.value shouldBeExactly 204 }

    // Verify position is no longer accessible
    client.get(DataRoutes.Node(fen = fenToDelete)).apply { status.value shouldBeExactly 404 }
  }

  @Test
  fun testDeleteNonExistentPosition() = testAuthenticated {
    val nonExistentFen = "nonexistent/fen"

    // Should return 204 even if position doesn't exist (idempotent)
    client.delete(DataRoutes.Node(fen = nonExistentFen)).apply { status.value shouldBeExactly 204 }
  }

  @Test
  fun testDeleteMove() = testAuthenticated {
    val originFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val moveName = "e4"

    // Verify move exists in getAllMoves
    val movesBefore = client.get(DataRoutes.Moves()).body<List<MoveFetched>>()
    val moveExists =
      movesBefore.any { it.origin.positionIdentifier == originFen && it.move == moveName }
    moveExists shouldBe true

    // Delete the move
    client.delete(DataRoutes.Move(fen = originFen, move = moveName)).apply {
      status.value shouldBeExactly 204
    }

    // Verify move is no longer in getAllMoves
    val movesAfter = client.get(DataRoutes.Moves()).body<List<MoveFetched>>()
    movesAfter shouldHaveSize (movesBefore.size - 1)
    val moveStillExists =
      movesAfter.any { it.origin.positionIdentifier == originFen && it.move == moveName }
    moveStillExists shouldBe false
  }

  @Test
  fun testDeleteNonExistentMove() = testAuthenticated {
    val nonExistentFen = "nonexistent/fen"
    val nonExistentMove = "Qxh7"

    // Should return 204 even if move doesn't exist (idempotent)
    client.delete(DataRoutes.Move(fen = nonExistentFen, move = nonExistentMove)).apply {
      status.value shouldBeExactly 204
    }
  }

  @Test
  fun testDeleteAllUserData() = testAuthenticated {
    // Verify user has data
    val movesBefore = client.get(DataRoutes.Moves()).body<List<MoveFetched>>()
    movesBefore shouldHaveSize 6

    // Delete all user data
    client.delete(DataRoutes.All()).apply { status.value shouldBeExactly 204 }

    // Verify all data is deleted
    val movesAfter = client.get(DataRoutes.Moves()).body<List<MoveFetched>>()
    movesAfter.shouldBeEmpty()
  }

  @Test
  fun testGetLastUpdate() = testAuthenticated {
    client.get(DataRoutes.LastUpdate()).apply {
      status.value shouldBeExactly 200
      val timestamp = body<Instant>()
      timestamp.shouldNotBeNull()
    }
  }

  @Test
  fun testGetLastUpdateNoData() = testAuthenticated {
    // Delete all data first
    client.delete(DataRoutes.All(hardFrom = Instant.parse("1970-01-01T00:00:00Z"))).apply {
      status.value shouldBeExactly 204
    }

    // Should return 204 No Content when no data exists
    client.get(DataRoutes.LastUpdate()).apply { status.value shouldBeExactly 204 }
  }
}
