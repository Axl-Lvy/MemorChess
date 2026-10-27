package proj.memorchess.axl.core.graph

/**
 * Effective marking of a move: [ownIsGood] wins, else good when any of [installMarkings] is good.
 * Returns `null` when nothing holds the move.
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
