package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Room backed coverage of the `NodeRepertoireTrainable` projection's join/filter logic, mirroring
 * the in-memory reference against a real isolated SQLite database.
 */
class TestRoomRepertoireMastery {

  private val database: CustomDatabase = freshDatabase()
  private val manager: DatabaseQueryManager = NonJsLocalDatabaseQueryManager(database)

  private fun freshDatabase(): CustomDatabase {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "room_mastery_${UUID.randomUUID()}.db")
    return getRoomDatabase(Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath))
  }

  @AfterTest
  fun tearDown() {
    database.close()
  }

  @Test
  fun replaceTrainableRepertoiresOverwritesThePreviousMembershipSet() = runTest {
    val position = PositionKey("k1")
    // TreeStore never writes a trainable row for a position it cannot resolve, so the join against
    // NodeEntity assumes the position exists.
    manager.insertNodes(DataNode(position, PreviousAndNextMoves(), CardStateFactory.new()))

    manager.replaceTrainableRepertoires(position, setOf("italian-game", "ruy-lopez"), lastReview = null)
    manager.replaceTrainableRepertoires(position, setOf("ruy-lopez"), lastReview = null)
    val snapshots = manager.getRepertoireMasterySnapshots(listOf("italian-game", "ruy-lopez"))

    snapshots.getValue("italian-game") shouldBe RepertoireMasterySnapshot(0, 0, null)
    snapshots.getValue("ruy-lopez") shouldBe RepertoireMasterySnapshot(0, 1, null)
  }

  @Test
  fun getRepertoireMasterySnapshotsCountsSolidPositionsAndTracksTheLatestReview() = runTest {
    val solidPosition = PositionKey("k1")
    val newPosition = PositionKey("k2")
    val reviewedAt = Instant.fromEpochSeconds(2_000)
    manager.insertNodes(
      DataNode(
        solidPosition,
        PreviousAndNextMoves(),
        CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = reviewedAt),
      ),
      DataNode(newPosition, PreviousAndNextMoves(), CardStateFactory.new()),
    )
    manager.replaceTrainableRepertoires(solidPosition, setOf("italian-game"), reviewedAt)
    manager.replaceTrainableRepertoires(newPosition, setOf("italian-game"), lastReview = null)

    val snapshot = manager.getRepertoireMasterySnapshots(listOf("italian-game")).getValue("italian-game")

    snapshot shouldBe RepertoireMasterySnapshot(solidCount = 1, totalCount = 2, lastReview = reviewedAt)
  }

  @Test
  fun getRepertoireMasterySnapshotsReturnsAZeroSnapshotForARepertoireWithNoTrainablePosition() =
    runTest {
      manager.getRepertoireMasterySnapshots(listOf("empty-repertoire")).getValue("empty-repertoire") shouldBe
        RepertoireMasterySnapshot(0, 0, null)
    }

  @Test
  fun getRepertoireMasterySnapshotsExcludesATrainableRowWhoseOwnPositionWasDeleted() = runTest {
    val position = PositionKey("k1")
    val reviewedAt = Instant.fromEpochSeconds(2_000)
    manager.insertNodes(
      DataNode(
        position,
        PreviousAndNextMoves(),
        CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = reviewedAt),
      )
    )
    manager.replaceTrainableRepertoires(position, setOf("italian-game"), reviewedAt)

    manager.deletePosition(position, DeleteMode.SOFT, "device-a", 1L, Instant.fromEpochSeconds(3_000))

    manager.getRepertoireMasterySnapshots(listOf("italian-game")).getValue("italian-game") shouldBe
      RepertoireMasterySnapshot(0, 0, null)
  }
}
