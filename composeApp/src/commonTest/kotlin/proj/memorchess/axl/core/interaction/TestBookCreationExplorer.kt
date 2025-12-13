package proj.memorchess.axl.core.interaction

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.BookQueryManager
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookCreationExplorer
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin

class TestBookCreationExplorer : TestWithKoin {

  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()
  private val bookQueryManager: BookQueryManager by inject()
  private val authManager: AuthManager by inject()

  private lateinit var bookCreationExplorer: BookCreationExplorer
  private var createdBookId: Long? = null

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedIn()
    runTest {
      database.deleteAll(null)
      nodeManager.resetCacheFromDataBase()
    }
    bookCreationExplorer = BookCreationExplorer()
  }

  @AfterTest
  override fun tearDown() {
    runTest { cleanupCreatedBook() }
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

  private suspend fun cleanupCreatedBook() {
    createdBookId?.let { bookQueryManager.deleteBook(it) }
    createdBookId = null
  }

  @Test
  fun testCreateBook() = runTest {
    val book = bookCreationExplorer.createBook("My New Book")
    createdBookId = book.id

    assertNotNull(book)
    assertEquals("My New Book", book.name)
    assertEquals(book, bookCreationExplorer.getCurrentBook())
  }

  @Test
  fun testLoadExistingBook() = runTest {
    val bookId = bookQueryManager.createBook("Existing Book")
    createdBookId = bookId
    val books = bookQueryManager.getAllBooks()
    val existingBook = books.find { it.id == bookId }!!

    val explorer = BookCreationExplorer(existingBook)
    explorer.loadBook(existingBook)

    assertEquals(existingBook.id, explorer.getCurrentBook()?.id)
    assertEquals(existingBook.name, explorer.getCurrentBook()?.name)
  }

  @Test
  fun testPlayMoveAndSaveAsGood() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    val moves = bookQueryManager.getBookMoves(book.id)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
    assertTrue(moves.first().isGood)
  }

  @Test
  fun testPlayMoveAndSaveAsBad() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsBad()

    val moves = bookQueryManager.getBookMoves(book.id)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
    assertFalse(moves.first().isGood)
  }

  @Test
  fun testBackNavigation() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    val afterE4 = bookCreationExplorer.currentPosition

    bookCreationExplorer.playMove("e5")

    bookCreationExplorer.back()
    assertEquals(afterE4, bookCreationExplorer.currentPosition)
  }

  @Test
  fun testReset() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.playMove("e5")

    bookCreationExplorer.reset()
    assertEquals(PositionIdentifier.START_POSITION, bookCreationExplorer.currentPosition)
  }

  @Test
  fun testDeleteBook() = runTest {
    val book = bookCreationExplorer.createBook("Book to Delete")

    bookCreationExplorer.deleteCurrentBook()

    assertNull(bookCreationExplorer.getCurrentBook())

    val books = bookQueryManager.getAllBooks()
    assertNull(books.find { it.id == book.id })
  }

  @Test
  fun testGetNextMoves() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    bookCreationExplorer.reset()

    val nextMoves = bookCreationExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e4"))
  }

  @Test
  fun testNavigateThroughSavedMoves() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    bookCreationExplorer.playMove("e5")
    bookCreationExplorer.saveCurrentMoveAsBad()

    bookCreationExplorer.reset()

    bookCreationExplorer.playMove("e4")

    val nextMoves = bookCreationExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e5"))
  }

  @Test
  fun testOverwriteExistingMove() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    bookCreationExplorer.back()
    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsBad()

    val moves = bookQueryManager.getBookMoves(book.id)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
    assertFalse(moves.first().isGood)
  }

  @Test
  fun testSaveWithoutBook() = runTest {
    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    assertNull(bookCreationExplorer.getCurrentBook())
  }

  @Test
  fun testSaveWithoutMove() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.saveCurrentMoveAsGood()

    val moves = bookQueryManager.getBookMoves(book.id)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testAvailableNextMovesUpdated() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    assertTrue(bookCreationExplorer.availableNextMoves.isEmpty())

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()
    bookCreationExplorer.reset()

    assertEquals(1, bookCreationExplorer.availableNextMoves.size)
    assertEquals("e4", bookCreationExplorer.availableNextMoves.first().move)
  }

  @Test
  fun testDeleteCurrentMove() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()

    var moves = bookQueryManager.getBookMoves(book.id)
    assertEquals(1, moves.size)

    bookCreationExplorer.deleteCurrentMove()

    moves = bookQueryManager.getBookMoves(book.id)
    assertTrue(moves.isEmpty())
    assertEquals(PositionIdentifier.START_POSITION, bookCreationExplorer.currentPosition)
  }

  @Test
  fun testDeleteCurrentMoveNavigatesBack() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()
    val afterE4Position = bookCreationExplorer.currentPosition

    bookCreationExplorer.playMove("e5")
    bookCreationExplorer.saveCurrentMoveAsBad()

    bookCreationExplorer.deleteCurrentMove()

    assertEquals(afterE4Position, bookCreationExplorer.currentPosition)

    val moves = bookQueryManager.getBookMoves(book.id)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
  }

  @Test
  fun testDeleteMoveWithoutBook() = runTest {
    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.deleteCurrentMove()

    assertNull(bookCreationExplorer.getCurrentBook())
  }

  @Test
  fun testDeleteMoveWithoutMove() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.deleteCurrentMove()

    assertEquals(PositionIdentifier.START_POSITION, bookCreationExplorer.currentPosition)
  }

  @Test
  fun testDeleteMoveUpdatesAvailableNextMoves() = runTest {
    val book = bookCreationExplorer.createBook("Test Book")
    createdBookId = book.id

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.saveCurrentMoveAsGood()
    bookCreationExplorer.reset()

    assertEquals(1, bookCreationExplorer.availableNextMoves.size)

    bookCreationExplorer.playMove("e4")
    bookCreationExplorer.deleteCurrentMove()

    assertTrue(bookCreationExplorer.availableNextMoves.isEmpty())
  }
}
