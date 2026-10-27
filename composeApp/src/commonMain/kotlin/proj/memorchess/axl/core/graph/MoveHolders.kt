package proj.memorchess.axl.core.graph

/**
 * Effective marking of a move from its holders: the user's own marking [ownIsGood] wins, otherwise
 * the move is good when at least one of [installMarkings] says good, and bad otherwise. Returns
 * `null` when nothing holds the move.
 */
fun effectiveMarking(ownIsGood: Boolean?, installMarkings: Iterable<Boolean>): Boolean? {
  if (ownIsGood != null) return ownIsGood
  var effective: Boolean? = null
  for (marking in installMarkings) {
    if (marking) return true
    effective = false
  }
  return effective
}
