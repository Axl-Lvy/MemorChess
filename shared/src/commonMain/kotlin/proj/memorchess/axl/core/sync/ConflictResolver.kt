package proj.memorchess.axl.core.sync

/** Which side of a comparison produced the winning row. */
enum class ResolutionSource {
  /** The caller's own copy won. The pushing side sends it; the applying side writes nothing. */
  LOCAL,

  /** The peer's copy won. The applying side writes it; the pushing side sends nothing. */
  REMOTE,
}

/** The outcome of reconciling one row: the surviving [row] and the side it came from. */
@ConsistentCopyVisibility
data class Resolution<T : SyncRow> internal constructor(val row: T, val source: ResolutionSource)

/**
 * Reconciles two versions of the same row by last write wins, the only implementation of that rule.
 * A version present on one side only wins.
 *
 * The same [SyncRow.originDevice] compares [SyncRow.deviceSeq] and never the clock, which can move
 * backwards. Different devices compare [SyncRow.updatedAt], ties going to the greater
 * [SyncRow.originDevice]; their sequences are unrelated and never compared. Both branches must stay
 * commutative, since convergence rests on each side picking the same winner.
 *
 * @throws IllegalArgumentException when both [local] and [remote] are `null`.
 */
fun <T : SyncRow> resolve(local: T?, remote: T?): Resolution<T> {
  require(local != null || remote != null) {
    "resolve called for a row that exists on neither side"
  }
  if (local == null) return Resolution(remote!!, ResolutionSource.REMOTE)
  if (remote == null) return Resolution(local, ResolutionSource.LOCAL)

  if (local.originDevice == remote.originDevice) {
    val bySeq = local.deviceSeq.compareTo(remote.deviceSeq)
    return if (bySeq >= 0) Resolution(local, ResolutionSource.LOCAL)
    else Resolution(remote, ResolutionSource.REMOTE)
  }

  val byTime = local.updatedAt.compareTo(remote.updatedAt)
  if (byTime != 0) {
    return if (byTime > 0) Resolution(local, ResolutionSource.LOCAL)
    else Resolution(remote, ResolutionSource.REMOTE)
  }

  val byDevice = local.originDevice.compareTo(remote.originDevice)
  return if (byDevice > 0) Resolution(local, ResolutionSource.LOCAL)
  else Resolution(remote, ResolutionSource.REMOTE)
}
