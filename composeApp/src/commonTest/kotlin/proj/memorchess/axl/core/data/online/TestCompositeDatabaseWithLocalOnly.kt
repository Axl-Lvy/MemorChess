package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

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
    val nodes = TestDatabaseQueryManager.minimalNodePair()

    localDatabase.insertNodes(*nodes.toTypedArray())
    val positionIdentifier = nodes.first().positionIdentifier
    assertEquals(
      localDatabase.getPosition(positionIdentifier),
      compositeDatabase.getPosition(positionIdentifier),
    )
  }
}
