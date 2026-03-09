package proj.memorchess.axl.core.data.online

import kotlin.getValue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestCompositeDatabaseWithNothing : TestWithKoin() {

  val compositeDatabase by inject<DatabaseQueryManager>()
  val localDatabase by inject<DatabaseQueryManager>(named("local"))
  val remoteDatabase by inject<SupabaseQueryManager>()

  override suspend fun setUp() {
    (localDatabase as TestDatabaseQueryManager).isActiveState = false
    assertFalse { remoteDatabase.isActive() }
    assertFalse { localDatabase.isActive() }
    // Clear remote database to start with clean state
    compositeDatabase.deleteAll(DateUtil.farInThePast())
    assertFalse { compositeDatabase.isActive() }
  }

  @Test
  fun testThrowOnGet() = test {
    val engine = GameEngine()
    val node =
      DataNode(engine.toPositionKey(), PreviousAndNextMoves(), PreviousAndNextDate.dummyToday())

    assertFailsWith<IllegalStateException> { compositeDatabase.getAllNodes(true) }
    assertFailsWith<IllegalStateException> { compositeDatabase.getPosition(node.positionKey) }
    assertFailsWith<IllegalStateException> { compositeDatabase.getPosition(node.positionKey) }
  }

  fun testLastUpdated() = test { assertNull(compositeDatabase.getLastUpdate()) }
}
