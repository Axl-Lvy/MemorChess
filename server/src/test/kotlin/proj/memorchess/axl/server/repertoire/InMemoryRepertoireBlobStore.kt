package proj.memorchess.axl.server.repertoire

import java.util.concurrent.ConcurrentHashMap

/** A real, in-process [RepertoireBlobStore], used in tests in place of a live R2 bucket. */
internal class InMemoryRepertoireBlobStore : RepertoireBlobStore {

  private val blobs = ConcurrentHashMap<String, ByteArray>()

  override suspend fun put(sha256: String, bytes: ByteArray) {
    blobs[sha256] = bytes
  }

  override suspend fun get(sha256: String): ByteArray? = blobs[sha256]

  override suspend fun delete(sha256: String) {
    blobs.remove(sha256)
  }
}
