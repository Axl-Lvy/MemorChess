package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Lightweight reference to a trainable position with its scheduling state.
 *
 * Unlike [DataNode][proj.memorchess.axl.core.data.DataNode], this does not hold a live reference to
 * graph edges. It only stores the scheduling metadata needed by [TrainingSchedule].
 *
 * @property positionKey The position to train.
 * @property cardState Scheduling state for the position. Only its
 *   [dueDate][proj.memorchess.axl.core.scheduling.CardState.dueDate] is consulted by
 *   [TrainingSchedule]; the rest is forwarded to whichever
 *   [proj.memorchess.axl.core.scheduling.SchedulingAlgorithm] runs the review.
 */
data class TrainingEntry(val positionKey: PositionKey, val cardState: CardState)
