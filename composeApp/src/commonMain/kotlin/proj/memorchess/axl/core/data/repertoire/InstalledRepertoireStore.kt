package proj.memorchess.axl.core.data.repertoire

import proj.memorchess.axl.core.config.INSTALLED_REPERTOIRES_SETTING

/**
 * Tracks which catalog repertoires are installed on this device.
 *
 * The ids are persisted as a comma joined string in [INSTALLED_REPERTOIRES_SETTING], which is part
 * of `ALL_SETTINGS_ITEMS` so the settings reset flow clears it. Catalog ids are slugs (for example
 * `london-system-white`) and therefore never contain the comma separator.
 */
class InstalledRepertoireStore {

  /** Returns the ids of every installed repertoire. */
  fun installedIds(): Set<String> =
    INSTALLED_REPERTOIRES_SETTING.getValue().split(SEPARATOR).filter { it.isNotBlank() }.toSet()

  /** Returns whether the repertoire with [id] is installed. */
  fun isInstalled(id: String): Boolean = id in installedIds()

  /**
   * Records the repertoire with [id] as installed. Idempotent.
   *
   * @throws IllegalArgumentException if [id] is blank or contains the comma separator.
   */
  fun markInstalled(id: String) {
    require(id.isNotBlank()) { "Repertoire id must not be blank" }
    require(SEPARATOR !in id) { "Repertoire id must not contain '$SEPARATOR': $id" }
    save(installedIds() + id)
  }

  /** Removes the repertoire with [id] from the installed set. Idempotent. */
  fun unmarkInstalled(id: String) {
    save(installedIds() - id)
  }

  private fun save(ids: Set<String>) {
    INSTALLED_REPERTOIRES_SETTING.setValue(ids.sorted().joinToString(SEPARATOR))
  }

  private companion object {
    const val SEPARATOR = ","
  }
}
