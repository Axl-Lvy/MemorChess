package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove

/**
 * Persistence DTO holding the previous and next moves of one position, keyed by move string. The
 * runtime graph uses [Node] and [Edge] instead.
 */
data class PreviousAndNextMoves(
  val previousMoves: Map<String, DataMove> = emptyMap(),
  val nextMoves: Map<String, DataMove> = emptyMap(),
) {

  /** Creates an instance from collections of previous and next moves. */
  constructor(
    previousMoves: Collection<DataMove>,
    nextMoves: Collection<DataMove>,
  ) : this(previousMoves.associateBy { it.move }, nextMoves.associateBy { it.move })

  /** Returns a new instance containing only non deleted moves. */
  fun filterNotDeleted(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values.filter { !it.isDeleted },
      nextMoves.values.filter { !it.isDeleted },
    )
}
