package proj.memorchess.axl.core.engine

/** Thrown when an attempted move is not legal in the current position. */
class IllegalMoveException(override val message: String?, override val cause: Throwable?) :
  RuntimeException(message, cause) {
  constructor(message: String?) : this(message, null)
}
