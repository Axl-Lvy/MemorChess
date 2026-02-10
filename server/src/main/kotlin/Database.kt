package proj.memorchess.axl.server

import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
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
  val url = System.getenv("DATABASE_URL") ?: environment.config.property("database.url").getString()
  val firstResult = executeMigrations(url, user, password)
  val nextMigrationVersion = firstResult.version + 1

  // Generate migration script before running migrations
  runBlocking {
    Database.connect(url = url, driver = "org.postgresql.Driver", user = user, password = password)
    val statements = mutableListOf<String>()
    transaction {
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
      val secondResult = executeMigrations(url, user, password)

      // Fallback: if Flyway couldn't run the migration (e.g. fat JAR service loading issue),
      // execute the statements directly via Exposed
      if (secondResult.migrationsExecuted == 0) {
        log.warn("Flyway did not apply migrations. Executing schema statements directly.")
        transaction {
          for (statement in statements) {
            exec(statement)
          }
        }
      }
    } else {
      log.info("No schema changes detected. Skipping migration script generation.")
    }
  }
}

private data class MigrationResult(val version: Int, val migrationsExecuted: Int)

private fun executeMigrations(url: String, user: String, password: String): MigrationResult {
  val migrationDir = Paths.get("server/src/main/resources/db/migration").toAbsolutePath()
  val flyway =
    Flyway.configure()
      .dataSource(url, user, password)
      .locations("filesystem:$migrationDir")
      .load()
  val migrationResult = flyway.migrate()
  if (!migrationResult.success) {
    throw Exception("Database migration failed")
  }
  val version =
    if (migrationResult.migrationsExecuted == 0) {
      migrationResult.initialSchemaVersion?.toInt() ?: 0
    } else {
      migrationResult.targetSchemaVersion?.toInt() ?: 0
    }
  return MigrationResult(version, migrationResult.migrationsExecuted)
}
