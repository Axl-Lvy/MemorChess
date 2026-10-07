package proj.memorchess.axl.server.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Counts every JDBC round trip a test drives through it, over a real Testcontainers connection. */
internal class CountingDataSource(
  private val delegate: DataSource,
  val executions: AtomicInteger = AtomicInteger(),
) : DataSource by delegate {
  override fun getConnection(): Connection = CountingConnection(delegate.connection, executions)
}

/** Wraps a real connection so every prepared statement it creates counts its own round trips. */
internal class CountingConnection(
  private val delegate: Connection,
  private val executions: AtomicInteger,
) : Connection by delegate {
  override fun prepareStatement(sql: String): PreparedStatement =
    CountingPreparedStatement(delegate.prepareStatement(sql), executions)
}

/** Counts one round trip per `executeQuery`, `executeUpdate`, `execute`, or `executeBatch` call. */
internal class CountingPreparedStatement(
  private val delegate: PreparedStatement,
  private val executions: AtomicInteger,
) : PreparedStatement by delegate {
  override fun executeQuery(): ResultSet {
    executions.incrementAndGet()
    return delegate.executeQuery()
  }

  override fun executeUpdate(): Int {
    executions.incrementAndGet()
    return delegate.executeUpdate()
  }

  override fun execute(): Boolean {
    executions.incrementAndGet()
    return delegate.execute()
  }

  override fun executeBatch(): IntArray {
    executions.incrementAndGet()
    return delegate.executeBatch()
  }
}
