package proj.memorchess.axl.server.db

import javax.sql.DataSource

/** Anchor for locating `schema.sql` on the classpath. */
private object SchemaResource

/**
 * Applies `schema.sql`, which is idempotent and therefore safe on every boot.
 *
 * There is no migration tool on purpose. The app is not in production, and the repo's stance is
 * that recreating a database is acceptable until it is. Introducing Flyway is a decision for when
 * real user data exists, not a default.
 *
 * The whole file goes through a single `execute` because the driver accepts a multi statement
 * string, and every statement in it is guarded by `IF NOT EXISTS`.
 *
 * @throws IllegalStateException when the resource is missing from the jar.
 */
internal fun applySchema(dataSource: DataSource) {
  val sql =
    checkNotNull(SchemaResource::class.java.getResourceAsStream("/schema.sql")) {
        "schema.sql is missing from the server resources"
      }
      .bufferedReader()
      .use { it.readText() }
  dataSource.connection.use { connection ->
    connection.createStatement().use { statement -> statement.execute(sql) }
  }
}
