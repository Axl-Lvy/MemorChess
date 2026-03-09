package proj.memorchess.axl.core.data.book

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldMatchEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin

class TestBookQueryManager : TestWithKoin() {

  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private val authManager: AuthManager by inject()

  private val createdBookIds = mutableListOf<Long>()

  override suspend fun setUp() {
    ensureSignedIn()
  }

  override suspend fun tearDown() {
    cleanupBooks()
    ensureSignedOut()
  }

  private suspend fun ensureSignedIn() {
    authManager.signInFromEmail(Secrets.testUserMail, Secrets.testUserPassword)
    eventually(TEST_TIMEOUT) { assertNotNull(authManager.user) }
  }

  private suspend fun ensureSignedOut() {
    if (authManager.user != null) {
      authManager.signOut()
      eventually(TEST_TIMEOUT) { assertNull(authManager.user) }
    }
  }

  private suspend fun cleanupBooks() {
    createdBookIds.forEach { bookQueryManager.deleteBook(it) }
    createdBookIds.clear()
  }

  @Test
  fun testCreateBook() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    assertTrue(bookId > 0)
    val books = bookQueryManager.getAllBooks()
    assertTrue(books.any { it.id == bookId && it.name == "Test Book" })
  }

  @Test
  fun testGetBook() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val book = bookQueryManager.getBook(bookId)

    assertEquals(bookId, book?.id)
    assertEquals("Test Book", book?.name)
  }

  @Test
  fun testGetNonExistentBook() = test {
    val book = bookQueryManager.getBook(-999L)

    assertEquals(null, book)
  }

  @Test
  fun testGetBookMoves() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionKey.START_POSITION,
        PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )
    bookQueryManager.addMoveToBook(bookId, move)

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
    assertTrue(moves.first().isGood)
  }

  @Test
  fun testGetBookMovesForNonExistentBook() = test {
    val moves = bookQueryManager.getBookMoves(-999L)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testHasPermission() = test {
    // Test user has BOOK_CREATION permission
    assertTrue(authManager.hasUserPermission(UserPermission.BOOK_CREATION))
  }

  @Test
  fun testAddMoveToBook() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionKey.START_POSITION,
        PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )

    val result = bookQueryManager.addMoveToBook(bookId, move)

    assertTrue(result)
    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
  }

  @Test
  fun testRegisterBookDownload() = test {
    val bookId = bookQueryManager.createBook("Download Test Book")
    createdBookIds.add(bookId)

    // Initial state
    var book = bookQueryManager.getBook(bookId)
    assertEquals(0, book?.downloads)

    // First download
    val result1 = bookQueryManager.registerBookDownload(bookId)
    assertTrue(result1)

    book = bookQueryManager.getBook(bookId)
    assertEquals(1, book?.downloads)

    // Second download (same user)
    val result2 = bookQueryManager.registerBookDownload(bookId)
    assertFalse(result2)

    book = bookQueryManager.getBook(bookId)
    assertEquals(1, book?.downloads)
  }

  @Test
  fun testDeleteBook() = test {
    val bookId = bookQueryManager.createBook("Book to Delete")

    val result = bookQueryManager.deleteBook(bookId)

    assertTrue(result)
    val books = bookQueryManager.getAllBooks()
    assertFalse(books.any { it.id == bookId })
  }

  @Test
  fun testDeleteNonExistentBook() = test {
    val result = bookQueryManager.deleteBook(-999L)

    assertFalse(result)
  }

  @Test
  fun testDeleteBookAlsoDeletesMoves() = test {
    val bookId = bookQueryManager.createBook("Book with Moves")

    val move =
      BookMove(
        PositionKey.START_POSITION,
        PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )
    bookQueryManager.addMoveToBook(bookId, move)

    bookQueryManager.deleteBook(bookId)

    val moves = bookQueryManager.getBookMoves(bookId)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testOverwriteMoveInBook() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val destination = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")

    val goodMove = BookMove(PositionKey.START_POSITION, destination, "e4", true)
    bookQueryManager.addMoveToBook(bookId, goodMove)

    val badMove = BookMove(PositionKey.START_POSITION, destination, "e4", false)
    bookQueryManager.addMoveToBook(bookId, badMove)

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
    assertFalse(moves.first().isGood)
  }

  @Test
  fun testMultipleBooks() = test {
    val bookId1 = bookQueryManager.createBook("Book 1")
    val bookId2 = bookQueryManager.createBook("Book 2")
    val bookId3 = bookQueryManager.createBook("Book 3")
    createdBookIds.addAll(listOf(bookId1, bookId2, bookId3))

    val books = bookQueryManager.getAllBooks()
    assertTrue(books.any { it.id == bookId1 })
    assertTrue(books.any { it.id == bookId2 })
    assertTrue(books.any { it.id == bookId3 })

    assertNotEquals(bookId1, bookId2)
    assertNotEquals(bookId2, bookId3)
    assertNotEquals(bookId1, bookId3)
  }

  @Test
  fun testAddMultipleMovesToBook() = test {
    val bookId = bookQueryManager.createBook("Multi Move Book")
    createdBookIds.add(bookId)

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(2, moves.size)
  }

  @Test
  fun testRemoveMoveFromBook() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionKey.START_POSITION,
        PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )
    bookQueryManager.addMoveToBook(bookId, move)

    var moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)

    val result = bookQueryManager.removeMoveFromBook(bookId, PositionKey.START_POSITION, "e4")

    assertTrue(result)
    moves = bookQueryManager.getBookMoves(bookId)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testRemoveMoveFromBookWithMultipleMoves() = test {
    val bookId = bookQueryManager.createBook("Multi Move Book")
    createdBookIds.add(bookId)

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))

    bookQueryManager.removeMoveFromBook(bookId, e4Position, "e5")

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
  }

  @Test
  fun testRemoveNonExistentMove() = test {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val result = bookQueryManager.removeMoveFromBook(bookId, PositionKey.START_POSITION, "e4")

    assertFalse(result)
  }

  @Test
  fun testCannotFetchWithNegativeLimit() = test {
    bookQueryManager.getAllBooks(0, 0) shouldHaveSize 0
    assertFails { bookQueryManager.getAllBooks(0, -1) }
  }

  @Test
  fun testFetchBookByName() = test {
    val bookId1 = bookQueryManager.createBook("AAA")
    val bookId2 = bookQueryManager.createBook("BBBSEITSRENITSR")
    createdBookIds.addAll(listOf(bookId1, bookId2))
    val result = bookQueryManager.getAllBooks(0, 50, "BSEITSRENITS")

    result shouldHaveSize 1
    result shouldMatchEach listOf { assertEquals(bookId2, it.id) }
  }
}
