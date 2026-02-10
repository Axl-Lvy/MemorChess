package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestCompositeDatabaseWithRemoteOnly :
  TestCompositeDatabase.TestCompositeDatabaseAuthenticated() {
  override fun setUp() {
    super.setUp()
    (localDatabase as proj.memorchess.axl.test_util.TestDatabaseQueryManager).isActiveState = false
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
    val nodes = TestDatabaseQueryManager.minimalNodePair()

    remoteDatabase.insertNodes(*nodes.toTypedArray())
    val positionIdentifier = nodes.first().positionIdentifier
    assertEquals(
      remoteDatabase.getPosition(positionIdentifier),
      remoteDatabase.getPosition(positionIdentifier),
    )
  }
}
