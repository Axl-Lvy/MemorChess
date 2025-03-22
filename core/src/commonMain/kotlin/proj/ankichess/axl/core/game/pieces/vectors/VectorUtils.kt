package proj.ankichess.axl.core.game.pieces.vectors

object VectorUtils {

  /** Vectors to move straight. */
  val STRAIGHT_VECTORS = setOf(Pair(0, 1), Pair(0, -1), Pair(-1, 0), Pair(1, 0))

  /** Vectors to move in diagonal */
  val DIAG_VECTORS = setOf(Pair(-1, 1), Pair(-1, -1), Pair(1, -1), Pair(1, 1))

  /** Vectors to move like a knight */
  val KNIGHT_VECTORS =
    setOf(
      Pair(1, 2),
      Pair(2, 1),
      Pair(-1, 2),
      Pair(2, -1),
      Pair(1, -2),
      Pair(-2, 1),
      Pair(-1, -2),
      Pair(-2, -1),
    )

  /** Vectors to move straight and in diagonal */
  val ALL_VECTORS = STRAIGHT_VECTORS + DIAG_VECTORS

  /**
   * Add a vector to coordinates.
   *
   * @param coords The coordinates.
   * @param v The vector.
   * @return [coords] translated by [v]
   */
  fun addVector(coords: Pair<Int, Int>, v: Pair<Int, Int>): Pair<Int, Int>? {
    val newX = coords.first + v.first
    if (!validateCoordinates(newX)) {
      return null
    }
    val newY = coords.second + v.second
    if (!validateCoordinates(newY)) {
      return null
    }
    return Pair(newX, newY)
  }

  /**
   * Tells if the coordinate correspond to a valid tile coordinate.
   *
   * @param n The coordinate to validate.
   * @return True iif the coordinate is valid.
   */
  private fun validateCoordinates(n: Int): Boolean {
    return n in 0..7
  }
}