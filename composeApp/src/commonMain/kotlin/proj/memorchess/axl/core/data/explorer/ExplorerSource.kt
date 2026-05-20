package proj.memorchess.axl.core.data.explorer

/** Which Lichess Opening Explorer database to query. */
enum class ExplorerSource(internal val path: String) {
  /** OTB master games. Smaller, higher quality, immutable history. Cached forever once fetched. */
  MASTERS("masters"),

  /**
   * Lichess online games. Larger, includes more rare lines. Continuously updated, so cached
   * responses expire after a finite time.
   */
  LICHESS("lichess"),
}
