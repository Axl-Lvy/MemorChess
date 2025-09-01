package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

/** Entity representing a node with its associated moves. */
internal data class NodeWithMoves(
  @Embedded val node: NodeEntity,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "destination")
  val previousMoves: List<MoveEntity>,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "origin")
  val nextMoves: List<MoveEntity>,
) {
  fun toStoredNode(): DataNode {
    return DataNode(
      PositionIdentifier(node.fenRepresentation),
      PreviousAndNextMoves(
        previousMoves.filter { !it.isDeleted }.map { it.toStoredMove() },
        nextMoves.filter { !it.isDeleted }.map { it.toStoredMove() },
        node.depth,
      ),
      PreviousAndNextDate(node.lastTrainedDate, node.nextTrainedDate),
      node.updatedAt,
      node.isDeleted,
    )
  }

  companion object {
    fun convertToEntity(dataNode: DataNode): NodeWithMoves {
      return NodeWithMoves(
        NodeEntity(
          dataNode.positionIdentifier.fenRepresentation,
          dataNode.previousAndNextTrainingDate.previousDate,
          dataNode.previousAndNextTrainingDate.nextDate,
          dataNode.previousAndNextMoves.depth,
          dataNode.isDeleted,
          dataNode.updatedAt,
        ),
        dataNode.previousAndNextMoves.previousMoves.map { MoveEntity.convertToEntity(it.value) },
        dataNode.previousAndNextMoves.nextMoves.map { MoveEntity.convertToEntity(it.value) },
      )
    }
  }
}
