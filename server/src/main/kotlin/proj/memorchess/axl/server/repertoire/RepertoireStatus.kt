package proj.memorchess.axl.server.repertoire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Moderation status of a published repertoire version.
 *
 * @property dbValue How the status is spelled in `repertoire_version.status` and on the wire.
 */
@Serializable
internal enum class RepertoireStatus(val dbValue: String) {

  /** Listed in the catalog. */
  @SerialName("published") PUBLISHED("published"),

  /** Reachable by id but kept out of the catalog listing. */
  @SerialName("unlisted") UNLISTED("unlisted"),

  /** Gone for good: no version of the id is served any more. */
  @SerialName("removed") REMOVED("removed");

  companion object {

    /**
     * The status [dbValue] names.
     *
     * @throws IllegalStateException when no entry is spelled [dbValue].
     */
    fun fromDb(dbValue: String): RepertoireStatus =
      entries.firstOrNull { it.dbValue == dbValue } ?: error("unknown status '$dbValue'")
  }
}
