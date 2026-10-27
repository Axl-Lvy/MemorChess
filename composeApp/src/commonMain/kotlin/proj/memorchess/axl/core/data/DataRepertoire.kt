package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil

/**
 * A repertoire registry row.
 *
 * @property id Stable slug: a catalog descriptor id, or a slugified user chosen name.
 * @property color Perspective, or `null` when the repertoire mixes both sides.
 * @property installedVersion Catalog version installed under [id], or `null` for a user created
 *   one.
 * @property originDevice Device that wrote this version.
 * @property deviceSeq That device's write counter at the time.
 */
data class DataRepertoire(
  val id: String,
  val name: String,
  val color: RepertoireColor?,
  val installedVersion: Int? = null,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
)
