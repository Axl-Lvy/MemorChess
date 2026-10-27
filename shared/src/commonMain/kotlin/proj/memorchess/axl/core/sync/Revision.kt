package proj.memorchess.axl.core.sync

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A position in the server's global write order: every stored row version gets the next one. It
 * encodes on the wire as its bare number.
 */
@JvmInline
@Serializable
value class Revision(val value: Long) : Comparable<Revision> {

  override fun compareTo(other: Revision): Int = value.compareTo(other.value)

  companion object {

    /** Below every assigned revision: the position of a device that has seen nothing. */
    val ZERO: Revision = Revision(0L)
  }
}
