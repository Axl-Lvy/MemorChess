package proj.memorchess.axl.core.data.book

import io.kotest.matchers.collections.shouldHaveSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import org.koin.core.component.inject
import proj.memorchess.axl.test_util.TestWithKoin

class TestBookQueryManager : TestWithKoin() {

  private val bookQueryManager: BookQueryManager by inject()

  @Test
  fun testGetNonExistentBook() = test {
    val book = bookQueryManager.getBook(-999L)
    assertNull(book)
  }

  @Test
  fun testGetBookMovesForNonExistentBook() = test {
    val moves = bookQueryManager.getBookMoves(-999L)
    assertEquals(0, moves.size)
  }

  @Test
  fun testCannotFetchWithNegativeLimit() = test {
    bookQueryManager.getAllBooks(0, 0) shouldHaveSize 0
    assertFails { bookQueryManager.getAllBooks(0, -1) }
  }
}
