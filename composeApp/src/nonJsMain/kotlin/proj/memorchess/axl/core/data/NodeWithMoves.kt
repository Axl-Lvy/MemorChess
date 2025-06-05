package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation

data class NodeWithMoves(
  @Embedded val node: NodeEntity,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "destination")
  override val previousMoves: List<MoveEntity>,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "origin")
  override val nextMoves: List<MoveEntity>,
) : IStoredNode {

  override val positionKey: PositionKey
    get() = PositionKey(node.fenRepresentation)

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
