package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.TokenResult

/** Outcome of [SyncEngine.eraseEverywhere]. */
sealed interface EraseOutcome {
  /** The user's synced data is gone: from the server and this device, or this device only. */
  data object Erased : EraseOutcome

  /** The session expired; nothing was deleted and the user must sign in again. */
  data object SignInRequired : EraseOutcome

  /** The server could not be reached or refused the erase; nothing was deleted. */
  data object Failed : EraseOutcome
}

/**
 * Erases the user's synced data on the server, then on this device. Signed out, erases this device
 * only. Deletes nothing locally unless the server erase succeeded.
 */
internal suspend fun runErase(
  authProvider: AuthProvider,
  applier: SyncApplier,
  apiClient: SyncApiClient,
  deviceIdentity: DeviceIdentity,
): EraseOutcome {
  val token =
    when (val result = authProvider.accessToken()) {
      is TokenResult.Ok -> result.accessToken
      TokenResult.SignedOut -> {
        applier.eraseAll()
        return EraseOutcome.Erased
      }
      TokenResult.Failed.Terminal -> return EraseOutcome.SignInRequired
      TokenResult.Failed.Transient -> return EraseOutcome.Failed
    }

  val deviceId = deviceIdentity.originDevice
  // A device asked to resync is refused rather than reported as reset: the erase may still fail,
  // and the server would then believe a device with live rows had wiped. The next cycle resyncs it.
  val registered = apiClient.registerDevice(token, deviceId, currentPlatform(), afterReset = false)
  if (registered != SyncRegisterOutcome.Ok) {
    LOGGER.w { "Register before erase failed: $registered" }
    return EraseOutcome.Failed
  }

  val outcome = apiClient.erase(token, deviceId, deviceIdentity.nextDeviceSeq())
  if (outcome !is SyncEraseOutcome.Ok) {
    LOGGER.w { "Erase refused: $outcome" }
    return EraseOutcome.Failed
  }
  try {
    applier.eraseAll()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    // The server erase is the truth: this device converges when it pulls its own tombstones.
    LOGGER.w(e) { "Local wipe after the server erase failed" }
  }
  return EraseOutcome.Erased
}

private val LOGGER = Logger.withTag("SyncErase")
