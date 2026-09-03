package proj.memorchess.axl.server.db

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test

internal class TestIdResolver {

  private fun key(suffix: String) = "fen-${System.nanoTime()}-$suffix"

  @Test
  fun anEmptyBatchTouchesNothing() {
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.resolvePositionIds(emptyList()).shouldBeEmpty()
      connection.resolveEdgeIds(emptyList()).shouldBeEmpty()
    }
  }

  @Test
  fun aSinglePositionGetsAnId() {
    val k = key("single")
    PostgresTestDb.dataSource().connection.use { connection ->
      val ids = connection.resolvePositionIds(listOf(k))
      ids shouldHaveSize 1
      (ids.getValue(k) > 0) shouldBe true
    }
  }

  @Test
  fun resolvingTheSameKeyTwiceReturnsTheSameId() {
    val k = key("stable")
    PostgresTestDb.dataSource().connection.use { connection ->
      val first = connection.resolvePositionIds(listOf(k)).getValue(k)
      val second = connection.resolvePositionIds(listOf(k)).getValue(k)
      second shouldBe first
    }
  }

  @Test
  fun aDuplicateWithinOneBatchCollapses() {
    val k = key("dupe")
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.resolvePositionIds(listOf(k, k, k)) shouldHaveSize 1
    }
  }

  @Test
  fun aMixOfNewAndExistingKeysAllResolve() {
    val existing = key("existing")
    val fresh = key("fresh")
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.resolvePositionIds(listOf(existing))
      connection.resolvePositionIds(listOf(existing, fresh)) shouldHaveSize 2
    }
  }

  @Test
  fun anEdgeResolvesAndIsStableAcrossCalls() {
    val identity = EdgeIdentity(key("o"), key("d"), "e4")
    PostgresTestDb.dataSource().connection.use { connection ->
      val first = connection.resolveEdgeIds(listOf(identity)).getValue(identity)
      val second = connection.resolveEdgeIds(listOf(identity)).getValue(identity)
      second shouldBe first
    }
  }

  @Test
  fun twoEdgesSharingAnOriginBothResolve() {
    val origin = key("shared-origin")
    val first = EdgeIdentity(origin, key("d1"), "e4")
    val second = EdgeIdentity(origin, key("d2"), "d4")
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.resolveEdgeIds(listOf(first, second)) shouldHaveSize 2
    }
  }

  @Test
  fun concurrentOverlappingBatchesDoNotDeadlock() {
    // The reason every batch is sorted by key. Two transactions inserting overlapping sets in
    // opposite orders deadlock; sorting gives them one lock order. Without the sort this fails
    // with "deadlock detected" rather than hanging, so it is a real assertion.
    val shared = (1..40).map { key("shared-$it") }
    val pool = Executors.newFixedThreadPool(4)
    try {
      val tasks =
        (1..4).map { worker ->
          Callable {
            val order = if (worker % 2 == 0) shared.reversed() else shared
            PostgresTestDb.dataSource().connection.use { connection ->
              connection.autoCommit = false
              connection.resolvePositionIds(order)
              connection.commit()
            }
            true
          }
        }
      pool.invokeAll(tasks).forEach { it.get() shouldBe true }
    } finally {
      pool.shutdownNow()
    }
  }
}
