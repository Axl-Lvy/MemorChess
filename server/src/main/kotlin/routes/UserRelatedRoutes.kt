package proj.memorchess.axl.server.routes

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.responds
import io.github.tabilzad.ktor.annotations.respondsNothing
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import proj.memorchess.axl.server.BASIC_AUTH
import proj.memorchess.axl.server.FORM_AUTH
import proj.memorchess.axl.server.createUser
import proj.memorchess.axl.server.data.hasUserPermission
import proj.memorchess.axl.shared.data.SignupRequest

@GenerateOpenApi
fun Route.configureUserRelatedRoutes() {
  route("/user") {
    @KtorDescription(
      summary = "Create a new user account",
      operationId = "signupUser",
      tags = ["authentication"],
    )
    post("/signup") {
      respondsNothing(
        status = HttpStatusCode.BadRequest,
        description = "Invalid request body, username already exists, or weak password",
      )
      respondsNothing(
        status = HttpStatusCode.Created,
        description = "User account created successfully",
      )

      try {
        val credentials = call.receive<SignupRequest>()

        createUser(credentials.email, credentials.password)
          ?: return@post call.respondText(
            "User with this email already exists",
            status = HttpStatusCode.BadRequest,
          )

        call.respond(HttpStatusCode.Created)
      } catch (e: Exception) {
        println("Error receiving request: ${e.message}")
        e.printStackTrace()
        call.respondText("Invalid request body: ${e.message}", status = HttpStatusCode.BadRequest)
      }
    }
    authenticate(BASIC_AUTH, FORM_AUTH) {
      @KtorDescription(
        summary = "Checks if the current user has a specific permission",
        operationId = "getUserPermission",
        tags = ["user"],
      )
      get("/permission/{permission}") {
        respondsNothing(
          status = HttpStatusCode.Unauthorized,
          description = "Unauthorized access to protected route",
        )
        responds<Boolean>(
          status = HttpStatusCode.OK,
          description = "Whether the user has the specified permission",
        )
        val principal =
          call.principal<UserIdPrincipal>()
            ?: return@get call.respondText("No principal", status = HttpStatusCode.Unauthorized)
        val permission =
          call.parameters["permission"]
            ?: return@get call.respondText("No permission", status = HttpStatusCode.BadRequest)
        call.respond(
          status = HttpStatusCode.OK,
          message = hasUserPermission(principal.name, permission),
        )
      }
    }
  }
}
