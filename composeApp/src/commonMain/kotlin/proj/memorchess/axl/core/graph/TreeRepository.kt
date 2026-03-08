package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey

/** Interface for persisting the opening tree. */
interface TreeRepository {

  /** Loads all data from the source into the given [tree] and optional [trainingSchedule]. */
  suspend fun loadInto(tree: OpeningTree, trainingSchedule: TrainingSchedule? = null)

  /** Saves a node to the underlying store. */
  suspend fun saveNode(node: DataNode)

  /** Deletes a position from the underlying store. */
  suspend fun deletePosition(positionKey: PositionKey)

  /** Deletes a specific move from the underlying store. */
  suspend fun deleteMove(origin: PositionKey, move: String)
}
