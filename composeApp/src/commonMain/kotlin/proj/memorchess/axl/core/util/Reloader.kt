package proj.memorchess.axl.core.util

/** Reloader. */
interface Reloader {

  /** Reloads. */
  fun reload()

  /**
   * Gets the key.
   *
   * @return the key
   */
  fun getKey(): Any
}
