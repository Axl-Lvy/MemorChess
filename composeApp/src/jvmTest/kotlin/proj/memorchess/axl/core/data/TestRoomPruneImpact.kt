package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Room backed coverage of [DatabaseQueryManager.pruneImpact]. Mirrors the convergence rule, cap
 * boundary, and review history cases of the in-memory reference against a real isolated SQLite
 * database, proving the point queries drive the same bounded breadth first walk.
 */
class TestRoomPruneImpact {

  private val database: CustomDatabase = freshDatabase()
  private val manager: DatabaseQueryManager = NonJsLocalDatabaseQueryManager(database)

  private fun freshDatabase(): CustomDatabase {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "room_count_${UUID.randomUUID()}.db")
    return getRoomDatabase(Room.databaseBuilder<CustomDatabase>(name = dbFile.absolutePath))
  }

  @AfterTest
  fun tearDown() {
    database.close()
  }

  private fun key(name: String) = PositionKey("$name x K")

  private fun move(from: String, to: String) =
    DataMove(key(from), key(to), "$from-$to", isGood = true)

  private suspend fun insertNode(
    name: String,
    previous: List<DataMove>,
    next: List<DataMove>,
    reviewed: Boolean = false,
  ) {
    val card = CardStateFactory.new()
    val reviewedCard = if (reviewed) card.copy(lastReview = Instant.fromEpochSeconds(1_000)) else card
    manager.insertNodes(DataNode(key(name), PreviousAndNextMoves(previous, next), reviewedCard))
  }

  /** Inserts a linear chain over [names], reviewing the positions named in [reviewed]. */
  private suspend fun insertChain(names: List<String>, reviewed: Set<String> = emptySet()) {
    for (index in names.indices) {
      val incoming = if (index == 0) emptyList() else listOf(move(names[index - 1], names[index]))
      val outgoing =
        if (index == names.lastIndex) emptyList() else listOf(move(names[index], names[index + 1]))
      insertNode(names[index], incoming, outgoing, reviewed = names[index] in reviewed)
    }
  }

  @Test fun unknownKeyIsZero() = runTest { manager.pruneImpact(key("nope")).positions shouldBe 0 }

  @Test
  fun leafIsOne() = runTest {
    insertNode("root", previous = listOf(), next = listOf())
    manager.pruneImpact(key("root")).positions shouldBe 1
  }

  @Test
  fun linearChainCountsEveryNode() = runTest {
    val a = move("a", "b")
    val b = move("b", "c")
    insertNode("a", previous = listOf(), next = listOf(a))
    insertNode("b", previous = listOf(a), next = listOf(b))
    insertNode("c", previous = listOf(b), next = listOf())
    manager.pruneImpact(key("a")).positions shouldBe 3
    manager.pruneImpact(key("b")).positions shouldBe 2
    manager.pruneImpact(key("c")).positions shouldBe 1
  }

  @Test
  fun convergentNodeIsNotDoubleCounted() = runTest {
    // a -> b -> d, plus a second parent c -> d. Deleting a's subtree keeps d (reachable through c).
    val ab = move("a", "b")
    val bd = move("b", "d")
    val cd = move("c", "d")
    insertNode("a", previous = listOf(), next = listOf(ab))
    insertNode("b", previous = listOf(ab), next = listOf(bd))
    insertNode("c", previous = listOf(), next = listOf(cd))
    insertNode("d", previous = listOf(bd, cd), next = listOf())
    manager.pruneImpact(key("a")).positions shouldBe 2
  }

  @Test
  fun countIsCappedAtTheRequestedBound() = runTest {
    // Linear chain of 10 nodes n0 -> n1 -> ... -> n9.
    insertChain((0 until 10).map { "n$it" })
    val root = key("n0")
    manager.pruneImpact(root, cap = 4).positions shouldBe 4
    manager.pruneImpact(root, cap = 5).positions shouldBe 5
    manager.pruneImpact(root, cap = 6).positions shouldBe 6
    manager.pruneImpact(root, cap = 100).positions shouldBe 10
    manager.pruneImpact(root, cap = 0).positions shouldBe 0
  }

  @Test
  fun unknownKeyIsNone() = runTest { manager.pruneImpact(key("nope")) shouldBe PruneImpact.NONE }

  @Test
  fun noReviewedPositionHasZeroReviewed() = runTest {
    insertChain(listOf("a", "b", "c"))
    manager.pruneImpact(key("a")) shouldBe PruneImpact(positions = 3, reviewedPositions = 0)
  }

  @Test
  fun reviewedCountIncludesTheRootAndSkipsPositionsAboveIt() = runTest {
    insertChain(listOf("a", "b", "c"), reviewed = setOf("a", "b"))
    manager.pruneImpact(key("b")) shouldBe PruneImpact(positions = 2, reviewedPositions = 1)
  }

  @Test
  fun everyReviewedPositionIsCounted() = runTest {
    insertChain(listOf("a", "b", "c"), reviewed = setOf("a", "b", "c"))
    manager.pruneImpact(key("a")) shouldBe PruneImpact(positions = 3, reviewedPositions = 3)
  }

  @Test
  fun reviewedConvergentPositionIsNotCounted() = runTest {
    val ab = move("a", "b")
    val cb = move("c", "b")
    insertNode("a", previous = listOf(), next = listOf(ab))
    insertNode("c", previous = listOf(), next = listOf(cb))
    insertNode("b", previous = listOf(ab, cb), next = listOf(), reviewed = true)
    manager.pruneImpact(key("a")) shouldBe PruneImpact(positions = 1, reviewedPositions = 0)
  }

  @Test
  fun reviewedCountCoversOnlyPositionsWithinTheCap() = runTest {
    insertChain(listOf("a", "b", "c"), reviewed = setOf("c"))
    manager.pruneImpact(key("a"), cap = 2) shouldBe PruneImpact(positions = 2, reviewedPositions = 0)
    manager.pruneImpact(key("a"), cap = 3) shouldBe PruneImpact(positions = 3, reviewedPositions = 1)
  }

  @Test
  fun reviewedCountSpansMoreKeysThanOneSqlChunk() = runTest {
    // 600 positions exceed the 500 key chunk, so the reviewed tally sums two IN queries.
    val names = (0 until 600).map { "n$it" }
    insertChain(names, reviewed = names.toSet())
    manager.pruneImpact(key("n0"), cap = 600) shouldBe
      PruneImpact(positions = 600, reviewedPositions = 600)
  }
}
