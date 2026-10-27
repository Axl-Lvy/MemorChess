package proj.memorchess.axl.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer
import proj.memorchess.axl.server.auth.UserId

/**
 * One Postgres container shared by the whole test run. Tests isolate themselves by user id, since
 * the only global tables, `position` and `move_edge`, are append only.
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
  internal fun newUserId(): UserId = UserId("user-${counter++}")
}
