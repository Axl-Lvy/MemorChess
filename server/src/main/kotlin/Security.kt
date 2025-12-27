package proj.memorchess.axl.server

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.respondsNothing
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.form
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

const val BASIC_AUTH = "basic_auth"
const val FORM_AUTH = "form_auth"

fun Application.configureSecurity() {
  authentication {
    basic(name = BASIC_AUTH) {
      realm = "Basic Auth Realm"
      validate { credentials ->
        if (credentials.name == credentials.password) {
          UserIdPrincipal(credentials.name)
        } else {
          null
        }
      }
    }
    form(name = FORM_AUTH) {
      userParamName = "username"
      passwordParamName = "password"
      validate { credentials ->
        if (credentials.name == "god") {
          UserIdPrincipal(credentials.name)
        } else {
          null
        }
      }
      challenge { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized) }
    }
  }
  routing {

    authenticate(FORM_AUTH) {
      post("/login") {
        call.respondText("Hello, ${call.principal<UserIdPrincipal>()?.name}!")
      }
    }
  }
}
