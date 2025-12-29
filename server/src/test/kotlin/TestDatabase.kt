package proj.memorchess.axl.server

import io.ktor.server.application.Application
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import proj.memorchess.axl.server.data.BooksTable
import proj.memorchess.axl.server.data.DownloadedBooksTable
import proj.memorchess.axl.server.data.MoveCrossBookTable
import proj.memorchess.axl.server.data.MovesTable
import proj.memorchess.axl.server.data.PositionsTable
import proj.memorchess.axl.server.data.UserMovesTable
import proj.memorchess.axl.server.data.UserPermissionsTable
import proj.memorchess.axl.server.data.UserPositionsTable
import proj.memorchess.axl.server.data.UsersTable

/**
 * Configures the test database with migrations and ensures a clean state.
 *
 * This sets up a separate test database and runs all migrations.
 */
fun Application.configureTestDatabase() {
  val user = environment.config.property("database.user").getString()
  val password = environment.config.property("database.password").getString()
  val url = environment.config.property("database.url").getString()


  // Clean and migrate to ensure fresh state
  if (!migrationApplied) {
    // Run migrations on test database
    val flyway =
      Flyway.configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .cleanDisabled(false) // Allow cleaning for tests
        .load()
    flyway.clean()
    flyway.migrate()
    Database.connect(url = url, driver = "org.postgresql.Driver", user = user, password = password)
    migrationApplied = true
    seedTestData()
  } else {
    Database.connect(url = url, driver = "org.postgresql.Driver", user = user, password = password)
//    cleanTestDatabase()
  }
  // Clean all data from the database
  //  cleanTestDatabase()
}

/**
 * Cleans all data from the test database tables while preserving the schema. This should be called
 * before each test to ensure a clean state.
 */
fun cleanTestDatabase() {
  transaction {
    exec("DELETE FROM ${DownloadedBooksTable.tableName}")
    exec("DELETE FROM ${MoveCrossBookTable.tableName}")
    exec("DELETE FROM ${UserMovesTable.tableName}")
    exec("DELETE FROM ${UserPositionsTable.tableName}")
    exec("DELETE FROM ${MovesTable.tableName}")
    exec("DELETE FROM ${PositionsTable.tableName}")
    exec("DELETE FROM ${UserPermissionsTable.tableName}")
    exec("DELETE FROM ${UsersTable.tableName}")
    exec("DELETE FROM ${BooksTable.tableName}")
  }
}

private var migrationApplied = false
