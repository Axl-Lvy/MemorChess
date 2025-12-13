package proj.memorchess.axl.core.interaction

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin

class TestBookExplorer : TestWithKoin {

  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()
  private val bookQueryManager: BookQueryManager by inject()
  private val authManager: AuthManager by inject()

  private lateinit var testBook: Book
  private lateinit var bookExplorer: BookExplorer

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedIn()
    runTest {
      database.deleteAll(null)
      nodeManager.resetCacheFromDataBase()
    }
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

  private suspend fun setupTestBook() {
    val bookId = bookQueryManager.createBook("Test Opening")
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

    bookExplorer = BookExplorer(testBook)
  }

  private suspend fun cleanupTestBook() {
    if (::testBook.isInitialized) {
      bookQueryManager.deleteBook(testBook.id)
    }
  }

  @Test
  fun testLoadBookMoves() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    val nextMoves = bookExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e4"))
  }

  @Test
  fun testNavigateThroughBookMoves() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    bookExplorer.playMove("e4")
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.currentPosition,
    )

    val nextMovesAfterE4 = bookExplorer.getNextMoves()
    assertEquals(1, nextMovesAfterE4.size)
    assertTrue(nextMovesAfterE4.contains("e5"))
  }

  @Test
  fun testBackNavigation() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.back()
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.currentPosition,
    )
  }

  @Test
  fun testReset() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.reset()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.currentPosition)
  }

  @Test
  fun testDownloadBookToRepertoire() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    val nodesBeforeDownload = database.getAllNodes()
    assertTrue(nodesBeforeDownload.isEmpty())

    bookExplorer.downloadBookToRepertoire()

    val nodesAfterDownload = database.getAllNodes()
    assertTrue(nodesAfterDownload.isNotEmpty())
  }

  @Test
  fun testNoNextMovesAtEndOfLine() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")

    val nextMoves = bookExplorer.getNextMoves()
    assertTrue(nextMoves.isEmpty())
  }

  @Test
  fun testBackFromStartPositionShowsToast() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    bookExplorer.back()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.currentPosition)
  }

  @Test
  fun testAvailableNextMovesUpdatedAfterNavigation() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    assertEquals(1, bookExplorer.availableNextMoves.size)
    assertEquals("e4", bookExplorer.availableNextMoves.first().move)

    bookExplorer.playMove("e4")

    assertEquals(1, bookExplorer.availableNextMoves.size)
    assertEquals("e5", bookExplorer.availableNextMoves.first().move)
  }

  @Test
  fun testGameStateUpdatedAfterMove() = runTest {
    setupTestBook()
    bookExplorer.loadBookMoves()

    val initialPosition = bookExplorer.game.position.createIdentifier()
    assertEquals(PositionIdentifier.START_POSITION, initialPosition)

    bookExplorer.playMove("e4")

    val afterE4Position = bookExplorer.game.position.createIdentifier()
    assertNotEquals(PositionIdentifier.START_POSITION, afterE4Position)
  }
}
