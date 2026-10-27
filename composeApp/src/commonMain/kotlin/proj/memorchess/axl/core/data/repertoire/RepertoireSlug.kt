package proj.memorchess.axl.core.data.repertoire

/** Sanitizes [name] into a candidate repertoire id of lowercase alphanumerics joined by hyphens. */
internal fun slugify(name: String): String =
  name.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
