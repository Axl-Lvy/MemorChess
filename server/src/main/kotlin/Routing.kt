package proj.memorchess.axl.server

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.responds
import io.github.tabilzad.ktor.annotations.respondsNothing
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import proj.memorchess.axl.server.data.getAllMoves
import proj.memorchess.axl.shared.data.MoveFetched

private const val OPEN_API_FILE = "openapi/openapi.yaml"

@GenerateOpenApi
fun Application.configureRouting() {
  routing { openAPI(path = "openapi", swaggerFile = OPEN_API_FILE) }
  routing { swaggerUI(path = "swagger", swaggerFile = OPEN_API_FILE) }
  routing {
    authenticate(BASIC_AUTH, FORM_AUTH) {
      @KtorDescription(
        summary = "Protected route that requires basic authentication",
        operationId = "protectedRouteBasic",
      )
      get("/protected/route/basic") {
        val principal =
          call.principal<UserIdPrincipal>()
            ?: return@get call.respondText("No principal", status = HttpStatusCode.Unauthorized)
        respondsNothing(
          status = HttpStatusCode.OK,
          description = "Authorized access to protected route",
        )
        respondsNothing(
          status = HttpStatusCode.Unauthorized,
          description = "Unauthorized access to protected route",
        )
        call.respondText("Hello ${principal.name}", status = HttpStatusCode.OK)
      }

      @KtorDescription(
        summary = "Fetch all moves for the authenticated user",
        operationId = "getUserMoves",
        tags = ["authenticated", "personal data"]
      )
      get("/data/moves") {
        val principal =
          call.principal<UserIdPrincipal>()
            ?: return@get call.respondText("No principal", status = HttpStatusCode.Unauthorized)

        val moves = suspendTransaction { getAllMoves(principal.name) }

        responds<List<MoveFetched>>(
          status = HttpStatusCode.OK,
          description = "Successfully retrieved user moves",
        )
        respondsNothing(status = HttpStatusCode.Unauthorized, description = "Unauthorized access")
        call.respond(HttpStatusCode.OK, moves)
      }
    }
  }
}
