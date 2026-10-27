package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlin.time.Instant

/** DAO for [RepertoireEntity] and [EdgeRepertoireTagEntity]. */
@Dao
interface RepertoireDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRepertoire(item: RepertoireEntity)

  /** Inserts a repertoire, queuing its own outbox entry in the same transaction. */
  @Transaction
  suspend fun insertRepertoireAndMarkDirty(item: RepertoireEntity) {
    insertRepertoire(item)
    upsertOutboxEntry(
      OutboxEntryEntity.KIND_REPERTOIRE,
      item.repertoireId,
      deviceSeq = item.deviceSeq,
    )
  }

  @Query("SELECT * FROM RepertoireEntity WHERE repertoireId = :id AND isDeleted IS FALSE")
  suspend fun getRepertoire(id: String): RepertoireEntity?

  /** [getRepertoire] without the `isDeleted IS FALSE` filter, for sync conflict resolution. */
  @Query("SELECT * FROM RepertoireEntity WHERE repertoireId = :id")
  suspend fun getRepertoireIncludingDeleted(id: String): RepertoireEntity?

  @Query("SELECT * FROM RepertoireEntity WHERE isDeleted IS FALSE")
  suspend fun getRepertoires(): List<RepertoireEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTag(item: EdgeRepertoireTagEntity)

  /** Inserts a tag, queuing its own outbox entry in the same transaction. */
  @Transaction
  suspend fun insertTagAndMarkDirty(item: EdgeRepertoireTagEntity) {
    insertTag(item)
    upsertOutboxEntry(
      OutboxEntryEntity.KIND_TAG,
      item.origin,
      item.destination,
      item.repertoireId,
      item.deviceSeq,
    )
  }

  @Query(
    "SELECT * FROM EdgeRepertoireTagEntity WHERE isDeleted IS FALSE " +
      "AND origin = :origin AND destination = :destination"
  )
  suspend fun getTags(origin: String, destination: String): List<EdgeRepertoireTagEntity>

  @Query(
    "SELECT * FROM EdgeRepertoireTagEntity WHERE isDeleted IS FALSE " +
      "AND (origin = :position OR destination = :position)"
  )
  suspend fun getTagsTouching(position: String): List<EdgeRepertoireTagEntity>

  /** [getTags] narrowed to one repertoire, without the `isDeleted IS FALSE` filter. */
  @Query(
    "SELECT * FROM EdgeRepertoireTagEntity " +
      "WHERE origin = :origin AND destination = :destination AND repertoireId = :repertoireId"
  )
  suspend fun getTagIncludingDeleted(
    origin: String,
    destination: String,
    repertoireId: String,
  ): EdgeRepertoireTagEntity?

  /** Every live tagged edge of [repertoireId], joined onto its live [MoveEntity] for the SAN. */
  @Query(
    "SELECT m.* FROM EdgeRepertoireTagEntity t JOIN MoveEntity m " +
      "ON t.origin = m.origin AND t.destination = m.destination " +
      "WHERE t.repertoireId = :repertoireId AND t.isDeleted IS FALSE AND m.isDeleted IS FALSE"
  )
  suspend fun edgesTaggedWith(repertoireId: String): List<MoveEntity>

  /**
   * Queues an outbox entry, keeping the higher `deviceSeq` on a repeat mark. Duplicates
   * [OutboxDao.upsert] so it can share a `@Transaction` with the row write it names.
   */
  @Query(
    "INSERT INTO OutboxEntryEntity (kind, key1, key2, key3, deviceSeq) " +
      "VALUES (:kind, :key1, :key2, :key3, :deviceSeq) " +
      "ON CONFLICT(kind, key1, key2, key3) DO UPDATE SET deviceSeq = MAX(deviceSeq, excluded.deviceSeq)"
  )
  suspend fun upsertOutboxEntry(
    kind: String,
    key1: String,
    key2: String = "",
    key3: String = "",
    deviceSeq: Long,
  )

  /** Hard wipes every repertoire row. */
  @Query("DELETE FROM RepertoireEntity") suspend fun eraseAllRepertoires()

  /** Hard wipes every tag row. */
  @Query("DELETE FROM EdgeRepertoireTagEntity") suspend fun eraseAllTags()

  @Query("DELETE FROM NodeRepertoireTrainableEntity WHERE positionKey = :positionKey")
  suspend fun clearTrainable(positionKey: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrainable(items: Collection<NodeRepertoireTrainableEntity>)

  /** Replaces [positionKey]'s entire trainable row set with [items] in one transaction. */
  @Transaction
  suspend fun replaceTrainable(
    positionKey: String,
    items: Collection<NodeRepertoireTrainableEntity>,
  ) {
    clearTrainable(positionKey)
    insertTrainable(items)
  }

  @Query(
    "SELECT COUNT(*) FROM NodeRepertoireTrainableEntity t JOIN NodeEntity n " +
      "ON n.positionKey = t.positionKey " +
      "WHERE t.repertoireId = :repertoireId AND n.isDeleted IS FALSE AND n.phase = 'REVIEW'"
  )
  suspend fun countSolid(repertoireId: String): Int

  /**
   * Counts [repertoireId]'s live trainable positions. The `isDeleted` join is a correctness
   * backstop for a tombstoned position's trainable row, not merely an optimization.
   */
  @Query(
    "SELECT COUNT(*) FROM NodeRepertoireTrainableEntity t JOIN NodeEntity n " +
      "ON n.positionKey = t.positionKey WHERE t.repertoireId = :repertoireId AND n.isDeleted IS FALSE"
  )
  suspend fun countTotal(repertoireId: String): Int

  @Query(
    "SELECT MAX(t.lastReview) FROM NodeRepertoireTrainableEntity t JOIN NodeEntity n " +
      "ON n.positionKey = t.positionKey WHERE t.repertoireId = :repertoireId AND n.isDeleted IS FALSE"
  )
  suspend fun maxLastReview(repertoireId: String): Instant?

  /** Hard wipes every trainable row. */
  @Query("DELETE FROM NodeRepertoireTrainableEntity") suspend fun eraseAllTrainable()
}
