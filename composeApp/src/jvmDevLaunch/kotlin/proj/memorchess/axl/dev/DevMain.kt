package proj.memorchess.axl.dev

import kotlin.system.exitProcess
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.launchDesktopWindow

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
  val started = TimeSource.Monotonic.markNow()
  when (runBlocking { prepareDevData(config.dataDir, koin, DateUtil.now()) }) {
    DevDataState.SEEDED ->
      println("Seeded dev data in ${started.elapsedNow().inWholeMilliseconds} ms")
    DevDataState.ALREADY_SEEDED -> Unit
    DevDataState.WIPED_BY_SCHEMA_CHANGE -> {
      System.err.println(
        "Dev database was wiped by a schema change; run ./gradlew :composeApp:devRun -Preseed",
      )
      exitProcess(1)
    }
  }
  session?.let { recordServerGeneration(config.dataDir, it) }
  launchDesktopWindow(devWindowTitle(config.dataDir))
}
