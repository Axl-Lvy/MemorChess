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
 * Repertoire registry and edge to repertoire tags.
 *
 * Nodes and moves are shared across repertoires, so a repertoire is a set of tags over existing
 * edges rather than a copy of the graph.
 *
 * @param database The persistence backend.
 * @param deviceIdentity Stamped onto every registry and tag row written here.
 * @param trainable Recomputed after a tag write, so a tag on a live good edge takes effect at once.
 * @param notifyDirty Called after every write that queues an outbox entry, so
 *   [proj.memorchess.axl.core.sync.SyncEngine] can schedule a push.
 */
class RepertoireTagStore(
  private val database: DatabaseQueryManager,
  private val deviceIdentity: DeviceIdentity,
  private val trainable: TrainableProjection,
  private val notifyDirty: () -> Unit = {},
) {

  /** Every registered repertoire. Read through of [DatabaseQueryManager.getRepertoires]. */
  suspend fun repertoires(): List<DataRepertoire> = database.getRepertoires()

  /**
   * Mastery snapshot per registered repertoire. See
   * [DatabaseQueryManager.getRepertoireMasterySnapshots]; bounded by [repertoires]' own id list.
   */
  suspend fun masterySnapshots(): Map<String, RepertoireMasterySnapshot> =
    database.getRepertoireMasterySnapshots(database.getRepertoires().map { it.id })

  /** The registry row of [id], or `null` when missing or deleted. */
  suspend fun repertoire(id: String): DataRepertoire? = database.getRepertoire(id)

  /**
   * Registers [id] in the repertoire registry with [name], [color] and [installedVersion] (`null`
   * for a repertoire the user creates), or overwrites an existing entry (a catalog reinstall
   * re-registering under the same id). Queues its own outbox entry.
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
   * Registers [newId] as a new repertoire and tags it with every live edge currently tagged with
   * [sourceId]. Nodes and moves are shared across repertoires, so this only duplicates the tag
   * rows, never the underlying graph. [sourceId]'s own tags are left untouched, so the same edge
   * ends up tagged with both repertoires.
   *
   * @throws IllegalArgumentException if [newId] is blank, or contains a comma (see [register]).
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

  /**
   * Every live tagged edge of [repertoireId]. Read through of
   * [DatabaseQueryManager.edgesTaggedWith].
   */
  suspend fun edgesTaggedWith(repertoireId: String): List<TaggedEdge> =
    database.edgesTaggedWith(repertoireId)

  /**
   * Tags the edge from [origin] to [destination] with [repertoireId], adding to any existing tags
   * on that edge rather than replacing them: an edge can belong to more than one repertoire (see
   * the design's many to many section). Idempotent: tagging an edge that already carries this
   * repertoire is a harmless repeat write. Recomputes [origin]'s trainable projection afterward, so
   * a tag on a live good edge takes effect immediately. Queues its own outbox entry.
   *
   * The tag records membership only: it carries no marking and so does not hold the move, except that
   * re-tagging a move the repertoire already holds keeps the existing marking. Callers
   * decide *when* to call this: [proj.memorchess.axl.core.interactions.LinesExplorer] only for a
   * genuinely new edge in a scoped session, [proj.memorchess.axl.core.pgn.PgnImporter] for every
   * edge an import describes, present or not.
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
        isGood =
          database
            .getTagIncludingDeleted(origin, destination, repertoireId)
            ?.takeIf { !it.isDeleted }
            ?.isGood,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    trainable.recompute(origin)
    notifyDirty()
  }

  /**
   * Tags [move] from [origin] to [destination] with [repertoireId] and the author's marking
   * [isGood], which makes the repertoire a holder of the move. The caller derives the move's
   * effective marking afterward.
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

  /** Tombstones the registry row of [id]. Queues its own outbox entry; a no-op when none is live. */
  internal suspend fun unregister(id: String) {
    val live = database.getRepertoire(id) ?: return
    database.insertRepertoire(
      live.copy(
        isDeleted = true,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    notifyDirty()
  }

  /** Tombstones the live tag of [repertoireId] on the edge from [origin] to [destination], if any. */
  internal suspend fun untag(origin: PositionKey, destination: PositionKey, repertoireId: String) {
    val live =
      database.getTagIncludingDeleted(origin, destination, repertoireId)?.takeIf { !it.isDeleted }
        ?: return
    database.insertTag(
      live.copy(
        isDeleted = true,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
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
