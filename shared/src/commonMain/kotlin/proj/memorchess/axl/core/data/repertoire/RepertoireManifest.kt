package proj.memorchess.axl.core.data.repertoire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catalog manifest served at `/v1/repertoires/manifest.json`. Only [schemaVersion] 2 is accepted,
 * since a version bump may change field semantics.
 *
 * @property repertoires The downloadable repertoires, in catalog order.
 */
@Serializable
data class RepertoireManifest(val schemaVersion: Int, val repertoires: List<RepertoireDescriptor>)

/**
 * One downloadable repertoire listed in the [RepertoireManifest].
 *
 * @property id Stable slug such as `london-system-white`.
 * @property moveCount Informational only; may be zero.
 * @property version Latest published version, which names the moves to download.
 * @property downloadCount Installs across every user; anonymous and spammable, so only a popularity
 *   hint. Defaults to `0` so an older cached manifest still decodes.
 */
@Serializable
data class RepertoireDescriptor(
  val id: String,
  val name: String,
  val color: RepertoireColor,
  val description: String,
  val moveCount: Int,
  val version: Int,
  val downloadCount: Int = 0,
)

/** Side a repertoire is built for. */
@Serializable
enum class RepertoireColor {
  /** The repertoire teaches lines from the white side. */
  @SerialName("white") WHITE,

  /** The repertoire teaches lines from the black side. */
  @SerialName("black") BLACK,
}
