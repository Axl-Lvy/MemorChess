package proj.memorchess.axl.core.graph

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardPhase
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
   * Returns the next [TrainingEntry] to train, or `null` when there is nothing left for [day].
   *
   * Selection runs in four tiers so that the in-session learning loop drains in step order without
   * starving the day's review queue:
   * 1. **Ready learning cards** — a [proj.memorchess.axl.core.scheduling.CardPhase.LEARNING] or
   *    `RELEARNING` card whose sub-day due moment has arrived. The earliest due wins, so a card on
   *    a one minute step surfaces before one on a ten minute step.
   * 2. **Review cards** due on or before [day] — picked by smallest graph depth to follow the
   *    natural opening order.
   * 3. **New cards** due on or before [day] — picked by [OpeningTree.introductionOrder], so a whole
   *    line is introduced before the next branch starts, in the order the lines were added to the
   *    repertoire.
   * 4. **Pending learning cards** — sub-day cards not yet due. Falling through to these guarantees
   *    a just-failed card always resurfaces in the same session rather than ending it early, again
   *    earliest-due first.
   *
   * The entry is **not** removed from the schedule: a card leaves the session only when [grade]
   * graduates it to a day grained review interval.
   */
  fun nextDue(day: LocalDate = DateUtil.today()): TrainingEntry? {
    val now = DateUtil.now()
    val candidates = candidates(day).toList()
    if (candidates.isEmpty()) return null

    val readyLearning = candidates.filter {
      it.cardState.isInSession() && it.cardState.dueDate <= now
    }
    if (readyLearning.isNotEmpty()) return readyLearning.minByOrNull { it.cardState.dueDate }

    val reviewLike = candidates.filterNot { it.cardState.isInSession() }
    val reviews = reviewLike.filter { it.cardState.phase == CardPhase.REVIEW }
    if (reviews.isNotEmpty()) {
      return reviews.minByOrNull { treeStore.current().getDepth(it.positionKey) }
    }

    val newCards = reviewLike.filter { it.cardState.phase == CardPhase.NEW }
    if (newCards.isNotEmpty()) {
      val order = treeStore.current().introductionOrder()
      return newCards.minByOrNull { order[it.positionKey] ?: Int.MAX_VALUE }
    }

    return candidates.minByOrNull { it.cardState.dueDate }
  }

  /**
   * Returns a trainable entry reachable from one of [position]'s outgoing edges. Picking such an
   * entry preserves the natural opening order during a session.
   */
  fun nextAfter(position: PositionKey, day: LocalDate = DateUtil.today()): TrainingEntry? {
    val node = treeStore.current().get(position) ?: return null
    val reachable = node.outgoing.values.map { it.to }.toSet()
    return candidates(day).firstOrNull { it.positionKey in reachable }
  }

  /** Counts entries still to train for [day]: review/new cards due plus any in-session card. */
  fun pendingCount(day: LocalDate = DateUtil.today()): Int = candidates(day).count()

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

  /**
   * Positions eligible to train for [day]. A position qualifies when it has at least one good
   * outgoing edge and is either an in-session learning card (always eligible until it graduates) or
   * a review/new card due on or before [day].
   */
  private fun candidates(day: LocalDate): Sequence<TrainingEntry> =
    treeStore.current().snapshot().values.asSequence().mapNotNull { node ->
      val hasGoodOutgoing = node.outgoing.values.any { it.isGood == true && !it.isDeleted }
      if (!hasGoodOutgoing) return@mapNotNull null
      val card = node.cardState
      val eligible = card.isInSession() || card.dueLocalDate(timeZone) <= day
      if (!eligible) null else TrainingEntry(node.positionKey, card)
    }
}
