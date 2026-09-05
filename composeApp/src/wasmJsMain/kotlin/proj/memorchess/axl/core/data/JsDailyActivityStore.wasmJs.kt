@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package proj.memorchess.axl.core.data

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString
import kotlinx.datetime.LocalDate

/** JS object stored in the [DAILY_ACTIVITY_STORE] object store. */
private external interface JsDailyActivityEntity : JsAny {
  var date: String
  var cardsReviewed: Int
  var isActive: Boolean
  var streakLength: Int
}

private fun <T : JsAny> emptyObject(): T = js("({})")

private fun JsDailyActivityEntity.toRecord(): DailyActivityRecord =
  DailyActivityRecord(LocalDate.parse(date), cardsReviewed, isActive, streakLength)

private fun DailyActivityRecord.toJsEntity(): JsDailyActivityEntity {
  val entity = emptyObject<JsDailyActivityEntity>()
  entity.date = date.toString()
  entity.cardsReviewed = cardsReviewed
  entity.isActive = isActive
  entity.streakLength = streakLength
  return entity
}

/** IndexedDB backed [DailyActivityStore] for wasmJs. */
internal object JsDailyActivityStore : DailyActivityStore {

  private suspend fun db(): Database = getIndexedDb()

  override suspend fun getRecord(date: LocalDate): DailyActivityRecord? {
    val database = db()
    return database.transaction(DAILY_ACTIVITY_STORE) {
      objectStore(DAILY_ACTIVITY_STORE)
        .get(Key(date.toString().toJsString()))
        ?.unsafeCast<JsDailyActivityEntity>()
        ?.toRecord()
    }
  }

  override suspend fun putRecord(record: DailyActivityRecord) {
    val database = db()
    database.writeTransaction(DAILY_ACTIVITY_STORE) {
      objectStore(DAILY_ACTIVITY_STORE).put(record.toJsEntity())
    }
  }

  override suspend fun eraseAll() {
    val database = db()
    database.writeTransaction(DAILY_ACTIVITY_STORE) { objectStore(DAILY_ACTIVITY_STORE).clear() }
  }
}

actual fun getPlatformSpecificDailyActivityStore(): DailyActivityStore {
  return JsDailyActivityStore
}
