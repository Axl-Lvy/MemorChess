package proj.memorchess.axl.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.form
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.getDigestFunction
import java.util.UUID
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.server.data.UserEntity
import proj.memorchess.axl.server.data.getUser

const val BASIC_AUTH = "basic_auth"
const val FORM_AUTH = "form_auth"

fun Application.configureSecurity() {
  authentication {
    basic(name = BASIC_AUTH) {
      realm = "Basic Auth Realm"
      validate { credentials ->
        val correspondingUser = validateCredentials(credentials.name, credentials.password)
        return@validate correspondingUser?.let { UserIdPrincipal(it.id.toString()) }
      }
    }
    form(name = FORM_AUTH) {
      userParamName = "username"
      passwordParamName = "password"
      validate { credentials ->
        val correspondingUser = validateCredentials(credentials.name, credentials.password)
        return@validate correspondingUser?.let { UserIdPrincipal(it.id.toString()) }
      }
      challenge { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized) }
    }
  }
  routing {
    authenticate(FORM_AUTH) {
      post("/login") { call.respondText("Hello, ${call.principal<UserIdPrincipal>()?.name}!") }
    }
  }
}

// When creating a user
fun createUser(email: String, password: String): Boolean {
  // Check if user already exists
  if (getUser(email) != null) {
    return false
  }
  val salt = generateSalt()
  val hash = hashPassword(password, salt)
  return transaction {
    UserEntity.new {
      this.email = email
      this.passwordHash = hash
      this.passwordHashSalt = salt
    }
    true
  }
}

/**
 * Validates user credentials by comparing the hashed password with the stored hash.
 *
 * @param email The username of the user
 * @param password The plaintext password to validate
 * @return The [UserEntity] if credentials are valid, null otherwise
 */
suspend fun validateCredentials(email: String, password: String): UserEntity? {
  val user = getUser(email) ?: return null
  val hash = hashPassword(password, user.passwordHashSalt) // Use stored salt
  return if (hash == user.passwordHash) user else null
}

private fun hashPassword(password: String, salt: String): String {
  val digestFunction = getDigestFunction("SHA-256") { salt }
  return digestFunction(password).decodeToString()
}

private fun generateSalt(): String = UUID.randomUUID().toString()
