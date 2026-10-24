package proj.memorchess.axl.core.sync

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.TokenResult

/** Outcome of [SyncEngine.eraseEverywhere]. */
sealed interface EraseOutcome {
  /**
   * Erased on the server and this device follows, or erased on this device only when signed out.
   * This device may lag until its next pull if its local wipe failed.
   */
  data object Erased : EraseOutcome

  /** The session expired; nothing was deleted and the user must sign in again. */
  data object SignInRequired : EraseOutcome

  /**
   * The server could not be reached or refused the erase. Nothing was deleted on this device; the
   * server may still have erased after a timeout.
   */
  data object Failed : EraseOutcome
}

/**
 * Erases the user's synced data on the server, then on this device. Signed out, erases this device
 * only and leaves a reset pending in [jobStore] for the next sign in. Deletes nothing locally
 * unless the server erase succeeded.
 */
internal suspend fun runErase(
  authProvider: AuthProvider,
  applier: SyncApplier,
  apiClient: SyncApiClient,
  deviceIdentity: DeviceIdentity,
  jobStore: SyncJobStore,
): EraseOutcome {
  val token =
    when (val result = authProvider.accessToken()) {
      is TokenResult.Ok -> result.accessToken
      TokenResult.SignedOut -> {
        applier.eraseAll()
        // Only once the wipe is done: reporting a reset over rows still present would let them
        // outlive tombstones the server has already collected.
        jobStore.resetPending = true
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
