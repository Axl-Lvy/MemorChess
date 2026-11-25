package proj.memorchess.axl.dev

import com.russhwolf.settings.PreferencesSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import proj.memorchess.axl.core.sync.PRODUCTION_SYNC_BASE_URL

class TestDevLaunchConfig {

  private val dataDir: File = Files.createTempDirectory("dev launch").resolve("a b+c-é").toFile()

  private val config = DevLaunchConfig(dataDir = dataDir, sessionFile = null)

  @Test
  fun noDataDirMeansNoDevConfig() {
    resolveDevLaunchConfig { null } shouldBe null
  }

  @Test
  fun dataDirPropertyGivesDevConfig() {
    val properties =
      mapOf(DEV_DATA_DIR_PROPERTY to dataDir.path, DEV_SESSION_FILE_PROPERTY to "/s/session")

    val resolved = resolveDevLaunchConfig { properties[it] }

    resolved shouldBe DevLaunchConfig(dataDir, File("/s/session"))
  }

  @Test
  fun normalLaunchKeepsTmpDatabase() {
    resolveDatabaseFile(null, File("/t")) shouldBe File("/t/my_room.db")
  }

  @Test
  fun normalLaunchKeepsProductionUrl() {
    resolveSyncBaseUrl(null, "http://x") shouldBe PRODUCTION_SYNC_BASE_URL
  }

  @Test
  fun devLaunchWithoutSessionIsUnreachable() {
    resolveSyncBaseUrl(config, null) shouldBe UNREACHABLE_SYNC_BASE_URL
  }

  @Test
  fun devLaunchUsesSessionUrl() {
    resolveSyncBaseUrl(config, "http://127.0.0.1:21234") shouldBe "http://127.0.0.1:21234"
  }

  @Test
  fun devDatabaseLivesInDataDir() {
    resolveDatabaseFile(config, File("/t")).parentFile shouldBe dataDir
  }

  @Test
  fun normalLaunchKeepsSharedPreferences() {
    resolveSettings(null).shouldBeInstanceOf<PreferencesSettings>()
  }

  @Test
  fun devSettingsPersistAcrossInstances() {
    resolveSettings(config).putString("k", "v")

    val reloaded = resolveSettings(config)

    reloaded.getStringOrNull("k") shouldBe "v"
  }
}
