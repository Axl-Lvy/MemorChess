package proj.memorchess.axl.core.data.online

import io.kotest.assertions.nondeterministic.eventually
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.online.database.CloudUploader
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TEST_TIMEOUT

/** Test to verify incremental sync behavior and background uploads. */
class TestIncrementalSync : TestCompositeDatabase.TestCompositeDatabaseAuthenticated() {

  private val cloudUploader: CloudUploader by inject()

  @Test
  fun testBackgroundUploadAfterInsert() = runTest {
    // Arrange
    val game = Game()
    val node =
      DataNode(
        game.position.createIdentifier(),
        PreviousAndNextMoves(),
        PreviousAndNextDate.dummyToday(),
      )

    // Act - Insert to composite (writes to local, queues cloud upload)
    compositeDatabase.insertNodes(node)

    // Assert - Local database should have the node immediately
    eventually(TEST_TIMEOUT) {
      val localNode = localDatabase.getPosition(node.positionIdentifier)
      assertNotNull(localNode, "Node should be immediately available in local database")
      cloudUploader.isQueueEmpty()
      // Remote database should eventually have the node
      val remoteNode = remoteDatabase.getPosition(node.positionIdentifier)
      assertNotNull(remoteNode, "Node should eventually be uploaded to cloud")
      assertEquals(node.positionIdentifier, remoteNode.positionIdentifier)
    }
  }
}
