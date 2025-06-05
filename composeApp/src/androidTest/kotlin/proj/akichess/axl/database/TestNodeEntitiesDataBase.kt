package proj.akichess.axl.database

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
    runBlocking {
      nodeEntityDao.createNewNode(
        NodeWithMoves.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves())
        )
      )
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertNotNull(retrievedNodes.first())
    assertTrue { retrievedNodes.first().nextMoves.isEmpty() }
    assertEquals(game.position.toImmutablePosition(), retrievedNodes.first().positionKey)
  }

  @Test
  fun testDeleteAll() {
    val retrievedNode: NodeWithMoves?
    val game = Game()
    runBlocking {
      nodeEntityDao.createNewNode(
        NodeWithMoves.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves())
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
      )
    val rootNode = StoredNode(rootPositionKey, listOf(linkMove), listOf())
    val childNode = StoredNode(game.position.toImmutablePosition(), PreviousAndNextMoves())
    runBlocking {
      nodeEntityDao.createNewNode(NodeWithMoves.convertToEntity(rootNode))
      nodeEntityDao.createNewNode(NodeWithMoves.convertToEntity(childNode))
      nodeEntityDao.delete(childNode.positionKey.fenRepresentation)
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertContains(retrievedNodes.first().nextMoves, linkMove)
    assertEquals(rootPositionKey, retrievedNodes.first().positionKey)
  }
}
