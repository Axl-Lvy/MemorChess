package proj.memorchess.axl

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.core.auth.AuthProvider
import proj.memorchess.axl.core.auth.LICHESS_REDIRECT_URI
import proj.memorchess.axl.core.auth.LichessOAuthClient
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthLauncher
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.auth.OidcClient
import proj.memorchess.axl.core.auth.OidcTokenStore
import proj.memorchess.axl.core.auth.SYNC_CLIENT_ID
import proj.memorchess.axl.core.auth.SYNC_REDIRECT_URI
import proj.memorchess.axl.core.auth.getPlatformSpecificSyncAuthProvider
import proj.memorchess.axl.core.config.FUZZ_ENABLED_SETTING
import proj.memorchess.axl.core.config.MAX_NEW_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.MAX_TOTAL_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.SHORT_TERM_ENABLED_SETTING
import proj.memorchess.axl.core.config.SettingSyncMetadataStore
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.explorer.ExplorerCache
import proj.memorchess.axl.core.data.explorer.LichessExplorerClient
import proj.memorchess.axl.core.data.explorer.getPlatformSpecificExplorerCache
import proj.memorchess.axl.core.data.getPlatformSpecificDailyActivityStore
import proj.memorchess.axl.core.data.getPlatformSpecificLocalDatabase
import proj.memorchess.axl.core.data.repertoire.CachedRepertoireCatalog
import proj.memorchess.axl.core.data.repertoire.PublishedRepertoireStore
import proj.memorchess.axl.core.data.repertoire.RepertoireCatalogClient
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishClient
import proj.memorchess.axl.core.data.study.LichessStudyClient
import proj.memorchess.axl.core.data.study.LichessStudyImporter
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.NodeCache
import proj.memorchess.axl.core.graph.NodeLoader
import proj.memorchess.axl.core.graph.Prefetcher
import proj.memorchess.axl.core.graph.RepertoireMoveExporter
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.SYNC_BASE_URL
import proj.memorchess.axl.core.sync.SyncApiClient
import proj.memorchess.axl.core.sync.SyncApplier
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.core.sync.SyncJobStore
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.getPlatformSpecificToastRenderer

/** Koin qualifier for the process lived background scope [NodeCache] and [Prefetcher] load on. */
const val PREFETCH_SCOPE: String = "prefetch"

/**
 * Koin qualifier for the process lived background scope on which a
 * [proj.memorchess.axl.core.config.ConfigItem] write fires its suspend
 * [DatabaseQueryManager.markDirty].
 */
const val SETTINGS_SYNC_SCOPE: String = "settingsSync"

/** Koin qualifier for the process lived background scope [SyncEngine]'s driving loop runs on. */
const val SYNC_ENGINE_SCOPE: String = "syncEngine"

/** Builds every Koin module of the app. */
fun initKoinModules(): Array<Module> {

  val dataModule = module {
    single<DatabaseQueryManager> { getPlatformSpecificLocalDatabase() }
    single<DailyActivityStore> { getPlatformSpecificDailyActivityStore() }
    single<Settings> { getPlatformSpecificSettings() }
    single { DeviceIdentity.persisted(get()) }
    single<CoroutineScope>(named(SETTINGS_SYNC_SCOPE)) {
      CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single { SettingSyncMetadataStore(get(), get()) }
    single { StreakTracker(get()) }
  }

  // Shared advancing RNG, not a per-card hash, so cards graded identically on the same day scatter
  // across their fuzz band instead of bunching on one due day.
  val schedulingRandom = Random(DateUtil.now().toEpochMilliseconds())

  val schedulingModule = module {
    single<SchedulingAlgorithm> {
      Fsrs6SchedulingAlgorithm(
        enableFuzz = { FUZZ_ENABLED_SETTING.getValue() },
        nextFuzz = { schedulingRandom.nextDouble() },
        enableShortTerm = { SHORT_TERM_ENABLED_SETTING.getValue() },
      )
    }
  }

  val graphModule = module {
    single<CoroutineScope>(named(PREFETCH_SCOPE)) {
      CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<NodeLoader> {
      val database: DatabaseQueryManager = get()
      NodeLoader { database.getPosition(it) }
    }
    single { NodeCache(get(), get(named(PREFETCH_SCOPE))) }
    single { Prefetcher(get(), get(named(PREFETCH_SCOPE))) }
    single { TrainableProjection(get(), get()) }
    single {
      RepertoireTagStore(get(), get(), get(), notifyDirty = { get<SyncEngine>().notifyDirty() })
    }
    single {
      TreeStore(
        get(),
        get(),
        get(),
        get(),
        get(),
        get(),
        notifyDirty = { get<SyncEngine>().notifyDirty() },
      )
    }
    single {
      TrainingScheduler(
        database = get(),
        treeStore = get(),
        tagStore = get(),
        algorithm = get(),
        maxNewMovesPerDay = { MAX_NEW_MOVES_PER_DAY_SETTING.getValue() },
        maxTotalMovesPerDay = { MAX_TOTAL_MOVES_PER_DAY_SETTING.getValue() },
        streakTracker = get(),
      )
    }
  }

  val studyModule = module {
    single { LichessStudyClient(get()) }
    single { LichessStudyImporter(get(), get(), get()) }
  }

  val authModule = module {
    single { OAuthTokenStore(get()) }
    single { LichessOAuthClient(get()) }
    single { OAuthLauncher() }
    single {
      LichessSignInController(
        launcher = get(),
        oauthClient = get(),
        tokenStore = get(),
        redirectUri = LICHESS_REDIRECT_URI,
      )
    }
    single { OidcTokenStore(get()) }
    single { OidcClient(get()) }
    single<AuthProvider> {
      getPlatformSpecificSyncAuthProvider(
        oidcClient = get(),
        tokenStore = get(),
        launcher = get(),
        redirectUri = SYNC_REDIRECT_URI,
        clientId = SYNC_CLIENT_ID,
      )
    }
  }

  val syncModule = module {
    single { SyncJobStore(get()) }
    single { SyncApplier(get(), get(), get()) }
    single { SyncApiClient(httpClient = get(), baseUrl = "$SYNC_BASE_URL/v1") }
    single<CoroutineScope>(named(SYNC_ENGINE_SCOPE)) {
      CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<SyncEngine> {
      SyncEngine(
        authProvider = get(),
        database = get(),
        applier = get(),
        apiClient = get(),
        jobStore = get(),
        deviceIdentity = get(),
        scope = get(named(SYNC_ENGINE_SCOPE)),
      )
    }
  }

  val explorerModule = module {
    single {
      HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }
    single {
      val tokenStore: OAuthTokenStore = get()
      LichessExplorerClient(httpClient = get(), tokenProvider = { tokenStore.getToken() })
    }
    single<ExplorerCache> { getPlatformSpecificExplorerCache() }
    single { CachedExplorer(get(), get()) }
  }

  val repertoireModule = module {
    single {
      RepertoireCatalogClient(httpClient = get(), baseUrl = "$SYNC_BASE_URL/v1/repertoires")
    }
    single { CachedRepertoireCatalog(get()) }
    single {
      RepertoirePublishClient(httpClient = get(), baseUrl = "$SYNC_BASE_URL/v1/repertoires")
    }
    single { PublishedRepertoireStore() }
    single { RepertoireMoveExporter(get(), get()) }
  }

  val otherModule = module { single<ToastRenderer> { getPlatformSpecificToastRenderer() } }

  return arrayOf(
    dataModule,
    schedulingModule,
    graphModule,
    studyModule,
    authModule,
    syncModule,
    explorerModule,
    repertoireModule,
    otherModule,
  )
}
