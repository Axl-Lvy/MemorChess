package proj.memorchess.axl.core.data.online

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestNoActiveDatabase : TestWithKoin {

  val compositeDatabase by inject<DatabaseQueryManager>()
  val localDatabase by inject<DatabaseQueryManager>(named("local"))
  val remoteDatabase by inject<SupabaseQueryManager>()

  override fun setUp() {
    super.setUp()
    (localDatabase as TestDatabaseQueryManager).isActiveState = false
    assertFalse { remoteDatabase.isActive() }
    assertFalse { localDatabase.isActive() }
  }

  @Test
  fun testOperationsFailWhenNoActiveDatabase() = runTest {
    assertFails { compositeDatabase.getAllNodes() }
    assertFails { compositeDatabase.getPosition(PositionIdentifier.START_POSITION) }
  }
}
