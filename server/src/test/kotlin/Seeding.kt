package proj.memorchess.axl.server

import io.ktor.server.application.Application
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.server.data.MoveEntity
import proj.memorchess.axl.server.data.PositionEntity
import proj.memorchess.axl.server.data.UserMoveEntity
import proj.memorchess.axl.server.data.UserPositionEntity

/**
 * Seeds the database with test data including:
 * - One test user
 * - Several chess positions (starting position + positions after some moves)
 * - Moves connecting these positions
 * - User positions and user moves linking the user to the data
 */
fun Application.seedTestData() {
  transaction {
    val testUser = createUser("admin", "admin") ?: return@transaction

    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    // Create positions
    val startPosition =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
      }

    val e4Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"
      }

    val e4e5Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq"
      }

    val e4e5Nf3Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq"
      }

    val d4Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq"
      }

    val d4d5Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq"
      }

    val nf3Position =
      PositionEntity.new {
        fenRepresentation = "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq"
      }

    // Create moves
    val e4Move =
      MoveEntity.new {
        origin = startPosition
        destination = e4Position
        name = "e4"
      }

    val e5Move =
      MoveEntity.new {
        origin = e4Position
        destination = e4e5Position
        name = "e5"
      }

    val nf3AfterE4E5Move =
      MoveEntity.new {
        origin = e4e5Position
        destination = e4e5Nf3Position
        name = "Nf3"
      }

    val d4Move =
      MoveEntity.new {
        origin = startPosition
        destination = d4Position
        name = "d4"
      }

    val d5Move =
      MoveEntity.new {
        origin = d4Position
        destination = d4d5Position
        name = "d5"
      }

    val nf3FromStartMove =
      MoveEntity.new {
        origin = startPosition
        destination = nf3Position
        name = "Nf3"
      }

    // Create user positions
    UserPositionEntity.new {
      user = testUser
      position = startPosition
      depth = 0
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = e4Position
      depth = 1
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = e4e5Position
      depth = 2
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = e4e5Nf3Position
      depth = 3
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = d4Position
      depth = 1
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = d4d5Position
      depth = 2
      lastTrainingDate = today
      nextTrainingDate = today
    }

    UserPositionEntity.new {
      user = testUser
      position = nf3Position
      depth = 1
      lastTrainingDate = today
      nextTrainingDate = today
    }

    // Create user moves
    UserMoveEntity.new {
      user = testUser
      move = e4Move
      isGood = true
    }

    UserMoveEntity.new {
      user = testUser
      move = e5Move
      isGood = true
    }

    UserMoveEntity.new {
      user = testUser
      move = nf3AfterE4E5Move
      isGood = true
    }

    UserMoveEntity.new {
      user = testUser
      move = d4Move
      isGood = true
    }

    UserMoveEntity.new {
      user = testUser
      move = d5Move
      isGood = true
    }

    UserMoveEntity.new {
      user = testUser
      move = nf3FromStartMove
      isGood = true
    }
  }
}
