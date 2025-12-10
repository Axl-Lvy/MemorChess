package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class TestCompositeDatabaseWithBoth : TestCompositeDatabase.TestCompositeDatabaseAuthenticated() {
  private val databaseSynchronizer: DatabaseSynchronizer by inject()

  override fun setUp() {
    super.setUp()
    assertTrue { remoteDatabase.isActive() }
    assertTrue { localDatabase.isActive() }
    runTest {
      // Clear remote database to start with clean state
      localDatabase.deleteAll(DateUtil.farInThePast())
      remoteDatabase.deleteAll(DateUtil.farInThePast())
      databaseSynchronizer.syncFromLocal()
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
