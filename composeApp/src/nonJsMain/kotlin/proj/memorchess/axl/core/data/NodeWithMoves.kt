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
      nextMoves.map { it.toStoredMove() },
      previousMoves.map { it.toStoredMove() },
    )
  }

  companion object {
    fun convertToEntity(storedNode: IStoredNode): NodeWithMoves {
      return NodeWithMoves(
        NodeEntity(storedNode.positionKey.fenRepresentation),
        storedNode.previousMoves.map { MoveEntity.convertToEntity(it) },
        storedNode.nextMoves.map { MoveEntity.convertToEntity(it) },
      )
    }
  }
}
