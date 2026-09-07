package proj.memorchess.axl.core.data

/**
 * One tagged edge of a repertoire: a live [DataEdgeRepertoireTag] joined onto the [DataMove] it
 * tags, carrying the SAN the tag row itself does not.
 */
data class TaggedEdge(val origin: PositionKey, val destination: PositionKey, val move: String)
