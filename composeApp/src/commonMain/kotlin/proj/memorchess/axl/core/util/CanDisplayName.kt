package proj.memorchess.axl.core.util

/**
 * Interface for objects that can provide a display name.
 *
 * @property displayName Name to display.
 *
 * Note that it is intentionally not called `name` to avoid confusion with the `name` property of
 * Kotlin enums.
 */
interface CanDisplayName {
  val displayName: String
}
