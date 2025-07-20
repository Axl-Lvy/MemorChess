package proj.memorchess.axl.core.config

import com.russhwolf.settings.get
import com.russhwolf.settings.set

/** A typed configuration item. */
@Suppress("UNCHECKED_CAST")
class ValueBasedAppConfigItem<StoredT : Any, T : Any>(
  override val name: String,
  override val defaultValue: T,
  val converter: ((StoredT) -> T),
  val serializer: ((T) -> StoredT),
) : ConfigItem<T> {
  constructor(
    name: String,
    defaultValue: T,
  ) : this(name, defaultValue, { it as T }, { it as StoredT })

  private val needConversion: Boolean

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
    val defaultStoredValue = serializer(defaultValue)
    val value =
      when (defaultStoredValue) {
        is String -> SETTINGS[name, defaultStoredValue]
        is Boolean -> SETTINGS[name, defaultStoredValue]
        is Int -> SETTINGS[name, defaultStoredValue]
        is Long -> SETTINGS[name, defaultStoredValue]
        is Float -> SETTINGS[name, defaultStoredValue]
        is Double -> SETTINGS[name, defaultStoredValue]
        else ->
          throw IllegalArgumentException(
            "Unsupported value type: ${defaultStoredValue::class.simpleName}"
          )
      }
    return converter(value as StoredT)
  }

  override fun setValue(value: T) {
    val valueToStore = serializer(value)
    when (valueToStore) {
      is String -> SETTINGS[name] = valueToStore
      is Boolean -> SETTINGS[name] = valueToStore
      is Int -> SETTINGS[name] = valueToStore
      is Long -> SETTINGS[name] = valueToStore
      is Float -> SETTINGS[name] = valueToStore
      is Double -> SETTINGS[name] = valueToStore
      else ->
        throw IllegalArgumentException("Unsupported value type: ${valueToStore::class.simpleName}")
    }
  }

  override fun reset() {
    SETTINGS.remove(name)
  }
}
