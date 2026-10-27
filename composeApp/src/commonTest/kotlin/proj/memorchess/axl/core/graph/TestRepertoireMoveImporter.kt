package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireMove
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.pgn.PgnImportException
import proj.memorchess.axl.core.pgn.PgnImportPreview
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

class TestRepertoireMoveImporter {

  private val database = TestDatabases.empty()
  private val store = testTreeStore(database)
  private val tagStore = testRepertoireTagStore(database)
  private val importer = RepertoireMoveImporter(store, tagStore)

  /** Plays [sans] from the start; even plies are marked good, odd plies bad. */
  private fun line(vararg sans: String): List<RepertoireMove> {
    val engine = GameEngine()
    return sans.mapIndexed { ply, san ->
      val from = engine.toPositionKey().value
      val played = engine.playSanMove(san)
      RepertoireMove(from, played, engine.toPositionKey().value, isGood = ply % 2 == 0)
    }
  }

  private suspend fun isGoodOf(move: RepertoireMove): Boolean? =
    database
      .getPosition(PositionKey(move.from))
      ?.previousAndNextMoves
      ?.nextMoves
      ?.get(move.move)
      ?.isGood

  @Test
  fun importWritesEveryMoveWithTheAuthorMarking() = runTest {
    val moves = line("e4", "e5", "Nf3")

    val summary = importer.import(moves, repertoireId = "a")

    assertEquals(PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 0), summary)
    assertEquals(listOf<Boolean?>(true, false, true), moves.map { isGoodOf(it) })
  }

  @Test
  fun importKeepsAnExistingGoodMoveGoodWhenTheAuthorMarksItBad() = runTest {
    val e4 = line("e4").single()
    importer.import(listOf(e4), repertoireId = "a")

    val summary = importer.import(listOf(e4.copy(isGood = false)), repertoireId = "b")

    assertEquals(true, isGoodOf(e4))
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 1), summary)
  }

  @Test
  fun importUpgradesAnExistingBadMoveWhenTheAuthorMarksItGood() = runTest {
    val e4 = line("e4").single()
    importer.import(listOf(e4.copy(isGood = false)), repertoireId = "a")

    val summary = importer.import(listOf(e4), repertoireId = "b")

    assertEquals(true, isGoodOf(e4))
    assertEquals(PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0), summary)
  }

  @Test
  fun importKeepsAnOwnMarking() = runTest {
    val e4 = line("e4").single()
    store.addMove(PositionKey(e4.from), e4.move, PositionKey(e4.to), isGood = false, fromDepth = 0)

    val summary = importer.import(listOf(e4), repertoireId = "a")

    assertEquals(false, isGoodOf(e4))
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 1), summary)
  }

  @Test
  fun importCreatesNoManualHold() = runTest {
    val e4 = line("e4").single()

    importer.import(listOf(e4), repertoireId = "a")

    assertNull(
      database
        .getPosition(PositionKey(e4.from))!!
        .previousAndNextMoves
        .nextMoves
        .getValue("e4")
        .ownIsGood
    )
  }

  @Test
  fun importTagsEveryMoveWithTheRepertoire() = runTest {
    val moves = line("e4", "e5")
    importer.import(moves.take(1), repertoireId = "other")

    importer.import(moves, repertoireId = "italian")

    moves.forEach {
      assertEquals(
        true,
        "italian" in tagStore.tagsFor(PositionKey(it.from), PositionKey(it.to)),
      )
    }
  }

  @Test
  fun importOfACycleTerminates() = runTest {
    val moves = line("Nf3", "Nf6", "Ng1", "Ng8")

    val summary = importer.import(moves, repertoireId = "a")

    assertEquals(4, summary.movesAdded)
  }

  @Test
  fun importRejectsAMoveFromAnUnreachableOriginWithoutWriting() = runTest {
    val detached = line("d4", "d5")[1]

    assertFailsWith<PgnImportException> {
      importer.import(line("e4") + detached, repertoireId = "a")
    }

    assertNull(isGoodOf(line("e4").single()))
  }

  @Test
  fun importRejectsAMalformedPositionKeyWithoutWriting() = runTest {
    val garbage = line("e4").single().copy(from = "not a fen")

    assertFailsWith<PgnImportException> {
      importer.import(line("d4") + garbage, repertoireId = "a")
    }

    assertNull(isGoodOf(line("d4").single()))
  }

  @Test
  fun previewCountsAGoodExistingMoveMarkedBadByTheAuthorAsPresent() = runTest {
    val e4 = line("e4").single()
    importer.import(listOf(e4), repertoireId = "a")

    val preview = importer.preview(listOf(e4.copy(isGood = false)))

    assertEquals(PgnImportPreview(totalMoves = 1, movesInCommon = 1), preview)
  }

  @Test
  fun previewCountsABadExistingMoveMarkedGoodByTheAuthorAsMissing() = runTest {
    val e4 = line("e4").single()
    importer.import(listOf(e4.copy(isGood = false)), repertoireId = "a")

    val preview = importer.preview(listOf(e4))

    assertEquals(PgnImportPreview(totalMoves = 1, movesInCommon = 0), preview)
  }

  @Test
  fun previewDoesNotWrite() = runTest {
    val e4 = line("e4").single()

    importer.preview(listOf(e4))

    assertNull(isGoodOf(e4))
  }

  @Test
  fun importReportsProgressEndingAtOne() = runTest {
    val reported = mutableListOf<Float>()

    importer.import(line("e4", "e5"), repertoireId = "a", onProgress = reported::add)

    assertEquals(1f, reported.last())
  }
}
