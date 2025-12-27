package proj.memorchess.axl.server

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
  EngineMain.main(args)
}

fun Application.module() {
  configureFlyway()
  configureSecurity()
  configureDatabases()
  configureRouting()
}
