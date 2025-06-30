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
      PositionKey(node.fenRepresentation),
      PreviousAndNextMoves(
        previousMoves.map { it.toStoredMove() },
        nextMoves.map { it.toStoredMove() },
      ),
      PreviousAndNextDate(node.lastTrainedDate, node.nextTrainedDate),
    )
  }

  companion object {
    fun convertToEntity(storedNode: IStoredNode): NodeWithMoves {
      return NodeWithMoves(
        NodeEntity(
          storedNode.positionKey.fenRepresentation,
          storedNode.previousAndNextTrainingDate.previousDate,
          storedNode.previousAndNextTrainingDate.nextDate,
        ),
        storedNode.previousAndNextMoves.previousMoves.map { MoveEntity.convertToEntity(it.value) },
        storedNode.previousAndNextMoves.nextMoves.map { MoveEntity.convertToEntity(it.value) },
      )
    }
  }
}
