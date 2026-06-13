package proj.memorchess.axl.core.pgn

/**
 * Pure Kotlin parser for PGN documents.
 *
 * Supports tag pairs, SAN movetext with nested variations of arbitrary depth, brace comments
 * (anywhere, including inside variations), rest-of-line comments introduced by `;`, escape lines
 * starting with `%`, NAGs (`$1` style and annotation glyphs like `!?`), move numbers in any common
 * layout (`1.`, `1...`, glued or spaced), and result markers (`1-0`, `0-1`, `1/2-1/2`, `*`).
 *
 * The parser is liberal about whitespace and newline layout, so any standard export (for example a
 * lichess study chapter) parses without preprocessing. Malformed input is reported through
 * [PgnParseException] with line and column information.
 */
object PgnParser {

  /**
   * Parses a PGN document that may contain multiple games.
   *
   * Games are separated by result markers or by a new tag pair section after movetext. A
   * headers-only section (with or without a result marker) yields a game with empty
   * [PgnGame.moves]. Empty input yields an empty list.
   *
   * @param text the full PGN document.
   * @return the parsed games in document order.
   * @throws PgnParseException if the document is malformed.
   */
  fun parse(text: String): List<PgnGame> = Parser(text).parseAllGames()
}

/** Position-aware token produced by the lexer. */
private sealed interface Token {
  /** The 1-based line where the token starts. */
  val line: Int

  /** The 1-based column where the token starts. */
  val column: Int

  /** A tag pair such as `[Event "..."]`. */
  data class TagPair(
    val key: String,
    val value: String,
    override val line: Int,
    override val column: Int,
  ) : Token

  /** A SAN move, already normalized (castling letters, no check markers, no annotations). */
  data class Move(val san: String, override val line: Int, override val column: Int) : Token

  /** An opening parenthesis starting a variation. */
  data class VariationStart(override val line: Int, override val column: Int) : Token

  /** A closing parenthesis ending a variation. */
  data class VariationEnd(override val line: Int, override val column: Int) : Token

  /** A game result marker (`1-0`, `0-1`, `1/2-1/2`, `*`). */
  data class GameResult(override val line: Int, override val column: Int) : Token
}

/** Saved state of the enclosing line while a variation is being parsed. */
private class VariationFrame(
  val savedCurrent: MutableNode,
  val savedPrevious: MutableNode?,
  val line: Int,
  val column: Int,
)

/** Mutable tree node used during parsing, converted to [PgnMoveNode] when a game is finalized. */
private class MutableNode(val san: String) {
  val children = mutableListOf<MutableNode>()

  fun toImmutable(): PgnMoveNode = PgnMoveNode(san, children.map { it.toImmutable() })
}

/** Single-pass lexer and tree builder over a PGN document. */
private class Parser(private val text: String) {

  private var index = 0
  private var line = 1
  private var column = 1

  private val games = mutableListOf<PgnGame>()
  private var headers = linkedMapOf<String, String>()
  private var root = MutableNode("")
  private var current = root
  private var previous: MutableNode? = null
  private val variationStack = ArrayDeque<VariationFrame>()
  private var movetextStarted = false

  fun parseAllGames(): List<PgnGame> {
    while (true) {
      val token = nextToken() ?: break
      when (token) {
        is Token.TagPair -> handleTagPair(token)
        is Token.Move -> handleMove(token)
        is Token.VariationStart -> handleVariationStart(token)
        is Token.VariationEnd -> handleVariationEnd(token)
        is Token.GameResult -> finalizeGame()
      }
    }
    finalizeGame()
    return games
  }

  private fun handleTagPair(token: Token.TagPair) {
    if (movetextStarted) {
      finalizeGame()
    }
    headers[token.key] = token.value
  }

  private fun handleMove(token: Token.Move) {
    val node = MutableNode(token.san)
    current.children.add(node)
    previous = current
    current = node
    movetextStarted = true
  }

  private fun handleVariationStart(token: Token.VariationStart) {
    val branchPoint =
      previous
        ?: throw PgnParseException("Variation with no preceding move", token.line, token.column)
    variationStack.addLast(VariationFrame(current, previous, token.line, token.column))
    current = branchPoint
    previous = null
  }

  private fun handleVariationEnd(token: Token.VariationEnd) {
    val frame =
      variationStack.removeLastOrNull()
        ?: throw PgnParseException("Unmatched ')'", token.line, token.column)
    current = frame.savedCurrent
    previous = frame.savedPrevious
  }

  /** Emits the game accumulated so far (if any) and resets the per game state. */
  private fun finalizeGame() {
    val openVariation = variationStack.firstOrNull()
    if (openVariation != null) {
      throw PgnParseException("Unterminated variation", openVariation.line, openVariation.column)
    }
    if (headers.isNotEmpty() || root.children.isNotEmpty()) {
      games.add(PgnGame(headers.toMap(), root.children.map { it.toImmutable() }))
    }
    headers = linkedMapOf()
    root = MutableNode("")
    current = root
    previous = null
    movetextStarted = false
  }

  private fun peek(): Char? = if (index < text.length) text[index] else null

  private fun advance(): Char {
    val c = text[index]
    index++
    if (c == '\n') {
      line++
      column = 1
    } else {
      column++
    }
    return c
  }

  private fun skipRestOfLine() {
    while (true) {
      val c = peek() ?: return
      if (c == '\n') return
      advance()
    }
  }

  /** Returns the next significant token, or null at end of input. */
  private fun nextToken(): Token? {
    while (true) {
      val c = peek() ?: return null
      when {
        c.isWhitespace() || c == '\uFEFF' -> advance()
        column == 1 && c == '%' -> skipRestOfLine()
        c == ';' -> skipRestOfLine()
        c == '{' -> skipBraceComment()
        c == '}' -> throw PgnParseException("Unexpected '}'", line, column)
        c == '[' -> return readTagPair()
        c == '(' -> {
          val token = Token.VariationStart(line, column)
          advance()
          return token
        }
        c == ')' -> {
          val token = Token.VariationEnd(line, column)
          advance()
          return token
        }
        c == '$' -> skipNag()
        c == '!' || c == '?' -> skipAnnotationGlyphs()
        c == '*' -> {
          val token = Token.GameResult(line, column)
          advance()
          return token
        }
        else -> {
          val token = readWord()
          if (token != null) return token
        }
      }
    }
  }

  private fun skipBraceComment() {
    val startLine = line
    val startColumn = column
    advance()
    while (true) {
      val c = peek() ?: throw PgnParseException("Unterminated comment", startLine, startColumn)
      advance()
      if (c == '}') return
    }
  }

  private fun skipNag() {
    val startLine = line
    val startColumn = column
    advance()
    var digits = 0
    while (peek()?.isDigit() == true) {
      advance()
      digits++
    }
    if (digits == 0) {
      throw PgnParseException("Invalid NAG: '$' must be followed by digits", startLine, startColumn)
    }
  }

  private fun skipAnnotationGlyphs() {
    while (peek() == '!' || peek() == '?') {
      advance()
    }
  }

  private fun readTagPair(): Token.TagPair {
    val startLine = line
    val startColumn = column
    advance()
    skipSpacesInTag(startLine, startColumn)
    val key = buildString {
      while (true) {
        val c = peek() ?: break
        if (c.isLetterOrDigit() || c == '_') {
          append(advance())
        } else {
          break
        }
      }
    }
    if (key.isEmpty()) {
      throw PgnParseException("Invalid tag pair: missing tag name", startLine, startColumn)
    }
    skipSpacesInTag(startLine, startColumn)
    if (peek() != '"') {
      throw PgnParseException("Invalid tag pair: expected '\"'", line, column)
    }
    advance()
    val value = buildString {
      while (true) {
        val c = peek() ?: throw PgnParseException(UNTERMINATED_TAG_PAIR, startLine, startColumn)
        advance()
        when (c) {
          '"' -> return@buildString
          '\\' -> {
            val escaped =
              peek() ?: throw PgnParseException(UNTERMINATED_TAG_PAIR, startLine, startColumn)
            advance()
            append(escaped)
          }
          else -> append(c)
        }
      }
    }
    skipSpacesInTag(startLine, startColumn)
    if (peek() != ']') {
      throw PgnParseException("Invalid tag pair: expected ']'", line, column)
    }
    advance()
    return Token.TagPair(key, value, startLine, startColumn)
  }

  private fun skipSpacesInTag(startLine: Int, startColumn: Int) {
    while (true) {
      val c = peek() ?: throw PgnParseException(UNTERMINATED_TAG_PAIR, startLine, startColumn)
      if (c == ' ' || c == '\t') advance() else return
    }
  }

  /**
   * Reads a whitespace-delimited word and classifies it. Returns null when the word is
   * insignificant (a move number or continuation dots) so lexing continues.
   */
  private fun readWord(): Token? {
    val startLine = line
    val startColumn = column
    val word = buildString {
      while (true) {
        val c = peek() ?: break
        if (c.isWhitespace() || c in WORD_TERMINATORS) break
        append(advance())
      }
    }
    return classifyWord(word, startLine, startColumn)
  }

  private fun classifyWord(word: String, startLine: Int, startColumn: Int): Token? {
    if (word in RESULT_MARKERS) return Token.GameResult(startLine, startColumn)
    if (DOTS_ONLY_REGEX.matches(word)) return null
    if (MOVE_NUMBER_REGEX.matches(word)) return null
    val gluedMatch = GLUED_MOVE_REGEX.matchEntire(word)
    val moveWord = if (gluedMatch != null) gluedMatch.groupValues[1] else word
    return toMoveToken(moveWord, word, startLine, startColumn)
  }

  private fun toMoveToken(
    moveWord: String,
    originalWord: String,
    startLine: Int,
    startColumn: Int,
  ): Token.Move {
    val stripped = moveWord.trimEnd('!', '?', '+', '#')
    val san =
      when {
        stripped == "O-O-O" || stripped == "0-0-0" -> "O-O-O"
        stripped == "O-O" || stripped == "0-0" -> "O-O"
        SAN_REGEX.matches(stripped) -> stripped
        else -> throw PgnParseException("Invalid SAN token '$originalWord'", startLine, startColumn)
      }
    return Token.Move(san, startLine, startColumn)
  }

  private companion object {
    /** Error raised when a tag pair is left open at the end of the input. */
    const val UNTERMINATED_TAG_PAIR = "Unterminated tag pair"

    /** Characters that end a movetext word in addition to whitespace. */
    const val WORD_TERMINATORS = "(){}[];$"

    /** Decisive and drawn result markers; `*` is handled at the character level. */
    val RESULT_MARKERS = setOf("1-0", "0-1", "1/2-1/2")

    /** Standalone continuation dots, tolerated between a move number and its move. */
    val DOTS_ONLY_REGEX = Regex("""\.+""")

    /** A move number with optional trailing dots, such as `1`, `1.` or `1...`. */
    val MOVE_NUMBER_REGEX = Regex("""\d+\.*""")

    /** A move glued to its move number, such as `1.e4` or `1...e5`. */
    val GLUED_MOVE_REGEX = Regex("""\d+\.+(.+)""")

    /** A SAN piece move, pawn push or pawn capture, with optional promotion. */
    val SAN_REGEX = Regex("""[KQRBN][a-h]?[1-8]?x?[a-h][1-8]|[a-h](?:x[a-h])?[1-8](?:=[QRBN])?""")
  }
}
