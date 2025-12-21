package proj.memorchess.axl.core.data.book

import kotlin.time.Instant

/**
 * Represents a book containing a collection of chess moves.
 *
 * @property id The unique identifier of the book.
 * @property name The name of the book.
 * @property createdAt The timestamp when the book was created.
 * @property downloads The number of times this book has been downloaded.
 */
data class Book(val id: Long, val name: String, val createdAt: Instant, val downloads: Int = 0)
