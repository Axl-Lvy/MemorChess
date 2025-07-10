package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/** Entity representing a node with its associated moves. */
data class NodeWithMoves(
  @Embedded val node: NodeEntity,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "destination")
  val previousMoves: List<MoveEntity>,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "origin")
  val nextMoves: List<MoveEntity>,
) {
  fun toStoredNode(): StoredNode {
    return StoredNode(
      PositionIdentifier(node.fenRepresentation),
      PreviousAndNextMoves(
        previousMoves.map { it.toStoredMove() },
        nextMoves.map { it.toStoredMove() },
        node.depth,
      ),
      PreviousAndNextDate(node.lastTrainedDate, node.nextTrainedDate),
    )
  }

  companion object {
    fun convertToEntity(storedNode: StoredNode): NodeWithMoves {
      return NodeWithMoves(
        NodeEntity(
          storedNode.positionIdentifier.fenRepresentation,
          storedNode.previousAndNextTrainingDate.previousDate,
          storedNode.previousAndNextTrainingDate.nextDate,
          storedNode.previousAndNextMoves.depth,
        ),
        storedNode.previousAndNextMoves.previousMoves.map { MoveEntity.convertToEntity(it.value) },
        storedNode.previousAndNextMoves.nextMoves.map { MoveEntity.convertToEntity(it.value) },
      )
    }
  }
}
