package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
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
  if (!registerForErase(token, deviceId, apiClient)) return EraseOutcome.Failed

  return when (val outcome = apiClient.erase(token, deviceId, deviceIdentity.nextDeviceSeq())) {
    is SyncEraseOutcome.Ok -> {
      applier.eraseAll()
      EraseOutcome.Erased
    }
    else -> {
      LOGGER.w { "Erase refused: $outcome" }
      EraseOutcome.Failed
    }
  }
}

/**
 * Registers this device, which the server requires before an erase. A device asked to resync
 * reports a reset once, since the erase wipes it right after.
 */
private suspend fun registerForErase(
  token: String,
  deviceId: String,
  apiClient: SyncApiClient,
): Boolean {
  val platform = currentPlatform()
  var outcome = apiClient.registerDevice(token, deviceId, platform, afterReset = false)
  if (outcome == SyncRegisterOutcome.ResyncRequired) {
    outcome = apiClient.registerDevice(token, deviceId, platform, afterReset = true)
  }
  if (outcome != SyncRegisterOutcome.Ok) LOGGER.w { "Register before erase failed: $outcome" }
  return outcome == SyncRegisterOutcome.Ok
}

private val LOGGER = Logger.withTag("SyncErase")
