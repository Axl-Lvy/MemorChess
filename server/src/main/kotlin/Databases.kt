package proj.memorchess.axl.server

import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
  Database.connect(
    url = environment.config.property("database.url").getString(),
    driver = "org.postgresql.Driver",
    user = environment.config.property("database.user").getString(),
    password = environment.config.property("database.password").getString(),
  )
}
