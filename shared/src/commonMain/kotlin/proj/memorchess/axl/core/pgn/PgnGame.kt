package proj.memorchess.axl.core.pgn

/**
 * A single game (or repertoire chapter) parsed from a PGN document.
 *
 * @property headers the tag pairs of the game, in document order.
 * @property moves the moves playable from the starting position. The first element is the mainline
 *   first move; any other elements are alternative first moves introduced by variations.
 */
data class PgnGame(val headers: Map<String, String>, val moves: List<PgnMoveNode>)

/**
 * A node of the parsed move tree.
 *
 * @property san the move in Standard Algebraic Notation, without check or checkmate markers.
 *   Promotion notation (such as `e8=Q`) is preserved and castling is normalized to `O-O` and
 *   `O-O-O`.
 * @property children the continuations after this move. The first child is the mainline
 *   continuation; any other children are alternatives introduced by variations.
 */
data class PgnMoveNode(val san: String, val children: List<PgnMoveNode>)
