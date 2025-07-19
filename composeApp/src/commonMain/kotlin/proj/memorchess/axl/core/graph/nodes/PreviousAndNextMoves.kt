package proj.memorchess.axl.core.graph.nodes

import proj.memorchess.axl.core.data.StoredMove

/**
 * Holds the previous and next moves for a chess position, along with the minimum depth at which we
 * can find those moves.
 *
 * @property previousMoves Map of previous moves (keyed by move string).
 * @property nextMoves Map of next moves (keyed by move string).
 * @property depth The minimum depth at which we can find these moves. depth.
 */
data class PreviousAndNextMoves(
  val previousMoves: MutableMap<String, StoredMove>,
  val nextMoves: MutableMap<String, StoredMove>,
  var depth: Int,
) {
  /** Creates an empty [PreviousAndNextMoves] instance at depth `0`. */
  constructor() : this(mutableMapOf(), mutableMapOf(), 0)

  /**
   * Creates an empty [PreviousAndNextMoves] instance at a specific depth.
   *
   * @param depth The depth to create the instance at.
   */
  constructor(depth: Int) : this(mutableMapOf(), mutableMapOf(), depth)

  /**
   * Creates a [PreviousAndNextMoves] instance from a collection of previous and next moves at depth
   * `0`.
   *
   * @param previousMoves The collection of previous moves.
   * @param nextMoves The collection of next moves.
   */
  constructor(
    previousMoves: Collection<StoredMove>,
    nextMoves: Collection<StoredMove>,
  ) : this(
    previousMoves.associateBy { it.move }.toMutableMap(),
    nextMoves.associateBy { it.move }.toMutableMap(),
    0,
  )

  /**
   * Creates a [PreviousAndNextMoves] instance from a collection of previous and next moves at a
   * specific depth.
   *
   * @param previousMoves The collection of previous moves.
   * @param nextMoves The collection of next moves.
   * @param depth The depth to create the instance at.
   */
  constructor(
    previousMoves: Collection<StoredMove>,
    nextMoves: Collection<StoredMove>,
    depth: Int,
  ) : this(
    previousMoves.associateBy { it.move }.toMutableMap(),
    nextMoves.associateBy { it.move }.toMutableMap(),
    depth,
  )

  /**
   * Adds a move to the previousMoves map.
   *
   * @param move The move to add.
   * @return The previous move associated with the key, or null if none existed.
   */
  fun addPreviousMove(move: StoredMove): StoredMove? {
    return previousMoves.put(move.move, move)
  }

  /**
   * Adds a move to the nextMoves map.
   *
   * @param move The move to add.
   * @return The previous move associated with the key, or null if none existed.
   */
  fun addNextMove(move: StoredMove): StoredMove? {
    return nextMoves.put(move.move, move)
  }

  /** Sets the isGood property for all previous moves. */
  fun setPreviousMovesAsGood() {
    previousMoves.values.forEach { it.isGood = true }
  }

  /**
   * Sets the isGood property for all previous moves.
   *
   * Please note that if a move was already marked as good, it will not be changed.
   */
  fun setPreviousMovesAsBadIfNotMarked() {
    previousMoves.values.forEach { it.isGood = it.isGood ?: false }
  }

  fun filterValidMoves(): PreviousAndNextMoves {
    val validPreviousMoves = previousMoves.filter { it.value.isGood != null }
    val validNextMoves = nextMoves.filter { it.value.isGood != null }
    return PreviousAndNextMoves(validPreviousMoves.values, validNextMoves.values, depth)
  }
}
