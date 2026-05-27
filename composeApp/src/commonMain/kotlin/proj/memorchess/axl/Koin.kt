package proj.memorchess.axl

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import proj.memorchess.axl.core.auth.LICHESS_REDIRECT_URI
import proj.memorchess.axl.core.auth.LichessOAuthClient
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthLauncher
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.config.FUZZ_ENABLED_SETTING
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.explorer.ExplorerCache
import proj.memorchess.axl.core.data.explorer.LichessExplorerClient
import proj.memorchess.axl.core.data.explorer.getPlatformSpecificExplorerCache
import proj.memorchess.axl.core.data.getPlatformSpecificLocalDatabase
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.getPlatformSpecificToastRenderer

/**
 * Initializes koin modules.
 *
 * @return An array of all koin modules.
 */
fun initKoinModules(): Array<Module> {

  val dataModule = module {
    single<DatabaseQueryManager> { getPlatformSpecificLocalDatabase() }
    single<Settings> { getPlatformSpecificSettings() }
  }

  val schedulingModule = module {
    single<SchedulingAlgorithm> {
      Fsrs6SchedulingAlgorithm(enableFuzz = { FUZZ_ENABLED_SETTING.getValue() })
    }
  }

  val graphModule = module {
    single { TreeStore(get()) }
    single { TrainingScheduler(get(), get()) }
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

  val otherModule = module { single<ToastRenderer> { getPlatformSpecificToastRenderer() } }

  return arrayOf(dataModule, schedulingModule, graphModule, authModule, explorerModule, otherModule)
}
