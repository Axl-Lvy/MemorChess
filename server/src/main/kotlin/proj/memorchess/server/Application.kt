package proj.memorchess.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import proj.memorchess.server.db.DatabaseFactory
import proj.memorchess.server.plugins.configureRouting
import proj.memorchess.server.plugins.configureSerialization
import proj.memorchess.server.plugins.configureUserPlugin

/** Entry point for the MemorChess server. */
fun main() {
  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  embeddedServer(Netty, port = port) {
      DatabaseFactory.init()
      configureSerialization()
      configureUserPlugin()
      configureRouting()
    }
    .start(wait = true)
}
