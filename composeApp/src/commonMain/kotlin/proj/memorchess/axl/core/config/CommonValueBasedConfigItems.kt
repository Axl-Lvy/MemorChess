package proj.memorchess.axl.core.config

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
  }

  override fun reset() {
    settings.remove(name)
  }
}
