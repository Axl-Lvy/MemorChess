package proj.memorchess.axl.core.data

import proj.memorchess.axl.core.date.PreviousAndNextDate

data class UnlinkedStoredNode(
  val positionIdentifier: PositionIdentifier,
  val previousAndNextTrainingDate: PreviousAndNextDate,
  val depth: Int,
  val isDeleted: Boolean,
)
