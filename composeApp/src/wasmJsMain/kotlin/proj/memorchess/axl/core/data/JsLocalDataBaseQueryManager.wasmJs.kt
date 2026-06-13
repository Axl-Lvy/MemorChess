@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Cursor
import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toJsString
import kotlin.time.Instant
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

// ---------------------------------------------------------------------------
// External JS interfaces for IndexedDB object stores
// ---------------------------------------------------------------------------

/** JS object stored in the "nodes" object store. */
private external interface JsNodeEntity : JsAny {
  var positionKey: String
  var dueDate: Double // epoch seconds
  var lastReview: Double // epoch seconds, 0 means null (brand new card)
  var stability: Double
  var difficulty: Double
  var reps: Int
  var lapses: Int
  var phase: String // CardPhase name
  var step: Int
  var depth: Int
  var isDeleted: Boolean
  var updatedAt: Double // epoch seconds (Double avoids Long to BigInt)
}

/** JS object stored in the "moves" object store. */
private external interface JsMoveEntity : JsAny {
  var origin: String
  var destination: String
  var move: String
  var isGood: Boolean
  var isDeleted: Boolean
  var createdAt: Double // epoch seconds (Double avoids Long→BigInt)
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
    createdAt = Instant.fromEpochSeconds(createdAt.toLong()),
    updatedAt = Instant.fromEpochSeconds(updatedAt.toLong()),
  )

private fun JsNodeEntity.toDataNode(
  previousMoves: List<DataMove>,
  nextMoves: List<DataMove>,
): DataNode {
  val lastReviewSec = lastReview.toLong()
  return DataNode(
    positionKey = PositionKey(positionKey),
    previousAndNextMoves =
      PreviousAndNextMoves(
        previousMoves.filter { !it.isDeleted },
        nextMoves.filter { !it.isDeleted },
      ),
    cardState =
      CardState(
        dueDate = Instant.fromEpochSeconds(dueDate.toLong()),
        lastReview = if (lastReviewSec == 0L) null else Instant.fromEpochSeconds(lastReviewSec),
        stability = stability,
        difficulty = difficulty,
        reps = reps,
        lapses = lapses,
        phase = runCatching { CardPhase.valueOf(phase) }.getOrDefault(CardPhase.NEW),
        step = step,
      ),
    depth = depth,
    updatedAt = Instant.fromEpochSeconds(updatedAt.toLong()),
    isDeleted = isDeleted,
  )
}

// ---------------------------------------------------------------------------
// Conversion: domain → JS
// ---------------------------------------------------------------------------

private fun DataNode.toJsNodeEntity(): JsNodeEntity {
  val node = this
  return emptyObject<JsNodeEntity>().apply {
    positionKey = node.positionKey.value
    dueDate = node.cardState.dueDate.epochSeconds.toDouble()
    lastReview = node.cardState.lastReview?.epochSeconds?.toDouble() ?: 0.0
    stability = node.cardState.stability
    difficulty = node.cardState.difficulty
    reps = node.cardState.reps
    lapses = node.cardState.lapses
    phase = node.cardState.phase.name
    step = node.cardState.step
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
    createdAt = dataMove.createdAt.epochSeconds.toDouble()
    updatedAt = dataMove.updatedAt.epochSeconds.toDouble()
  }
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

internal const val NODES_STORE = "nodes"
internal const val MOVES_STORE = "moves"
internal const val EXPLORER_CACHE_STORE = "explorerCache"
internal const val DB_NAME = "memorchess"
internal const val DB_VERSION = 5

// ---------------------------------------------------------------------------
// IndexedDB-backed DatabaseQueryManager
// ---------------------------------------------------------------------------

/** Local database for wasmJs backed by IndexedDB. */
object JsLocalDatabaseQueryManager : DatabaseQueryManager {

  private suspend fun db(): Database = getIndexedDb()

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

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val database = db()
    database.writeTransaction(NODES_STORE, MOVES_STORE) {
      val nodesStore = objectStore(NODES_STORE)
      val movesStore = objectStore(MOVES_STORE)
      val originMoves: List<JsMoveEntity> =
        movesStore.index("origin").getAll(Key(position.value.toJsString())).toList()
      val destMoves: List<JsMoveEntity> =
        movesStore.index("destination").getAll(Key(position.value.toJsString())).toList()

      when (mode) {
        DeleteMode.HARD -> {
          for (move in originMoves + destMoves) {
            movesStore.delete(Key(move.origin.toJsString(), move.destination.toJsString()))
          }
          nodesStore.delete(Key(position.value.toJsString()))
        }
        DeleteMode.SOFT -> {
          val jsNode = nodesStore.get(Key(position.value.toJsString()))?.unsafeCast<JsNodeEntity>()
          if (jsNode != null && !jsNode.isDeleted) {
            jsNode.isDeleted = true
            nodesStore.put(jsNode)
          }
          for (move in originMoves + destMoves) {
            if (!move.isDeleted) {
              move.isDeleted = true
              movesStore.put(move)
            }
          }
        }
      }
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val database = db()
    database.writeTransaction(MOVES_STORE) {
      val movesStore = objectStore(MOVES_STORE)
      val moves: List<JsMoveEntity> =
        movesStore.index("origin").getAll(Key(origin.value.toJsString())).toList()
      for (m in moves) {
        if (m.move != move) continue
        when (mode) {
          DeleteMode.HARD ->
            movesStore.delete(Key(m.origin.toJsString(), m.destination.toJsString()))
          DeleteMode.SOFT ->
            if (!m.isDeleted) {
              m.isDeleted = true
              movesStore.put(m)
            }
        }
      }
    }
  }

  override suspend fun eraseAll() {
    val database = db()
    database.writeTransaction(NODES_STORE, MOVES_STORE) {
      objectStore(MOVES_STORE).clear()
      objectStore(NODES_STORE).clear()
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
