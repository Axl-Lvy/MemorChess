package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil

/**
 * Manages training schedule for spaced-repetition learning.
 *
 * Stores only scheduling metadata ([TrainingEntry]) keyed by day. Does **not** hold live references
 * to graph edges or [DataNode][proj.memorchess.axl.core.data.DataNode] objects, avoiding shared
 * mutable state with [OpeningTree].
 */
class TrainingSchedule(private val openingTree: OpeningTree) {

  private val entriesByDay = mutableMapOf<Int, MutableMap<PositionKey, TrainingEntry>>()

  /**
   * Registers a position for training. Does **not** modify the [OpeningTree].
   *
   * @param entry The training entry to schedule.
   */
  fun addEntry(entry: TrainingEntry) {
    val daysUntil = DateUtil.daysUntil(entry.trainingDate.nextDate)
    entriesByDay.getOrPut(daysUntil) { mutableMapOf() }[entry.positionKey] = entry
  }

  /**
   * Picks the minimum-depth entry scheduled for the given day and removes it from the schedule.
   *
   * Depth is looked up from [OpeningTree] at query time, so it is always fresh.
   *
   * @param day The day offset to query.
   * @return The entry with the minimum depth, or null if none scheduled.
   */
  fun getEntryFromDay(day: Int): TrainingEntry? {
    val candidates = entriesByDay[day] ?: return null
    val position = candidates.keys.minByOrNull { openingTree.getDepth(it) } ?: return null
    return candidates.remove(position)
  }

  /**
   * Gets a trainable entry reachable from the given position's next moves.
   *
   * @param day The day offset to query.
   * @param positionKey The position whose children to check.
   * @return A reachable entry, or null if none found.
   */
  fun getEntryAfterPosition(day: Int, positionKey: PositionKey): TrainingEntry? {
    val todayEntries = entriesByDay[day] ?: return null
    val nextPositions =
      openingTree.get(positionKey)?.nextMoves?.values?.map { it.destination } ?: emptyList()
    for (candidate in nextPositions) {
      val entry = todayEntries.remove(candidate)
      if (entry != null) return entry
    }
    return null
  }

  /** Returns the number of entries scheduled for training on the given day. */
  fun getNumberOfNodesToTrain(day: Int): Int = entriesByDay[day]?.size ?: 0

  /** Clears all scheduled entries. */
  fun clear() {
    entriesByDay.clear()
  }
}
