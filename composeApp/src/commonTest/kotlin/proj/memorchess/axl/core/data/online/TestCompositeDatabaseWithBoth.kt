package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
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
  fun testLastUpdatedFail() = runTest {
    // Arrange
    val game = Game()
    val node1 =
      DataNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.Companion.dummyToday(),
        DateUtil.tomorrow().atTime(LocalTime(0, 0, 0)),
      )
    localDatabase.insertNodes(node1)
    val node2 =
      DataNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.Companion.dummyToday(),
        DateUtil.now(),
      )
    remoteDatabase.insertNodes(node2)
    assertFailsWith<IllegalStateException> { compositeDatabase.getLastUpdate() }
  }
}
