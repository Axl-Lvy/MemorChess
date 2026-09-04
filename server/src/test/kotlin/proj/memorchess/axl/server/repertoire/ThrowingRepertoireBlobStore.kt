package proj.memorchess.axl.server.repertoire

/** A [RepertoireBlobStore] whose [put] always fails, used to exercise the publish rollback path. */
internal class ThrowingRepertoireBlobStore : RepertoireBlobStore {

  override suspend fun put(sha256: String, bytes: ByteArray): Nothing =
    throw RuntimeException("blob store unavailable")

  override suspend fun get(sha256: String): ByteArray? = null

  override suspend fun delete(sha256: String) {}
}
