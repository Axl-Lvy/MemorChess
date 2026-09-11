package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

/**
 * Tests for the repertoire registry and edge tagging maintained by [RepertoireTagStore]. Cases that
 * need a tagged edge to exist build the graph through a [TreeStore] over the same database.
 */
class TestRepertoireTagStore {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")

  @Test
  fun tagAddsToAnEdgesExistingTagsRatherThanReplacingThem() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = true, fromDepth = 0)

    tagStore.tag(start, posA, "italian-game")
    tagStore.tag(start, posA, "ruy-lopez")

    assertEquals(setOf("italian-game", "ruy-lopez"), tagStore.tagsFor(start, posA))
  }

  @Test
  fun edgesTaggedWithReadsThroughToTheDatabase() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = true, fromDepth = 0)
    tagStore.tag(start, posA, "italian-game")

    val edges = tagStore.edgesTaggedWith("italian-game")

    assertEquals(listOf(TaggedEdge(start, posA, "e4")), edges)
  }

  @Test
  fun taggingAGoodEdgeMakesItsOriginTrainableInThatRepertoire() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = true, fromDepth = 0)

    tagStore.tag(start, posA, "italian-game")

    assertEquals(
      1,
      database
        .getRepertoireMasterySnapshots(listOf("italian-game"))
        .getValue("italian-game")
        .totalCount,
    )
  }

  @Test
  fun taggingABadEdgeDoesNotMakeItsOriginTrainable() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = false, fromDepth = 0)

    tagStore.tag(start, posA, "italian-game")

    assertEquals(
      0,
      database
        .getRepertoireMasterySnapshots(listOf("italian-game"))
        .getValue("italian-game")
        .totalCount,
    )
  }

  @Test
  fun registerThenRepertoiresListsIt() = runTest {
    val tagStore = testRepertoireTagStore(TestDatabases.empty())

    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)

    assertEquals(listOf("italian-game"), tagStore.repertoires().map { it.id })
  }

  @Test
  fun registerRejectsABlankId() = runTest {
    val tagStore = testRepertoireTagStore(TestDatabases.empty())

    assertFailsWith<IllegalArgumentException> { tagStore.register("", "Blank", null) }
  }

  @Test
  fun registerRejectsAnIdContainingAComma() = runTest {
    val tagStore = testRepertoireTagStore(TestDatabases.empty())

    assertFailsWith<IllegalArgumentException> {
      tagStore.register("italian,game", "Italian Game", null)
    }
  }

  @Test
  fun forkRegistersTheNewRepertoire() = runTest {
    val tagStore = testRepertoireTagStore(TestDatabases.empty())
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)

    tagStore.fork("italian-game", "italian-game-copy", "Italian Game copy", RepertoireColor.WHITE)

    assertEquals(
      listOf("italian-game", "italian-game-copy"),
      tagStore.repertoires().map { it.id }.sorted(),
    )
  }

  @Test
  fun forkCopiesTheSourcesTaggedEdgesToTheNewId() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = true, fromDepth = 0)
    tagStore.tag(start, posA, "italian-game")

    tagStore.fork("italian-game", "italian-game-copy", "Italian Game copy", RepertoireColor.WHITE)

    assertEquals(
      listOf(TaggedEdge(start, posA, "e4")),
      tagStore.edgesTaggedWith("italian-game-copy"),
    )
  }

  @Test
  fun forkLeavesTheSourcesOwnTagsUntouched() = runTest {
    val database = TestDatabases.empty()
    val tagStore = testRepertoireTagStore(database)
    testTreeStore(database).addMove(start, "e4", posA, isGood = true, fromDepth = 0)
    tagStore.tag(start, posA, "italian-game")

    tagStore.fork("italian-game", "italian-game-copy", "Italian Game copy", RepertoireColor.WHITE)

    assertEquals(listOf(TaggedEdge(start, posA, "e4")), tagStore.edgesTaggedWith("italian-game"))
  }

  @Test
  fun forkRejectsABlankNewId() = runTest {
    val tagStore = testRepertoireTagStore(TestDatabases.empty())
    tagStore.register("italian-game", "Italian Game", RepertoireColor.WHITE)

    assertFailsWith<IllegalArgumentException> {
      tagStore.fork("italian-game", "", "Copy", RepertoireColor.WHITE)
    }
  }
}
