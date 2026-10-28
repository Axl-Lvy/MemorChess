package proj.memorchess.axl.core.sync

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import proj.memorchess.axl.core.data.repertoire.RepertoireColor

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

  /** Opaque id of the device that wrote this version. Breaks [updatedAt] ties. */
  val originDevice: DeviceId

  /**
   * Per device counter, strictly increasing with every local write that device makes.
   *
   * For two versions written by the same device this is the **only** thing [resolve] compares, and
   * [updatedAt] is ignored. A counter cannot move backwards, whereas a clock can, so this is what
   * keeps a device's own newer write from losing to its own older one. Only comparable between rows
   * carrying the same [originDevice]; counters from different devices are unrelated.
   */
  val deviceSeq: Long

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
 * @property key Identifier of the setting.
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
 * @property name Display name shown in the library and picker.
 * @property color Perspective, or `null` when the repertoire mixes both sides (a two sided import
 *   such as a Lichess study). Travels as the [RepertoireColor.name], and a name this build has
 *   never heard of decodes as `null`.
 * @property installedVersion Catalog version installed under this id, or `null` for a repertoire
 *   the user created.
 */
@Serializable
data class RepertoireSyncRow(
  val id: String,
  val name: String,
  @Serializable(with = RepertoireColorNameSerializer::class) val color: RepertoireColor?,
  val installedVersion: Int? = null,
  override val isDeleted: Boolean,
  override val updatedAt: Instant,
  override val originDevice: DeviceId,
  override val deviceSeq: Long,
) : SyncRow

/**
 * One edge tagged as belonging to one repertoire. Many to many: the same edge can carry more than
 * one row, one per repertoire it belongs to.
 *
 * @property origin Cropped FEN of the origin position.
 * @property destination Cropped FEN of the destination position.
 * @property repertoireId The repertoire this edge belongs to.
 * @property move The tagged move in standard algebraic notation, so a device that has never seen
 *   the move can create it.
 * @property isGood The author's marking for a tag written by an install, which makes the repertoire
 *   a holder of the move, or `null` for a tag that only records membership.
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

/** Encodes a [RepertoireColor] as its enum name, decoding an unknown name as `null`. */
internal object RepertoireColorNameSerializer : KSerializer<RepertoireColor?> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("proj.memorchess.axl.RepertoireColorName", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: RepertoireColor?) {
    if (value == null) encoder.encodeNull() else encoder.encodeString(value.name)
  }

  override fun deserialize(decoder: Decoder): RepertoireColor? =
    if (decoder.decodeNotNullMark()) RepertoireColor.fromNameOrNull(decoder.decodeString())
    else decoder.decodeNull()
}
