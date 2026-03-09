package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

class TestCompositeDatabaseWithLocalOnly : TestCompositeDatabase() {
  override suspend fun setUp() {
    assertFalse { remoteDatabase.isActive() }
    assertTrue { localDatabase.isActive() }
    // Clear remote database to start with clean state
    compositeDatabase.deleteAll(DateUtil.farInThePast())
    assertTrue { compositeDatabase.isActive() }
  }

  @Test
  fun testConsistency() = test {
    val engine = GameEngine()
    val node =
      DataNode(engine.toPositionKey(), PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    localDatabase.insertNodes(node)
    assertEquals(
      localDatabase.getPosition(node.positionKey),
      compositeDatabase.getPosition(node.positionKey),
    )
  }
}
