package proj.memorchess.axl.core.sync

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Id of one install taking part in sync, encoded on the wire as its bare string. Untrusted input
 * goes through [parseOrNull]; the constructor only rewraps a value that was already accepted.
 */
@JvmInline
@Serializable
value class DeviceId(val value: String) : Comparable<DeviceId> {

  override fun compareTo(other: DeviceId): Int = value.compareTo(other.value)

  companion object {

    /** Origin the server stamps on the tombstones of an erase, so no device may claim it. */
    val ERASE_ORIGIN: DeviceId = DeviceId(Uuid.NIL.toString())

    /** A fresh random device id. */
    fun random(): DeviceId = DeviceId(Uuid.random().toString())

    /**
     * The device [raw] names when it is a canonical (lowercase, hyphenated) UUID other than
     * [ERASE_ORIGIN], otherwise `null`.
     */
    fun parseOrNull(raw: String): DeviceId? {
      val uuid = Uuid.parseOrNull(raw) ?: return null
      if (uuid == Uuid.NIL || uuid.toString() != raw) return null
      return DeviceId(raw)
    }
  }
}
