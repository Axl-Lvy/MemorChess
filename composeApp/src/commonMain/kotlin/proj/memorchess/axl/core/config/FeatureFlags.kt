package proj.memorchess.axl.core.config

/**
 * Feature flags for controlling feature availability at runtime.
 *
 * Flags default to production values. Tests override them as needed.
 */
object FeatureFlags {
  /**
   * Whether user authentication and personal data synchronization are enabled.
   *
   * When `false`, sign-in UI and database sync (except community books) are hidden. Defaults to
   * `false`; tests set this to `true` to exercise the auth flow.
   */
  var isAuthEnabled: Boolean = false
}
