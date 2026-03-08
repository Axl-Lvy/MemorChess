package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove

/**
 * Immutable snapshot of the previous and next moves for a chess position.
 *
 * Safe to use in [data class][DataMove] equality, sets, and maps. For the mutable graph-resident
 * version, see [MutablePreviousAndNextMoves].
 *
 * @property previousMoves Map of previous moves (keyed by move string).
 * @property nextMoves Map of next moves (keyed by move string).
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

  /** Returns a new instance containing only non-deleted moves. */
  fun filterNotDeleted(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values.filter { !it.isDeleted },
      nextMoves.values.filter { !it.isDeleted },
    )

  /** Creates a mutable copy for use in the live graph. */
  fun toMutable(): MutablePreviousAndNextMoves =
    MutablePreviousAndNextMoves(previousMoves, nextMoves)
}
