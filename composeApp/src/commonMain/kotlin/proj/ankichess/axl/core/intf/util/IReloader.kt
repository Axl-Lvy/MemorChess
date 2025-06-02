package proj.ankichess.axl.core.intf.util

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
