package proj.memorchess.axl.core.pgn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore

class TestPgnImporter {

  private val database = TestDatabases.empty()
  private val store = testTreeStore(database)
  private val importer = PgnImporter(store)

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  /** `isGood` of the persisted [move] leaving [from], or `null` when the move was not stored. */
  private suspend fun isGoodOf(from: PositionKey, move: String): Boolean? =
    database.getPosition(from)?.previousAndNextMoves?.nextMoves?.get(move)?.isGood

  @Test
  fun doubleImportIsIdempotent() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 2. Nf3 Nc6 *")

    // Act
    val firstSummary = importer.import(games)
    val databaseAfterFirstImport =
      database.getAllNodes(withDeletedOnes = true).associateBy { it.positionKey }
    val secondSummary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), firstSummary)
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 4), secondSummary)
    assertEquals(
      databaseAfterFirstImport,
      database.getAllNodes(withDeletedOnes = true).associateBy { it.positionKey },
    )
  }

  @Test
  fun overlapPreservesCardStateAndAddsOnlyNewMoves() = runTest {
    // Arrange
    val startKey = keyAfter()
    val afterE4Key = keyAfter("e4")
    store.addMove(from = startKey, move = "e4", to = afterE4Key, isGood = true, fromDepth = 0)
    val reviewedState = CardStateFactory.new().copy(stability = 3.5, difficulty = 4.2, reps = 7)
    store.updateCardState(startKey, reviewedState)
    val games = PgnParser.parse("1. e4 e5 *")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 1), summary)
    val startNode = assertNotNull(database.getPosition(startKey))
    assertEquals(reviewedState, startNode.cardState)
    assertEquals(setOf("e4"), startNode.previousAndNextMoves.nextMoves.keys)
    val afterE4Node = assertNotNull(database.getPosition(afterE4Key))
    assertEquals(setOf("e5"), afterE4Node.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun illegalMoveAbortsWithoutWriting() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 2. Ke3 *")

    // Act
    assertFailsWith<PgnImportException> { importer.import(games) }

    // Assert
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }

  @Test
  fun variationsAreImportedWithCorrectDepths() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 *")

    // Act
    val summary = importer.import(games)

    // Assert
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
    // Arrange
    val games = PgnParser.parse("1. e4 e5 *\n\n1. d4 d5 *")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    val startNode = assertNotNull(database.getPosition(keyAfter()))
    assertEquals(setOf("e4", "d4"), startNode.previousAndNextMoves.nextMoves.keys)
    assertNotNull(database.getPosition(keyAfter("e4", "e5")))
    assertNotNull(database.getPosition(keyAfter("d4", "d5")))
  }

  @Test
  fun blackPerspectiveMarksOnlyBlackMovesAsGood() = runTest {
    // Arrange: the Scandinavian, a black repertoire.
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    // Act
    val summary = importer.import(games, perspective = Player.BLACK)

    // Assert: white's moves are stored but not trainable, black's are the repertoire.
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    assertEquals(false, isGoodOf(keyAfter(), "e4"))
    assertEquals(true, isGoodOf(keyAfter("e4"), "d5"))
    assertEquals(false, isGoodOf(keyAfter("e4", "d5"), "exd5"))
    assertEquals(true, isGoodOf(keyAfter("e4", "d5", "exd5"), "Qxd5"))
  }

  @Test
  fun whitePerspectiveMarksOnlyWhiteMovesAsGood() = runTest {
    // Arrange: the London System, a white repertoire.
    val games = PgnParser.parse("1. d4 d5 2. Bf4 *")

    // Act
    val summary = importer.import(games, perspective = Player.WHITE)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 3, movesAlreadyPresent = 0), summary)
    assertEquals(true, isGoodOf(keyAfter(), "d4"))
    assertEquals(false, isGoodOf(keyAfter("d4"), "d5"))
    assertEquals(true, isGoodOf(keyAfter("d4", "d5"), "Bf4"))
  }

  @Test
  fun perspectiveImportIsIdempotent() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    // Act
    val firstSummary = importer.import(games, perspective = Player.BLACK)
    val secondSummary = importer.import(games, perspective = Player.BLACK)

    // Assert: re-importing the same one sided repertoire adds nothing, including opponent moves.
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), firstSummary)
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 4), secondSummary)
  }

  @Test
  fun previewOnEmptyGraphReportsNoOverlap() = runTest {
    // Arrange: nothing imported yet, so none of the repertoire's moves are in common.
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    // Act
    val preview = importer.preview(games, perspective = Player.BLACK)

    // Assert
    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 0), preview)
  }

  @Test
  fun previewReportsPartialOverlap() = runTest {
    // Arrange: import the trunk, then preview a repertoire that extends it.
    importer.import(PgnParser.parse("1. e4 d5 *"), perspective = Player.BLACK)
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")

    // Act
    val preview = importer.preview(games, perspective = Player.BLACK)

    // Assert: e4 and d5 already present, exd5 and Qxd5 are new.
    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 2), preview)
  }

  @Test
  fun previewReportsFullOverlapAfterInstall() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")
    importer.import(games, perspective = Player.BLACK)

    // Act
    val preview = importer.preview(games, perspective = Player.BLACK)

    // Assert: a fully installed repertoire has every move in common and writes nothing.
    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 4), preview)
  }

  @Test
  fun previewClassifiesByPerspectiveLikeImport() = runTest {
    // Arrange: install for black, then preview the same PGN as a white repertoire.
    val games = PgnParser.parse("1. e4 d5 2. exd5 Qxd5 *")
    importer.import(games, perspective = Player.BLACK)

    // Act: white's good moves (e4, exd5) are stored as not good, so none match.
    val preview = importer.preview(games, perspective = Player.WHITE)

    // Assert
    assertEquals(PgnImportPreview(totalMoves = 4, movesInCommon = 0), preview)
  }

  @Test
  fun previewDoesNotWriteAnything() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 d5 *")

    // Act
    importer.preview(games, perspective = Player.BLACK)

    // Assert
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }

  @Test
  fun emptyGameImportsNothing() = runTest {
    // Arrange
    val games = PgnParser.parse("[Event \"Repertoire\"]\n\n*")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 0), summary)
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }
}
