package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.MAX_NEW_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestTrainingNodeOrder : TestWithKoin() {

  private val treeStore: TreeStore by inject()
  private val scheduler: TrainingScheduler by inject()
  private val database: DatabaseQueryManager by inject()

  override suspend fun setUp() {
    database.eraseAll()
    database.insertNodes(*TestDatabaseQueryManager.vienna().getAllNodes(true).toTypedArray())
    treeStore.load()
  }

  @Test
  fun testMinimumDepth() = test {
    val entry = scheduler.nextDue()
    assertNotNull(entry)
    assertTrue { entry.positionKey == PositionKey.START_POSITION }
  }

  @Test
  fun testSchedulerHonorsNewLimitSetting() = test {
    // The injected scheduler reads the cap from the config item. Vienna positions are all brand
    // new, so dropping the new limit to zero empties the queue; restoring it brings them back.
    assertNotNull(scheduler.nextDue())
    MAX_NEW_MOVES_PER_DAY_SETTING.setValue(0)
    assertNull(scheduler.nextDue())
    MAX_NEW_MOVES_PER_DAY_SETTING.reset()
    assertNotNull(scheduler.nextDue())
  }

  @Test
  fun testNextMove() = test {
    val first = scheduler.nextDue()
    checkNotNull(first)
    val node = treeStore.current().get(first.positionKey)
    checkNotNull(node)
    // The user plays the first edge out of the start; the next trainable entry must be reachable
    // from where they land.
    val landedOn = node.outgoing.values.first().to
    val nextEntry = scheduler.nextAfter(landedOn)
    assertNotNull(nextEntry)
    val nextNode = treeStore.current().get(nextEntry.positionKey)
    checkNotNull(nextNode)
    val move = nextNode.outgoing.values.first().move
    assertTrue("Move is $move but should be Nc3") { move == "Nc3" }
  }
}
