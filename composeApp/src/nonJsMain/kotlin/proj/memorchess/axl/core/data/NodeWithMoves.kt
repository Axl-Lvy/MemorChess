package proj.memorchess.axl.core.data

import androidx.room.Embedded
import androidx.room.Relation
import proj.memorchess.axl.core.date.PreviousAndNextDate

/** Entity representing a node with its associated moves. */
data class NodeWithMoves(
  @Embedded val node: NodeEntity,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "destination")
  val previousMoves: List<MoveEntity>,
  @Relation(parentColumn = "fenRepresentation", entityColumn = "origin")
  val nextMoves: List<MoveEntity>,
) {
  fun toDataPosition(): DataPosition {
    return DataPosition(
      PositionIdentifier(node.fenRepresentation),
      node.depth,
      PreviousAndNextDate(node.lastTrainedDate, node.nextTrainedDate),
      node.updatedAt,
      node.isDeleted,
    )
  }

  fun toDataMoves(): List<DataMove> {
    return (previousMoves + nextMoves)
      .filter { !it.isDeleted }
      .map { it.toStoredMove() }
      .distinctBy { Triple(it.origin, it.destination, it.move) }
  }

  companion object {
    fun convertToEntities(
      moves: List<DataMove>,
      positions: List<DataPosition>,
    ): List<NodeWithMoves> {
      val positionMap = positions.associateBy { it.positionIdentifier.fenRepresentation }
      val movesByOrigin = moves.groupBy { it.origin.fenRepresentation }
      val movesByDestination = moves.groupBy { it.destination.fenRepresentation }

      return positions.map { position ->
        val fen = position.positionIdentifier.fenRepresentation
        val nextMoves = movesByOrigin[fen]?.map { MoveEntity.convertToEntity(it) } ?: emptyList()
        val previousMoves = movesByDestination[fen]?.map { MoveEntity.convertToEntity(it) } ?: emptyList()
        NodeWithMoves(
          NodeEntity(
            fen,
            position.previousAndNextTrainingDate.previousDate,
            position.previousAndNextTrainingDate.nextDate,
            position.depth,
            position.isDeleted,
            position.updatedAt,
          ),
          previousMoves,
          nextMoves,
        )
      }
    }
  }
}
