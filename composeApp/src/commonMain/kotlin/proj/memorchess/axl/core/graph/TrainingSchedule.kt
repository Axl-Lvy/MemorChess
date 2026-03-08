package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil

/** Manages training schedule for spaced-repetition learning. */
class TrainingSchedule(private val openingTree: OpeningTree) {

  private val nodesByDay = mutableMapOf<Int, MutableMap<PositionKey, DataNode>>()

  /** Adds a node to the training schedule. */
  fun addNode(node: DataNode) {
    openingTree.put(node.positionKey, node.previousAndNextMoves)
    val daysUntil = DateUtil.daysUntil(node.previousAndNextTrainingDate.nextDate)
    nodesByDay.getOrPut(daysUntil) { mutableMapOf() }[node.positionKey] = node
  }

  /** Picks the minimum-depth node scheduled for the given day. */
  fun getNodeFromDay(day: Int): DataNode? {
    val candidates = nodesByDay[day] ?: return null
    val position =
      candidates.entries.minByOrNull { it.value.previousAndNextMoves.depth }?.key ?: return null
    return candidates.remove(position)
  }

  /** Gets a node to train that follows the given position, preferring reachable children. */
  fun getNodeToTrainAfterPosition(day: Int, positionKey: PositionKey): DataNode? {
    val todayNodes = nodesByDay[day] ?: return null
    for (candidatePosition in
      openingTree.get(positionKey)?.nextMoves?.values?.map { it.destination } ?: emptyList()) {
      val candidateNode = todayNodes.remove(candidatePosition)
      if (candidateNode != null) return candidateNode
    }
    return null
  }

  /** Returns the number of nodes scheduled for training on the given day. */
  fun getNumberOfNodesToTrain(day: Int): Int = nodesByDay[day]?.size ?: 0

  /** Clears all scheduled nodes. */
  fun clear() {
    nodesByDay.clear()
  }
}
