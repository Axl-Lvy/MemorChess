package proj.memorchess.axl.core.data.explorer

/** Which Lichess Opening Explorer database to query. */
enum class ExplorerSource(internal val path: String) {
  /** OTB master games; immutable, so cached forever. */
  MASTERS("masters"),

  /** Lichess online games; continuously updated, so cached responses expire. */
  LICHESS("lichess"),
}
