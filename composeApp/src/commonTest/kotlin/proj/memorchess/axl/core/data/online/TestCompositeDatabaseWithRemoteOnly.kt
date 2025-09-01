package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestCompositeDatabaseWithRemoteOnly :
  TestCompositeDatabase.TestCompositeDatabaseAuthenticated() {
  override fun setUp() {
    super.setUp()
    (localDatabase as TestDatabaseQueryManager).isActiveState = false
    assertTrue { remoteDatabase.isActive() }
    assertFalse { localDatabase.isActive() }
    runTest {
      // Clear remote database to start with clean state
      compositeDatabase.deleteAll(DateUtil.farInThePast())
    }
    assertTrue { compositeDatabase.isActive() }
  }

  @Test
  fun testConsistency() = runTest {
    val game = Game()
    val node =
      DataNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )

    remoteDatabase.insertNodes(node)
    assertEquals(
      remoteDatabase.getPosition(node.positionIdentifier),
      remoteDatabase.getPosition(node.positionIdentifier),
    )
  }
}
