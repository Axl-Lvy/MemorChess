package proj.memorchess.shared.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Data transfer object for a book.
 *
 * @property id The unique identifier of the book.
 * @property name The name of the book.
 * @property createdAt The timestamp when the book was created.
 * @property downloads The number of times this book has been downloaded.
 */
@Serializable
data class BookDto(val id: Long, val name: String, val createdAt: Instant, val downloads: Int = 0)
