package proj.memorchess.axl.core.config

abstract class SecretsTemplate {
  open val supabaseAnonKey = NOT_FOUND
  open val testUserMail = NOT_FOUND
  open val testUserPassword = NOT_FOUND
}

private const val NOT_FOUND = "NOT_FOUND_IN_LOCAL_PROPERTIES"
