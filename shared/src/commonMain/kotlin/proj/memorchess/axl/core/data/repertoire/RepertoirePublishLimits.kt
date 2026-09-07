package proj.memorchess.axl.core.data.repertoire

/**
 * Repertoire publish caps and shape rules shared by `:server` (which enforces them) and
 * `:composeApp` (which pre checks them before submitting, so a client side rejection can never
 * drift from what the server actually enforces).
 */
object RepertoirePublishLimits {

  /** Largest payload accepted for one repertoire version, checked before parsing. */
  const val MAX_REPERTOIRE_PAYLOAD_BYTES: Int = 512 * 1024

  /** Largest number of distinct `(position, move)` edges accepted in one repertoire. */
  const val MAX_REPERTOIRE_MOVES: Int = 5_000

  /** Shortest id accepted. */
  const val MIN_ID_LENGTH: Int = 3

  /** Longest id accepted. */
  const val MAX_ID_LENGTH: Int = 64

  /** Longest title accepted. Shown in the public catalog, so an unbounded one is a footgun. */
  const val MAX_TITLE_LENGTH: Int = 200

  /** Longest description accepted, for the same reason as [MAX_TITLE_LENGTH]. */
  const val MAX_DESCRIPTION_LENGTH: Int = 2_000

  /**
   * Deepest line accepted, in plies from the starting position. A real opening repertoire never
   * needs more than a few dozen plies down any single line. This bounds pathological input (for
   * example a short sequence of legal moves repeated many times) that could otherwise overflow a
   * parser's call stack before [MAX_REPERTOIRE_MOVES] is ever reached.
   */
  const val MAX_PLY_DEPTH: Int = 200

  /** Shape a repertoire id (a catalog slug) must match. */
  val ID_PATTERN: Regex = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

  /** Describes what is wrong with [id] as a catalog slug, or `null` when it is well formed. */
  fun idProblem(id: String): String? =
    when {
      id.length < MIN_ID_LENGTH || id.length > MAX_ID_LENGTH ->
        "id must be $MIN_ID_LENGTH to $MAX_ID_LENGTH characters, was ${id.length}"
      !ID_PATTERN.matches(id) -> "id must be lowercase letters, digits and single hyphens, was '$id'"
      else -> null
    }
}
