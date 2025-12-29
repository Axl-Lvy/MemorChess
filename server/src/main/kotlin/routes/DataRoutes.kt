package proj.memorchess.axl.server.routes

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.responds
import io.github.tabilzad.ktor.annotations.respondsNothing
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.server.BASIC_AUTH
import proj.memorchess.axl.server.FORM_AUTH
import proj.memorchess.axl.server.data.getAllMoves
import proj.memorchess.axl.shared.data.MoveFetched

@GenerateOpenApi
fun Route.configureProtectedDataRoutes() {
  authenticate(BASIC_AUTH, FORM_AUTH) {
    route("/data") {
      @KtorDescription(
        summary = "Fetch all moves for the authenticated user",
        operationId = "getUserMoves",
        tags = ["personal data"],
      )
      get("/moves") {
        val principal =
          call.principal<UserIdPrincipal>()
            ?: return@get call.respondText("No principal", status = HttpStatusCode.Unauthorized)

        val moves = transaction { getAllMoves(principal.name) }

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
