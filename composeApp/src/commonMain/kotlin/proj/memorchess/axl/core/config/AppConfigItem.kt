package proj.memorchess.axl.core.config

import com.russhwolf.settings.get
import com.russhwolf.settings.set

/** A typed configuration item. */
@Suppress("UNCHECKED_CAST")
class AppConfigItem<StoredT : Any, T : Any>(
  val name: String,
  val defaultValue: T,
  val converter: ((StoredT) -> T),
  val serializer: ((T) -> StoredT),
) {
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

  /**
   * Gets the value from settings, or the default value if it doesn't exist.
   *
   * @return The value from settings, or the default value if it doesn't exist.
   */
  fun getValue(): T {
    val defaultStoredValue = serializer(defaultValue)
    val value =
      when (defaultStoredValue) {
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

  /**
   * Sets the value to settings.
   *
   * @param value The value to set.
   */
  fun setValue(value: T) {
    val valueToStore = serializer(value)
    when (valueToStore) {
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

  /** Resets the value to the default value. */
  fun reset() {
    settings.remove(name)
  }
}
