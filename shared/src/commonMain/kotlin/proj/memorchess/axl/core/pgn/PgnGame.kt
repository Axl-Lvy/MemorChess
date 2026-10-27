package proj.memorchess.axl.core.pgn

/**
 * A single game (or repertoire chapter) parsed from a PGN document.
 *
 * @property headers Tag pairs, in document order.
 * @property moves First moves; the first is the mainline, the others come from variations.
 */
data class PgnGame(val headers: Map<String, String>, val moves: List<PgnMoveNode>)

/**
 * A node of the parsed move tree.
 *
 * @property san SAN without check or mate markers, with castling normalized to `O-O` and `O-O-O`.
 * @property children Continuations; the first is the mainline, the others come from variations.
 */
data class PgnMoveNode(val san: String, val children: List<PgnMoveNode>)
