package proj.memorchess.axl.core.data.online

import kotlin.uuid.Uuid
import proj.memorchess.axl.core.config.USER_ID_SETTING

/**
 * Provides a stable user UUID that persists across app launches.
 *
 * The UUID identifies the user (not the device), so it can be shared across devices by exporting
 * and importing settings.
 */
class UserIdProvider {

  /** Returns the user's UUID, generating and persisting a new one if absent. */
  fun getUserId(): String {
    val existing = USER_ID_SETTING.getValue()
    if (existing.isNotEmpty()) return existing
    val newId = Uuid.random().toString()
    USER_ID_SETTING.setValue(newId)
    return newId
  }
}
