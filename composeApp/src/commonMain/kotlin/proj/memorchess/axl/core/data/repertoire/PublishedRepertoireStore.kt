package proj.memorchess.axl.core.data.repertoire

import proj.memorchess.axl.core.config.PUBLISHED_REPERTOIRES_SETTING

/**
 * Records, per local repertoire id, the public slug it was last published under.
 *
 * Local only bookkeeping, never verified against the server on load: a second device publishing the
 * same repertoire under the same default slug still succeeds as a version bump (the server only
 * checks that the same author owns the id), so the worst case of this being stale is a row that
 * offers "Publish" once more instead of "Update", never a failure.
 */
class PublishedRepertoireStore {

  /** The public slug [localId] was last published under, or `null` if never published. */
  fun publishedSlug(localId: String): String? = entries()[localId]

  /** Records that [localId] is now published under [slug]. */
  fun recordPublished(localId: String, slug: String) {
    require(localId.isNotBlank()) { "Repertoire id must not be blank" }
    require(SEPARATOR !in localId && PAIR_SEPARATOR !in localId) {
      "Repertoire id must not contain '$SEPARATOR' or '$PAIR_SEPARATOR': $localId"
    }
    require(SEPARATOR !in slug && PAIR_SEPARATOR !in slug) {
      "Slug must not contain '$SEPARATOR' or '$PAIR_SEPARATOR': $slug"
    }
    save(entries() + (localId to slug))
  }

  /** Clears [localId]'s recorded slug, for example after the server reports it was removed. */
  fun clearPublished(localId: String) {
    save(entries() - localId)
  }

  private fun entries(): Map<String, String> =
    PUBLISHED_REPERTOIRES_SETTING.getValue()
      .split(SEPARATOR)
      .filter { it.isNotBlank() }
      .associate { pair ->
        val (id, slug) = pair.split(PAIR_SEPARATOR, limit = 2)
        id to slug
      }

  private fun save(entries: Map<String, String>) {
    PUBLISHED_REPERTOIRES_SETTING.setValue(
      entries.entries.sortedBy { it.key }.joinToString(SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }
    )
  }

  private companion object {
    const val SEPARATOR = ","
    const val PAIR_SEPARATOR = "="
  }
}
