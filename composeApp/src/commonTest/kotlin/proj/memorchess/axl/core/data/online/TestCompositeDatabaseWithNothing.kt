package proj.memorchess.axl.core.data.online

import kotlin.getValue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.KtorQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestCompositeDatabaseWithNothing : TestWithKoin {

  val compositeDatabase by inject<DatabaseQueryManager>()
  val localDatabase by inject<DatabaseQueryManager>(named("local"))
  val remoteDatabase by inject<KtorQueryManager>()

  override fun setUp() {
    super.setUp()
    (localDatabase as TestDatabaseQueryManager).isActiveState = false
    assertFalse { remoteDatabase.isActive() }
    assertFalse { localDatabase.isActive() }
    runTest {
      // Clear remote database to start with clean state
      compositeDatabase.deleteAll(DateUtil.farInThePast())
    }
    assertFalse { compositeDatabase.isActive() }
  }

  @Test
  fun testThrowOnGet() = runTest {
    val (_, positions) = TestDatabaseQueryManager.minimalNodePair()

    assertFailsWith<IllegalStateException> { compositeDatabase.getAllPositions(true) }
    assertFailsWith<IllegalStateException> {
      compositeDatabase.getPosition(positions.first().positionIdentifier)
    }
  }

  fun testLastUpdated() = runTest { assertNull(compositeDatabase.getLastUpdate()) }
}
