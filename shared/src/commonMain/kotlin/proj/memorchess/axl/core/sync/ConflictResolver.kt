package proj.memorchess.axl.core.sync

/** Which side of a comparison produced the winning row. */
enum class ResolutionSource {
  /** The caller's own copy won. The pushing side sends it; the applying side writes nothing. */
  LOCAL,

  /** The peer's copy won. The applying side writes it; the pushing side sends nothing. */
  REMOTE,
}

/** The outcome of reconciling one row. */
sealed interface Resolution<T : SyncRow> {

  /** The row that survives. */
  val row: T

  /** Where [row] came from. */
  val source: ResolutionSource
}

/**
 * The surviving row and the side it came from.
 *
 * @property row The winner.
 * @property source Which side [row] came from.
 */
data class Winner<T : SyncRow>(override val row: T, override val source: ResolutionSource) :
  Resolution<T>

/**
 * Reconciles one row, per row last write wins.
 *
 * This is the **only** implementation of that rule. Both the client apply path and the server write
 * path call it, so the two cannot drift apart. The rule, in order:
 * 1. A row present on one side only wins; there is nothing to compare it against.
 * 2. Otherwise the later [SyncRow.updatedAt] wins.
 * 3. On an exact tie, the lexicographically greater [SyncRow.originDevice] wins. Without a
 *    deterministic tiebreak two devices can hand a row back and forth indefinitely, and because the
 *    comparison is symmetric both sides independently reach the same answer.
 *
 * Deletion is not a case here. A tombstone is a row with [SyncRow.isDeleted] set, so an older
 * delete loses to a newer write (a resurrection) and a newer delete wins, both of which fall out of
 * the ordering above rather than from a branch.
 *
 * Callers must never pass two rows with different identities; this function assumes both sides
 * describe the same key.
 *
 * @param local The caller's own copy, or `null` when it holds none.
 * @param remote The peer's copy, or `null` when the peer holds none.
 * @throws IllegalArgumentException when both are `null`, which no caller should ever do.
 */
fun <T : SyncRow> resolve(local: T?, remote: T?): Resolution<T> {
  require(local != null || remote != null) {
    "resolve called for a row that exists on neither side"
  }
  if (local == null) return Winner(remote!!, ResolutionSource.REMOTE)
  if (remote == null) return Winner(local, ResolutionSource.LOCAL)

  val byTime = local.updatedAt.compareTo(remote.updatedAt)
  if (byTime != 0) {
    return if (byTime > 0) Winner(local, ResolutionSource.LOCAL)
    else Winner(remote, ResolutionSource.REMOTE)
  }

  val byDevice = local.originDevice.compareTo(remote.originDevice)
  return if (byDevice >= 0) Winner(local, ResolutionSource.LOCAL)
  else Winner(remote, ResolutionSource.REMOTE)
}
