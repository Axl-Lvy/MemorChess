package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * One synchronized row, in the shape it travels over the wire.
 *
 * Only facts travel. Derived projections owned by [proj.memorchess.axl.core.graph.TreeStore], such
 * as a node's depth, `hasGoodOutgoing` and `createdAt`, are recomputed after a pull rather than
 * merged, because merging them independently produces a store that disagrees with itself.
 */
sealed interface SyncRow {

  /** When the owning device last wrote this row. Decides conflicts; see [resolve]. */
  val updatedAt: Instant

  /** Opaque id of the device that wrote this version. Breaks [updatedAt] ties deterministically. */
  val originDevice: String

  /** Whether this row is a tombstone. A deletion is an ordinary write, never a special case. */
  val isDeleted: Boolean
}

/**
 * A position and its scheduling state.
 *
 * @property positionKey Cropped FEN, as a plain string rather than a
 *   [proj.memorchess.axl.core.data.PositionKey], because an inline value class on the wire adds
 *   surprise without benefit. Conversion happens at the client and server boundaries.
 * @property dueDate Moment the card is next due.
 * @property lastReview Moment of the most recent review, or `null` for a brand new card.
 * @property firstReview Moment of the very first review, or `null` for a never reviewed card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property difficulty FSRS card difficulty.
 * @property reps Total number of recorded reviews.
 * @property lapses Total number of times the card has been forgotten.
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
  override val originDevice: String,
) : SyncRow

/**
 * A move between two positions, and whether it is one to learn.
 *
 * @property origin Cropped FEN of the origin position.
 * @property destination Cropped FEN of the destination position.
 * @property move The move in standard algebraic notation.
 * @property isGood Whether the move has to be learned.
 */
@Serializable
data class EdgeSyncRow(
  val origin: String,
  val destination: String,
  val move: String,
  val isGood: Boolean,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: String,
) : SyncRow

/**
 * One persisted setting.
 *
 * @property key Identifier of the setting.
 * @property value Its serialized value.
 */
@Serializable
data class SettingSyncRow(
  val key: String,
  val value: String,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: String,
) : SyncRow
