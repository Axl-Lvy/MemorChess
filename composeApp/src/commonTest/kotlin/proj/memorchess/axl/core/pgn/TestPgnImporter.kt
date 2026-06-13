package proj.memorchess.axl.core.pgn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestPgnImporter {

  private val database = TestDatabaseQueryManager.empty()
  private val store = TreeStore(database)
  private val importer = PgnImporter(store)

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  @Test
  fun doubleImportIsIdempotent() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 2. Nf3 Nc6 *")

    // Act
    val firstSummary = importer.import(games)
    val databaseAfterFirstImport = database.dataNodes.toMap()
    val secondSummary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), firstSummary)
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 4), secondSummary)
    assertEquals(databaseAfterFirstImport, database.dataNodes.toMap())
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
    val startNode = assertNotNull(database.dataNodes[startKey])
    assertEquals(reviewedState, startNode.cardState)
    assertEquals(setOf("e4"), startNode.previousAndNextMoves.nextMoves.keys)
    val afterE4Node = assertNotNull(database.dataNodes[afterE4Key])
    assertEquals(setOf("e5"), afterE4Node.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun illegalMoveAbortsWithoutWriting() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 2. Ke3 *")

    // Act
    assertFailsWith<PgnImportException> { importer.import(games) }

    // Assert
    assertTrue(database.dataNodes.isEmpty())
    assertTrue(store.current().snapshot().isEmpty())
  }

  @Test
  fun variationsAreImportedWithCorrectDepths() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 *")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 6, movesAlreadyPresent = 0), summary)
    val afterE4Node = assertNotNull(database.dataNodes[keyAfter("e4")])
    assertEquals(setOf("e5", "c5"), afterE4Node.previousAndNextMoves.nextMoves.keys)
    assertEquals(0, assertNotNull(database.dataNodes[keyAfter()]).depth)
    assertEquals(1, afterE4Node.depth)
    assertEquals(2, assertNotNull(database.dataNodes[keyAfter("e4", "e5")]).depth)
    assertEquals(2, assertNotNull(database.dataNodes[keyAfter("e4", "c5")]).depth)
    assertEquals(3, assertNotNull(database.dataNodes[keyAfter("e4", "e5", "Nf3")]).depth)
    assertEquals(3, assertNotNull(database.dataNodes[keyAfter("e4", "c5", "Nf3")]).depth)
    assertEquals(4, assertNotNull(database.dataNodes[keyAfter("e4", "e5", "Nf3", "Nc6")]).depth)
  }

  @Test
  fun multiGamePgnImportsAllGames() = runTest {
    // Arrange
    val games = PgnParser.parse("1. e4 e5 *\n\n1. d4 d5 *")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    val startNode = assertNotNull(database.dataNodes[keyAfter()])
    assertEquals(setOf("e4", "d4"), startNode.previousAndNextMoves.nextMoves.keys)
    assertTrue(database.dataNodes.containsKey(keyAfter("e4", "e5")))
    assertTrue(database.dataNodes.containsKey(keyAfter("d4", "d5")))
  }

  @Test
  fun emptyGameImportsNothing() = runTest {
    // Arrange
    val games = PgnParser.parse("[Event \"Repertoire\"]\n\n*")

    // Act
    val summary = importer.import(games)

    // Assert
    assertEquals(PgnImportSummary(movesAdded = 0, movesAlreadyPresent = 0), summary)
    assertTrue(database.dataNodes.isEmpty())
    assertTrue(store.current().snapshot().isEmpty())
  }
}
