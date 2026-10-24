package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Registers one device, and reports a completed local wipe.
 *
 * @property platform [DevicePlatform.wireName], kept as a plain string on the wire so a platform
 *   added by a newer client never breaks an older peer's decoding. Callers work with the enum and
 *   convert here, at the boundary.
 * @property afterReset `true` only on the register call that follows a `410`, once the caller has
 *   wiped its synced local state.
 */
@Serializable
data class SyncDeviceRegisterRequest(val platform: String, val afterReset: Boolean = false)

/**
 * Whether one device has confirmed committing everything its user has.
 *
 * @property synced `true` when the device's acknowledgement is at or above the user's highest
 *   revision.
 */
@Serializable data class SyncDeviceStatusResponse(val synced: Boolean)

/**
 * Asks the server to erase everything its user has synced, on behalf of one device.
 *
 * @property device Canonical UUID of the calling device, which must be registered under the caller.
 * @property deviceSeq The caller's own sequence number at the moment of the erase.
 */
@Serializable data class SyncEraseRequest(val device: String, val deviceSeq: Long)

/**
 * Outcome of an erase.
 *
 * @property serverTime The server's clock at the moment of the erase.
 * @property revision Highest revision assigned by the erase, or `0` when nothing was live.
 */
@Serializable data class SyncEraseResponse(val serverTime: Instant, val revision: Long)

/**
 * Platforms a device can report.
 *
 * @property wireName How this platform is spelled on the wire and stored in `sync_device`.
 */
enum class DevicePlatform(val wireName: String) {

  /** Android, phone or tablet. */
  ANDROID("android"),

  /** iOS. */
  IOS("ios"),

  /** Desktop, on the JVM. */
  JVM("jvm"),

  /** The browser build, compiled to WebAssembly. */
  WASM_JS("wasmjs");

  companion object {

    /**
     * The platform [wireName] names, or `null` when this build has never heard of it.
     *
     * `null` rather than a throw for the same reason [SyncDeviceRegisterRequest.platform] is a
     * plain string: a platform added by a newer client must not break an older peer, which an enum
     * on the wire would turn into a decoding failure.
     */
    fun fromWire(wireName: String): DevicePlatform? = entries.firstOrNull {
      it.wireName == wireName
    }
  }
}
