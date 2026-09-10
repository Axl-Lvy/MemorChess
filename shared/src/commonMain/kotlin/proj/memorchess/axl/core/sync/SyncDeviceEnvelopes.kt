package proj.memorchess.axl.core.sync

import kotlinx.serialization.Serializable

/**
 * Registers one device, and reports a completed local wipe.
 *
 * @property platform One of [DevicePlatform], as a plain string.
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
 * Platforms a device can report.
 *
 * Plain strings for the same reason as [RejectionCode]: a platform added by a newer client must not
 * break an older server's decoding.
 */
object DevicePlatform {

  /** Android, phone or tablet. */
  const val ANDROID: String = "android"

  /** iOS. */
  const val IOS: String = "ios"

  /** Desktop, on the JVM. */
  const val JVM: String = "jvm"

  /** The browser build, compiled to WebAssembly. */
  const val WASM_JS: String = "wasmjs"
}
