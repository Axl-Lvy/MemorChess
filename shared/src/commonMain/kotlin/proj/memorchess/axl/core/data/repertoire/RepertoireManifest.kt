package proj.memorchess.axl.core.data.repertoire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catalog manifest published on the `repertoire-data` branch.
 *
 * Decoded with `ignoreUnknownKeys` so future additive fields are tolerated. Only [schemaVersion] 1
 * is accepted; any other value is treated as malformed because a version bump may change field
 * semantics, and failing loudly (with the stale cache fallback) is safer than a best effort parse
 * of an unknown layout.
 *
 * @property schemaVersion Version of the manifest layout. The client accepts version 1 only.
 * @property repertoires The repertoires available for download, in catalog order.
 */
@Serializable
data class RepertoireManifest(val schemaVersion: Int, val repertoires: List<RepertoireDescriptor>)

/**
 * One downloadable repertoire listed in the [RepertoireManifest].
 *
 * @property id Stable unique identifier, a slug such as `london-system-white`.
 * @property name Human readable name shown in the library.
 * @property color Side the repertoire is built for.
 * @property description Short marketing style description of the repertoire.
 * @property moveCount Number of moves in the PGN, informational only. May be zero.
 * @property file Path of the PGN file relative to the catalog base URL, such as
 *   `pgn/london-system-white.pgn`.
 */
@Serializable
data class RepertoireDescriptor(
  val id: String,
  val name: String,
  val color: RepertoireColor,
  val description: String,
  val moveCount: Int,
  val file: String,
)

/** Side a repertoire is built for. */
@Serializable
enum class RepertoireColor {
  /** The repertoire teaches lines from the white side. */
  @SerialName("white") WHITE,

  /** The repertoire teaches lines from the black side. */
  @SerialName("black") BLACK,
}
