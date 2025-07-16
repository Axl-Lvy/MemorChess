package proj.memorchess.axl.core.config

/**
 * Secrets template.
 *
 * It contains every key that can be used, even if they are not found in **local.properties**.
 *
 * To add a new secret:
 * 1. Add a property here with a default value of [NOT_FOUND]
 * 2. Add the actual value to your local.properties file using UPPER_SNAKE_CASE format (e.g.,
 *    MY_NEW_SECRET=value)
 * 3. The build system will automatically convert UPPER_SNAKE_CASE to camelCase in the generated
 *    Secrets.kt file
 *
 * See CONTRIBUTING.md for more details on secrets management.
 */
abstract class SecretsTemplate {

  /** Supabase anon key */
  open val supabaseAnonKey = NOT_FOUND

  /** Mail of the user account used for testing */
  open val testUserMail = NOT_FOUND

  /** Password of the user account used for testing */
  open val testUserPassword = NOT_FOUND
}

/** Default value when a key is not found in **local.properties** */
private const val NOT_FOUND = "NOT_FOUND_IN_LOCAL_PROPERTIES"
