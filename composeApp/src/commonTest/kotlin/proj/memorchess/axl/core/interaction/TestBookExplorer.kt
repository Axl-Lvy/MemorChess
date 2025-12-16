package proj.memorchess.axl.core.interaction

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.graph.nodes.IsolatedBookNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin

/** Tests for [BookExplorer] which handles exploring and downloading book moves. */
class TestBookExplorer : TestWithKoin {

  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private val authManager: AuthManager by inject()
  private val database: DatabaseQueryManager by inject()

  private lateinit var testBook: Book
  private lateinit var bookExplorer: BookExplorer
  private var createdBookId: Long? = null

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedIn()
    runTest { database.deleteAll(null) }
  }

  @AfterTest
  override fun tearDown() {
    runTest { cleanupTestBook() }
    ensureSignedOut()
    super.tearDown()
  }

  private fun ensureSignedIn() {
    runTest { authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword) }
    Awaitility.awaitUntilTrue { authManager.user != null }
  }

  private fun ensureSignedOut() {
    runTest {
      if (authManager.user != null) {
        authManager.signOut()
        Awaitility.awaitUntilTrue { authManager.user == null }
      }
    }
  }

  private suspend fun cleanupTestBook() {
    createdBookId?.let { bookQueryManager.deleteBook(it) }
    createdBookId = null
  }

  private suspend fun setupTestBook(canEdit: Boolean = false) {
    val bookId = bookQueryManager.createBook("Test Opening")
    createdBookId = bookId
    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      testBook.id,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(testBook.id, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(testBook.id, BookMove(e5Position, nf3Position, "Nf3", true))

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, canEdit, nodeManager)
  }

  @Test
  fun testGetNextMoves() = runTest {
    setupTestBook()

    val nextMoves = bookExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e4"))
  }

  @Test
  fun testNavigateThroughBookMoves() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )

    val nextMovesAfterE4 = bookExplorer.getNextMoves()
    assertEquals(1, nextMovesAfterE4.size)
    assertTrue(nextMovesAfterE4.contains("e5"))
  }

  @Test
  fun testBackNavigation() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.back()
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )
  }

  @Test
  fun testReset() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.reset()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.game.position.createIdentifier())
  }

  @Test
  fun testDownloadBookToRepertoire() = runTest {
    setupTestBook()

    val nodesBeforeDownload = database.getAllNodes()
    assertTrue(nodesBeforeDownload.isEmpty())

    bookExplorer.downloadBookToRepertoire()

    val nodesAfterDownload = database.getAllNodes()
    assertTrue(nodesAfterDownload.isNotEmpty())
  }

  @Test
  fun testNoNextMovesAtEndOfLine() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")

    val nextMoves = bookExplorer.getNextMoves()
    assertTrue(nextMoves.isEmpty())
  }

  @Test
  fun testBackFromStartPositionStaysAtStart() = runTest {
    setupTestBook()

    bookExplorer.back()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.game.position.createIdentifier())
  }

  @Test
  fun testGameStateUpdatedAfterMove() = runTest {
    setupTestBook()

    val initialPosition = bookExplorer.game.position.createIdentifier()
    assertEquals(PositionIdentifier.START_POSITION, initialPosition)

    bookExplorer.playMove("e4")

    val afterE4Position = bookExplorer.game.position.createIdentifier()
    assertNotEquals(PositionIdentifier.START_POSITION, afterE4Position)
  }

  @Test
  fun testCanEditFalseBlocksInteraction() = runTest {
    setupTestBook(canEdit = false)

    assertFalse(bookExplorer.canEdit)
  }

  @Test
  fun testCanEditTrueAllowsInteraction() = runTest {
    setupTestBook(canEdit = true)

    assertTrue(bookExplorer.canEdit)
  }

  @Test
  fun testForwardNavigation() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.back()
    bookExplorer.forward()

    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )
  }

  @Test
  fun testSaveMove() = runTest {
    setupTestBook(canEdit = true)

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")
    bookExplorer.playMove("Nc6")
    bookExplorer.save()

    val moves = bookQueryManager.getBookMoves(testBook.id)
    assertTrue(moves.any { it.move == "Nc6" })
  }

  @Test
  fun testDeleteMoves() = runTest {
    setupTestBook(canEdit = true)

    val initialMoves = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(3, initialMoves.size)

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.delete()

    val movesAfterDelete = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(2, movesAfterDelete.size)
    assertFalse(movesAfterDelete.any { it.move == "Nf3" })
  }

  @Test
  fun testCalculateNumberOfNodeToDelete() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.back()
    val count = bookExplorer.calculateNumberOfNodeToDelete()

    assertEquals(4, count)
  }
}
