package proj.memorchess.axl.test_util

import com.russhwolf.settings.Settings

/** A simple implementation of [Settings] for testing purposes, backed by a mutable map. */
class TestSettings : Settings {

  private val map = mutableMapOf<String, Any?>()

  override val keys: Set<String>
    get() = map.keys

  override val size: Int
    get() = map.size

  override fun clear() {
    map.clear()
  }

  override fun remove(key: String) {
    map.remove(key)
  }

  override fun hasKey(key: String): Boolean {
    return map.containsKey(key)
  }

  override fun putInt(key: String, value: Int) {
    map[key] = value
  }

  override fun getInt(key: String, defaultValue: Int): Int {
    return map[key] as? Int ?: defaultValue
  }

  override fun getIntOrNull(key: String): Int? {
    return map[key] as? Int
  }

  override fun putLong(key: String, value: Long) {
    map[key] = value
  }

  override fun getLong(key: String, defaultValue: Long): Long {
    return map[key] as? Long ?: defaultValue
  }

  override fun getLongOrNull(key: String): Long? {
    return map[key] as? Long
  }

  override fun putString(key: String, value: String) {
    map[key] = value
  }

  override fun getString(key: String, defaultValue: String): String {
    return map[key] as? String ?: defaultValue
  }

  override fun getStringOrNull(key: String): String? {
    return map[key] as? String
  }

  override fun putFloat(key: String, value: Float) {
    map[key] = value
  }

  override fun getFloat(key: String, defaultValue: Float): Float {
    return map[key] as? Float ?: defaultValue
  }

  override fun getFloatOrNull(key: String): Float? {
    return map[key] as? Float
  }

  override fun putDouble(key: String, value: Double) {
    map[key] = value
  }

  override fun getDouble(key: String, defaultValue: Double): Double {
    return map[key] as? Double ?: defaultValue
  }

  override fun getDoubleOrNull(key: String): Double? {
    return map[key] as? Double
  }

  override fun putBoolean(key: String, value: Boolean) {
    map[key] = value
  }

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return map[key] as? Boolean ?: defaultValue
  }

  override fun getBooleanOrNull(key: String): Boolean? {
    return map[key] as? Boolean
  }
}
