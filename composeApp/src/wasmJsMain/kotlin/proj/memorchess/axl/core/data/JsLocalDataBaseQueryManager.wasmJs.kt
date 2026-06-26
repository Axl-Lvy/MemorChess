@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Cursor
import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.bound
import com.juul.indexeddb.lowerBound
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.set
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.time.Instant
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainingEntry
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
  var firstReview: Double // epoch seconds, 0 means null (never reviewed card)
  var stability: Double
  var difficulty: Double
  var reps: Int
  var lapses: Int
  var phase: String // CardPhase name
  var step: Int
  var depth: Int
  var hasGoodOutgoing: Int // 0/1; IndexedDB cannot range-index a JS boolean directly
  var createdAt: Double // epoch seconds (Double avoids Long to BigInt)
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
  val firstReviewSec = firstReview.toLong()
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
        firstReview = if (firstReviewSec == 0L) null else Instant.fromEpochSeconds(firstReviewSec),
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
    hasGoodOutgoing = hasGoodOutgoing == 1,
    createdAt = Instant.fromEpochSeconds(createdAt.toLong()),
  )
}

/**
 * Builds a [proj.memorchess.axl.core.graph.TrainingEntry] from a node row without loading any
 * edges, the IndexedDB counterpart of the Room `NodeCardProjection` mapping.
 */
private fun JsNodeEntity.toTrainingEntry(): TrainingEntry {
  val lastReviewSec = lastReview.toLong()
  val firstReviewSec = firstReview.toLong()
  return TrainingEntry(
    PositionKey(positionKey),
    CardState(
      dueDate = Instant.fromEpochSeconds(dueDate.toLong()),
      lastReview = if (lastReviewSec == 0L) null else Instant.fromEpochSeconds(lastReviewSec),
      firstReview = if (firstReviewSec == 0L) null else Instant.fromEpochSeconds(firstReviewSec),
      stability = stability,
      difficulty = difficulty,
      reps = reps,
      lapses = lapses,
      phase = runCatching { CardPhase.valueOf(phase) }.getOrDefault(CardPhase.NEW),
      step = step,
    ),
  )
}

// ---------------------------------------------------------------------------
// Compound IndexedDB key range helpers
// ---------------------------------------------------------------------------

/** Builds a JS array usable as a compound IndexedDB key from already-JS values. */
private fun jsKeyArray(vararg elements: JsAny): JsArray<JsAny> {
  val array = JsArray<JsAny>()
  for ((index, element) in elements.withIndex()) {
    array[index] = element
  }
  return array
}

private fun Int.toJsKey(): JsAny = toDouble().toJsNumber()

private fun Double.toJsKey(): JsAny = toJsNumber()

// Sentinels spanning the full ordered domain of a compound key slot.
private val LOW_NUMBER: JsAny = Double.NEGATIVE_INFINITY.toJsNumber()
private val HIGH_NUMBER: JsAny = Double.POSITIVE_INFINITY.toJsNumber()

// ---------------------------------------------------------------------------
// Conversion: domain → JS
// ---------------------------------------------------------------------------

private fun DataNode.toJsNodeEntity(): JsNodeEntity {
  val node = this
  return emptyObject<JsNodeEntity>().apply {
    positionKey = node.positionKey.value
    dueDate = node.cardState.dueDate.epochSeconds.toDouble()
    lastReview = node.cardState.lastReview?.epochSeconds?.toDouble() ?: 0.0
    firstReview = node.cardState.firstReview?.epochSeconds?.toDouble() ?: 0.0
    stability = node.cardState.stability
    difficulty = node.cardState.difficulty
    reps = node.cardState.reps
    lapses = node.cardState.lapses
    phase = node.cardState.phase.name
    step = node.cardState.step
    depth = node.depth
    hasGoodOutgoing = if (node.hasGoodOutgoing) 1 else 0
    createdAt = node.createdAt.epochSeconds.toDouble()
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
internal const val DB_VERSION = 7

/** Compound-key value for `hasGoodOutgoing = true`, encoded as the integer `1`. */
private val GOOD: JsAny = 1.toJsKey()

/** The two in-session phases, used to span both LEARNING and RELEARNING with bounded queries. */
private val IN_SESSION_PHASES = listOf(CardPhase.LEARNING.name, CardPhase.RELEARNING.name)

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

  override suspend fun getNodesPage(cursor: String?, limit: Int): NodesPage {
    require(limit > 0) { "Page limit must be strictly positive, was $limit" }
    val database = db()
    return database.transaction(NODES_STORE, MOVES_STORE) {
      val nodesStore = objectStore(NODES_STORE)
      val movesStore = objectStore(MOVES_STORE)
      // Primary key range over positionKey: open lower bound skips the cursor itself so the page
      // starts strictly after it, mirroring the SQL `positionKey > :cursor`. With a null cursor the
      // whole store is in range and the cursor walks from the smallest key ascending.
      val range = cursor?.let { lowerBound(it.toJsString(), open = true) }
      val live = mutableListOf<JsNodeEntity>()
      nodesStore
        .openCursor(range, Cursor.Direction.Next)
        .map { it.value as JsNodeEntity }
        // Soft deleted rows are skipped in place so the page holds up to `limit` live nodes,
        // exactly
        // like the Room `WHERE isDeleted IS FALSE ... LIMIT :limit` query.
        .firstOrNull { jsNode ->
          if (!jsNode.isDeleted) {
            live.add(jsNode)
          }
          live.size >= limit
        }

      val nodes = live.map { jsNode ->
        val key = jsNode.positionKey
        val nextMoves: List<JsMoveEntity> =
          movesStore.index("origin").getAll(Key(key.toJsString())).toList()
        val previousMoves: List<JsMoveEntity> =
          movesStore.index("destination").getAll(Key(key.toJsString())).toList()
        jsNode.toDataNode(
          previousMoves = previousMoves.map { it.toDataMove() },
          nextMoves = nextMoves.map { it.toDataMove() },
        )
      }
      val nextCursor = if (nodes.size == limit) nodes.last().positionKey.value else null
      NodesPage(nodes, nextCursor)
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

  override suspend fun nextReadyLearningCard(now: Instant): TrainingEntry? =
    nextLearningCard(dueBound = now.epochSeconds.toDouble(), ready = true)

  override suspend fun nextPendingLearningCard(now: Instant): TrainingEntry? =
    nextLearningCard(dueBound = now.epochSeconds.toDouble(), ready = false)

  /**
   * Earliest-due in-session card. [ready] selects `dueDate <= now`, otherwise `dueDate > now`. Runs
   * one bounded cursor per in-session phase on the `good_phase_due` index and keeps the smaller due
   * date, mirroring the SQL `phase IN (LEARNING, RELEARNING) ORDER BY dueDate ASC LIMIT 1`. Soft
   * deleted rows are skipped so the result matches the Room `WHERE isDeleted = 0` queries.
   */
  private suspend fun nextLearningCard(dueBound: Double, ready: Boolean): TrainingEntry? {
    val database = db()
    return database.transaction(NODES_STORE) {
      val index = objectStore(NODES_STORE).index("good_phase_due")
      var best: JsNodeEntity? = null
      for (phase in IN_SESSION_PHASES) {
        val range =
          if (ready) {
            bound(
              jsKeyArray(GOOD, phase.toJsString(), LOW_NUMBER),
              jsKeyArray(GOOD, phase.toJsString(), dueBound.toJsKey()),
            )
          } else {
            bound(
              jsKeyArray(GOOD, phase.toJsString(), dueBound.toJsKey()),
              jsKeyArray(GOOD, phase.toJsString(), HIGH_NUMBER),
              lowerOpen = true,
            )
          }
        // The index orders by due date ascending, so the first non-deleted row of this phase is the
        // earliest-due live candidate; deleted rows are skipped in place.
        val candidate =
          index
            .openCursor(range, Cursor.Direction.Next)
            .map { it.value as JsNodeEntity }
            .firstOrNull { !it.isDeleted }
        if (candidate != null && (best == null || candidate.dueDate < best.dueDate)) {
          best = candidate
        }
      }
      best?.toTrainingEntry()
    }
  }

  override suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry? {
    val database = db()
    val end = dayEndExclusive.epochSeconds.toDouble()
    return database.transaction(NODES_STORE) {
      dueGoodRows(phase = CardPhase.REVIEW.name, dayEnd = end)
        .minWithOrNull(compareBy { it.depth })
        ?.toTrainingEntry()
    }
  }

  override suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry? {
    val database = db()
    val end = dayEndExclusive.epochSeconds.toDouble()
    return database.transaction(NODES_STORE) {
      dueGoodRows(phase = CardPhase.NEW.name, dayEnd = end)
        .minWithOrNull(compareBy({ it.depth }, { it.createdAt }))
        ?.toTrainingEntry()
    }
  }

  /**
   * All trainable, live rows of [phase] due before [dayEnd], read from the bounded compound range
   * on `good_phase_due_depth_created`. The set is bounded to the due rows of one phase; the caller
   * reduces it by the tier's ordering (`depth`, or `depth` then `createdAt`) because the index
   * orders by due date first. Soft deleted rows are excluded to match the Room `WHERE isDeleted =
   * 0` queries.
   */
  private suspend fun com.juul.indexeddb.Transaction.dueGoodRows(
    phase: String,
    dayEnd: Double,
  ): List<JsNodeEntity> {
    val range =
      bound(
        jsKeyArray(GOOD, phase.toJsString(), LOW_NUMBER, LOW_NUMBER, LOW_NUMBER),
        jsKeyArray(GOOD, phase.toJsString(), dayEnd.toJsKey(), LOW_NUMBER, LOW_NUMBER),
        upperOpen = true,
      )
    return objectStore(NODES_STORE)
      .index("good_phase_due_depth_created")
      .openCursor(range, Cursor.Direction.Next)
      .map { it.value as JsNodeEntity }
      .toList()
      .filter { !it.isDeleted }
  }

  override suspend fun getSchedulingCounts(
    dayStart: Instant,
    dayEndExclusive: Instant,
  ): SchedulingCounts {
    val database = db()
    val start = dayStart.epochSeconds.toDouble()
    val end = dayEndExclusive.epochSeconds.toDouble()
    return database.transaction(NODES_STORE) {
      val store = objectStore(NODES_STORE)
      // firstReview/lastReview store the "never reviewed" null as the 0.0 sentinel, so a day window
      // that begins at epoch 0 would otherwise sweep those nulls in. Excluding values <= 0.0 keeps
      // parity with Room (NULL excluded) and InMemory regardless of where the window starts.
      val introduced =
        store
          .index("firstReview")
          .openCursor(
            bound(start.toJsKey(), end.toJsKey(), upperOpen = true),
            Cursor.Direction.Next,
          )
          .map { it.value as JsNodeEntity }
          .toList()
          .count { !it.isDeleted && it.firstReview > 0.0 }
      val trained =
        store
          .index("lastReview")
          .openCursor(
            bound(start.toJsKey(), end.toJsKey(), upperOpen = true),
            Cursor.Direction.Next,
          )
          .map { it.value as JsNodeEntity }
          .toList()
          .count { !it.isDeleted && it.lastReview > 0.0 }
      val dueReviews = dueGoodRows(phase = CardPhase.REVIEW.name, dayEnd = end).size
      val dueNew = dueGoodRows(phase = CardPhase.NEW.name, dayEnd = end).size
      val inSession = IN_SESSION_PHASES.sumOf { phase ->
        store
          .index("good_phase_due")
          .openCursor(
            bound(
              jsKeyArray(GOOD, phase.toJsString(), LOW_NUMBER),
              jsKeyArray(GOOD, phase.toJsString(), HIGH_NUMBER),
            ),
            Cursor.Direction.Next,
          )
          .map { it.value as JsNodeEntity }
          .toList()
          .count { !it.isDeleted }
      }
      SchedulingCounts(
        introducedToday = introduced,
        trainedToday = trained,
        dueReviews = dueReviews,
        dueNew = dueNew,
        inSession = inSession,
      )
    }
  }

  override suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
  ): TrainingEntry? {
    if (keys.isEmpty()) return null
    val database = db()
    val end = dayEndExclusive.epochSeconds.toDouble()
    return database.transaction(NODES_STORE) {
      val store = objectStore(NODES_STORE)
      // Bounded by the candidate count (branching factor): one primary-key get per key, in order.
      for (key in keys) {
        val node = store.get(Key(key.value.toJsString()))?.unsafeCast<JsNodeEntity>() ?: continue
        if (node.isDeleted || node.hasGoodOutgoing != 1) continue
        val inSession =
          node.phase == CardPhase.LEARNING.name || node.phase == CardPhase.RELEARNING.name
        if (inSession || node.dueDate < end) return@transaction node.toTrainingEntry()
      }
      null
    }
  }

  override suspend fun countDescendants(key: PositionKey, cap: Int): Int {
    if (cap <= 0) return 0
    if (!nodeExistsLive(key.value)) return 0
    return cappedDescendantCount(key, cap) { liveSingleParentChildren(it) }
  }

  /** True when [positionKey] has a non-deleted node row. */
  private suspend fun nodeExistsLive(positionKey: String): Boolean {
    val database = db()
    return database.transaction(NODES_STORE) {
      val node =
        objectStore(NODES_STORE).get(Key(positionKey.toJsString()))?.unsafeCast<JsNodeEntity>()
      node != null && !node.isDeleted
    }
  }

  /**
   * Non-deleted children of [origin] whose only non-deleted incoming edge comes from within the
   * subtree (incoming count at most one), resolved with index lookups. A convergent position
   * reachable through an outside parent is excluded.
   */
  private suspend fun liveSingleParentChildren(origin: PositionKey): List<PositionKey> {
    val candidates = childMoveDestinations(origin.value)
    val result = mutableListOf<PositionKey>()
    for (child in candidates) {
      if (isLiveSingleParent(child)) result.add(PositionKey(child))
    }
    return result
  }

  /** Destinations of every non-deleted move leaving [origin]. */
  private suspend fun childMoveDestinations(origin: String): List<String> {
    val database = db()
    return database.transaction(MOVES_STORE) {
      val moves: List<JsMoveEntity> =
        objectStore(MOVES_STORE).index("origin").getAll(Key(origin.toJsString())).toList()
      moves.filterNot { it.isDeleted }.map { it.destination }
    }
  }

  /** True when [child] is a live node reached by at most one non-deleted incoming edge. */
  private suspend fun isLiveSingleParent(child: String): Boolean {
    val database = db()
    return database.transaction(NODES_STORE, MOVES_STORE) {
      val node = objectStore(NODES_STORE).get(Key(child.toJsString()))?.unsafeCast<JsNodeEntity>()
      if (node == null || node.isDeleted) {
        false
      } else {
        val incoming: List<JsMoveEntity> =
          objectStore(MOVES_STORE).index("destination").getAll(Key(child.toJsString())).toList()
        incoming.count { !it.isDeleted } <= 1
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
