package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.book.Book
import proj.memorchess.axl.core.data.book.BookMove
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.BookExplorer
import proj.memorchess.axl.test_util.InMemoryBookQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

/** Tests for [BookExplorer] which handles exploring and downloading book moves. */
class TestBookExplorer : TestWithKoin() {

  private val bookQueryManager: InMemoryBookQueryManager by inject()
  private val database: DatabaseQueryManager by inject()

  private lateinit var testBook: Book
  private lateinit var bookExplorer: BookExplorer

  override suspend fun setUp() {
    database.deleteAll(null)
  }

  private suspend fun setupTestBook() {
    val bookId = bookQueryManager.createBook("Test Opening")
    testBook = bookQueryManager.getBook(bookId)!!

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3Position, "Nf3", true))

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)
  }

  @Test
  fun testGetNextMoves() = test {
    setupTestBook()

    val nextMoves = bookExplorer.getNextMoves()
    assertEquals(1, nextMoves.size)
    assertTrue(nextMoves.contains("e4"))
  }

  @Test
  fun testNavigateThroughBookMoves() = test {
    setupTestBook()

    bookExplorer.playMove("e4")
    assertEquals(
      PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.engine.toPositionKey(),
    )

    val nextMovesAfterE4 = bookExplorer.getNextMoves()
    assertEquals(1, nextMovesAfterE4.size)
    assertTrue(nextMovesAfterE4.contains("e5"))
  }

  @Test
  fun testBackNavigation() = test {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.back()
    assertEquals(
      PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq"),
      bookExplorer.engine.toPositionKey(),
    )
  }

  @Test
  fun testReset() = test {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")

    bookExplorer.reset()
    assertEquals(PositionKey.START_POSITION, bookExplorer.engine.toPositionKey())
  }

  @Test
  fun testDownloadBookToRepertoire() = test {
    setupTestBook()

    val nodesBeforeDownload = database.getAllNodes()
    assertTrue(nodesBeforeDownload.isEmpty())

    bookExplorer.downloadBookToRepertoire()

    val nodesAfterDownload = database.getAllNodes()
    assertTrue(nodesAfterDownload.isNotEmpty())
  }

  @Test
  fun testDownloadBookToRepertoireCalculatesCorrectDepth() = test {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    val startNode = nodeMap[PositionKey.START_POSITION]!!
    assertEquals(0, startNode.depth)

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e4Node = nodeMap[e4Position]!!
    assertEquals(1, e4Node.depth)

    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Node = nodeMap[e5Position]!!
    assertEquals(2, e5Node.depth)

    val nf3Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nf3Node = nodeMap[nf3Position]!!
    assertEquals(3, nf3Node.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithCycleDoesNotHang() = test {
    val bookId = bookQueryManager.createBook("Test Cycle")

    val nf3Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")
    val nf6Position = PositionKey("rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq")
    val ng1Position = PositionKey("rnbqkb1r/pppppppp/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQkq")
    val ng8Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf6Position, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf6Position, ng1Position, "Ng1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(ng1Position, ng8Position, "Ng8", false))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(5, nodes.size)
  }

  @Test
  fun testDownloadBookToRepertoireWithCycleCalculatesCorrectDepth() = test {
    val bookId = bookQueryManager.createBook("Test Cycle Depth")

    val nf3Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")
    val nf6Position = PositionKey("rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq")
    val ng1Position = PositionKey("rnbqkb1r/pppppppp/5n2/8/8/8/PPPPPPPP/RNBQKB1R b KQkq")
    val ng8Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf6Position, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf6Position, ng1Position, "Ng1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(ng1Position, ng8Position, "Ng8", false))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[nf3Position]!!.depth)
    assertEquals(2, nodeMap[nf6Position]!!.depth)
    assertEquals(3, nodeMap[ng1Position]!!.depth)
    assertEquals(4, nodeMap[ng8Position]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithComplexCycle() = test {
    val bookId = bookQueryManager.createBook("Complex Cycle")

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nc6Position = PositionKey("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3Position, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nc6Position, "Nc6", false))
    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(nc6Position, PositionKey.START_POSITION, "back-to-start", true),
    )

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(5, nodes.size)

    val nodeMap = nodes.associateBy { it.positionKey }
    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithMultiplePaths() = test {
    val bookId = bookQueryManager.createBook("Multiple Paths")

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val c5Position = PositionKey("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3FromC5 = PositionKey("rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val nf3FromE5 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, nf3FromC5, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3FromE5, "Nf3", true))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[e4Position]!!.depth)
    assertEquals(2, nodeMap[c5Position]!!.depth)
    assertEquals(2, nodeMap[e5Position]!!.depth)
    assertEquals(3, nodeMap[nf3FromC5]!!.depth)
    assertEquals(3, nodeMap[nf3FromE5]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithTranspositions() = test {
    val bookId = bookQueryManager.createBook("Transpositions")

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val d4Position = PositionKey("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq")
    val d5Position = PositionKey("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq")
    val e5FromD5 = PositionKey("rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPP1PPPP/RNBQKBNR b KQkq")

    val c5Position = PositionKey("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val d4FromC5 = PositionKey("rnbqkbnr/pp1ppppp/8/2p5/3PP3/8/PPP2PPP/RNBQKBNR b KQkq")
    val cxd4Position = PositionKey("rnbqkbnr/pp1ppppp/8/8/3pP3/8/PPP2PPP/RNBQKBNR w KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, d4Position, "d4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(d4Position, d5Position, "d5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(d5Position, e5FromD5, "e4", true))

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, d4FromC5, "d4", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(d4FromC5, cxd4Position, "cxd4", false))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[e4Position]!!.depth)
    assertEquals(1, nodeMap[d4Position]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireCountsAllMovesCorrectly() = test {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(4, nodes.size)

    val startNode = nodes.find { it.positionKey == PositionKey.START_POSITION }!!
    assertEquals(1, startNode.previousAndNextMoves.nextMoves.size)
    assertTrue(startNode.previousAndNextMoves.nextMoves.containsKey("e4"))
  }

  @Test
  fun testDownloadBookToRepertoireWithSelfLoop() = test {
    val bookId = bookQueryManager.createBook("Self Loop")

    val nf3Position = PositionKey("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, nf3Position, "Nf3", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(nf3Position, nf3Position, "self-loop", false))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    assertEquals(2, nodes.size)

    val nodeMap = nodes.associateBy { it.positionKey }
    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[nf3Position]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithDiamondPattern() = test {
    val bookId = bookQueryManager.createBook("Diamond Pattern")

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val c5Position = PositionKey("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    bookQueryManager.addMoveToBook(
      bookId,
      BookMove(PositionKey.START_POSITION, e4Position, "e4", true),
    )
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, c5Position, "c5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(e4Position, e5Position, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(c5Position, nf3Position, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(e5Position, nf3Position, "Nf3", true))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[e4Position]!!.depth)
    assertEquals(2, nodeMap[c5Position]!!.depth)
    assertEquals(2, nodeMap[e5Position]!!.depth)
    assertEquals(3, nodeMap[nf3Position]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithDeepLinearChain() = test {
    val bookId = bookQueryManager.createBook("Deep Chain")

    val pos1 = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionKey("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")
    val pos5 = PositionKey("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq")
    val pos6 = PositionKey("r1bqk1nr/pppp1ppp/2n5/1Bb1p3/4P3/5N2/PPPP1PPP/RNBQK2R w KQkq")

    bookQueryManager.addMoveToBook(bookId, BookMove(PositionKey.START_POSITION, pos1, "e4", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nc6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos5, "Bb5", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos5, pos6, "Bc5", false))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(7, nodes.size)
    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[pos1]!!.depth)
    assertEquals(2, nodeMap[pos2]!!.depth)
    assertEquals(3, nodeMap[pos3]!!.depth)
    assertEquals(4, nodeMap[pos4]!!.depth)
    assertEquals(5, nodeMap[pos5]!!.depth)
    assertEquals(6, nodeMap[pos6]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithBackwardEdgeToNonRoot() = test {
    val bookId = bookQueryManager.createBook("Backward Edge")

    val pos1 = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionKey("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(bookId, BookMove(PositionKey.START_POSITION, pos1, "e4", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nc6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos2, "backward", true))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(5, nodes.size)
    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[pos1]!!.depth)
    assertEquals(2, nodeMap[pos2]!!.depth)
    assertEquals(3, nodeMap[pos3]!!.depth)
    assertEquals(4, nodeMap[pos4]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireWithMultipleCycles() = test {
    val bookId = bookQueryManager.createBook("Multiple Cycles")

    val pos1 = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val pos2 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val pos3 = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")
    val pos4 = PositionKey("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq")

    bookQueryManager.addMoveToBook(bookId, BookMove(PositionKey.START_POSITION, pos1, "e4", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos1, pos2, "e5", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos3, "Nf3", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos3, pos4, "Nf6", false))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos2, pos1, "cycle1", true))
    bookQueryManager.addMoveToBook(bookId, BookMove(pos4, pos3, "cycle2", true))

    testBook = bookQueryManager.getBook(bookId)!!

    val nodeManager: NodeManager by inject(named("book")) { parametersOf(bookId) }
    nodeManager.resetCacheFromSource()
    bookExplorer = BookExplorer(testBook, nodeManager)

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    assertEquals(5, nodes.size)
    assertEquals(0, nodeMap[PositionKey.START_POSITION]!!.depth)
    assertEquals(1, nodeMap[pos1]!!.depth)
    assertEquals(2, nodeMap[pos2]!!.depth)
    assertEquals(3, nodeMap[pos3]!!.depth)
    assertEquals(4, nodeMap[pos4]!!.depth)
  }

  @Test
  fun testDownloadBookToRepertoireVerifiesMovesAreLinked() = test {
    setupTestBook()

    bookExplorer.downloadBookToRepertoire()

    val nodes = database.getAllNodes()
    val nodeMap = nodes.associateBy { it.positionKey }

    val e4Position = PositionKey("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq")
    val e5Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq")
    val nf3Position = PositionKey("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq")

    val startNode = nodeMap[PositionKey.START_POSITION]!!
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
  fun testNoNextMovesAtEndOfLine() = test {
    setupTestBook()

    bookExplorer.playMove("e4")
    bookExplorer.playMove("e5")
    bookExplorer.playMove("Nf3")

    val nextMoves = bookExplorer.getNextMoves()
    assertTrue(nextMoves.isEmpty())
  }

  @Test
  fun testBackFromStartPositionStaysAtStart() = test {
    setupTestBook()

    bookExplorer.back()
    assertEquals(PositionKey.START_POSITION, bookExplorer.engine.toPositionKey())
  }
}
