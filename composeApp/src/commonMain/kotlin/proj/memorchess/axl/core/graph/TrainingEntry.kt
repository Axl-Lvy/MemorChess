package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.PreviousAndNextDate

/**
 * Lightweight reference to a trainable position with its training schedule.
 *
 * Unlike [DataNode][proj.memorchess.axl.core.data.DataNode], this does not hold a live reference to
 * graph edges. It only stores the scheduling metadata needed by [TrainingSchedule].
 *
 * @property positionKey The position to train.
 * @property trainingDate When this position was last trained and when it should be trained next.
 */
data class TrainingEntry(val positionKey: PositionKey, val trainingDate: PreviousAndNextDate)
