package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class TestCompositeDatabaseWithLocalOnly : TestCompositeDatabase() {
  override fun setUp() {
    super.setUp()
    assertFalse { remoteDatabase.isActive() }
    assertTrue { localDatabase.isActive() }
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
      StoredNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )

    localDatabase.insertNodes(node)
    assertEquals(
      localDatabase.getPosition(node.positionIdentifier),
      compositeDatabase.getPosition(node.positionIdentifier),
    )
  }
}
