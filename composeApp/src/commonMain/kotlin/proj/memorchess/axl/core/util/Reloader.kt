package proj.memorchess.axl.core.util

/** Triggers reloads of whatever observes its key. */
interface Reloader {

  /** Requests a reload, changing [getKey]. */
  fun reload()

  /** A key that changes on every [reload], for use as an effect key. */
  fun getKey(): Any
}
