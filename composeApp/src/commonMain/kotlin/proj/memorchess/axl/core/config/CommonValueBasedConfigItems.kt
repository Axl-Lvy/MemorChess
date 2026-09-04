package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import proj.memorchess.axl.SETTINGS_SYNC_SCOPE
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey

class TimeBasedConfig(name: String, defaultValue: Instant) :
  ValueBasedAppConfigItem<Long, Instant>(
    name,
    defaultValue,
    { milliseconds -> Instant.fromEpochMilliseconds(milliseconds) },
    { instant -> instant.toEpochMilliseconds() },
  )

class DurationBasedConfigItem(name: String, defaultValue: Duration) :
  ValueBasedAppConfigItem<Long, Duration>(
    name,
    defaultValue,
    { milliseconds -> milliseconds.milliseconds },
    { duration -> duration.inWholeMilliseconds },
  )

class StringBasedConfig(name: String, defaultValue: String) :
  ValueBasedAppConfigItem<String, String>(name, defaultValue)

class IntBasedConfigItem(name: String, defaultValue: Int) :
  ValueBasedAppConfigItem<Int, Int>(name, defaultValue)

class DoubleBasedConfigItem(name: String, defaultValue: Double) :
  ValueBasedAppConfigItem<Double, Double>(name, defaultValue)

class BooleanBasedConfigItem(name: String, defaultValue: Boolean) :
  ValueBasedAppConfigItem<Boolean, Boolean>(name, defaultValue)

/** A typed configuration item. */
@Suppress("UNCHECKED_CAST")
sealed class ValueBasedAppConfigItem<StoredT : Any, T : Any>(
  override val name: String,
  override val defaultValue: T,
  val converter: ((StoredT) -> T),
  val serializer: ((T) -> StoredT),
) : ConfigItem<T>, KoinComponent {
  protected constructor(
    name: String,
    defaultValue: T,
  ) : this(name, defaultValue, { it as T }, { it as StoredT })

  private val needConversion: Boolean

  private val settings: Settings by inject()
  private val syncMetadata: SettingSyncMetadataStore by inject()
  private val syncScope: CoroutineScope by inject(named(SETTINGS_SYNC_SCOPE))
  private val database: DatabaseQueryManager by inject()

  /** Checks if the default value type is supported. */
  init {
    if (
      !(defaultValue is String ||
        defaultValue is Boolean ||
        defaultValue is Int ||
        defaultValue is Long ||
        defaultValue is Float ||
        defaultValue is Double)
    ) {
      try {
        converter(serializer(defaultValue))
      } catch (e: ClassCastException) {
        throw IllegalArgumentException(
          "The conversion pipeline is incorrect. Did you provide a converter and a serializer?",
          e,
        )
      }
      needConversion = true
    } else {
      needConversion = false
    }
  }

  override fun getValue(): T {
    val value =
      when (val defaultStoredValue = serializer(defaultValue)) {
        is String -> settings[name, defaultStoredValue]
        is Boolean -> settings[name, defaultStoredValue]
        is Int -> settings[name, defaultStoredValue]
        is Long -> settings[name, defaultStoredValue]
        is Float -> settings[name, defaultStoredValue]
        is Double -> settings[name, defaultStoredValue]
        else ->
          throw IllegalArgumentException(
            "Unsupported value type: ${defaultStoredValue::class.simpleName}"
          )
      }
    return converter(value as StoredT)
  }

  override fun setValue(value: T) {
    when (val valueToStore = serializer(value)) {
      is String -> settings[name] = valueToStore
      is Boolean -> settings[name] = valueToStore
      is Int -> settings[name] = valueToStore
      is Long -> settings[name] = valueToStore
      is Float -> settings[name] = valueToStore
      is Double -> settings[name] = valueToStore
      else ->
        throw IllegalArgumentException("Unsupported value type: ${valueToStore::class.simpleName}")
    }
    stampAndMarkDirty(name, isDeleted = false, syncMetadata, database, syncScope)
  }

  /**
   * Resets the value to the default value and stamps a tombstone.
   *
   * The sync metadata's `isDeleted` flips to `true` rather than merely bumping the sequence: reset
   * changes the effective value (back to [defaultValue]), and the sync design needs metadata to
   * change whenever the effective value does. Treating it as a tombstone rather than a live value
   * change also gives it cleaner wire semantics once a `SyncEngine` exists: a peer applies the
   * tombstone by removing its own copy of the key and falling back to its own [defaultValue],
   * instead of adopting this device's default verbatim, which would be wrong across a version skew
   * where devices disagree on what the default is.
   */
  override fun reset() {
    settings.remove(name)
    stampAndMarkDirty(name, isDeleted = true, syncMetadata, database, syncScope)
  }
}

/**
 * Stamps [name]'s sync metadata and queues it in the outbox, at the same `deviceSeq`.
 *
 * [ConfigItem.setValue] and [ConfigItem.reset] are synchronous but [DatabaseQueryManager.markDirty]
 * is suspend, so the outbox write is fired and forgotten on [syncScope] rather than blocking the
 * caller; [stamp][SettingSyncMetadataStore.stamp] itself is synchronous and runs inline, so the
 * sync metadata is never stale relative to the value it describes. [metadataStore] and [database]
 * are resolved outside the launched block, synchronously, so the Koin lookup always runs while the
 * call site's own Koin scope is still open rather than racing a later teardown.
 */
internal fun stampAndMarkDirty(
  name: String,
  isDeleted: Boolean,
  metadataStore: SettingSyncMetadataStore,
  database: DatabaseQueryManager,
  syncScope: CoroutineScope,
) {
  val seq = metadataStore.stamp(name, isDeleted)
  syncScope.launch { database.markDirty(DirtyKey.SettingKey(name), seq) }
}
