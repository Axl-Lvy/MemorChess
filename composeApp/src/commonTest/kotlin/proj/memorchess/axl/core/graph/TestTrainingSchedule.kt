package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate

class TestTrainingSchedule {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")

  private fun makeEntry(
    positionKey: PositionKey,
    depth: Int = 0,
    nextDate: kotlinx.datetime.LocalDate = DateUtil.today(),
  ): TrainingEntry {
    return TrainingEntry(positionKey, PreviousAndNextDate(DateUtil.today(), nextDate))
  }

  /** Registers a position in the tree with the given depth and a dummy next move. */
  private fun OpeningTree.registerPosition(positionKey: PositionKey, depth: Int) {
    val moves = getOrCreate(positionKey, depth)
    moves.addNextMove(DataMove(positionKey, posA, "e4", isGood = true))
  }

  @Test
  fun addNodeAndGetFromDay() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    tree.registerPosition(startPos, 0)
    schedule.addEntry(makeEntry(startPos, depth = 0))

    val result = schedule.getEntryFromDay(0)
    assertNotNull(result)
    assertEquals(startPos, result.positionKey)
  }

  @Test
  fun getNodeFromDayRemovesNode() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    tree.registerPosition(startPos, 0)
    schedule.addEntry(makeEntry(startPos))

    assertNotNull(schedule.getEntryFromDay(0))
    assertNull(schedule.getEntryFromDay(0))
  }

  @Test
  fun getNodeFromDayReturnsMinDepth() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    tree.registerPosition(posA, 5)
    tree.registerPosition(startPos, 0)
    tree.registerPosition(posB, 3)
    schedule.addEntry(makeEntry(posA, depth = 5))
    schedule.addEntry(makeEntry(startPos, depth = 0))
    schedule.addEntry(makeEntry(posB, depth = 3))

    val result = schedule.getEntryFromDay(0)
    assertNotNull(result)
    assertEquals(startPos, result.positionKey)
  }

  @Test
  fun getNumberOfNodesToTrain() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    assertEquals(0, schedule.getNumberOfNodesToTrain(0))
    tree.registerPosition(startPos, 0)
    tree.registerPosition(posA, 1)
    schedule.addEntry(makeEntry(startPos))
    schedule.addEntry(makeEntry(posA))
    assertEquals(2, schedule.getNumberOfNodesToTrain(0))
  }

  @Test
  fun clearEmptiesEverything() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)
    tree.registerPosition(startPos, 0)
    schedule.addEntry(makeEntry(startPos))
    schedule.clear()
    assertEquals(0, schedule.getNumberOfNodesToTrain(0))
  }

  @Test
  fun getNodeToTrainAfterPositionPrefersReachable() {
    val tree = OpeningTree()
    val schedule = TrainingSchedule(tree)

    // posA is reachable from startPos via nextMoves
    val startMoves = MutablePreviousAndNextMoves()
    startMoves.addNextMove(DataMove(startPos, posA, "e4", isGood = true))
    tree.put(startPos, startMoves)

    tree.registerPosition(posA, 1)
    tree.registerPosition(posB, 1)
    schedule.addEntry(makeEntry(posA))
    schedule.addEntry(makeEntry(posB))

    val result = schedule.getEntryAfterPosition(0, startPos)
    assertNotNull(result)
    assertEquals(posA, result.positionKey)
  }
}
