package proj.memorchess.axl.server

import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import proj.memorchess.axl.server.data.ALL_TABLES

/**
 * Configures and runs Flyway database migrations.
 *
 * This function sets up Flyway to manage database schema migrations using SQL scripts located in
 * db/migration resources folder.
 */
@OptIn(ExperimentalDatabaseMigrationApi::class)
fun Application.configureDatabase() {
  val user = environment.config.property("database.user").getString()
  val password = environment.config.property("database.password").getString()
  val jdbcUrl = environment.config.property("database.url.jdbc").getString()
  val r2dbcUrl = environment.config.property("database.url.r2dbc").getString()
  val nextMigrationVersion = executeMigrations(jdbcUrl, user, password) + 1

  // Generate migration script before running migrations
  runBlocking {
    val db =
      R2dbcDatabase.connect(url = r2dbcUrl, driver = "postgresql", user = user, password = password)
    val statements = mutableListOf<String>()
    suspendTransaction(db) {
      statements.addAll(MigrationUtils.statementsRequiredForDatabaseMigration(*ALL_TABLES))
    }

    if (statements.isNotEmpty()) {
      val scriptName = "V${nextMigrationVersion}__auto_generated.sql"
      val sourceDir = Paths.get("server/src/main/resources/db/migration")
      val sourceFile = sourceDir.resolve(scriptName).toFile()

      sourceFile.parentFile.mkdirs()
      sourceFile.writeText(statements.joinToString(";\n", postfix = ";\n"))

      log.info("Generated migration script: $scriptName with ${statements.size} statement(s)")
      log.debug("Statements before migration:\n${statements.joinToString("\n")}")

      // Execute the migration we just generated
      executeMigrations(jdbcUrl, user, password)
    } else {
      log.info("No schema changes detected. Skipping migration script generation.")
    }
  }
}

private fun executeMigrations(url: String, user: String, password: String): Int {
  val migrationDir = Paths.get("server/src/main/resources/db/migration").toAbsolutePath()
  val flyway =
    Flyway.configure().dataSource(url, user, password).locations("filesystem:$migrationDir").load()
  val migrationResult = flyway.migrate()
  if (!migrationResult.success) {
    throw Exception("Database migration failed")
  }
  return if (migrationResult.migrationsExecuted == 0) {
    migrationResult.initialSchemaVersion?.toInt() ?: 0
  } else {
    migrationResult.targetSchemaVersion?.toInt() ?: 0
  }
}
