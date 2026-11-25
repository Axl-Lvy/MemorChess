package proj.memorchess.axl.dev

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.time.Instant
import org.koin.core.component.get
import proj.memorchess.axl.core.auth.AccessToken
import proj.memorchess.axl.core.auth.Account
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.test_util.TestWithKoin

class TestDevData : TestWithKoin() {

  private val dataDir: File = Files.createTempDirectory("dev-data").toFile()

  @Test
  fun emptyDatabaseIsSeeded() = test {
    val state = prepareDevData(dataDir, getKoin(), DateUtil.now())

    state shouldBe DevDataState.SEEDED
    get<RepertoireTagStore>().repertoires() shouldHaveSize SEED_REPERTOIRES.size
    File(dataDir, "seeded").isFile.shouldBeTrue()
  }

  @Test
  fun seededDatabaseIsLeftAlone() = test {
    prepareDevData(dataDir, getKoin(), DateUtil.now())

    val state = prepareDevData(dataDir, getKoin(), DateUtil.now())

    state shouldBe DevDataState.ALREADY_SEEDED
    get<RepertoireTagStore>().repertoires() shouldHaveSize SEED_REPERTOIRES.size
  }

  @Test
  fun emptyDatabaseAfterASeedWasWipedBySchemaChange() = test {
    File(dataDir, "seeded").writeText("earlier")

    val state = prepareDevData(dataDir, getKoin(), DateUtil.now())

    state shouldBe DevDataState.WIPED_BY_SCHEMA_CHANGE
    get<RepertoireTagStore>().repertoires().shouldBeEmpty()
  }

  @Test
  fun serverGenerationIsRecordedForDevReseed() {
    val session =
      DevSession(
        baseUrl = "http://127.0.0.1:21234",
        accessToken = AccessToken("jwt"),
        expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
        account = Account("dev-user", "Dev user"),
        generation = DevServerGeneration("gen-7"),
      )

    recordServerGeneration(dataDir, session)

    File(dataDir, "server-generation").readText() shouldBe "gen-7"
  }

  @Test
  fun windowTitleNamesTheWorktree() {
    val worktreeDataDir = File("/work/feat+x/composeApp/build/dev-data")

    devWindowTitle(worktreeDataDir) shouldBe "Memor Chess (dev: feat+x)"
  }
}
