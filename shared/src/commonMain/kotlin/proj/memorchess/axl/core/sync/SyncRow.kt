package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * One synchronized row, in its wire shape. Derived projections such as depth, `hasGoodOutgoing` and
 * `createdAt` never travel; they are recomputed after a pull so the store stays self-consistent.
 */
sealed interface SyncRow {

  /** When the owning device last wrote this row. Decides conflicts; see [resolve]. */
  val updatedAt: Instant

  /** Opaque id of the device that wrote this version. Breaks [updatedAt] ties. */
  val originDevice: DeviceId

  /**
   * Per device write counter, strictly increasing. Between two versions from the same
   * [originDevice], [resolve] compares only this, since a clock can move backwards.
   */
  val deviceSeq: Long

  /** Whether this row is a tombstone. */
  val isDeleted: Boolean
}

/**
 * A position and its scheduling state.
 *
 * @property positionKey Cropped FEN, as a plain string on the wire.
 * @property lastReview Most recent review, or `null` for a brand new card.
 * @property firstReview First review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property phase Name of the card's phase in the FSRS state machine.
 * @property step Index into the active learning or relearning step ladder.
 */
@Serializable
data class NodeSyncRow(
  val positionKey: String,
  val dueDate: Instant,
  val lastReview: Instant?,
  val firstReview: Instant?,
  val stability: Double,
  val difficulty: Double,
  val reps: Int,
  val lapses: Int,
  val phase: String,
  val step: Int,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow

/**
 * The user's manual hold on a move between two positions. A tombstone means the user no longer
 * holds the move; installed repertoires may still hold it through their tags.
 *
 * @property origin Cropped FEN of the origin position.
 * @property destination Cropped FEN of the destination position.
 * @property move The move in standard algebraic notation.
 * @property isGood The user's own marking: whether the move has to be learned.
 */
@Serializable
data class EdgeSyncRow(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow

/**
 * One persisted setting.
 *
 * @property value Its serialized value.
 */
@Serializable
data class SettingSyncRow(
  val key: String,
  val value: String,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow

/**
 * A repertoire's identity: display name and perspective.
 *
 * @property id Slug: a catalog descriptor id, or a slugified user chosen name.
 * @property color Serialized [proj.memorchess.axl.core.data.repertoire.RepertoireColor] name, or
 *   `null` when the repertoire mixes both sides.
 * @property installedVersion Catalog version installed under this id, or `null` for a user created
 *   repertoire.
 */
@Serializable
data class RepertoireSyncRow(
  val id: String,
  val name: String,
  val color: String?,
  val installedVersion: Int? = null,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow

/**
 * One edge tagged as belonging to one repertoire; an edge carries one row per repertoire.
 *
 * @property origin Cropped FEN of the origin position.
 * @property destination Cropped FEN of the destination position.
 * @property move The tagged move in SAN, so a device that has never seen the move can create it.
 * @property isGood The author's marking for a tag written by an install, or `null` for a tag that
 *   only records membership.
 */
@Serializable
data class EdgeRepertoireTagSyncRow(
  val origin: String,
  val destination: String,
  val repertoireId: String,
  val move: String,
  val isGood: Boolean? = null,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow
