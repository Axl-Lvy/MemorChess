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
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlin.time.Instant
import proj.memorchess.axl.server.BASIC_AUTH
import proj.memorchess.axl.server.FORM_AUTH
import proj.memorchess.axl.server.data.addMoves
import proj.memorchess.axl.server.data.deleteAllUserData
import proj.memorchess.axl.server.data.deleteMove
import proj.memorchess.axl.server.data.deletePosition
import proj.memorchess.axl.server.data.getAllMoves
import proj.memorchess.axl.server.data.getLastUpdate
import proj.memorchess.axl.server.data.getNode
import proj.memorchess.axl.shared.data.MoveFetched
import proj.memorchess.axl.shared.data.NodeFetched
import proj.memorchess.axl.shared.routes.DataRoutes

private const val NO_PRINCIPAL_MESSAGE = "No principal"
private const val UNAUTHORIZED_ACCESS_DESCRIPTION = "Unauthorized access"

@GenerateOpenApi
fun Route.configureProtectedDataRoutes() {
  authenticate(BASIC_AUTH, FORM_AUTH) {
    @KtorDescription(
      summary = "Fetch all moves for the authenticated user",
      operationId = "getUserMoves",
      tags = ["personal data"],
    )
    get<DataRoutes.Moves> { resource ->
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@get call.respondText(NO_PRINCIPAL_MESSAGE, status = HttpStatusCode.Unauthorized)

      val moves = getAllMoves(principal.name, resource.withDeletedOnes)

      responds<List<MoveFetched>>(
        status = HttpStatusCode.OK,
        description = "Successfully retrieved user moves",
      )
      respondsNothing(
        status = HttpStatusCode.Unauthorized,
        description = UNAUTHORIZED_ACCESS_DESCRIPTION,
      )
      call.respond(HttpStatusCode.OK, moves)
    }

    @KtorDescription(
      summary = "Add or update moves for the authenticated user",
      operationId = "addUserMoves",
      tags = ["personal data"],
    )
    post<DataRoutes.Moves> {
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@post call.respondText(
            NO_PRINCIPAL_MESSAGE,
            status = HttpStatusCode.Unauthorized,
          )

      try {
        val moves = call.receive<List<MoveFetched>>()
        addMoves(principal.name, moves)

        respondsNothing(status = HttpStatusCode.Created, description = "Successfully added moves")
        respondsNothing(
          status = HttpStatusCode.Unauthorized,
          description = UNAUTHORIZED_ACCESS_DESCRIPTION,
        )
        respondsNothing(status = HttpStatusCode.BadRequest, description = "Invalid request body")
        call.respond(HttpStatusCode.Created)
      } catch (e: Exception) {
        call.respondText("Invalid request body: ${e.message}", status = HttpStatusCode.BadRequest)
      }
    }

    @KtorDescription(
      summary = "Fetch data for a specific position",
      operationId = "getUserPosition",
      tags = ["personal data"],
    )
    get<DataRoutes.Node> { resource ->
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@get call.respondText(NO_PRINCIPAL_MESSAGE, status = HttpStatusCode.Unauthorized)

      val nodeFetched =
        getNode(principal.name, resource.fen)
          ?: return@get call.respondText("Position not found", status = HttpStatusCode.NotFound)
      call.respond(HttpStatusCode.OK, nodeFetched)
      responds<NodeFetched>(
        status = HttpStatusCode.OK,
        description = "Successfully retrieved position data",
      )
      respondsNothing(
        status = HttpStatusCode.Unauthorized,
        description = UNAUTHORIZED_ACCESS_DESCRIPTION,
      )
      respondsNothing(status = HttpStatusCode.NotFound, description = "Position not found")
    }

    @KtorDescription(
      summary = "Delete a specific position",
      operationId = "deleteUserPosition",
      tags = ["personal data"],
    )
    delete<DataRoutes.Node> { resource ->
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@delete call.respondText(
            NO_PRINCIPAL_MESSAGE,
            status = HttpStatusCode.Unauthorized,
          )

      deletePosition(principal.name, resource.fen, resource.updatedAt ?: Instant.fromEpochMilliseconds(0))

      respondsNothing(
        status = HttpStatusCode.NoContent,
        description = "Successfully deleted position",
      )
      respondsNothing(
        status = HttpStatusCode.Unauthorized,
        description = UNAUTHORIZED_ACCESS_DESCRIPTION,
      )
      call.respond(HttpStatusCode.NoContent)
    }

    @KtorDescription(
      summary = "Delete a specific move",
      operationId = "deleteUserMove",
      tags = ["personal data"],
    )
    delete<DataRoutes.Move> { resource ->
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@delete call.respondText(
            NO_PRINCIPAL_MESSAGE,
            status = HttpStatusCode.Unauthorized,
          )

      deleteMove(principal.name, resource.fen, resource.move, resource.updatedAt ?: Instant.fromEpochMilliseconds(0))

      respondsNothing(status = HttpStatusCode.NoContent, description = "Successfully deleted move")
      respondsNothing(
        status = HttpStatusCode.Unauthorized,
        description = UNAUTHORIZED_ACCESS_DESCRIPTION,
      )
      call.respond(HttpStatusCode.NoContent)
    }

    @KtorDescription(
      summary = "Delete all user data",
      operationId = "deleteAllUserData",
      tags = ["personal data"],
    )
    delete<DataRoutes.All> { all ->
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@delete call.respondText(
            NO_PRINCIPAL_MESSAGE,
            status = HttpStatusCode.Unauthorized,
          )

      deleteAllUserData(principal.name, all.hardFrom, all.updatedAt ?: Instant.fromEpochMilliseconds(0))

      respondsNothing(
        status = HttpStatusCode.NoContent,
        description = "Successfully deleted all user data",
      )
      respondsNothing(
        status = HttpStatusCode.Unauthorized,
        description = UNAUTHORIZED_ACCESS_DESCRIPTION,
      )
      call.respond(HttpStatusCode.NoContent)
    }

    @KtorDescription(
      summary = "Get the timestamp of the last update",
      operationId = "getLastUpdate",
      tags = ["personal data"],
    )
    get<DataRoutes.LastUpdate> {
      val principal =
        call.principal<UserIdPrincipal>()
          ?: return@get call.respondText(NO_PRINCIPAL_MESSAGE, status = HttpStatusCode.Unauthorized)

      val lastUpdate = getLastUpdate(principal.name)

      if (lastUpdate == null) {
        respondsNothing(status = HttpStatusCode.NoContent, description = "No data found for user")
        respondsNothing(
          status = HttpStatusCode.Unauthorized,
          description = UNAUTHORIZED_ACCESS_DESCRIPTION,
        )
        call.respond(HttpStatusCode.NoContent)
      } else {
        responds<Instant>(
          status = HttpStatusCode.OK,
          description = "Successfully retrieved last update timestamp",
        )
        respondsNothing(
          status = HttpStatusCode.Unauthorized,
          description = UNAUTHORIZED_ACCESS_DESCRIPTION,
        )
        call.respond(HttpStatusCode.OK, lastUpdate)
      }
    }
  }
}
