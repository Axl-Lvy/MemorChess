package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation

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
      previousMoves.map { it.toStoredMove() }.toMutableList(),
      nextMoves.map { it.toStoredMove() }.toMutableList(),
      node.lastTrainedDate,
      node.nextTrainedDate,
    )
  }

  companion object {
    fun convertToEntity(storedNode: IStoredNode): NodeWithMoves {
      return NodeWithMoves(
        NodeEntity(
          storedNode.positionKey.fenRepresentation,
          storedNode.lastTrainedDate,
          storedNode.nextTrainedDate,
        ),
        storedNode.previousMoves.map { MoveEntity.convertToEntity(it) },
        storedNode.nextMoves.map { MoveEntity.convertToEntity(it) },
      )
    }
  }
}
