package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

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
    val (moves, positions) = TestDatabaseQueryManager.minimalNodePair()

    remoteDatabase.insertMoves(moves, positions)
    val positionIdentifier = positions.first().positionIdentifier
    assertEquals(
      remoteDatabase.getPosition(positionIdentifier),
      remoteDatabase.getPosition(positionIdentifier),
    )
  }
}
