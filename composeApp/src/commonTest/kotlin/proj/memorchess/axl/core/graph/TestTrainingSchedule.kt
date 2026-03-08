package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate

class TestTrainingSchedule {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")

  private fun makeNode(
    positionKey: PositionKey,
    depth: Int = 0,
    nextDate: kotlinx.datetime.LocalDate = DateUtil.today(),
  ): DataNode {
    val moves = PreviousAndNextMoves(depth)
    moves.addNextMove(DataMove(positionKey, posA, "e4", isGood = true))
    return DataNode(positionKey, moves, PreviousAndNextDate(DateUtil.today(), nextDate))
  }

  @Test
  fun addNodeAndGetFromDay() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    val node = makeNode(startPos, depth = 0)
    schedule.addNode(node)

    val result = schedule.getNodeFromDay(0)
    assertNotNull(result)
    assertEquals(startPos, result.positionKey)
  }

  @Test
  fun getNodeFromDayRemovesNode() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    schedule.addNode(makeNode(startPos))

    assertNotNull(schedule.getNodeFromDay(0))
    assertNull(schedule.getNodeFromDay(0))
  }

  @Test
  fun getNodeFromDayReturnsMinDepth() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    schedule.addNode(makeNode(posA, depth = 5))
    schedule.addNode(makeNode(startPos, depth = 0))
    schedule.addNode(makeNode(posB, depth = 3))

    val result = schedule.getNodeFromDay(0)
    assertNotNull(result)
    assertEquals(startPos, result.positionKey)
  }

  @Test
  fun getNumberOfNodesToTrain() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    assertEquals(0, schedule.getNumberOfNodesToTrain(0))
    schedule.addNode(makeNode(startPos))
    schedule.addNode(makeNode(posA))
    assertEquals(2, schedule.getNumberOfNodesToTrain(0))
  }

  @Test
  fun clearEmptiesEverything() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    schedule.addNode(makeNode(startPos))
    schedule.clear()
    assertEquals(0, schedule.getNumberOfNodesToTrain(0))
  }

  @Test
  fun getNodeToTrainAfterPositionPrefersReachable() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)

    // posA is reachable from startPos via nextMoves
    val startMoves = PreviousAndNextMoves()
    startMoves.addNextMove(DataMove(startPos, posA, "e4", isGood = true))
    tree.put(startPos, startMoves)

    schedule.addNode(makeNode(posA))
    schedule.addNode(makeNode(posB))

    val result = schedule.getNodeToTrainAfterPosition(0, startPos)
    assertNotNull(result)
    assertEquals(posA, result.positionKey)
  }
}
