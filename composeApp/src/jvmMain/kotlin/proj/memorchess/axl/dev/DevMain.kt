package proj.memorchess.axl.dev

import java.io.File
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.config.MAX_NEW_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.MAX_TOTAL_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.launchDesktopWindow

private const val SEED_RANDOM = 42

/** Records which dev server run this data was synced with; `devReseed` compares it. */
private const val SERVER_GENERATION_FILE = "server-generation"

/** Desktop entry point for `./gradlew :composeApp:devRun`: per-worktree, seeded dev data. */
fun main() {
  val config = currentDevLaunchConfig
  if (config == null) {
    System.err.println("DevMain runs only through ./gradlew :composeApp:devRun")
    exitProcess(1)
  }
  val session = readDevSession(config.sessionFile)
  expiredSessionHint(session, DateUtil.now())?.let(System.err::println)
  // Must run before anything reads SYNC_BASE_URL, which is initialised once from this property.
  session?.let { System.setProperty(DEV_SYNC_URL_PROPERTY, it.baseUrl) }
  val devAuth = module { single<AuthProvider> { DevAuthProvider(session) } }
  val koin = startKoin {
    allowOverride(true)
    modules(*initKoinModules(), devAuth)
  }
    .koin
  mirrorLichessToken(sharedLichessTokenStore(), koin.get<OAuthTokenStore>())
  runBlocking { seedIfEmpty(config, koin) }
  session?.let { File(config.dataDir, SERVER_GENERATION_FILE).writeText(it.generation.value) }
  launchDesktopWindow("Memor Chess (dev: ${worktreeName(config)})")
}

private suspend fun seedIfEmpty(config: DevLaunchConfig, koin: Koin) {
  val database = koin.get<DatabaseQueryManager>()
  if (database.getNodesPage(null, 1).nodes.isNotEmpty()) return
  val marker = File(config.dataDir, "seeded")
  if (marker.exists()) {
    System.err.println(
      "Dev database was wiped by a schema change; run ./gradlew :composeApp:devRun -Preseed",
    )
    exitProcess(1)
  }
  val started = TimeSource.Monotonic.markNow()
  val tagStore = koin.get<RepertoireTagStore>()
  val treeStore = koin.get<TreeStore>()
  DevSeeder(
      tagStore = tagStore,
      importer = PgnImporter(treeStore, tagStore),
      treeStore = treeStore,
      database = database,
      activityStore = koin.get(),
      simulator =
        SeedSimulator(
          algorithm = Fsrs6SchedulingAlgorithm(enableFuzz = { false }, enableShortTerm = { true }),
          random = Random(SEED_RANDOM),
          timeZone = TimeZone.currentSystemDefault(),
          maxNewPerDay = MAX_NEW_MOVES_PER_DAY_SETTING.getValue(),
          maxTotalPerDay = MAX_TOTAL_MOVES_PER_DAY_SETTING.getValue(),
        ),
    )
    .seed(DateUtil.now())
  marker.writeText(DateUtil.now().toString())
  println("Seeded dev data in ${started.elapsedNow().inWholeMilliseconds} ms")
}

/** The worktree folder name, from `<worktree>/composeApp/build/dev-data`. */
private fun worktreeName(config: DevLaunchConfig): String =
  config.dataDir.absoluteFile.parentFile?.parentFile?.parentFile?.name ?: "?"
