package proj.memorchess.axl.core.util

/** Reloader. */
interface IReloader {
  /** Reloads. */
  fun reload()

  /**
   * Gets the key.
   *
   * @return the key
   */
  fun getKey(): Any
}
