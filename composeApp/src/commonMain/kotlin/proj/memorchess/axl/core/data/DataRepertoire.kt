package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil

/**
 * Data class representing a repertoire registry row in the database.
 *
 * @property id Stable slug: a catalog descriptor id, or a slugified user chosen name.
 * @property name Display name shown in the library and picker.
 * @property color Perspective, or `null` when the repertoire mixes both sides.
 * @property isDeleted Whether this row has been deleted.
 * @property updatedAt Date at which this row was last updated.
 * @property originDevice Device that wrote this version. Stamped by
 *   [proj.memorchess.axl.core.graph.TreeStore].
 * @property deviceSeq That device's write counter at the time.
 */
data class DataRepertoire(
  val id: String,
  val name: String,
  val color: RepertoireColor?,
  val isDeleted: Boolean = false,
  val updatedAt: Instant = DateUtil.now(),
  val originDevice: String = "",
  val deviceSeq: Long = 0L,
)
