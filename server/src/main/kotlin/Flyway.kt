package proj.memorchess.axl.server

import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.flywaydb.core.Flyway

/**
 * Configures and runs Flyway database migrations.
 *
 * This function sets up Flyway to manage database schema migrations using SQL scripts located in
 * db/migration resources folder.
 */
fun Application.configureFlyway() {
  val url = environment.config.property("database.url").getString()
  val user = environment.config.property("database.user").getString()
  val password = environment.config.property("database.password").getString()

  val flyway =
    Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load()

  // Run migrations
  flyway.migrate()

  log.info("Flyway migrations completed successfully")
}
