package proj.memorchess.axl.core.data

/** Implementation of [IStoredMove] that can be used in core. */
data class StoredMove(
  private val origin: PositionKey,
  private val destination: PositionKey,
  override val move: String,
  override var isGood: Boolean = true,
) : IStoredMove {
  override fun getOrigin(): PositionKey {
    return origin
  }

  override fun getDestination(): PositionKey {
    return destination
  }
}
