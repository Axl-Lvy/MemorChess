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
import org.junit.After
import org.junit.Before
import proj.memorchess.axl.core.data.CustomDatabase
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataPosition
import proj.memorchess.axl.core.data.MoveEntity
import proj.memorchess.axl.core.data.NodeEntityDao
import proj.memorchess.axl.core.data.NodeWithMoves
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game

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
    val position = DataPosition(
      game.position.createIdentifier(),
      0,
      PreviousAndNextDate.dummyToday(),
    )
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntities(
          emptyList(),
          listOf(position),
        )
      )
      retrievedNodes = nodeEntityDao.getAllNodes()
    }
    assertEquals(1, retrievedNodes.size)
    assertNotNull(retrievedNodes.first())
    assertTrue { retrievedNodes.first().nextMoves.isEmpty() }
    assertEquals(
      game.position.createIdentifier(),
      retrievedNodes.first().toDataPosition().positionIdentifier,
    )
  }

  @Test
  fun testDeleteAll() {
    val retrievedNode: NodeWithMoves?
    val game = Game()
    val position = DataPosition(
      game.position.createIdentifier(),
      0,
      PreviousAndNextDate.dummyToday(),
    )
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntities(
          emptyList(),
          listOf(position),
        )
      )
      nodeEntityDao.deleteAllNodes()
      retrievedNode = nodeEntityDao.getAllNodes().firstOrNull { !it.node.isDeleted }
    }
    assertNull(retrievedNode)
  }

  @Test
  fun testDeleteSingle() {
    val retrievedNodes: List<NodeWithMoves>
    val game = Game()
    val rootPositionKey = game.position.createIdentifier()
    game.playMove("e4")
    val childPositionKey = game.position.createIdentifier()
    val linkMove =
      MoveEntity(
        rootPositionKey.fenRepresentation,
        childPositionKey.fenRepresentation,
        "e4",
        true,
      )
    val now = DateUtil.now()
    val dataMove = DataMove(
      rootPositionKey,
      childPositionKey,
      "e4",
      isGood = true,
      updatedAt = now,
    )
    val rootPosition = DataPosition(
      rootPositionKey,
      0,
      PreviousAndNextDate.dummyToday(),
      now,
    )
    val childPosition = DataPosition(
      childPositionKey,
      1,
      PreviousAndNextDate.dummyToday(),
      now,
    )
    runBlocking {
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntities(listOf(dataMove), listOf(rootPosition))
      )
      nodeEntityDao.insertNodeAndMoves(
        NodeWithMoves.convertToEntities(listOf(dataMove), listOf(childPosition))
      )
      nodeEntityDao.delete(childPositionKey.fenRepresentation)
      retrievedNodes = nodeEntityDao.getAllNodes().filter { !it.node.isDeleted }
    }
    assertEquals(1, retrievedNodes.size)
    assertContains(
      retrievedNodes.first().nextMoves.map { it.toStoredMove() },
      linkMove.toStoredMove(),
    )
    assertEquals(rootPositionKey, retrievedNodes.first().toDataPosition().positionIdentifier)
  }
}
