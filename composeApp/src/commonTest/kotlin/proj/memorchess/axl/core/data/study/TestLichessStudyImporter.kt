package proj.memorchess.axl.core.data.study

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.pgn.PgnImportSummary
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore

class TestLichessStudyImporter {

  private val database = TestDatabases.empty()
  private val store = testTreeStore(database)

  private fun importerRespondingPgn(pgn: String): LichessStudyImporter {
    val engine = MockEngine { respond(content = ByteReadChannel(pgn), status = HttpStatusCode.OK) }
    return LichessStudyImporter(LichessStudyClient(HttpClient(engine)), store)
  }

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  @Test
  fun singleChapterStudyIsImported() = runTest {
    // Arrange
    val importer = importerRespondingPgn("1. e4 e5 2. Nf3 Nc6 *")

    // Act
    val result = importer.import("https://lichess.org/study/abcd1234")

    // Assert
    val summary = assertIs<LichessStudyImportResult.Success>(result).summary
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    assertNotNull(database.getPosition(keyAfter("e4", "e5", "Nf3", "Nc6")))
  }

  @Test
  fun multiChapterStudyAggregatesAllChapters() = runTest {
    // Arrange
    val importer = importerRespondingPgn("1. e4 e5 *\n\n1. d4 d5 *")

    // Act
    val result = importer.import("abcd1234")

    // Assert
    val summary = assertIs<LichessStudyImportResult.Success>(result).summary
    assertEquals(PgnImportSummary(movesAdded = 4, movesAlreadyPresent = 0), summary)
    val startNode = assertNotNull(database.getPosition(keyAfter()))
    assertEquals(setOf("e4", "d4"), startNode.previousAndNextMoves.nextMoves.keys)
    assertNotNull(database.getPosition(keyAfter("e4", "e5")))
    assertNotNull(database.getPosition(keyAfter("d4", "d5")))
  }

  @Test
  fun importMergesWithThePersistedGraph() = runTest {
    // Arrange
    val seededNodes = TestDatabases.convertStringMovesToNodes(listOf("e4"))
    database.insertNodes(*seededNodes.toTypedArray())
    val importer = importerRespondingPgn("1. e4 e5 *")

    // Act
    val result = importer.import("abcd1234")

    // Assert
    val summary = assertIs<LichessStudyImportResult.Success>(result).summary
    assertEquals(PgnImportSummary(movesAdded = 1, movesAlreadyPresent = 1), summary)
  }

  @Test
  fun invalidInputIsReportedAsFetchFailed() = runTest {
    // Arrange
    val importer = importerRespondingPgn("1. e4 *")

    // Act
    val result = importer.import("not a study")

    // Assert
    val error = assertIs<LichessStudyImportResult.FetchFailed>(result).error
    assertEquals(LichessStudyResult.InvalidUrl, error)
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
  }

  @Test
  fun missingStudyIsReportedAsFetchFailed() = runTest {
    // Arrange
    val engine = MockEngine { respond(content = "", status = HttpStatusCode.NotFound) }
    val importer = LichessStudyImporter(LichessStudyClient(HttpClient(engine)), store)

    // Act
    val result = importer.import("abcd1234")

    // Assert
    val error = assertIs<LichessStudyImportResult.FetchFailed>(result).error
    assertEquals(LichessStudyResult.NotFound, error)
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
  }

  @Test
  fun illegalMoveInTheStudyLeavesTheGraphUntouched() = runTest {
    // Arrange
    val importer = importerRespondingPgn("1. e4 e5 2. Ke3 *")

    // Act
    val result = importer.import("abcd1234")

    // Assert
    assertIs<LichessStudyImportResult.ImportFailed>(result)
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
    assertNull(store.node(PositionKey.START_POSITION))
  }
}
