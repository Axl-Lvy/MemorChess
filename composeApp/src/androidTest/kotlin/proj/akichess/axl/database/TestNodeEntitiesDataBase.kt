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
import proj.ankichess.axl.core.data.CustomDatabase
import proj.ankichess.axl.core.data.NodeEntity
import proj.ankichess.axl.core.data.NodeEntityDao
import proj.ankichess.axl.core.impl.data.StoredNode
import proj.ankichess.axl.core.impl.engine.Game

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
    val retrievedNodes: List<NodeEntity>
    val game = Game()
    runBlocking {
      nodeEntityDao.insert(
        NodeEntity.convertToEntity(StoredNode(game.position.toImmutablePosition(), ""))
      )
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertNotNull(retrievedNodes.first())
    assertTrue { retrievedNodes.first().getAvailableMoveList().isEmpty() }
    assertEquals(game.position.toImmutablePosition(), retrievedNodes.first().positionKey)
  }

  @Test
  fun testDeleteAll() {
    val retrievedNode: NodeEntity?
    val game = Game()
    runBlocking {
      nodeEntityDao.insert(
        NodeEntity.convertToEntity(StoredNode(game.position.toImmutablePosition(), ""))
      )
      nodeEntityDao.deleteAll()
      retrievedNode = nodeEntityDao.getAll().firstOrNull()
    }
    assertNull(retrievedNode)
  }

  @Test
  fun testDeleteSingle() {
    val retrievedNodes: List<NodeEntity>
    val game = Game()
    val rootNode = StoredNode(game.position.toImmutablePosition(), listOf("e4"), listOf())
    game.playMove("e4")
    val childNode = StoredNode(game.position.toImmutablePosition(), "")
    runBlocking {
      nodeEntityDao.insert(NodeEntity.convertToEntity(rootNode))
      nodeEntityDao.insert(NodeEntity.convertToEntity(childNode))
      nodeEntityDao.delete(childNode.positionKey.fenRepresentation)
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    assertContains(retrievedNodes.first().getAvailableMoveList(), "e4")
    assertEquals(rootNode.positionKey, retrievedNodes.first().positionKey)
  }

  @Test
  fun testReplace() {
    val retrievedNodes: List<NodeEntity>
    val game = Game()
    runBlocking {
      nodeEntityDao.insert(
        NodeEntity.convertToEntity(StoredNode(game.position.toImmutablePosition(), ""))
      )
      nodeEntityDao.insert(
        NodeEntity.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), listOf("e4"), listOf())
        )
      )
      nodeEntityDao.insert(
        NodeEntity.convertToEntity(
          StoredNode(game.position.toImmutablePosition(), listOf("e3"), listOf())
        )
      )
      retrievedNodes = nodeEntityDao.getAll()
    }
    assertEquals(1, retrievedNodes.size)
    val node = retrievedNodes.first()
    assertEquals(1, node.getAvailableMoveList().size)
    assertContains(node.getAvailableMoveList(), "e3")
    assertEquals(game.position.toImmutablePosition(), node.positionKey)
  }
}
