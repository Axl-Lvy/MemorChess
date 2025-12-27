package proj.memorchess.axl.server

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.responds
import io.github.tabilzad.ktor.annotations.respondsNothing
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
  val database =
    Database.connect(
      url = environment.config.property("database.url").getString(),
      driver = "org.postgresql.Driver",
      user = environment.config.property("database.user").getString(),
      password = environment.config.property("database.password").getString(),
    )
  val userService = UserService(database)
  @GenerateOpenApi
  routing {
    // Create user
    post("/users") {
      val user = call.receive<ExposedUser>()
      val id = userService.create(user)
      responds<String>(HttpStatusCode.Created, "ID of the created user")
      call.respond(HttpStatusCode.Created, id)
    }

    // Read user
    get("/users/{id}") {
      val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
      val user = userService.read(id)
      if (user != null) {
        responds<ExposedUser>(status = HttpStatusCode.OK, description = "Corresponding user")
        call.respond(HttpStatusCode.OK, user)
      } else {
        respondsNothing(HttpStatusCode.NotFound, "User not found")
        call.respond(HttpStatusCode.NotFound)
      }
    }

    // Update user
    put("/users/{id}") {
      val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
      val user = call.receive<ExposedUser>()
      userService.update(id, user)
      respondsNothing(HttpStatusCode.OK, "User updated successfully")
      call.respond(HttpStatusCode.OK)
    }

    // Delete user
    delete("/users/{id}") {
      val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
      userService.delete(id)
      respondsNothing(HttpStatusCode.OK, "User deleted successfully")
      call.respond(HttpStatusCode.OK)
    }
  }
}
