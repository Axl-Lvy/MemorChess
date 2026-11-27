package proj.memorchess.axl.core.data

import io.github.alluhemanth.chess.core.ChessGame
import io.github.alluhemanth.chess.core.piece.PieceType
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.PieceKind

class TestPositionKeyCodec {

  @Test
  fun startPositionPacksToTwentyFourBytes() {
    val encoded = PositionKeyCodec.encode(PositionKey.START_POSITION)

    encoded.size shouldBe 24
    PositionKeyCodec.decode(encoded) shouldBe PositionKey.START_POSITION
  }

  @Test
  fun randomGamesRoundTrip() {
    val keys = randomGameKeys(Random(9), games = 20, plies = 40)

    for (key in keys) {
      val encoded = PositionKeyCodec.encode(key)
      PositionKeyCodec.decode(encoded) shouldBe key
      encoded.size shouldBeLessThanOrEqual 24
      isEscaped(encoded) shouldBe false
    }
    keys.distinct().size shouldBe keys.map { PositionKeyCodec.encode(it).toList() }.distinct().size
  }

  @Test
  fun enPassantRoundTrips() {
    val keys =
      listOf(
        "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6",
        "rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b KQkq d3",
        "rnbqkbnr/1ppppppp/8/8/pP6/8/P1PPPPPP/RNBQKBNR b KQkq b3",
        "rnbqkbnr/ppppppp1/8/6Pp/8/8/PPPPPP1P/RNBQKBNR w KQkq h6",
      )

    for (fen in keys) assertPackedRoundTrip(PositionKey(fen))
  }

  @Test
  fun everyCastlingSubsetRoundTrips() {
    val rights = "KQkq"

    for (mask in 0 until 16) {
      val subset = rights.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.ifEmpty { "-" }
      assertPackedRoundTrip(PositionKey("r3k2r/8/8/8/8/8/8/R3K2R w $subset"))
    }
  }

  @Test
  fun twoKingsOnlyPacksToNineBytes() {
    val key = PositionKey("8/8/4k3/8/8/4K3/8/8 b -")

    val encoded = PositionKeyCodec.encode(key)

    encoded.size shouldBe 9
    PositionKeyCodec.decode(encoded) shouldBe key
  }

  @Test
  fun oddAndEvenPieceCountsRoundTrip() {
    assertPackedRoundTrip(PositionKey("8/8/4k3/8/8/4K3/4P3/8 w -"))
    assertPackedRoundTrip(PositionKey("8/4p3/4k3/8/8/4K3/4P3/8 b -"))
  }

  @Test
  fun promotedQueensRoundTrip() {
    assertPackedRoundTrip(PositionKey("4k3/8/8/8/8/8/8/QQQ1K3 w -"))
  }

  @Test
  fun fallbackRoundTrips() {
    val keys =
      listOf(
        "",
        "a",
        "posA b K",
        "not a fen",
        "4k3/8/8/8/8/8/8/4K3 w K",
        "r3k2r/8/8/8/8/8/8/R3K2R w kK",
        "8/8/8/8/8/8/8/8 w -",
      )

    for (value in keys) {
      val encoded = PositionKeyCodec.encode(PositionKey(value))
      PositionKeyCodec.decode(encoded) shouldBe PositionKey(value)
      isEscaped(encoded) shouldBe true
    }
  }

  @Test
  fun orderIsUnsignedBytewise() {
    val keys =
      listOf(
        PositionKey.START_POSITION,
        PositionKey("8/8/4k3/8/8/4K3/8/8 b -"),
        PositionKey("rnbqkbnr/pppp1ppp/8/8/3Pp3/8/PPP1PPPP/RNBQKBNR b KQkq d3"),
        PositionKey("b"),
        PositionKey("a"),
        PositionKey("ab"),
      )

    val sorted = keys.sortedWith(PositionKeyCodec.ORDER)

    sorted shouldBe keys.sortedWith { a, b -> unsignedCompare(encode(a), encode(b)) }
    sorted.take(3) shouldBe listOf(PositionKey("a"), PositionKey("ab"), PositionKey("b"))
  }

  private fun encode(key: PositionKey) = PositionKeyCodec.encode(key)

  private fun unsignedCompare(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
      val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
      if (diff != 0) return diff
    }
    return a.size - b.size
  }

  private fun isEscaped(encoded: ByteArray) =
    encoded.size >= 8 && (0 until 8).all { encoded[it] == 0.toByte() }

  private fun assertPackedRoundTrip(key: PositionKey) {
    val encoded = PositionKeyCodec.encode(key)
    PositionKeyCodec.decode(encoded) shouldBe key
    isEscaped(encoded) shouldBe false
  }

  /**
   * Plays seeded random legal games, mirroring every move on a [GameEngine] so the collected keys
   * are exactly what the app would store.
   */
  private fun randomGameKeys(random: Random, games: Int, plies: Int): List<PositionKey> {
    val keys = mutableListOf<PositionKey>()
    repeat(games) {
      val game = ChessGame()
      val engine = GameEngine()
      keys += engine.toPositionKey()
      repeat(plies) {
        val legal = game.getLegalMoves()
        if (legal.isEmpty()) return@repeat
        val move = legal[random.nextInt(legal.size)]
        game.makeMove(move)
        val from = move.from.rank.value - 1 to move.from.file.value - 'a'
        val to = move.to.rank.value - 1 to move.to.file.value - 'a'
        val promotion = move.promotionPieceType
        if (promotion == null) engine.playCoordinateMove(from, to)
        else engine.playCoordinateMoveWithPromotion(from, to, promotion.toPieceKind())
        keys += engine.toPositionKey()
      }
    }
    return keys
  }

  private fun PieceType.toPieceKind(): PieceKind =
    when (this) {
      PieceType.QUEEN -> PieceKind.QUEEN
      PieceType.ROOK -> PieceKind.ROOK
      PieceType.BISHOP -> PieceKind.BISHOP
      PieceType.KNIGHT -> PieceKind.KNIGHT
      else -> error("Not a promotion piece: $this")
    }
}
