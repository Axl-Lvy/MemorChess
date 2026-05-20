package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove

/**
 * Persistence DTO carrying the previous and next moves of a single position.
 *
 * This type lives at the [proj.memorchess.axl.core.data.DatabaseQueryManager] seam only. The
 * runtime graph uses [Node] and [Edge] instead. [TreeStore] is responsible for converting between
 * the two representations.
 *
 * @property previousMoves Map of incoming moves, keyed by move string.
 * @property nextMoves Map of outgoing moves, keyed by move string.
 */
data class PreviousAndNextMoves(
  val previousMoves: Map<String, DataMove> = emptyMap(),
  val nextMoves: Map<String, DataMove> = emptyMap(),
) {

  /**
   * Creates an instance from collections of previous and next moves.
   *
   * @param previousMoves The collection of previous moves.
   * @param nextMoves The collection of next moves.
   */
  constructor(
    previousMoves: Collection<DataMove>,
    nextMoves: Collection<DataMove>,
  ) : this(previousMoves.associateBy { it.move }, nextMoves.associateBy { it.move })

  /** Returns a new instance containing only moves where [DataMove.isGood] is not null. */
  fun filterValidMoves(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values.filter { it.isGood != null },
      nextMoves.values.filter { it.isGood != null },
    )

  /** Returns a new instance containing only non deleted moves. */
  fun filterNotDeleted(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values.filter { !it.isDeleted },
      nextMoves.values.filter { !it.isDeleted },
    )
}
