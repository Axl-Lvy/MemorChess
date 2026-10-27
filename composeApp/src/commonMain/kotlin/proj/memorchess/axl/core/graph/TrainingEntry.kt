package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardState

/** Trainable position with its scheduling state, holding neither edges nor a tree reference. */
data class TrainingEntry(val positionKey: PositionKey, val cardState: CardState)
