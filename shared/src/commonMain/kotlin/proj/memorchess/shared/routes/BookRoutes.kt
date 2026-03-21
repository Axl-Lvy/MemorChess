package proj.memorchess.shared.routes

import io.ktor.resources.Resource

/** Type-safe routes for the books API. */
@Resource("/api/books")
class BooksRoute {

  /** Search/list books with pagination and optional text filter. */
  @Resource("")
  class Search(
    val parent: BooksRoute = BooksRoute(),
    val offset: Long = 0,
    val limit: Int = 50,
    val text: String = "",
  )

  /** Get a single book by its ID. */
  @Resource("/{id}") class ById(val parent: BooksRoute = BooksRoute(), val id: Long)

  /** Get all moves for a book (browsing only, no side effects). */
  @Resource("/{id}/moves") class Moves(val parent: BooksRoute = BooksRoute(), val id: Long)

  /** Download a book: registers the download and returns moves in a single call. */
  @Resource("/{id}/download") class Download(val parent: BooksRoute = BooksRoute(), val id: Long)
}

/** Health check endpoint. */
@Resource("/api/health") class HealthRoute
