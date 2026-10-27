package proj.memorchess.axl.core.data

/** A live [DataEdgeRepertoireTag] joined onto its [DataMove], carrying the SAN the tag lacks. */
data class TaggedEdge(val origin: PositionKey, val destination: PositionKey, val move: String)
