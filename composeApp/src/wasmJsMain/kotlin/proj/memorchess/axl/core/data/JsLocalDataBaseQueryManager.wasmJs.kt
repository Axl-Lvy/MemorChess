@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Cursor
import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.openDatabase
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toJsString
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.PreviousAndNextMoves

// ---------------------------------------------------------------------------
// External JS interfaces for IndexedDB object stores
// ---------------------------------------------------------------------------

/** JS object stored in the "nodes" object store. */
private external interface JsNodeEntity : JsAny {
  var positionKey: String
  var lastTrainedDate: Int // epoch days
  var nextTrainedDate: Int // epoch days
  var depth: Int
  var isDeleted: Boolean
  var updatedAt: Double // epoch seconds (Double avoids Long→BigInt)
}

/** JS object stored in the "moves" object store. */
private external interface JsMoveEntity : JsAny {
  var origin: String
  var destination: String
  var move: String
  var isGood: Boolean
  var isDeleted: Boolean
  var updatedAt: Double // epoch seconds (Double avoids Long→BigInt)
}

// ---------------------------------------------------------------------------
// JS object creation (same pattern as library-internal jso)
// ---------------------------------------------------------------------------

private fun <T : JsAny> emptyObject(): T = js("({})")

// ---------------------------------------------------------------------------
// JsArray → List helper
// ---------------------------------------------------------------------------

/** Converts a [JsArray] returned by IndexedDB `getAll()` to a Kotlin [List]. */
@Suppress("UNCHECKED_CAST")
private fun <T : JsAny> JsArray<*>.toList(): List<T> {
  val result = mutableListOf<T>()
  for (i in 0 until length) {
    val item = this[i] ?: continue
    result.add(item as T)
  }
  return result
}

// ---------------------------------------------------------------------------
// Conversion: JS → domain
// ---------------------------------------------------------------------------

private fun JsMoveEntity.toDataMove(): DataMove =
  DataMove(
    origin = PositionKey(origin),
    destination = PositionKey(destination),
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    updatedAt = Instant.fromEpochSeconds(updatedAt.toLong()),
  )

private fun JsNodeEntity.toDataNode(
  previousMoves: List<DataMove>,
  nextMoves: List<DataMove>,
): DataNode =
  DataNode(
    positionKey = PositionKey(positionKey),
    previousAndNextMoves =
      PreviousAndNextMoves(
        previousMoves.filter { !it.isDeleted },
        nextMoves.filter { !it.isDeleted },
      ),
    previousAndNextTrainingDate =
      PreviousAndNextDate(
        LocalDate.fromEpochDays(lastTrainedDate),
        LocalDate.fromEpochDays(nextTrainedDate),
      ),
    depth = depth,
    updatedAt = Instant.fromEpochSeconds(updatedAt.toLong()),
    isDeleted = isDeleted,
  )

// ---------------------------------------------------------------------------
// Conversion: domain → JS
// ---------------------------------------------------------------------------

private fun DataNode.toJsNodeEntity(): JsNodeEntity {
  val node = this
  return emptyObject<JsNodeEntity>().apply {
    positionKey = node.positionKey.value
    lastTrainedDate = node.previousAndNextTrainingDate.previousDate.toEpochDays().toInt()
    nextTrainedDate = node.previousAndNextTrainingDate.nextDate.toEpochDays().toInt()
    depth = node.depth
    isDeleted = node.isDeleted
    updatedAt = node.updatedAt.epochSeconds.toDouble()
  }
}

private fun DataMove.toJsMoveEntity(): JsMoveEntity {
  val dataMove = this
  val isGoodValue = dataMove.isGood
  checkNotNull(isGoodValue) {
    "A DataMove must have a isGood value to be inserted into the database"
  }
  return emptyObject<JsMoveEntity>().apply {
    origin = dataMove.origin.value
    destination = dataMove.destination.value
    move = dataMove.move
    isGood = isGoodValue
    isDeleted = dataMove.isDeleted
    updatedAt = dataMove.updatedAt.epochSeconds.toDouble()
  }
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val NODES_STORE = "nodes"
private const val MOVES_STORE = "moves"
private const val DB_NAME = "memorchess"
private const val DB_VERSION = 1

// ---------------------------------------------------------------------------
// IndexedDB-backed DatabaseQueryManager
// ---------------------------------------------------------------------------

/** Local database for wasmJs backed by IndexedDB. */
object JsLocalDatabaseQueryManager : DatabaseQueryManager {

  private val databaseDeferred = CompletableDeferred<Database>()
  private var initialized = false

  private suspend fun db(): Database {
    if (!initialized) {
      initialized = true
      val database =
        openDatabase(DB_NAME, DB_VERSION) { database, oldVersion, _ ->
          if (oldVersion < 1) {
            val nodesStore = database.createObjectStore(NODES_STORE, KeyPath("positionKey"))
            nodesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
            nodesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)

            val movesStore =
              database.createObjectStore(MOVES_STORE, KeyPath("origin", "destination"))
            movesStore.createIndex("origin", KeyPath("origin"), unique = false)
            movesStore.createIndex("destination", KeyPath("destination"), unique = false)
            movesStore.createIndex("isDeleted", KeyPath("isDeleted"), unique = false)
            movesStore.createIndex("updatedAt", KeyPath("updatedAt"), unique = false)
          }
        }
      databaseDeferred.complete(database)
    }
    return databaseDeferred.await()
  }

  override fun isActive(): Boolean = true

  override suspend fun insertNodes(vararg positions: DataNode) {
    val database = db()
    database.writeTransaction(NODES_STORE, MOVES_STORE) {
      val nodesStore = objectStore(NODES_STORE)
      val movesStore = objectStore(MOVES_STORE)
      for (dataNode in positions) {
        nodesStore.put(dataNode.toJsNodeEntity())
        val allMoves =
          dataNode.previousAndNextMoves.previousMoves.values +
            dataNode.previousAndNextMoves.nextMoves.values
        for (move in allMoves) {
          movesStore.put(move.toJsMoveEntity())
        }
      }
    }
  }

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    val database = db()
    return database.transaction(NODES_STORE, MOVES_STORE) {
      val allJsNodes: List<JsNodeEntity> = objectStore(NODES_STORE).getAll().toList()
      val allJsMoves: List<JsMoveEntity> = objectStore(MOVES_STORE).getAll().toList()

      // Build lookup maps to avoid N+1 queries
      val movesByOrigin = mutableMapOf<String, MutableList<DataMove>>()
      val movesByDestination = mutableMapOf<String, MutableList<DataMove>>()
      for (jsMove in allJsMoves) {
        val move = jsMove.toDataMove()
        movesByOrigin.getOrPut(move.origin.value) { mutableListOf() }.add(move)
        movesByDestination.getOrPut(move.destination.value) { mutableListOf() }.add(move)
      }

      allJsNodes
        .map { node ->
          val key = node.positionKey
          node.toDataNode(
            previousMoves = movesByDestination[key] ?: emptyList(),
            nextMoves = movesByOrigin[key] ?: emptyList(),
          )
        }
        .let { nodes -> if (withDeletedOnes) nodes else nodes.filter { !it.isDeleted } }
    }
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    val database = db()
    return database.transaction(NODES_STORE, MOVES_STORE) {
      val jsNode =
        objectStore(NODES_STORE)
          .get(Key(positionKey.value.toJsString()))
          ?.unsafeCast<JsNodeEntity>() ?: return@transaction null
      if (jsNode.isDeleted) return@transaction null

      val movesStore = objectStore(MOVES_STORE)
      val nextMoves: List<JsMoveEntity> =
        movesStore.index("origin").getAll(Key(positionKey.value.toJsString())).toList()
      val previousMoves: List<JsMoveEntity> =
        movesStore.index("destination").getAll(Key(positionKey.value.toJsString())).toList()

      jsNode.toDataNode(previousMoves.map { it.toDataMove() }, nextMoves.map { it.toDataMove() })
    }
  }

  override suspend fun deletePosition(position: PositionKey) {
    val database = db()
    database.writeTransaction(NODES_STORE, MOVES_STORE) {
      val nodesStore = objectStore(NODES_STORE)
      val movesStore = objectStore(MOVES_STORE)

      // Soft-delete the node
      val jsNode = nodesStore.get(Key(position.value.toJsString()))?.unsafeCast<JsNodeEntity>()
      if (jsNode != null && !jsNode.isDeleted) {
        jsNode.isDeleted = true
        nodesStore.put(jsNode)
      }

      // Soft-delete moves from this position (origin)
      val originMoves: List<JsMoveEntity> =
        movesStore.index("origin").getAll(Key(position.value.toJsString())).toList()
      for (move in originMoves) {
        if (!move.isDeleted) {
          move.isDeleted = true
          movesStore.put(move)
        }
      }

      // Soft-delete moves to this position (destination)
      val destMoves: List<JsMoveEntity> =
        movesStore.index("destination").getAll(Key(position.value.toJsString())).toList()
      for (move in destMoves) {
        if (!move.isDeleted) {
          move.isDeleted = true
          movesStore.put(move)
        }
      }
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    val database = db()
    database.writeTransaction(MOVES_STORE) {
      val movesStore = objectStore(MOVES_STORE)
      val moves: List<JsMoveEntity> =
        movesStore.index("origin").getAll(Key(origin.value.toJsString())).toList()
      for (m in moves) {
        if (m.move == move && !m.isDeleted) {
          m.isDeleted = true
          movesStore.put(m)
        }
      }
    }
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    val database = db()
    database.writeTransaction(NODES_STORE, MOVES_STORE) {
      val nodesStore = objectStore(NODES_STORE)
      val movesStore = objectStore(MOVES_STORE)
      val threshold = hardFrom?.epochSeconds?.toDouble()

      // Process all nodes: hard-delete if newer than threshold, else soft-delete
      val allNodes: List<JsNodeEntity> = nodesStore.getAll().toList()
      for (node in allNodes) {
        if (threshold != null && node.updatedAt >= threshold) {
          nodesStore.delete(Key(node.positionKey.toJsString()))
        } else if (!node.isDeleted) {
          node.isDeleted = true
          nodesStore.put(node)
        }
      }

      // Process all moves: hard-delete if newer than threshold, else soft-delete
      val allMoves: List<JsMoveEntity> = movesStore.getAll().toList()
      for (move in allMoves) {
        if (threshold != null && move.updatedAt >= threshold) {
          movesStore.delete(Key(move.origin.toJsString(), move.destination.toJsString()))
        } else if (!move.isDeleted) {
          move.isDeleted = true
          movesStore.put(move)
        }
      }
    }
  }

  override suspend fun getLastUpdate(): Instant? {
    val database = db()
    return database.transaction(NODES_STORE, MOVES_STORE) {
      val lastNodeUpdate =
        objectStore(NODES_STORE)
          .index("updatedAt")
          .openCursor(autoContinue = true, direction = Cursor.Direction.Previous)
          .map { (it.value as JsNodeEntity).updatedAt }
          .firstOrNull()

      val lastMoveUpdate =
        objectStore(MOVES_STORE)
          .index("updatedAt")
          .openCursor(autoContinue = true, direction = Cursor.Direction.Previous)
          .map { (it.value as JsMoveEntity).updatedAt }
          .firstOrNull()

      val maxEpochSeconds =
        when {
          lastNodeUpdate != null && lastMoveUpdate != null ->
            lastNodeUpdate.coerceAtLeast(lastMoveUpdate)
          else -> lastNodeUpdate ?: lastMoveUpdate
        }

      maxEpochSeconds?.let { Instant.fromEpochSeconds(it.toLong()).truncateToSeconds() }
    }
  }
}

actual fun getPlatformSpecificLocalDatabase(): DatabaseQueryManager {
  return JsLocalDatabaseQueryManager
}
