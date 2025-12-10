package proj.memorchess.axl.core.data.book

import kotlin.test.*
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil

class TestBookDataClasses {

  @Test
  fun testBookCreation() {
    val now = DateUtil.now()
    val book = Book(id = 1L, name = "Italian Game", createdAt = now)

    assertEquals(1L, book.id)
    assertEquals("Italian Game", book.name)
    assertEquals(now, book.createdAt)
  }

  @Test
  fun testBookEquality() {
    val now = DateUtil.now()
    val book1 = Book(id = 1L, name = "Italian Game", createdAt = now)
    val book2 = Book(id = 1L, name = "Italian Game", createdAt = now)

    assertEquals(book1, book2)
  }

  @Test
  fun testBookInequalityById() {
    val now = DateUtil.now()
    val book1 = Book(id = 1L, name = "Italian Game", createdAt = now)
    val book2 = Book(id = 2L, name = "Italian Game", createdAt = now)

    assertNotEquals(book1, book2)
  }

  @Test
  fun testBookInequalityByName() {
    val now = DateUtil.now()
    val book1 = Book(id = 1L, name = "Italian Game", createdAt = now)
    val book2 = Book(id = 1L, name = "Sicilian Defense", createdAt = now)

    assertNotEquals(book1, book2)
  }

  @Test
  fun testBookCopy() {
    val now = DateUtil.now()
    val book = Book(id = 1L, name = "Italian Game", createdAt = now)
    val copiedBook = book.copy(name = "Modified Name")

    assertEquals(1L, copiedBook.id)
    assertEquals("Modified Name", copiedBook.name)
    assertEquals(now, copiedBook.createdAt)
  }

  @Test
  fun testBookMoveCreation() {
    val origin = PositionIdentifier.START_POSITION
    val destination =
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

    val bookMove = BookMove(origin, destination, "e4", true)

    assertEquals(origin, bookMove.origin)
    assertEquals(destination, bookMove.destination)
    assertEquals("e4", bookMove.move)
    assertTrue(bookMove.isGood)
  }

  @Test
  fun testBookMoveGoodAndBad() {
    val origin = PositionIdentifier.START_POSITION
    val destination =
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

    val goodMove = BookMove(origin, destination, "e4", true)
    val badMove = BookMove(origin, destination, "e4", false)

    assertTrue(goodMove.isGood)
    assertFalse(badMove.isGood)
  }

  @Test
  fun testBookMoveEquality() {
    val origin = PositionIdentifier.START_POSITION
    val destination =
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")

    val move1 = BookMove(origin, destination, "e4", true)
    val move2 = BookMove(origin, destination, "e4", true)

    assertEquals(move1, move2)
  }

  @Test
  fun testBookMoveInequalityByMove() {
    val origin = PositionIdentifier.START_POSITION
    val dest1 = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val dest2 = PositionIdentifier("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq")

    val move1 = BookMove(origin, dest1, "e4", true)
    val move2 = BookMove(origin, dest2, "d4", true)

    assertNotEquals(move1, move2)
  }

  @Test
  fun testBookMoveInequalityByIsGood() {
    val origin = PositionIdentifier.START_POSITION
    val destination = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")

    val move1 = BookMove(origin, destination, "e4", true)
    val move2 = BookMove(origin, destination, "e4", false)

    assertNotEquals(move1, move2)
  }

  @Test
  fun testBookMoveCopy() {
    val origin = PositionIdentifier.START_POSITION
    val destination = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")

    val move = BookMove(origin, destination, "e4", true)
    val copiedMove = move.copy(isGood = false)

    assertEquals(origin, copiedMove.origin)
    assertEquals(destination, copiedMove.destination)
    assertEquals("e4", copiedMove.move)
    assertFalse(copiedMove.isGood)
  }

  @Test
  fun testUserPermissionValues() {
    assertEquals("BOOK_CREATION", UserPermission.BOOK_CREATION.value)
  }

  @Test
  fun testUserPermissionEnumEntries() {
    val permissions = UserPermission.entries
    assertEquals(1, permissions.size)
    assertTrue(permissions.contains(UserPermission.BOOK_CREATION))
  }
}
