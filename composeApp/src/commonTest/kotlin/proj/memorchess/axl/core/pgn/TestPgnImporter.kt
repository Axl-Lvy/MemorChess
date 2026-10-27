package proj.memorchess.axl.core.pgn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.drainAllNodes
import proj.memorchess.axl.test_util.testRepertoireTagStore
import proj.memorchess.axl.test_util.testTreeStore

class TestPgnImporter {

  private val database = TestDatabases.empty()
  private val store = testTreeStore(database)
  private val tagStore = testRepertoireTagStore(database)
  private val importer = PgnImporter(store, tagStore)

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  /** `isGood` of the persisted [move] leaving [from], or `null` when the move was not stored. */
  private suspend fun isGoodOf(from: PositionKey, move: String): Boolean? =
    database.getPosition(from)?.previousAndNextMoves?.nextMoves?.get(move)?.isGood

  @Test
  fun anInstalledMoveIsNotAlreadyPresentForAPgnImport() = runTest {
    store.install(
      "london",
      listOf(
        proj.memorchess.axl.core.graph.InstalledMove(keyAfter(), "e4", keyAfter("e4"), true, 0)
      ),
    )

    val summary = importer.import(PgnParser.parse("1. e4 *"), perspective = Player.WHITE)

    assertEquals(1, summary.movesAdded)
    assertEquals(
      true,
      database.getPosition(keyAfter())!!.previousAndNextMoves.nextMoves.getValue("e4").ownIsGood,
    )
  }

  @Test
  fun doubleImportIsIdempotent() = runTest {
    val games = PgnParser.parse("1. e4 e5 2. Nf3 Nc6 *")

    val firstSummary = importer.import(games)
    val databaseAfterFirstImport = drainAllNodes(database).associateBy { it.positionKey }
    val secondSummary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), firstSummary)
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 4), secondSummary)
    assertEquals(databaseAfterFirstImport, drainAllNodes(database).associateBy { it.positionKey })
  }

  @Test
  fun overlapPreservesCardStateAndAddsOnlyNewMoves() = runTest {
    val startKey = keyAfter()
    val afterE4Key = keyAfter("e4")
    store.addMove(from = startKey, move = "e4", to = afterE4Key, isGood = true, fromDepth = 0)
    val reviewedState = CardStateFactory.new().copy(stability = 3.5, difficulty = 4.2, reps = 7)
    store.updateCardState(startKey, reviewedState)
    val games = PgnParser.parse("1. e4 e5 *")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 1), summary)
    val startNode = assertNotNull(database.getPosition(startKey))
    assertEquals(reviewedState, startNode.cardState)
    assertEquals(setOf("e4"), startNode.previousAndNextMoves.nextMoves.keys)
    val afterE4Node = assertNotNull(database.getPosition(afterE4Key))
    assertEquals(setOf("e5"), afterE4Node.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun overDisambiguatedMoveIsStoredAsTheMoveTheBoardPlays() = runTest {
    val games = PgnParser.parse("1. Ngf3 *")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 0), summary)
    val startNode = assertNotNull(database.getPosition(PositionKey.START_POSITION))
    assertEquals(setOf("Nf3"), startNode.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun overDisambiguatedMoveMatchesTheSameMoveAlreadyPresent() = runTest {
    importer.import(PgnParser.parse("1. Nf3 *"))

    val summary = importer.import(PgnParser.parse("1. Ngf3 *"))

    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 1), summary)
  }

  @Test
  fun illegalMoveAbortsWithoutWriting() = runTest {
    val games = PgnParser.parse("1. e4 e5 2. Ke3 *")

    assertFailsWith<PgnImportException> { importer.import(games) }

    assertTrue(drainAllNodes(database).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }

  @Test
  fun variationsAreImportedWithCorrectDepths() = runTest {
    val games = PgnParser.parse("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 *")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 6, movesAlreadyPresent = 0), summary)
    val afterE4Node = assertNotNull(database.getPosition(keyAfter("e4")))
    assertEquals(setOf("e5", "c5"), afterE4Node.previousAndNextMoves.nextMoves.keys)
    assertEquals(0, assertNotNull(database.getPosition(keyAfter())).depth)
    assertEquals(1, afterE4Node.depth)
    assertEquals(2, assertNotNull(database.getPosition(keyAfter("e4", "e5"))).depth)
    assertEquals(2, assertNotNull(database.getPosition(keyAfter("e4", "c5"))).depth)
    assertEquals(3, assertNotNull(database.getPosition(keyAfter("e4", "e5", "Nf3"))).depth)
    assertEquals(3, assertNotNull(database.getPosition(keyAfter("e4", "c5", "Nf3"))).depth)
    assertEquals(4, assertNotNull(database.getPosition(keyAfter("e4", "e5", "Nf3", "Nc6"))).depth)
  }

  @Test
  fun multiGamePgnImportsAllGames() = runTest {
    val games = PgnParser.parse("1. e4 e5 *\n\n1. d4 d5 *")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    val startNode = assertNotNull(database.getPosition(keyAfter()))
    assertEquals(setOf("e4", "d4"), startNode.previousAndNextMoves.nextMoves.keys)
    assertNotNull(database.getPosition(keyAfter("e4", "e5")))
    assertNotNull(database.getPosition(keyAfter("d4", "d5")))
  }

  @Test
  fun importReportsPerGameProgressReservingTheLastSliceForTheWrite() = runTest {
    // Three planning steps, plus a fourth reserved for the write.
    val games = PgnParser.parse("1. e4 *\n\n1. d4 *\n\n1. c4 *")
    val reported = mutableListOf<Float>()

    importer.import(games, onProgress = reported::add)

    assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), reported)
  }

  @Test
  fun importOfOneGameReservesHalfForPlanningAndHalfForTheWrite() = runTest {
    val games = PgnParser.parse("1. e4 e5 *")
    val reported = mutableListOf<Float>()

    importer.import(games, onProgress = reported::add)

    assertEquals(listOf(0.5f, 1f), reported)
  }

  @Test
  fun importOfNoGamesReportsOnlyTheFinalWrite() = runTest {
    val reported = mutableListOf<Float>()

    val summary = importer.import(emptyList(), onProgress = reported::add)

    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 0), summary)
    assertEquals(listOf(1f), reported)
  }

  @Test
  fun importWithoutAProgressCallbackStillImports() = runTest {
    val games = PgnParser.parse("1. e4 e5 *")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 2, movesAlreadyPresent = 0), summary)
  }

  @Test
  fun blackPerspectiveMarksOnlyBlackMovesAsGood() = runTest {
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    val summary = importer.import(games, perspective = Player.BLACK)

    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    assertEquals(false, isGoodOf(keyAfter(), "e4"))
    assertEquals(true, isGoodOf(keyAfter("e4"), "d5"))
    assertEquals(false, isGoodOf(keyAfter("e4", "d5"), "exd5"))
    assertEquals(true, isGoodOf(keyAfter("e4", "d5", "exd5"), "Qxd5"))
  }

  @Test
  fun whitePerspectiveMarksOnlyWhiteMovesAsGood() = runTest {
    val games = PgnParser.parse("1. d4 d5 2. Bf4 *")

    val summary = importer.import(games, perspective = Player.WHITE)

    assertEquals(PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 0), summary)
    assertEquals(true, isGoodOf(keyAfter(), "d4"))
    assertEquals(false, isGoodOf(keyAfter("d4"), "d5"))
    assertEquals(true, isGoodOf(keyAfter("d4", "d5"), "Bf4"))
  }

  @Test
  fun perspectiveImportIsIdempotent() = runTest {
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    val firstSummary = importer.import(games, perspective = Player.BLACK)
    val secondSummary = importer.import(games, perspective = Player.BLACK)

    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), firstSummary)
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 4), secondSummary)
  }

  @Test
  fun previewOnEmptyGraphReportsNoOverlap() = runTest {
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    val preview = importer.preview(games, perspective = Player.BLACK)

    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 0), preview)
  }

  @Test
  fun previewReportsPartialOverlap() = runTest {
    importer.import(PgnParser.parse("1. e4 d5 *"), perspective = Player.BLACK)
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    val preview = importer.preview(games, perspective = Player.BLACK)

    // e4 and d5 are already present, exd5 and Qxd5 are new.
    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 2), preview)
  }

  @Test
  fun previewReportsFullOverlapAfterInstall() = runTest {
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")
    importer.import(games, perspective = Player.BLACK)

    val preview = importer.preview(games, perspective = Player.BLACK)

    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 4), preview)
  }

  @Test
  fun previewClassifiesByPerspectiveLikeImport() = runTest {
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")
    importer.import(games, perspective = Player.BLACK)

    // e4 and exd5 were stored as not good, so none match.
    val preview = importer.preview(games, perspective = Player.WHITE)

    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 0), preview)
  }

  @Test
  fun previewDoesNotWriteAnything() = runTest {
    val games = PgnParser.parse("1. e4 d5 *")

    importer.preview(games, perspective = Player.BLACK)

    assertTrue(drainAllNodes(database).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }

  @Test
  fun emptyGameImportsNothing() = runTest {
    val games = PgnParser.parse("[Event \"Repertoire\"]\n\n*")

    val summary = importer.import(games)

    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 0), summary)
    assertTrue(drainAllNodes(database).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }

  @Test
  fun importTagsEveryPlannedEdgeIncludingOnesAlreadyPresentFromAnotherRepertoire() = runTest {
    val games = PgnParser.parse("1. e4 e5 *")
    val startKey = keyAfter()
    val afterE4Key = keyAfter("e4")

    importer.import(games, perspective = null, repertoireId = "ruy-lopez")
    importer.import(games, perspective = null, repertoireId = "italian-game")

    assertEquals(setOf("ruy-lopez", "italian-game"), tagStore.tagsFor(startKey, afterE4Key))
  }

  @Test
  fun importRejectsExtendingARepertoireWhoseRegisteredColorConflicts() = runTest {
    tagStore.register("scandinavian-black", "Scandinavian", RepertoireColor.BLACK)
    val games = PgnParser.parse("1. e4 d5 *")

    assertFailsWith<PgnImportException> {
      importer.import(games, perspective = Player.WHITE, repertoireId = "scandinavian-black")
    }
  }
}
