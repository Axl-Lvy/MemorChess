package proj.memorchess.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import proj.memorchess.server.db.BookTable
import proj.memorchess.server.db.DatabaseFactory
import proj.memorchess.server.db.MoveCrossBookTable
import proj.memorchess.server.db.MovesTable
import proj.memorchess.server.db.PositionsTable
import proj.memorchess.server.plugins.configureRouting
import proj.memorchess.server.plugins.configureSerialization
import proj.memorchess.server.plugins.configureUserPlugin
import proj.memorchess.shared.USER_ID_HEADER
import proj.memorchess.shared.dto.BookDto
import proj.memorchess.shared.dto.BookMoveDto
import proj.memorchess.shared.routes.BooksRoute

class BookRoutesTest {

  companion object {
    private val postgres =
      PostgreSQLContainer("postgres:17").apply {
        withDatabaseName("memorchess_test")
        withUsername("test")
        withPassword("test")
        start()
      }
  }

  private val testUserId = UUID.randomUUID()

  @BeforeTest
  fun setup() {
    DatabaseFactory.init(postgres.jdbcUrl, postgres.username, postgres.password)
  }

  @AfterTest
  fun teardown() {
    transaction {
      exec("TRUNCATE downloaded_books, move_cross_book, moves, positions, book, app_user CASCADE")
    }
  }

  private fun seedBook(name: String, downloads: Int = 0): Long = transaction {
    BookTable.insert {
        it[BookTable.name] = name
        it[BookTable.createdAt] = OffsetDateTime.now()
        it[BookTable.downloads] = downloads
      }[BookTable.id]
      .value
  }

  private fun seedMove(
    bookId: Long,
    originFen: String,
    destFen: String,
    moveName: String,
    isGood: Boolean,
  ) = transaction {
    val originId =
      PositionsTable.insert { it[fenRepresentation] = originFen }[PositionsTable.id].value

    val destId = PositionsTable.insert { it[fenRepresentation] = destFen }[PositionsTable.id].value

    val moveId =
      MovesTable.insert {
          it[origin] = originId
          it[destination] = destId
          it[this.name] = moveName
        }[MovesTable.id]
        .value

    MoveCrossBookTable.insert {
      it[this.moveId] = moveId
      it[this.bookId] = bookId
      it[this.isGood] = isGood
    }
  }

  @Test
  fun testHealthCheck() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val response = client.get("/api/health")
    assertEquals(HttpStatusCode.OK, response.status)
  }

  @Test
  fun testListBooks() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    seedBook("Italian Game", downloads = 5)
    seedBook("Sicilian Defense", downloads = 10)

    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    val books: List<BookDto> =
      client.get(BooksRoute.Search()) { header(USER_ID_HEADER, testUserId.toString()) }.body()
    assertEquals(2, books.size)
    assertEquals("Sicilian Defense", books[0].name)
    assertEquals("Italian Game", books[1].name)
  }

  @Test
  fun testGetBookById() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val bookId = seedBook("Ruy Lopez")

    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    val book: BookDto =
      client
        .get(BooksRoute.ById(id = bookId)) { header(USER_ID_HEADER, testUserId.toString()) }
        .body()
    assertEquals("Ruy Lopez", book.name)
  }

  @Test
  fun testGetBookNotFound() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    val response =
      client.get(BooksRoute.ById(id = -999)) { header(USER_ID_HEADER, testUserId.toString()) }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun testGetBookMoves() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val bookId = seedBook("Test Book")
    val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val e4Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"
    seedMove(bookId, startFen, e4Fen, "e4", true)

    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    val moves: List<BookMoveDto> =
      client
        .get(BooksRoute.Moves(id = bookId)) { header(USER_ID_HEADER, testUserId.toString()) }
        .body()
    assertEquals(1, moves.size)
    assertEquals("e4", moves[0].move)
    assertTrue(moves[0].isGood)
    assertEquals(startFen, moves[0].origin)
    assertEquals(e4Fen, moves[0].destination)
  }

  @Test
  fun testDownloadBook() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val bookId = seedBook("Download Book")
    val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val e4Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"
    seedMove(bookId, startFen, e4Fen, "e4", true)

    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }

    // First download
    val moves: List<BookMoveDto> =
      client
        .post(BooksRoute.Download(id = bookId)) { header(USER_ID_HEADER, testUserId.toString()) }
        .body()
    assertEquals(1, moves.size)

    val book1 = transaction { BookTable.selectAll().where { BookTable.id eq bookId }.first() }
    assertEquals(1, book1[BookTable.downloads])

    // Second download (same user) — should not increment
    client
      .post(BooksRoute.Download(id = bookId)) { header(USER_ID_HEADER, testUserId.toString()) }
      .body<List<BookMoveDto>>()

    val book2 = transaction { BookTable.selectAll().where { BookTable.id eq bookId }.first() }
    assertEquals(1, book2[BookTable.downloads])
  }

  @Test
  fun testSearchBooks() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    seedBook("Italian Game")
    seedBook("Sicilian Defense")

    val client = createClient {
      install(ContentNegotiation) { json() }
      install(Resources)
    }
    val books: List<BookDto> =
      client
        .get(BooksRoute.Search(text = "sicilian")) { header(USER_ID_HEADER, testUserId.toString()) }
        .body()
    assertEquals(1, books.size)
    assertEquals("Sicilian Defense", books[0].name)
  }

  @Test
  fun testMissingUserIdHeader() = testApplication {
    application {
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    val response = client.get("/api/books")
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }
}
