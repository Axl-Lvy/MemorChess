package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardState

/** Entity representing a node with its associated moves. */
data class NodeWithMoves(
  @Embedded val node: NodeEntity,
  @Relation(parentColumn = "positionKey", entityColumn = "destination")
  val previousMoves: List<MoveEntity>,
  @Relation(parentColumn = "positionKey", entityColumn = "origin") val nextMoves: List<MoveEntity>,
) {
  fun toStoredNode(): DataNode {
    return DataNode(
      PositionKey(node.positionKey),
      PreviousAndNextMoves(
        previousMoves.filter { !it.isDeleted }.map { it.toStoredMove() },
        nextMoves.filter { !it.isDeleted }.map { it.toStoredMove() },
      ),
      CardState(
        dueDate = node.dueDate,
        lastReview = node.lastReview,
        stability = node.stability,
        difficulty = node.difficulty,
        reps = node.reps,
        lapses = node.lapses,
      ),
      node.depth,
      node.updatedAt,
      node.isDeleted,
    )
  }

  companion object {
    fun convertToEntity(dataNode: DataNode): NodeWithMoves {
      val card = dataNode.cardState
      return NodeWithMoves(
        NodeEntity(
          positionKey = dataNode.positionKey.value,
          dueDate = card.dueDate,
          lastReview = card.lastReview,
          stability = card.stability,
          difficulty = card.difficulty,
          reps = card.reps,
          lapses = card.lapses,
          depth = dataNode.depth,
          isDeleted = dataNode.isDeleted,
          updatedAt = dataNode.updatedAt,
        ),
        dataNode.previousAndNextMoves.previousMoves.map { MoveEntity.convertToEntity(it.value) },
        dataNode.previousAndNextMoves.nextMoves.map { MoveEntity.convertToEntity(it.value) },
      )
    }
  }
}
