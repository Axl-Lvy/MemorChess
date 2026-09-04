package proj.memorchess.axl.server.db

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class TestSchema {

  @Test
  fun everyTableExists() {
    val tables = mutableListOf<String>()
    PostgresTestDb.dataSource().connection.use { connection ->
      connection
        .prepareStatement(
          "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
        )
        .use { statement ->
          statement.executeQuery().use { rows -> while (rows.next()) tables += rows.getString(1) }
        }
    }
    tables shouldContainAll
      listOf(
        "position",
        "move_edge",
        "user_node",
        "user_edge",
        "user_setting",
        "repertoire_version",
      )
  }

  @Test
  fun theRevisionSequenceExists() {
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.prepareStatement("SELECT nextval('sync_revision')").use { statement ->
        statement.executeQuery().use { rows ->
          rows.next() shouldBe true
          (rows.getLong(1) > 0) shouldBe true
        }
      }
    }
  }

  @Test
  fun applyingTheSchemaTwiceIsIdempotent() {
    // Startup applies it every boot, so it has to survive being run again.
    applySchema(PostgresTestDb.dataSource())
    applySchema(PostgresTestDb.dataSource())
  }

  @Test
  fun aDuplicatePositionKeyIsRejected() {
    val key = "dup-${System.nanoTime()}"
    PostgresTestDb.dataSource().connection.use { connection ->
      connection.prepareStatement("INSERT INTO position (position_key) VALUES (?)").use {
        it.setString(1, key)
        it.executeUpdate() shouldBe 1
      }
      connection
        .prepareStatement("INSERT INTO position (position_key) VALUES (?) ON CONFLICT DO NOTHING")
        .use {
          it.setString(1, key)
          it.executeUpdate() shouldBe 0
        }
    }
  }
}
