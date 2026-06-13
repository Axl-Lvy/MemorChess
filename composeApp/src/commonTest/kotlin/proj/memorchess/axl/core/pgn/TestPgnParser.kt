package proj.memorchess.axl.core.pgn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class TestPgnParser {

  @Test
  fun parsesSimpleMainline() {
    val games = PgnParser.parse("1. e4 e5 2. Nf3 Nc6 1-0")

    games.shouldHaveSize(1)
    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3", "Nc6")
    games[0].moves.shouldHaveSize(1)
  }

  @Test
  fun parsesHeaders() {
    val pgn =
      """
      [Event "Test Study: Chapter 1"]
      [Site "https://lichess.org/study/abcd1234"]
      [Result "*"]

      1. d4 d5 *
      """
        .trimIndent()

    val games = PgnParser.parse(pgn)

    games.shouldHaveSize(1)
    games[0].headers shouldBe
      mapOf(
        "Event" to "Test Study: Chapter 1",
        "Site" to "https://lichess.org/study/abcd1234",
        "Result" to "*",
      )
    mainlineOf(games[0]) shouldBe listOf("d4", "d5")
  }

  @Test
  fun parsesHeaderValueWithEscapedQuoteAndBackslash() {
    val games = PgnParser.parse("""[Event "He said \"hi\" \\ bye"] 1. e4 *""")

    games[0].headers["Event"] shouldBe """He said "hi" \ bye"""
  }

  @Test
  fun parsesHeadersOnlyGame() {
    val games = PgnParser.parse("[Event \"Empty chapter\"]\n[Result \"*\"]\n\n*")

    games.shouldHaveSize(1)
    games[0].headers["Event"] shouldBe "Empty chapter"
    games[0].moves.shouldBeEmpty()
  }

  @Test
  fun parsesHeadersOnlyGameWithoutResultMarker() {
    val games = PgnParser.parse("[Event \"Empty chapter\"]")

    games.shouldHaveSize(1)
    games[0].moves.shouldBeEmpty()
  }

  @Test
  fun parsesEmptyInputAsNoGames() {
    PgnParser.parse("").shouldBeEmpty()
    PgnParser.parse("   \n\t\n  ").shouldBeEmpty()
  }

  @Test
  fun parsesMultiGameFile() {
    val pgn =
      """
      [Event "Chapter 1"]

      1. e4 e5 1-0

      [Event "Chapter 2"]

      1. d4 d5 0-1

      [Event "Chapter 3"]

      1. c4 1/2-1/2
      """
        .trimIndent()

    val games = PgnParser.parse(pgn)

    games.shouldHaveSize(3)
    games[0].headers["Event"] shouldBe "Chapter 1"
    mainlineOf(games[0]) shouldBe listOf("e4", "e5")
    games[1].headers["Event"] shouldBe "Chapter 2"
    mainlineOf(games[1]) shouldBe listOf("d4", "d5")
    games[2].headers["Event"] shouldBe "Chapter 3"
    mainlineOf(games[2]) shouldBe listOf("c4")
  }

  @Test
  fun resultMarkersMidFileSeparateGamesWithoutHeaders() {
    val games = PgnParser.parse("1. e4 e5 1-0 1. d4 d5 * 1. c4 0-1")

    games.shouldHaveSize(3)
    mainlineOf(games[0]) shouldBe listOf("e4", "e5")
    mainlineOf(games[1]) shouldBe listOf("d4", "d5")
    mainlineOf(games[2]) shouldBe listOf("c4")
  }

  @Test
  fun tagPairAfterMovetextStartsNewGameWithoutResultMarker() {
    val games = PgnParser.parse("[Event \"A\"] 1. e4 e5 [Event \"B\"] 1. d4 *")

    games.shouldHaveSize(2)
    games[0].headers["Event"] shouldBe "A"
    mainlineOf(games[0]) shouldBe listOf("e4", "e5")
    games[1].headers["Event"] shouldBe "B"
    mainlineOf(games[1]) shouldBe listOf("d4")
  }

  @Test
  fun parsesSimpleVariation() {
    val games = PgnParser.parse("1. e4 e5 (1... c5 2. Nf3 d6) 2. Nf3 *")

    val e4 = games[0].moves.single()
    e4.san shouldBe "e4"
    e4.children.map { it.san } shouldBe listOf("e5", "c5")
    e4.children[0].children.map { it.san } shouldBe listOf("Nf3")
    e4.children[1].children.single().san shouldBe "Nf3"
    e4.children[1].children.single().children.single().san shouldBe "d6"
  }

  @Test
  fun parsesMultipleVariationsOnSameMove() {
    val games = PgnParser.parse("1. e4 e5 (1... c5) (1... e6) (1... c6) 2. Nf3 *")

    val e4 = games[0].moves.single()
    e4.children.map { it.san } shouldBe listOf("e5", "c5", "e6", "c6")
    e4.children[0].children.map { it.san } shouldBe listOf("Nf3")
  }

  @Test
  fun parsesVariationOnFirstMove() {
    val games = PgnParser.parse("1. e4 (1. d4 d5) 1... e5 *")

    games[0].moves.map { it.san } shouldBe listOf("e4", "d4")
    games[0].moves[0].children.single().san shouldBe "e5"
    games[0].moves[1].children.single().san shouldBe "d5"
  }

  @Test
  fun parsesDeeplyNestedVariations() {
    val games = PgnParser.parse("1. e4 e5 (1... c5 2. Nf3 (2. c3 d5 (2... Nf6 (2... e6)))) *")

    val e4 = games[0].moves.single()
    val c5 = e4.children[1]
    c5.san shouldBe "c5"
    c5.children.map { it.san } shouldBe listOf("Nf3", "c3")
    val c3 = c5.children[1]
    c3.children.map { it.san } shouldBe listOf("d5", "Nf6", "e6")
  }

  @Test
  fun continuesMainlineAfterNestedVariationsClose() {
    val games = PgnParser.parse("1. d4 d5 (1... Nf6 2. c4 (2. Bg5 e6)) 2. Bf4 Nf6 *")

    mainlineOf(games[0]) shouldBe listOf("d4", "d5", "Bf4", "Nf6")
  }

  @Test
  fun ignoresCommentsAnywhere() {
    val pgn =
      """
      {Before anything} 1. e4 {The king's pawn
      spanning two lines} e5 (1... c5 {Sicilian, with (parens) and [brackets] inside} 2. Nf3) 2. Nf3 {done} *
      """
        .trimIndent()

    val games = PgnParser.parse(pgn)

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3")
    games[0].moves.single().children.map { it.san } shouldBe listOf("e5", "c5")
  }

  @Test
  fun ignoresSemicolonCommentsToEndOfLine() {
    val games = PgnParser.parse("1. e4 e5 ; this is ignored 2. Qh5??\n2. Nf3 *")

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3")
  }

  @Test
  fun ignoresPercentEscapeLines() {
    val games =
      PgnParser.parse("%opening import v1\n1. e4 e5\n% another escape line 2. Qh5\n2. Nf3 *")

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3")
  }

  @Test
  fun ignoresNagsAndAnnotationGlyphs() {
    val games = PgnParser.parse("1. e4 $1 e5 $14 $140 2. Nf3!? Nc6?? 3. Bb5! a6?! !? *")

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6")
  }

  @Test
  fun stripsCheckAndMateMarkers() {
    val games = PgnParser.parse("1. e4 e5 2. Qh5+ g6 3. Qxe5+ Ne7 4. Qxh8# *")

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Qh5", "g6", "Qxe5", "Ne7", "Qxh8")
  }

  @Test
  fun keepsPromotionNotation() {
    val games = PgnParser.parse("1. e8=Q d1=N 2. gxh8=R+ axb1=B# *")

    mainlineOf(games[0]) shouldBe listOf("e8=Q", "d1=N", "gxh8=R", "axb1=B")
  }

  @Test
  fun keepsLetterCastlingAndNormalizesZeroCastling() {
    val games = PgnParser.parse("1. O-O 0-0 2. O-O-O 0-0-0 3. 0-0+ O-O-O# *")

    mainlineOf(games[0]) shouldBe listOf("O-O", "O-O", "O-O-O", "O-O-O", "O-O", "O-O-O")
  }

  @Test
  fun acceptsAllCommonMoveNumberLayouts() {
    val games = PgnParser.parse("1.e4 e5 2. Nf3 2... Nc6 3 . Bb5 3 ... a6 4...Ba4 *")

    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4")
  }

  @Test
  fun acceptsLiberalWhitespaceLayout() {
    val games = PgnParser.parse("\uFEFF[Event\t\"A\"]\r\n\r\n1.\ne4\t\te5\r\n(1...\nc5)\n2. Nf3\n*")

    games[0].headers["Event"] shouldBe "A"
    mainlineOf(games[0]) shouldBe listOf("e4", "e5", "Nf3")
    games[0].moves.single().children.map { it.san } shouldBe listOf("e5", "c5")
  }

  @Test
  fun toleratesEmptyVariation() {
    val games = PgnParser.parse("1. e4 () e5 *")

    val e4 = games[0].moves.single()
    e4.san shouldBe "e4"
    e4.children.map { it.san } shouldBe listOf("e5")
  }

  @Test
  fun parsesDisambiguatedMoves() {
    val games = PgnParser.parse("1. Nbd2 R1e2 2. Qh4xe1 exd5 *")

    mainlineOf(games[0]) shouldBe listOf("Nbd2", "R1e2", "Qh4xe1", "exd5")
  }

  @Test
  fun rejectsUnbalancedOpenParenthesis() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 (1... c5 2. Nf3 *") }

    exception.message.orEmpty() shouldContain "Unterminated variation"
    exception.line shouldBe 1
    exception.column shouldBe 7
  }

  @Test
  fun rejectsUnbalancedOpenParenthesisAtEndOfInput() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 (1... c5") }

    exception.message.orEmpty() shouldContain "Unterminated variation"
  }

  @Test
  fun rejectsUnmatchedCloseParenthesis() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 ) e5 *") }

    exception.message.orEmpty() shouldContain "Unmatched ')'"
    exception.line shouldBe 1
    exception.column shouldBe 7
  }

  @Test
  fun rejectsVariationBeforeAnyMove() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("(1. e4) 1. d4 *") }

    exception.message.orEmpty() shouldContain "Variation with no preceding move"
  }

  @Test
  fun rejectsVariationDirectlyInsideVariation() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 ((1... c5)) *") }

    exception.message.orEmpty() shouldContain "Variation with no preceding move"
  }

  @Test
  fun rejectsResultMarkerInsideVariation() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 (1... c5 1-0) *") }

    exception.message.orEmpty() shouldContain "Unterminated variation"
  }

  @Test
  fun rejectsUnterminatedComment() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 {forever open") }

    exception.message.orEmpty() shouldContain "Unterminated comment"
    exception.line shouldBe 1
    exception.column shouldBe 7
  }

  @Test
  fun rejectsUnexpectedClosingBrace() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 } e5 *") }

    exception.message.orEmpty() shouldContain "Unexpected '}'"
  }

  @Test
  fun rejectsGarbageToken() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 banana 2. Nf3 *") }

    exception.message.orEmpty() shouldContain "Invalid SAN token 'banana'"
  }

  @Test
  fun rejectsNagWithoutDigits() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 $ e5 *") }

    exception.message.orEmpty() shouldContain "Invalid NAG"
  }

  @Test
  fun rejectsUnterminatedTagPair() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("[Event \"never closed") }

    exception.message.orEmpty() shouldContain "Unterminated tag pair"
  }

  @Test
  fun rejectsTagPairWithoutQuotedValue() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("[Event Casual]") }

    exception.message.orEmpty() shouldContain "expected '\"'"
  }

  @Test
  fun rejectsTagPairWithoutClosingBracket() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("[Event \"A\" 1. e4 *") }

    exception.message.orEmpty() shouldContain "expected ']'"
  }

  @Test
  fun rejectsTagPairWithoutName() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("[\"A\"] 1. e4 *") }

    exception.message.orEmpty() shouldContain "missing tag name"
  }

  @Test
  fun reportsAccuratePositionOnLaterLine() {
    val exception = shouldThrow<PgnParseException> { PgnParser.parse("1. e4 e5\n2. Nf3 garbage *") }

    exception.line shouldBe 2
    exception.column shouldBe 8
    exception.message.orEmpty() shouldContain "(line 2, column 8)"
  }

  @Test
  fun parsesLichessStudyLikeExport() {
    val pgn =
      """
      [Event "My Repertoire: Vienna"]
      [Site "https://lichess.org/study/aaaabbbb/ccccdddd"]
      [Result "*"]
      [Variant "Standard"]
      [ECO "C29"]
      [Opening "Vienna Game"]
      [UTCDate "2024.01.15"]
      [UTCTime "12.00.00"]

      1. e4 e5 2. Nc3 Nf6 { The main move. } 3. f4 d5 (3... exf4 4. e5 { White is better } 4... Ng8 (4... Qe7 5. Qe2)) 4. fxe5 Nxe4 5. Qf3 $1 *

      [Event "My Repertoire: London"]
      [Site "https://lichess.org/study/aaaabbbb/eeeeffff"]
      [Result "*"]

      1. d4 d5 2. Bf4 Nf6 3. e3 e6 4. Nf3 Bd6 5. Bg3 O-O *
      """
        .trimIndent()

    val games = PgnParser.parse(pgn)

    games.shouldHaveSize(2)
    games[0].headers["Opening"] shouldBe "Vienna Game"
    mainlineOf(games[0]) shouldBe
      listOf("e4", "e5", "Nc3", "Nf6", "f4", "d5", "fxe5", "Nxe4", "Qf3")
    val f4 = games[0].moves[0].children[0].children[0].children[0].children[0]
    f4.san shouldBe "f4"
    f4.children.map { it.san } shouldBe listOf("d5", "exf4")
    val e5Push = f4.children[1].children.single()
    e5Push.san shouldBe "e5"
    e5Push.children.map { it.san } shouldBe listOf("Ng8", "Qe7")
    games[1].headers["Event"] shouldBe "My Repertoire: London"
    mainlineOf(games[1]) shouldBe
      listOf("d4", "d5", "Bf4", "Nf6", "e3", "e6", "Nf3", "Bd6", "Bg3", "O-O")
  }
}

/** Walks the first child chain of the game and returns the mainline SAN moves. */
private fun mainlineOf(game: PgnGame): List<String> {
  val moves = mutableListOf<String>()
  var node = game.moves.firstOrNull()
  while (node != null) {
    moves.add(node.san)
    node = node.children.firstOrNull()
  }
  return moves
}
