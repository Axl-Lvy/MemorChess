package proj.memorchess.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/** Initializes the database connection pool, runs Flyway migrations, and connects Exposed. */
object DatabaseFactory {

  /** Initializes the database using environment variables for connection configuration. */
  fun init() {
    val dataSource = createDataSource()
    runMigrations(dataSource)
    Database.connect(dataSource)
  }

  /**
   * Initializes the database with an explicit JDBC URL, user, and password.
   *
   * Used by tests to point at a Testcontainers instance.
   */
  fun init(url: String, user: String, password: String) {
    val dataSource = createDataSource(url, user, password)
    runMigrations(dataSource)
    Database.connect(dataSource)
  }

  private fun createDataSource(): HikariDataSource {
    val url = requireNotNull(System.getenv("DB_URL")) { "DB_URL environment variable is required" }
    val user =
      requireNotNull(System.getenv("DB_USER")) { "DB_USER environment variable is required" }
    val password =
      requireNotNull(System.getenv("DB_PASSWORD")) {
        "DB_PASSWORD environment variable is required"
      }
    return createDataSource(url, user, password)
  }

  private fun createDataSource(url: String, user: String, password: String): HikariDataSource {
    val config =
      HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
      }
    return HikariDataSource(config)
  }

  private fun runMigrations(dataSource: HikariDataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
  }
}
