package proj.memorchess.axl.core.engine

/** The two players / piece colors in a chess game. */
enum class Player {
  WHITE,
  BLACK;

  /** The other player. */
  val opponent: Player
    get() = if (this == WHITE) BLACK else WHITE
}
