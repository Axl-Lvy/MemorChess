package proj.memorchess.axl.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import proj.memorchess.axl.core.data.CustomDatabase
import proj.memorchess.axl.core.data.MoveEntity
import proj.memorchess.axl.core.data.NodeEntityDao
import proj.memorchess.axl.core.data.NodeWithMoves
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves

class TestNodeEntitiesDataBase {
  private lateinit var nodeEntityDao: NodeEntityDao
  private lateinit var db: CustomDatabase

  @Before
  fun createDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, CustomDatabase::class.java).build()
    nodeEntityDao = db.getNodeEntityDao()
  }

  @After
  @Throws(IOException::class)
  fun closeDb() {
    db.close()
  }

  @Test
  fun writeAndReadNode() {
    val retrievedNodes: List<NodeWithMoves>
    val game = Game()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves(), today, today)
        )
      )
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertNotNull(retrievedNodes.first())
    assertTrue { retrievedNodes.first().nextMoves.isEmpty() }
    assertEquals(
      game.position.toImmutablePosition(),
      retrievedNodes.first().toStoredNode().positionKey,
    )
  }

  @Test
  fun testDeleteAll() {
    val retrievedNode: NodeWithMoves?
    val game = Game()
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves(), today, today)
        )
      )
      nodeEntityDao.deleteAll()
      retrievedNode = nodeEntityDao.getAll().firstOrNull()
    }
    assertNull(retrievedNode)
  }

  @Test
  fun testDeleteSingle() {
    val retrievedNodes: List<NodeWithMoves>
    val game = Game()
    val rootPositionKey = game.position.toImmutablePosition()
    game.playMove("e4")
    val linkMove =
      MoveEntity(
        rootPositionKey.fenRepresentation,
        game.position.toImmutablePosition().fenRepresentation,
        "e4",
        true,
      )
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val rootNode =
      StoredNode(
        rootPositionKey,
        PreviousAndNextMoves(listOf(), listOf(linkMove.toStoredMove())),
        today,
        today,
      )
    val childNode =
      StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves(), today, today)
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(NodeWithMoves.convertToEntity(rootNode))
      nodeEntityDao.insertNodeAndMoves(NodeWithMoves.convertToEntity(childNode))
      nodeEntityDao.delete(childNode.positionKey.fenRepresentation)
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertContains(retrievedNodes.first().nextMoves, linkMove)
    assertEquals(rootPositionKey, retrievedNodes.first().toStoredNode().positionKey)
  }
}
