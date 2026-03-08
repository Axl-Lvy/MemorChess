package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataMove

/**
 * Mutable graph-resident version of [PreviousAndNextMoves].
 *
 * Maps are mutable internally but exposed as read-only views. All mutations go through explicit
 * methods. Use [toImmutable] to obtain a snapshot safe for persistence or equality.
 */
class MutablePreviousAndNextMoves(
  previousMoves: Map<String, DataMove> = emptyMap(),
  nextMoves: Map<String, DataMove> = emptyMap(),
) {

  private val _previousMoves: MutableMap<String, DataMove> = previousMoves.toMutableMap()
  private val _nextMoves: MutableMap<String, DataMove> = nextMoves.toMutableMap()

  /** Read-only view of previous moves (keyed by move string). */
  val previousMoves: Map<String, DataMove>
    get() = _previousMoves

  /** Read-only view of next moves (keyed by move string). */
  val nextMoves: Map<String, DataMove>
    get() = _nextMoves

  // --- Mutation methods ---

  /**
   * Adds a move to the previousMoves map.
   *
   * @param move The move to add.
   * @return The previous move associated with the key, or null if none existed.
   */
  fun addPreviousMove(move: DataMove): DataMove? = _previousMoves.put(move.move, move)

  /**
   * Adds a move to the nextMoves map.
   *
   * @param move The move to add.
   * @return The previous move associated with the key, or null if none existed.
   */
  fun addNextMove(move: DataMove): DataMove? = _nextMoves.put(move.move, move)

  /**
   * Gets the next move for the given key, or adds the default value if absent.
   *
   * @param key The move string key.
   * @param defaultValue Factory for the default [DataMove].
   * @return The existing or newly inserted [DataMove].
   */
  fun getOrAddNextMove(key: String, defaultValue: () -> DataMove): DataMove =
    _nextMoves.getOrPut(key, defaultValue)

  /** Removes a specific previous move by move string. */
  fun removePreviousMove(moveString: String) {
    _previousMoves.remove(moveString)
  }

  /** Clears all next moves. */
  fun clearNextMoves() {
    _nextMoves.clear()
  }

  /**
   * Updates a specific next move entry. Used to propagate isGood changes from the destination's
   * previousMoves back to this origin's nextMoves.
   *
   * @param move The updated move to replace the existing entry with the same key.
   */
  fun updateNextMove(move: DataMove) {
    if (_nextMoves.containsKey(move.move)) {
      _nextMoves[move.move] = move
    }
  }

  /** Sets the isGood property to `true` for all previous moves. */
  fun setPreviousMovesAsGood() {
    _previousMoves.replaceAll { _, move -> move.copy(isGood = true) }
  }

  /**
   * Sets the isGood property to `false` for previous moves that are not already marked.
   *
   * If a move was already marked (good or bad), it will not be changed.
   */
  fun setPreviousMovesAsBadIfNotMarked() {
    _previousMoves.replaceAll { _, move -> move.copy(isGood = move.isGood ?: false) }
  }

  // --- Snapshot methods ---

  /** Creates an immutable snapshot for persistence or equality. */
  fun toImmutable(): PreviousAndNextMoves =
    PreviousAndNextMoves(_previousMoves.toMap(), _nextMoves.toMap())

  /** Returns an immutable snapshot containing only moves where [DataMove.isGood] is not null. */
  fun filterValidMoves(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      _previousMoves.values.filter { it.isGood != null },
      _nextMoves.values.filter { it.isGood != null },
    )

  /** Returns an immutable snapshot containing only non-deleted moves. */
  fun filterNotDeleted(): PreviousAndNextMoves =
    PreviousAndNextMoves(
      _previousMoves.values.filter { !it.isDeleted },
      _nextMoves.values.filter { !it.isDeleted },
    )
}
