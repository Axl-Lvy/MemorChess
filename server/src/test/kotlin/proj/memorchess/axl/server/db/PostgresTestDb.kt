package proj.memorchess.axl.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * One Postgres container shared by the whole test run.
 *
 * Starting a container per test class costs seconds each. Tests isolate themselves with a distinct
 * user id instead, which is realistic: the store is multi tenant by design, and the only global
 * state is the shared `position` and `move_edge` tables, which are append only.
 */
internal object PostgresTestDb {

  private val container =
    PostgreSQLContainer("postgres:17-alpine").apply {
      withReuse(false)
      start()
    }

  private val pool: DataSource by lazy {
    HikariDataSource(
        HikariConfig().apply {
          jdbcUrl = container.jdbcUrl
          username = container.username
          password = container.password
          maximumPoolSize = 8
        }
      )
      .also { applySchema(it) }
  }

  private var counter = 0

  /** A pooled data source against a container with the schema already applied. */
  internal fun dataSource(): DataSource = pool

  /** A fresh user id, so tests never see each other's rows. */
  internal fun newUserId(): String = "user-${counter++}"
}
