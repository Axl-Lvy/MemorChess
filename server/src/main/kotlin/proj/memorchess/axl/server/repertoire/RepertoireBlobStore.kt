package proj.memorchess.axl.server.repertoire

/**
 * Storage for repertoire payload bytes, addressed by the payload's sha256 hex digest.
 *
 * The same hash is always the same bytes, so [put] is idempotent and callers never need to check
 * existence before writing.
 */
internal interface RepertoireBlobStore {

  /** Stores [bytes] under [sha256]. Writing the same hash twice is a no op. */
  suspend fun put(sha256: String, bytes: ByteArray)

  /** Returns the bytes stored under [sha256], or `null` when nothing is stored there. */
  suspend fun get(sha256: String): ByteArray?

  /** Removes the object stored under [sha256]. Removing an absent hash is a no op. */
  suspend fun delete(sha256: String)
}
