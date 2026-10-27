package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.DataEdgeRepertoireTag
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.RepertoireMasterySnapshot
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.sync.DeviceIdentity

/**
 * Repertoire registry and edge to repertoire tags; a repertoire is a set of tags over shared edges.
 *
 * @param notifyDirty Called after every write that queues an outbox entry.
 */
class RepertoireTagStore(
  private val database: DatabaseQueryManager,
  private val deviceIdentity: DeviceIdentity,
  private val trainable: TrainableProjection,
  private val notifyDirty: () -> Unit = {},
) {

  /** Every registered repertoire. */
  suspend fun repertoires(): List<DataRepertoire> = database.getRepertoires()

  /** Mastery snapshot per registered repertoire. */
  suspend fun masterySnapshots(): Map<String, RepertoireMasterySnapshot> =
    database.getRepertoireMasterySnapshots(database.getRepertoires().map { it.id })

  /** The registry row of [id], or `null` when missing or deleted. */
  suspend fun repertoire(id: String): DataRepertoire? = database.getRepertoire(id)

  /**
   * Registers or overwrites [id] in the registry; [installedVersion] is `null` for a user's own.
   *
   * @throws IllegalArgumentException if [id] is blank, or contains a comma.
   */
  suspend fun register(
    id: String,
    name: String,
    color: RepertoireColor?,
    installedVersion: Int? = null,
  ) {
    require(id.isNotBlank()) { "Repertoire id must not be blank" }
    require(',' !in id) { "Repertoire id must not contain ',': $id" }
    database.insertRepertoire(
      DataRepertoire(
        id = id,
        name = name,
        color = color,
        installedVersion = installedVersion,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    notifyDirty()
  }

  /**
   * Registers [newId] and tags it on every live edge tagged with [sourceId], leaving those tags.
   *
   * @throws IllegalArgumentException if [newId] is blank, or contains a comma.
   */
  suspend fun fork(sourceId: String, newId: String, newName: String, color: RepertoireColor?) {
    register(newId, newName, color)
    for (edge in edgesTaggedWith(sourceId)) {
      tag(edge.origin, edge.destination, newId)
    }
  }

  /**
   * The marking of every live tag on the edge from [origin] to [destination], keyed by repertoire
   * id: the author's marking for an installed repertoire, `null` for a membership only tag.
   */
  suspend fun markings(origin: PositionKey, destination: PositionKey): Map<String, Boolean?> =
    database.getTags(origin, destination).associate { it.repertoireId to it.isGood }

  /** Every repertoire the live edge from [origin] to [destination] is tagged with. */
  suspend fun tagsFor(origin: PositionKey, destination: PositionKey): Set<String> =
    database.getTags(origin, destination).map { it.repertoireId }.toSet()

  /** Every live tagged edge of [repertoireId]. */
  suspend fun edgesTaggedWith(repertoireId: String): List<TaggedEdge> =
    database.edgesTaggedWith(repertoireId)

  /**
   * Idempotently adds a membership-only [repertoireId] tag, which does not hold the move, to the
   * edge from [origin] to [destination], then recomputes [origin]'s trainable projection.
   *
   * @throws IllegalStateException if no move from [origin] to [destination] is stored.
   */
  suspend fun tag(origin: PositionKey, destination: PositionKey, repertoireId: String) {
    val move =
      checkNotNull(storedMove(origin, destination)) {
        "Cannot tag the unknown move from $origin to $destination"
      }
    database.insertTag(
      DataEdgeRepertoireTag(
        origin = origin,
        destination = destination,
        repertoireId = repertoireId,
        move = move,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    trainable.recompute(origin)
    notifyDirty()
  }

  /**
   * Tags [move] with [repertoireId] and the author's marking [isGood], making the repertoire a
   * holder. The caller must derive the move's effective marking afterward.
   */
  internal suspend fun tagInstalled(
    origin: PositionKey,
    destination: PositionKey,
    repertoireId: String,
    move: String,
    isGood: Boolean,
  ) {
    database.insertTag(
      DataEdgeRepertoireTag(
        origin = origin,
        destination = destination,
        repertoireId = repertoireId,
        move = move,
        isGood = isGood,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    notifyDirty()
  }

  /** Tombstones every live tag on the edge from [origin] to [destination]. */
  internal suspend fun tombstoneTags(origin: PositionKey, destination: PositionKey) {
    for (live in database.getTags(origin, destination)) {
      database.insertTag(
        live.copy(
          isDeleted = true,
          updatedAt = DateUtil.now(),
          originDevice = deviceIdentity.originDevice,
          deviceSeq = deviceIdentity.nextDeviceSeq(),
        )
      )
    }
  }

  /** SAN of the stored move from [origin] to [destination], preferring a live one. */
  private suspend fun storedMove(origin: PositionKey, destination: PositionKey): String? {
    val candidates =
      database
        .getPositionIncludingDeleted(origin)
        ?.previousAndNextMoves
        ?.nextMoves
        ?.values
        ?.filter { it.destination == destination }
        .orEmpty()
    return (candidates.firstOrNull { !it.isDeleted } ?: candidates.firstOrNull())?.move
  }
}
