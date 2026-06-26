package proj.memorchess.axl.core.data

import androidx.room.Room
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Room backed coverage of [DatabaseQueryManager.countDescendants]. Mirrors the convergence rule and
 * cap boundary cases of the in-memory reference against a real isolated SQLite database, proving
 * the `childrenOf` / `incomingCount` point queries drive the same bounded breadth first walk.
 */
class TestRoomCountDescendants {

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

  private suspend fun insertNode(name: String, previous: List<DataMove>, next: List<DataMove>) {
    manager.insertNodes(
      DataNode(key(name), PreviousAndNextMoves(previous, next), CardStateFactory.new())
    )
  }

  @Test fun unknownKeyIsZero() = runTest { manager.countDescendants(key("nope")) shouldBe 0 }

  @Test
  fun leafIsOne() = runTest {
    insertNode("root", previous = listOf(), next = listOf())
    manager.countDescendants(key("root")) shouldBe 1
  }

  @Test
  fun linearChainCountsEveryNode() = runTest {
    val a = move("a", "b")
    val b = move("b", "c")
    insertNode("a", previous = listOf(), next = listOf(a))
    insertNode("b", previous = listOf(a), next = listOf(b))
    insertNode("c", previous = listOf(b), next = listOf())
    manager.countDescendants(key("a")) shouldBe 3
    manager.countDescendants(key("b")) shouldBe 2
    manager.countDescendants(key("c")) shouldBe 1
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
    manager.countDescendants(key("a")) shouldBe 2
  }

  @Test
  fun countIsCappedAtTheRequestedBound() = runTest {
    // Linear chain of 10 nodes n0 -> n1 -> ... -> n9.
    val names = (0 until 10).map { "n$it" }
    for (index in names.indices) {
      val incoming = if (index == 0) emptyList() else listOf(move(names[index - 1], names[index]))
      val outgoing =
        if (index == names.lastIndex) emptyList() else listOf(move(names[index], names[index + 1]))
      insertNode(names[index], previous = incoming, next = outgoing)
    }
    val root = key("n0")
    manager.countDescendants(root, cap = 4) shouldBe 4
    manager.countDescendants(root, cap = 5) shouldBe 5
    manager.countDescendants(root, cap = 6) shouldBe 6
    manager.countDescendants(root, cap = 100) shouldBe 10
    manager.countDescendants(root, cap = 0) shouldBe 0
  }
}
