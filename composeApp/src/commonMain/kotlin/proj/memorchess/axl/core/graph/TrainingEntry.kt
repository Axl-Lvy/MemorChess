package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Lightweight reference to a trainable position with its scheduling state.
 *
 * Holds neither edges nor a live tree reference. The [TrainingScheduler] enriches the entry on
 * demand by looking the position up in the cached [OpeningTree].
 *
 * @property positionKey The position to train.
 * @property cardState Scheduling state for the position. Only its
 *   [dueDate][proj.memorchess.axl.core.scheduling.CardState.dueDate] is consulted by
 *   [TrainingScheduler]; the rest is forwarded to whichever
 *   [proj.memorchess.axl.core.scheduling.SchedulingAlgorithm] runs the review.
 */
data class TrainingEntry(val positionKey: PositionKey, val cardState: CardState)
