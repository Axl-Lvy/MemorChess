package proj.memorchess.axl.core.engine.moves

class IllegalMoveException(override val message: String?, override val cause: Throwable?) :
  RuntimeException(message, cause) {
  constructor(message: String?) : this(message, null)

  constructor(cause: Throwable?) : this(null, cause)

  constructor() : this(null, null)
}
