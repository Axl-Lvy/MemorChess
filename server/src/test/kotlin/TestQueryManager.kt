package proj.memorchess.axl.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.server.data.MoveEntity
import proj.memorchess.axl.server.data.MovesTable
import proj.memorchess.axl.server.data.PositionEntity
import proj.memorchess.axl.server.data.PositionsTable
import proj.memorchess.axl.server.data.UserMoveEntity
import proj.memorchess.axl.server.data.UserMovesTable
import proj.memorchess.axl.server.data.UserPermissionEntity
import proj.memorchess.axl.server.data.addMoves
import proj.memorchess.axl.server.data.deleteAllUserData
import proj.memorchess.axl.server.data.deleteMove
import proj.memorchess.axl.server.data.deletePosition
import proj.memorchess.axl.server.data.getAllMoves
import proj.memorchess.axl.server.data.getLastUpdate
import proj.memorchess.axl.server.data.getNode
import proj.memorchess.axl.server.data.getUser
import proj.memorchess.axl.server.data.hasUserPermission
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.PositionFetched

class TestQueryManager {

  @Test
  fun testGetUser_existingUser() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()
    user.email shouldBe "admin"
  }

  @Test
  fun testGetUser_nonExistentUser() = testApplicationWithTestModule {
    val user = getUser("nonexistent@example.com")
    user.shouldBeNull()
  }

  @Test
  fun testHasUserPermission_withPermission() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    // Grant a permission to the user
    transaction {
      UserPermissionEntity.new {
        this.user = user
        this.permission = "test_permission"
      }
    }

    val hasPermission = hasUserPermission(user.id.value.toString(), "test_permission")
    hasPermission shouldBe true
  }

  @Test
  fun testHasUserPermission_withoutPermission() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val hasPermission = hasUserPermission(user.id.value.toString(), "nonexistent_permission")
    hasPermission shouldBe false
  }

  @Test
  fun testGetAllMoves_returnsAllNonDeletedMoves() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val moves = getAllMoves(user.id.value.toString())
    moves.shouldNotBeEmpty()
    moves shouldHaveSize 6
    moves.all { !it.isDeleted } shouldBe true
  }

  @Test
  fun testGetAllMoves_excludesDeletedMoves() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    // Mark a move as deleted
    transaction {
      val moveToDelete = UserMoveEntity.find { UserMovesTable.userId eq user.id.value }.first()
      moveToDelete.isDeleted = true
    }

    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize 5
    moves.all { !it.isDeleted } shouldBe true
  }

  @Test
  fun testAddMoves_createsNewPositionsAndMoves() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

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

    addMoves("admin", listOf(newMove))

    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize 7
  }

  @Test
  fun testAddMoves_handlesExistingPositions() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    // Use existing positions
    val existingOriginPosition =
      PositionFetched(
        positionIdentifier = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq",
        depth = 0,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val newDestinationPosition =
      PositionFetched(
        positionIdentifier = "rnbqkbnr/pppppppp/8/8/8/6P1/PPPPPP1P/RNBQKBNR b KQkq",
        depth = 1,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val newMove =
      MoveFetched(
        origin = existingOriginPosition,
        destination = newDestinationPosition,
        move = "g3",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
      )

    addMoves("admin", listOf(newMove))

    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize 7
  }

  @Test
  fun testAddMoves_withNonExistentUser() = testApplicationWithTestModule {
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    val position =
      PositionFetched(
        positionIdentifier = "test_fen",
        depth = 0,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val move =
      MoveFetched(
        origin = position,
        destination = position,
        move = "test",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
      )

    // Should not throw an exception, just return early
    addMoves("nonexistent@example.com", listOf(move))

    // Verify no moves were added by checking the total count remains the same
    val user = getUser("admin")
    user.shouldNotBeNull()
    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize 6
  }

  @Test
  fun testGetNode_existingPosition() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val fen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq"
    val node = getNode(user.id.value.toString(), fen)

    node.shouldNotBeNull()
    node.position.positionIdentifier shouldBe fen
    node.moves shouldHaveSize 2
  }

  @Test
  fun testGetNode_nonExistentPosition() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val fen = "nonexistent/position/fen"
    val node = getNode(user.id.value.toString(), fen)

    node.shouldBeNull()
  }

  @Test
  fun testGetNode_excludesDeletedMoves() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val fen = "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq"

    // Mark one move as deleted
    transaction {
      val positionEntity = PositionEntity.find { PositionsTable.fenRepresentation eq fen }.first()
      val moveEntities = MoveEntity.find { MovesTable.origin eq positionEntity.id.value }
      if (!moveEntities.empty()) {
        val firstMove = moveEntities.first()
        val userMove =
          UserMoveEntity.find {
              (UserMovesTable.userId eq user.id.value) and
                (UserMovesTable.moveId eq firstMove.id.value)
            }
            .firstOrNull()
        userMove?.isDeleted = true
      }
    }

    val node = getNode(user.id.value.toString(), fen)

    node.shouldNotBeNull()
    node.position.positionIdentifier shouldBe fen
    node.moves shouldHaveSize 1
  }

  @Test
  fun testGetNode_startingPosition() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val node = getNode(user.id.value.toString(), fen)

    node.shouldNotBeNull()
    node.position.positionIdentifier shouldBe fen
    node.moves.shouldNotBeEmpty()
    node.moves shouldHaveSize 3
  }

  @Test
  fun testDeletePosition_existingPosition() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq"

    // Verify position exists before deletion
    var node = getNode(user.id.value.toString(), fen)
    node.shouldNotBeNull()

    deletePosition(user.id.value.toString(), fen)

    // Verify position is marked as deleted
    node = getNode(user.id.value.toString(), fen)
    node.shouldBeNull()
  }

  @Test
  fun testDeletePosition_nonExistentPosition() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    // Should not throw exception
    deletePosition(user.id.value.toString(), "nonexistent/fen")

    // Verify no side effects - all moves still exist
    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize 6
  }

  @Test
  fun testDeleteMove_existingMove() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val originFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val moveName = "e4"

    // Verify move exists before deletion
    var moves = getAllMoves(user.id.value.toString())
    val originalCount = moves.size
    moves.any { it.origin.positionIdentifier == originFen && it.move == moveName } shouldBe true

    deleteMove(user.id.value.toString(), originFen, moveName)

    // Verify move is marked as deleted
    moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize (originalCount - 1)
    moves.none { it.origin.positionIdentifier == originFen && it.move == moveName } shouldBe true
  }

  @Test
  fun testDeleteMove_nonExistentMove() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val originalMoves = getAllMoves(user.id.value.toString())

    // Should not throw exception
    deleteMove(user.id.value.toString(), "nonexistent/fen", "nonexistent_move")

    // Verify no side effects
    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize originalMoves.size
  }

  @Test
  fun testDeleteMove_nonExistentOrigin() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val originalMoves = getAllMoves(user.id.value.toString())

    // Should not throw exception
    deleteMove(user.id.value.toString(), "nonexistent/fen", "e4")

    // Verify no side effects
    val moves = getAllMoves(user.id.value.toString())
    moves shouldHaveSize originalMoves.size
  }

  @Test
  fun testDeleteAllUserData_softDelete() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val originalMoves = getAllMoves(user.id.value.toString())
    originalMoves.shouldNotBeEmpty()

    // Soft delete (hardFrom = null)
    deleteAllUserData(user.id.value.toString(), null)

    // All moves should be marked as deleted
    val moves = getAllMoves(user.id.value.toString())
    moves.shouldBeEmpty()

    // But data still exists in database (soft deleted)
    transaction {
      val allUserMoves = UserMoveEntity.find { UserMovesTable.userId eq user.id.value }.toList()
      allUserMoves.shouldNotBeEmpty()
      allUserMoves.all { it.isDeleted } shouldBe true
    }
  }

  @Test
  fun testDeleteAllUserData_hardDeleteFromTimestamp() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val now = Clock.System.now()

    // Hard delete from now (should delete all data created after now, which is none in our test
    // data)
    deleteAllUserData(user.id.value.toString(), now)

    // All moves should still be marked as soft deleted
    val moves = getAllMoves(user.id.value.toString())
    moves.shouldBeEmpty()

    // Data should still exist in database (soft deleted only)
    transaction {
      val allUserMoves = UserMoveEntity.find { UserMovesTable.userId eq user.id.value }.toList()
      allUserMoves.shouldNotBeEmpty()
    }
  }

  @Test
  fun testGetLastUpdate_withMoves() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val lastUpdate = getLastUpdate(user.id.value.toString())
    lastUpdate.shouldNotBeNull()
  }

  @Test
  fun testGetLastUpdate_noData() = testApplicationWithTestModule {
    // Create a new user with no data
    val newUser = createUser("newuser@example.com", "password")
    newUser.shouldNotBeNull()

    val lastUpdate = getLastUpdate(newUser.id.value.toString())
    lastUpdate.shouldBeNull()
  }

  @Test
  fun testGetLastUpdate_returnsLatestTimestamp() = testApplicationWithTestModule {
    val user = getUser("admin")
    user.shouldNotBeNull()

    val lastUpdate = getLastUpdate(user.id.value.toString())
    lastUpdate.shouldNotBeNull()

    // Add a new move and verify last update changes
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    val position =
      PositionFetched(
        positionIdentifier = "new_position_for_timestamp_test",
        depth = 0,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val move =
      MoveFetched(
        origin = position,
        destination = position,
        move = "test_move",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
      )

    addMoves("admin", listOf(move))

    val newLastUpdate = getLastUpdate(user.id.value.toString())
    newLastUpdate.shouldNotBeNull()
    // The new update should be greater than or equal to the previous one
    (newLastUpdate >= lastUpdate) shouldBe true
  }

  @Test
  fun testMultipleUsers_dataIsolation() = testApplicationWithTestModule {
    // Create a second user
    val user2 = createUser("user2@example.com", "password")
    user2.shouldNotBeNull()

    val user1 = getUser("admin")
    user1.shouldNotBeNull()

    // User 1 should have 6 moves
    val user1Moves = getAllMoves(user1.id.value.toString())
    user1Moves shouldHaveSize 6

    // User 2 should have 0 moves
    val user2Moves = getAllMoves(user2.id.value.toString())
    user2Moves.shouldBeEmpty()

    // Add move to user 2
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.UTC).date

    val position =
      PositionFetched(
        positionIdentifier = "user2_position",
        depth = 0,
        lastTrainingDate = today,
        nextTrainingDate = today,
        updatedAt = now,
        isDeleted = false,
      )

    val move =
      MoveFetched(
        origin = position,
        destination = position,
        move = "user2_move",
        isGood = true,
        isDeleted = false,
        updatedAt = now,
      )

    addMoves("user2@example.com", listOf(move))

    // User 2 should now have 1 move
    val user2MovesAfter = getAllMoves(user2.id.value.toString())
    user2MovesAfter shouldHaveSize 1

    // User 1 should still have 6 moves (data isolation)
    val user1MovesAfter = getAllMoves(user1.id.value.toString())
    user1MovesAfter shouldHaveSize 6
  }
}
