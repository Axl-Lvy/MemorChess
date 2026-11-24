package proj.memorchess.axl.core.data

/**
 * What a recursive delete from one position would remove.
 *
 * @property positions Positions removed, the starting one included. Equals the walk's cap when the
 *   subtree is at least that large.
 * @property reviewedPositions How many of [positions] carry review history (reviewed at least once).
 */
data class PruneImpact(val positions: Int, val reviewedPositions: Int) {
  companion object {
    /** Impact of a delete that removes nothing. */
    val NONE: PruneImpact = PruneImpact(positions = 0, reviewedPositions = 0)
  }
}
