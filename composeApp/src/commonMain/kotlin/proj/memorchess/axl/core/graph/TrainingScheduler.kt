package proj.memorchess.axl.core.graph

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.ReviewGrade
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm

/**
 * Spaced repetition driver for the opening tree.
 *
 * Reads the cached graph through [TreeStore.current] to enumerate trainable positions, asks
 * [SchedulingAlgorithm] for the next state after a review, and writes the result back through
 * [TreeStore.updateCardState]. Holds no graph state of its own.
 *
 * A position is trainable when it has at least one outgoing edge marked as good.
 */
class TrainingScheduler(
  private val treeStore: TreeStore,
  private val algorithm: SchedulingAlgorithm,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

  /**
   * Returns the [TrainingEntry] with the smallest depth that is due on or before [day], or `null`
   * when there is nothing to train. The entry is **not** removed from the schedule: removal is
   * driven by writing a future due date through [grade].
   */
  fun nextDue(day: LocalDate = DateUtil.today()): TrainingEntry? =
    trainableEntries(day).minByOrNull { treeStore.current().getDepth(it.positionKey) }

  /**
   * Returns a trainable entry reachable from one of [position]'s outgoing edges, due on or before
   * [day]. Picking such an entry preserves the natural opening order during a session.
   */
  fun nextAfter(position: PositionKey, day: LocalDate = DateUtil.today()): TrainingEntry? {
    val node = treeStore.current().get(position) ?: return null
    val reachable = node.outgoing.values.map { it.to }.toSet()
    return trainableEntries(day).firstOrNull { it.positionKey in reachable }
  }

  /** Counts trainable entries due on or before [day]. */
  fun pendingCount(day: LocalDate = DateUtil.today()): Int = trainableEntries(day).count()

  /**
   * Persists the result of a review for [position]. Computes the next [CardState][CardState]
   * through [SchedulingAlgorithm] and stores it through [TreeStore].
   */
  suspend fun grade(position: PositionKey, grade: ReviewGrade) {
    val node = treeStore.current().get(position) ?: return
    val now = DateUtil.now()
    val nextState = algorithm.schedule(node.cardState, grade, now)
    treeStore.updateCardState(position, nextState)
  }

  private fun trainableEntries(day: LocalDate): Sequence<TrainingEntry> =
    treeStore.current().snapshot().values.asSequence().mapNotNull { node ->
      val hasGoodOutgoing = node.outgoing.values.any { it.isGood == true && !it.isDeleted }
      if (!hasGoodOutgoing) return@mapNotNull null
      val due = node.cardState.dueLocalDate(timeZone)
      if (due > day) null else TrainingEntry(node.positionKey, node.cardState)
    }

  /**
   * Number of calendar days from [DateUtil.today] until [entry]'s due date, clamped to zero. Kept
   * for backwards compatibility with the legacy TrainingBoardPage day counter.
   */
  fun daysUntilDue(entry: TrainingEntry): Int {
    val today = DateUtil.today()
    val due = entry.cardState.dueLocalDate(timeZone)
    return today.daysUntil(due).coerceAtLeast(0)
  }
}
