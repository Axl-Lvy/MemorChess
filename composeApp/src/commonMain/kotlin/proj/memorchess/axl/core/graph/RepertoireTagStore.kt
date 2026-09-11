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

  /**
   * Registers [id] in the repertoire registry with [name] and [color], or overwrites an existing
   * entry's name/color (a catalog reinstall re-registering under the same id). Queues its own
   * outbox entry.
   *
   * @throws IllegalArgumentException if [id] is blank, or contains a comma (mirrors
   *   [proj.memorchess.axl.core.data.repertoire.InstalledRepertoireStore]'s own separator rule).
   */
  suspend fun register(id: String, name: String, color: RepertoireColor?) {
    require(id.isNotBlank()) { "Repertoire id must not be blank" }
    require(',' !in id) { "Repertoire id must not contain ',': $id" }
    database.insertRepertoire(
      DataRepertoire(
        id = id,
        name = name,
        color = color,
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
   * Callers decide *when* to call this: [proj.memorchess.axl.core.interactions.LinesExplorer] only
   * for a genuinely new edge in a scoped session, [proj.memorchess.axl.core.pgn.PgnImporter] for
   * every edge an import describes, present or not.
   */
  suspend fun tag(origin: PositionKey, destination: PositionKey, repertoireId: String) {
    database.insertTag(
      DataEdgeRepertoireTag(
        origin = origin,
        destination = destination,
        repertoireId = repertoireId,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    )
    trainable.recompute(origin)
    notifyDirty()
  }

  /** Tombstones every live tag on the edge from [origin] to [destination]. */
  internal suspend fun tombstoneTags(origin: PositionKey, destination: PositionKey) {
    for (repertoireId in tagsFor(origin, destination)) {
      database.insertTag(
        DataEdgeRepertoireTag(
          origin = origin,
          destination = destination,
          repertoireId = repertoireId,
          isDeleted = true,
          updatedAt = DateUtil.now(),
          originDevice = deviceIdentity.originDevice,
          deviceSeq = deviceIdentity.nextDeviceSeq(),
        )
      )
    }
  }
}
