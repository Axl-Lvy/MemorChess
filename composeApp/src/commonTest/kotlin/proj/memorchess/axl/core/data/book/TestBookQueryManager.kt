package proj.memorchess.axl.core.data.book

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin

class TestBookQueryManager : TestWithKoin {

  private val bookQueryManager: BookQueryManager by inject()
  private val authManager: AuthManager by inject()

  private val createdBookIds = mutableListOf<Long>()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedIn()
  }

  @AfterTest
  override fun tearDown() {
    //    runTest { cleanupBooks() }
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

  private suspend fun cleanupBooks() {
    createdBookIds.forEach { bookQueryManager.deleteBook(it) }
    createdBookIds.clear()
  }

  @Test
  fun testCreateBook() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    assertTrue(bookId > 0)
    val books = bookQueryManager.getAllBooks()
    assertTrue(books.any { it.id == bookId && it.name == "Test Book" })
  }

  @Test
  fun testGetBookMoves() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionIdentifier.START_POSITION,
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
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
  fun testGetBookMovesForNonExistentBook() = runTest {
    val moves = bookQueryManager.getBookMoves(-999L)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testHasPermission() = runTest {
    // Test user has BOOK_CREATION permission
    assertTrue(bookQueryManager.hasPermission(UserPermission.BOOK_CREATION))
  }

  @Test
  fun testAddMoveToBook() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionIdentifier.START_POSITION,
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )

    val result = bookQueryManager.addMoveToBook(bookId, move)

    assertTrue(result)
    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
  }

  @Test
  fun testDeleteBook() = runTest {
    val bookId = bookQueryManager.createBook("Book to Delete")

    val result = bookQueryManager.deleteBook(bookId)

    assertTrue(result)
    val books = bookQueryManager.getAllBooks()
    assertFalse(books.any { it.id == bookId })
  }

  @Test
  fun testDeleteBookAlsoDeletesMoves() = runTest {
    val bookId = bookQueryManager.createBook("Book with Moves")

    val move =
      BookMove(
        PositionIdentifier.START_POSITION,
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )
    bookQueryManager.addMoveToBook(bookId, move)

    bookQueryManager.deleteBook(bookId)

    val moves = bookQueryManager.getBookMoves(bookId)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testOverwriteMoveInBook() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val destination = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")

    val goodMove = BookMove(PositionIdentifier.START_POSITION, destination, "e4", true)
    bookQueryManager.addMoveToBook(bookId, goodMove)

    val badMove = BookMove(PositionIdentifier.START_POSITION, destination, "e4", false)
    bookQueryManager.addMoveToBook(bookId, badMove)

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
    assertFalse(moves.first().isGood)
  }

  @Test
  fun testMultipleBooks() = runTest {
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
  fun testAddMultipleMovesToBook() = runTest {
    val bookId = bookQueryManager.createBook("Multi Move Book")
    createdBookIds.add(bookId)

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(2, moves.size)
  }

  @Test
  fun testRemoveMoveFromBook() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val move =
      BookMove(
        PositionIdentifier.START_POSITION,
        PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
        "e4",
        true,
      )
    bookQueryManager.addMoveToBook(bookId, move)

    var moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)

    val result =
      bookQueryManager.removeMoveFromBook(
        bookId,
        PositionIdentifier.START_POSITION.fenRepresentation,
        "e4",
      )

    assertTrue(result)
    moves = bookQueryManager.getBookMoves(bookId)
    assertTrue(moves.isEmpty())
  }

  @Test
  fun testRemoveMoveFromBookWithMultipleMoves() = runTest {
    val bookId = bookQueryManager.createBook("Multi Move Book")
    createdBookIds.add(bookId)

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))

    bookQueryManager.removeMoveFromBook(bookId, e4Position.fenRepresentation, "e5")

    val moves = bookQueryManager.getBookMoves(bookId)
    assertEquals(1, moves.size)
    assertEquals("e4", moves.first().move)
  }

  @Test
  fun testRemoveNonExistentMove() = runTest {
    val bookId = bookQueryManager.createBook("Test Book")
    createdBookIds.add(bookId)

    val result =
      bookQueryManager.removeMoveFromBook(
        bookId,
        PositionIdentifier.START_POSITION.fenRepresentation,
        "e4",
      )

    assertFalse(result)
  }
}
