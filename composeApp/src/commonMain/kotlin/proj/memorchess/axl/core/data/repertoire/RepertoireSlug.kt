package proj.memorchess.axl.core.data.repertoire

/**
 * Sanitizes [name] into a candidate repertoire id: lowercase, non alphanumerics collapsed to single
 * hyphens, leading/trailing hyphens trimmed. Shared by the publish, create, and fork flows so their
 * ids are generated the same way.
 */
internal fun slugify(name: String): String =
  name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
