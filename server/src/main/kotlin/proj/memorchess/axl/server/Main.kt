package proj.memorchess.axl.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import javax.sql.DataSource
import proj.memorchess.axl.server.auth.jwksProvider
import proj.memorchess.axl.server.db.applySchema
import proj.memorchess.axl.server.repertoire.RepertoireStore
import proj.memorchess.axl.server.repertoire.S3RepertoireBlobStore
import proj.memorchess.axl.server.routes.repertoireModule
import proj.memorchess.axl.server.routes.staticFrontendModule
import proj.memorchess.axl.server.sync.SyncStore

/**
 * Connection pool size. One per request in flight; Postgres is the scarce resource, not the JVM.
 */
private const val POOL_SIZE = 10

/**
 * Starts the sync server.
 *
 * Configuration comes from the environment and is validated before anything binds, so a
 * misconfigured deployment fails immediately rather than serving 500s.
 */
fun main() {
  val config = serverConfigFromEnv()
  val dataSource = config.pool()
  applySchema(dataSource)

  val blobStore =
    S3RepertoireBlobStore(
      endpoint = config.r2Endpoint,
      bucket = config.r2Bucket,
      accessKeyId = config.r2AccessKeyId,
      secretAccessKey = config.r2SecretAccessKey,
    )
  val repertoireStore = RepertoireStore(dataSource, blobStore)

  embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
      syncModule(
        config = config,
        jwkProvider = jwksProvider(config.jwksUrl),
        store = SyncStore(dataSource),
        readiness = { dataSource.isReachable() },
      )
      repertoireModule(store = repertoireStore)
      staticFrontendModule(config.staticDir)
    }
    .start(wait = true)
}

private fun ServerConfig.pool(): DataSource =
  HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = this@pool.jdbcUrl
      username = dbUser
      password = dbPassword
      maximumPoolSize = POOL_SIZE
    }
  )

/** Whether a connection can be taken from the pool and used. */
private fun DataSource.isReachable(): Boolean = runCatching {
  connection.use { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
}
  .isSuccess
