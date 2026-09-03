package proj.memorchess.axl.core.sync

/** Which side of a comparison produced the winning row. */
enum class ResolutionSource {
  /** The caller's own copy won. The pushing side sends it; the applying side writes nothing. */
  LOCAL,

  /** The peer's copy won. The applying side writes it; the pushing side sends nothing. */
  REMOTE,
}

/**
 * The outcome of reconciling one row.
 *
 * Constructed only by [resolve]; callers receive these rather than making them.
 *
 * @property row The row that survives.
 * @property source Which side [row] came from.
 */
@ConsistentCopyVisibility
data class Resolution<T : SyncRow> internal constructor(val row: T, val source: ResolutionSource)

/**
 * Reconciles one row, per row last write wins.
 *
 * This is the **only** implementation of that rule. Both the client apply path and the server write
 * path call it, so the two cannot drift apart.
 *
 * A row present on one side only wins, since there is nothing to compare it against. Otherwise the
 * rule depends on whether the two versions share an author:
 * - **Same [SyncRow.originDevice]**: the greater [SyncRow.deviceSeq] wins, and [SyncRow.updatedAt]
 *   is not consulted at all. A device's own counter is ground truth about the order of its own
 *   writes, and unlike its clock it cannot move backwards. This is what makes an NTP correction, a
 *   manual time change, or a row re-stamped after a refusal harmless: none of them can make a
 *   device's older write beat its newer one, which would otherwise resurrect data the user deleted.
 * - **Different [SyncRow.originDevice]**: the later [SyncRow.updatedAt] wins, and on an exact tie
 *   the lexicographically greater [SyncRow.originDevice] does. The wall clock is the only reference
 *   two devices share, and sequence numbers from different devices are unrelated counters that must
 *   never be compared.
 *
 * Both branches are **commutative**, and that is the property convergence actually rests on: each
 * side must pick the same winner whichever way round it asks. A rule that can tie on two rows with
 * different content has no perspective independent answer, so the two sides would keep different
 * rows forever. The only tie left is same author and same sequence, which by construction is the
 * same version, since a device never reuses a sequence number.
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
