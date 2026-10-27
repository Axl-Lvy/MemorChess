package proj.memorchess.axl.server.db

import javax.sql.DataSource

/** Anchor for locating `schema.sql` on the classpath. */
private object SchemaResource

/**
 * Applies the idempotent `schema.sql`, safe on every boot.
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
