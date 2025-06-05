package proj.memorchess.axl.core.data

data class StoredMove(
  private val origin: PositionKey,
  private val destination: PositionKey,
  override val move: String,
  override val isGood: Boolean = true,
) : IStoredMove {
  override fun getOrigin(): PositionKey {
    return origin
  }

  override fun getDestination(): PositionKey {
    return destination
  }
}
