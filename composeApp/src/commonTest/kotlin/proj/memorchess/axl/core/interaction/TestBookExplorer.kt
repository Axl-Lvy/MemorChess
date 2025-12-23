package proj.memorchess.axl.core.interaction

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.config.generated.Secrets
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.graph.nodes.IsolatedBookNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.ToastRendererForTests

/** Tests for [BookExplorer] which handles exploring and downloading book moves. */
class TestBookExplorer : TestWithKoin {

  private val bookQueryManager: SupabaseBookQueryManager by inject()
  private val authManager: AuthManager by inject()
  private val database: DatabaseQueryManager by inject()

  private lateinit var testBook: Book
  private lateinit var bookExplorer: BookExplorer
  private var createdBookId: Long? = null

  @BeforeTest
  override fun setUp() {
    super.setUp()
    ensureSignedIn()
    runTest { database.deleteAll(null) }
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

  private suspend fun cleanupTestBook() {
    createdBookId?.let { bookQueryManager.deleteBook(it) }
    createdBookId = null
  }

  private suspend fun setupTestBook(canEdit: Boolean = false) {
    val bookId = bookQueryManager.createBook("Test Opening")
    createdBookId = bookId
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

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, canEdit, nodeManager)
  }

  @Test
  fun testGetNextMoves() = runTest {
    setupTestBook()

    val nextMoves = bookExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e4"))
  }

  @Test
  fun testNavigateThroughBookMoves() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )

    val nextMovesAfterE4 = bookExplorer.getNextMoves()
    assertEquals(1, nextMovesAfterE4.size)
    assertTrue(nextMovesAfterE4.contains("e5"))
  }

  @Test
  fun testBackNavigation() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.back()
    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )
  }

  @Test
  fun testReset() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.reset()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.game.position.createIdentifier())
  }

  @Test
  fun testDownloadBookToRepertoire() = runTest {
    setupTestBook()

    val nodesBeforeDownload = database.getAllNodes()
    assertTrue(nodesBeforeDownload.isEmpty())

    bookExplorer.downloadBookToRepertoire()

    val nodesAfterDownload = database.getAllNodes()
    assertTrue(nodesAfterDownload.isNotEmpty())
  }

  @Test
  fun testDownloadBookToRepertoireFail() = runTest {
    setupTestBook()

    val nodesBeforeDownload = database.getAllNodes()
    assertTrue(nodesBeforeDownload.isEmpty())

    ensureSignedOut()
    ToastRendererForTests.clear()
    bookExplorer.downloadBookToRepertoire()

    val nodesAfterDownload = database.getAllNodes()
    assertTrue(nodesAfterDownload.isEmpty())
    ToastRendererForTests.messages.find {
      it.second.contains("Failed to download book Test Opening.")
    }
    ensureSignedIn()
  }

  @Test
  fun testDownloadBookToRepertoireCalculatesCorrectDepth() = runTest {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    val startNode = nodeMap[PositionIdentifier.START_POSITION]!!
    assertEquals(0, startNode.previousAndNextMoves.depth)

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e4Node = nodeMap[e4Position]!!
    assertEquals(1, e4Node.previousAndNextMoves.depth)

    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Node = nodeMap[e5Position]!!
    assertEquals(2, e5Node.previousAndNextMoves.depth)

    val nf3Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nf3Node = nodeMap[nf3Position]!!
    assertEquals(3, nf3Node.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithCycleDoesNotHang() = runTest {
    val bookId = bookQueryManager.createBook("Test Cycle")
    createdBookId = bookId

    val nf3Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")
    val nf6Position = PositionIdentifier("rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq")
    val ng1Position = PositionIdentifier("rnbqkb1r/pppppppp/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQkq")
    val ng8Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf6Position, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf6Position, ng1Position, "Ng1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(ng1Position, ng8Position, "Ng8", false))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(5, nodes.size)
  }

  @Test
  fun testDownloadBookToRepertoireWithCycleCalculatesCorrectDepth() = runTest {
    val bookId = bookQueryManager.createBook("Test Cycle Depth")
    createdBookId = bookId

    val nf3Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")
    val nf6Position = PositionIdentifier("rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq")
    val ng1Position = PositionIdentifier("rnbqkb1r/pppppppp/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQkq")
    val ng8Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf6Position, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf6Position, ng1Position, "Ng1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(ng1Position, ng8Position, "Ng8", false))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[nf3Position]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[nf6Position]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[ng1Position]!!.previousAndNextMoves.depth)
    assertEquals(4, nodeMap[ng8Position]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithComplexCycle() = runTest {
    val bookId = bookQueryManager.createBook("Complex Cycle")
    createdBookId = bookId

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nc6Position =
      PositionIdentifier("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3Position, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nc6Position, "Nc6", false))
    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(nc6Position, PositionIdentifier.START_POSITION, "back-to-start", true),
    )

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(5, nodes.size)

    val nodeMap = nodes.associateBy { it.positionIdentifier }
    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithMultiplePaths() = runTest {
    val bookId = bookQueryManager.createBook("Multiple Paths")
    createdBookId = bookId

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val c5Position = PositionIdentifier("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3FromC5 = PositionIdentifier("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nf3FromE5 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, nf3FromC5, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3FromE5, "Nf3", true))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[e4Position]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[c5Position]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[e5Position]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[nf3FromC5]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[nf3FromE5]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithTranspositions() = runTest {
    val bookId = bookQueryManager.createBook("Transpositions")
    createdBookId = bookId

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val d4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq")
    val d5Position = PositionIdentifier("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq")
    val e5FromD5 = PositionIdentifier("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPP1PPPP/RNBQKBNR b KQkq")

    val c5Position = PositionIdentifier("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val d4FromC5 = PositionIdentifier("rnbqkbnr/pp1ppppp/8/2p5/3PP3/8/PPP2PPP/RNBQKBNR b KQkq")
    val cxd4Position = PositionIdentifier("rnbqkbnr/pp1ppppp/8/8/3pP3/8/PPP2PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, d4Position, "d4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(d4Position, d5Position, "d5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(d5Position, e5FromD5, "e4", true))

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, d4FromC5, "d4", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(d4FromC5, cxd4Position, "cxd4", false))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[e4Position]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[d4Position]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireCountsAllMovesCorrectly() = runTest {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(4, nodes.size)

    val startNode = nodes.find { it.positionIdentifier == PositionIdentifier.START_POSITION }!!
    assertEquals(1, startNode.previousAndNextMoves.nextMoves.size)
    assertTrue(startNode.previousAndNextMoves.nextMoves.containsKey("e4"))
  }

  @Test
  fun testDownloadBookToRepertoireWithSelfLoop() = runTest {
    val bookId = bookQueryManager.createBook("Self Loop")
    createdBookId = bookId

    val nf3Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf3Position, "self-loop", false))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(2, nodes.size)

    val nodeMap = nodes.associateBy { it.positionIdentifier }
    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[nf3Position]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithDiamondPattern() = runTest {
    val bookId = bookQueryManager.createBook("Diamond Pattern")
    createdBookId = bookId

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val c5Position = PositionIdentifier("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, nf3Position, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3Position, "Nf3", true))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[e4Position]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[c5Position]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[e5Position]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[nf3Position]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithDeepLinearChain() = runTest {
    val bookId = bookQueryManager.createBook("Deep Chain")
    createdBookId = bookId

    val pos1 = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionIdentifier("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")
    val pos5 = PositionIdentifier("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq")
    val pos6 = PositionIdentifier("r1bqk1nr/pppp1ppp/2n5/1Bb1p3/4P3/5N2/PPPP1PPP/RNBQK2R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, pos1, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nc6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos5, "Bb5", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos5, pos6, "Bc5", false))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(7, nodes.size)
    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[pos1]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[pos2]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[pos3]!!.previousAndNextMoves.depth)
    assertEquals(4, nodeMap[pos4]!!.previousAndNextMoves.depth)
    assertEquals(5, nodeMap[pos5]!!.previousAndNextMoves.depth)
    assertEquals(6, nodeMap[pos6]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithBackwardEdgeToNonRoot() = runTest {
    val bookId = bookQueryManager.createBook("Backward Edge")
    createdBookId = bookId

    val pos1 = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionIdentifier("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, pos1, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nc6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos2, "backward", true))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(5, nodes.size)
    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[pos1]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[pos2]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[pos3]!!.previousAndNextMoves.depth)
    assertEquals(4, nodeMap[pos4]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithMultipleCycles() = runTest {
    val bookId = bookQueryManager.createBook("Multiple Cycles")
    createdBookId = bookId

    val pos1 = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionIdentifier("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionIdentifier.START_POSITION, pos1, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos1, "cycle1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos3, "cycle2", true))

    val books = bookQueryManager.getAllBooks()
    testBook = books.find { it.id == bookId }!!

    val nodeManager: NodeManager<IsolatedBookNode> by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, false, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    assertEquals(5, nodes.size)
    assertEquals(0, nodeMap[PositionIdentifier.START_POSITION]!!.previousAndNextMoves.depth)
    assertEquals(1, nodeMap[pos1]!!.previousAndNextMoves.depth)
    assertEquals(2, nodeMap[pos2]!!.previousAndNextMoves.depth)
    assertEquals(3, nodeMap[pos3]!!.previousAndNextMoves.depth)
    assertEquals(4, nodeMap[pos4]!!.previousAndNextMoves.depth)
  }

  @Test
  fun testDownloadBookToRepertoireVerifiesMovesAreLinked() = runTest {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionIdentifier }

    val e4Position = PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionIdentifier("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    val startNode = nodeMap[PositionIdentifier.START_POSITION]!!
    assertTrue(startNode.previousAndNextMoves.nextMoves.containsKey("e4"))
    assertEquals(e4Position, startNode.previousAndNextMoves.nextMoves["e4"]!!.destination)

    val e4Node = nodeMap[e4Position]!!
    assertTrue(e4Node.previousAndNextMoves.nextMoves.containsKey("e5"))
    assertEquals(e5Position, e4Node.previousAndNextMoves.nextMoves["e5"]!!.destination)

    val e5Node = nodeMap[e5Position]!!
    assertTrue(e5Node.previousAndNextMoves.nextMoves.containsKey("Nf3"))
    assertEquals(nf3Position, e5Node.previousAndNextMoves.nextMoves["Nf3"]!!.destination)
  }

  @Test
  fun testNoNextMovesAtEndOfLine() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")

    val nextMoves = bookExplorer.getNextMoves()
    assertTrue(nextMoves.isEmpty())
  }

  @Test
  fun testBackFromStartPositionStaysAtStart() = runTest {
    setupTestBook()

    bookExplorer.back()
    assertEquals(PositionIdentifier.START_POSITION, bookExplorer.game.position.createIdentifier())
  }

  @Test
  fun testGameStateUpdatedAfterMove() = runTest {
    setupTestBook()

    val initialPosition = bookExplorer.game.position.createIdentifier()
    assertEquals(PositionIdentifier.START_POSITION, initialPosition)

    bookExplorer.playMove("e4")

    val afterE4Position = bookExplorer.game.position.createIdentifier()
    assertNotEquals(PositionIdentifier.START_POSITION, afterE4Position)
  }

  @Test
  fun testCanEditFalseBlocksInteraction() = runTest {
    setupTestBook(canEdit = false)

    assertFalse(bookExplorer.canEdit)
  }

  @Test
  fun testCanEditTrueAllowsInteraction() = runTest {
    setupTestBook(canEdit = true)

    assertTrue(bookExplorer.canEdit)
  }

  @Test
  fun testForwardNavigation() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.back()
    bookExplorer.forward()

    assertEquals(
      PositionIdentifier("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.game.position.createIdentifier(),
    )
  }

  @Test
  fun testSaveMove() = runTest {
    setupTestBook(canEdit = true)

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")
    bookExplorer.playMove("Nc6")
    bookExplorer.save()

    val moves = bookQueryManager.getBookMoves(testBook.id)
    assertTrue(moves.any { it.move == "Nc6" })
  }

  @Test
  fun testDeleteMoves() = runTest {
    setupTestBook(canEdit = true)

    val initialMoves = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(3, initialMoves.size)

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.delete()

    val movesAfterDelete = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(2, movesAfterDelete.size)
    assertFalse(movesAfterDelete.any { it.move == "Nf3" })
  }

  @Test
  fun testDeleteMoveFail() = runTest {
    setupTestBook(canEdit = true)

    val initialMoves = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(3, initialMoves.size)

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    ensureSignedOut()
    ToastRendererForTests.clear()
    bookExplorer.delete()
    ensureSignedIn()

    val movesAfterDelete = bookQueryManager.getBookMoves(testBook.id)
    assertEquals(3, movesAfterDelete.size)
    assertTrue(movesAfterDelete.any { it.move == "Nf3" })
    assertNotNull(
      ToastRendererForTests.messages.find { it.second.contains("Failed to delete move") }
    )
  }

  @Test
  fun testCalculateNumberOfNodeToDelete() = runTest {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.back()
    val count = bookExplorer.calculateNumberOfNodeToDelete()

    assertEquals(4, count)
  }

  @Test
  fun testDifferentBooksDontShareNodeManager() = runTest {
    val bookId1 = bookQueryManager.createBook("Test Book 1")
    val bookId2 = bookQueryManager.createBook("Test Book 2")

    try {
      val nodeManager1: NodeManager<IsolatedBookNode> by
        inject(named("book")) { parametersOf(bookId1) }
      val nodeManager2: NodeManager<IsolatedBookNode> by
        inject(named("book")) { parametersOf(bookId2) }

      assertNotEquals(
        nodeManager1,
        nodeManager2,
        "BookExplorers with different bookIds should not share the same NodeManager",
      )
    } finally {
      bookQueryManager.deleteBook(bookId1)
      bookQueryManager.deleteBook(bookId2)
    }
  }
}
