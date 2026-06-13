package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
 *
 * Two daily caps bound a session, both counted on distinct positions within the calendar day in
 * [timeZone]:
 * - [maxNewMovesPerDay] caps positions whose very first review happens today, counted through
 *   [CardState.firstReview][proj.memorchess.axl.core.scheduling.CardState.firstReview];
 * - [maxTotalMovesPerDay] caps everything trained today, counted through
 *   [CardState.lastReview][proj.memorchess.axl.core.scheduling.CardState.lastReview].
 *
 * Cards mid learning steps are exempt: like Anki, a card that entered the session finishes its
 * sub-day steps even when the caps are reached. The counts derive from the card states on every
 * call, so they reset at local midnight on their own. Training ahead (passing a future [day]) gets
 * a fresh budget for that day by design, matching Anki's study-ahead behavior.
 *
 * The caps are suppliers rather than flat values so a settings change takes effect immediately on
 * the long lived singleton; defaults are unlimited so that direct construction stays usable without
 * any configuration wiring.
 */
class TrainingScheduler(
  private val treeStore: TreeStore,
  private val algorithm: SchedulingAlgorithm,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val maxNewMovesPerDay: () -> Int = { Int.MAX_VALUE },
  private val maxTotalMovesPerDay: () -> Int = { Int.MAX_VALUE },
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
    val candidates = cappedCandidates(day)
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
    return cappedCandidates(day).firstOrNull { it.positionKey in reachable }
  }

  /**
   * Counts entries still to train for [day]: review/new cards due plus any in-session card. The
   * count honors the daily caps, so the UI shows what the trainer will actually serve.
   */
  fun pendingCount(day: LocalDate = DateUtil.today()): Int = cappedCandidates(day).size

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
   * The candidates for [day] after applying the daily caps.
   *
   * In-session learning cards are always kept so they can finish their sub-day steps. Due review
   * cards consume the total budget first, smallest depth first; due new cards then take whatever is
   * left of both the new card budget and the total budget, in introduction order.
   */
  private fun cappedCandidates(day: LocalDate): List<TrainingEntry> {
    val tree = treeStore.current()
    val cardStates = tree.snapshot().values.map { it.cardState }
    val introducedToday = cardStates.count { it.firstReview.isOn(day) }
    val trainedToday = cardStates.count { it.lastReview.isOn(day) }
    val newRemaining = (maxNewMovesPerDay() - introducedToday).coerceAtLeast(0)
    val totalRemaining = (maxTotalMovesPerDay() - trainedToday).coerceAtLeast(0)

    val all = candidates(day).toList()
    val inSession = all.filter { it.cardState.isInSession() }
    val reviews =
      all
        .filter { !it.cardState.isInSession() && it.cardState.phase == CardPhase.REVIEW }
        .sortedBy { tree.getDepth(it.positionKey) }
        .take(totalRemaining)
    val newBudget = minOf(newRemaining, totalRemaining - reviews.size).coerceAtLeast(0)
    val newCards =
      if (newBudget == 0) emptyList()
      else {
        val order = tree.introductionOrder()
        all
          .filter { it.cardState.phase == CardPhase.NEW }
          .sortedBy { order[it.positionKey] ?: Int.MAX_VALUE }
          .take(newBudget)
      }
    return inSession + reviews + newCards
  }

  private fun Instant?.isOn(day: LocalDate): Boolean =
    this != null && toLocalDateTime(timeZone).date == day

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
