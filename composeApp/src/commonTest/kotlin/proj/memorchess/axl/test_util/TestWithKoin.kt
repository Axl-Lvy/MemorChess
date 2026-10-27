package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.SETTINGS_SYNC_SCOPE
import proj.memorchess.axl.core.auth.LICHESS_REDIRECT_URI
import proj.memorchess.axl.core.auth.LichessOAuthClient
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthLauncher
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.explorer.LichessExplorerClient
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.theme.AppTheme

/**
 * Base class for tests that need Koin. Use [test] for coroutine tests, or [koinSetUp] and
 * [koinTearDown] around `runComposeUiTest`.
 */
abstract class TestWithKoin : KoinComponent {

  /** Recording navigator provided under [LocalNavigator]. */
  protected val navigator: RememberLastRouteNavigator = RememberLastRouteNavigator()

  /** Override to add suspend setup logic within [test]'s single coroutine scope. */
  open suspend fun setUp() {}

  /** Override to add suspend teardown logic within [test]'s single coroutine scope. */
  open suspend fun tearDown() {}

  /**
   * Runs [block] in a single [runTest] with Koin and [setUp]/[tearDown] around it, since wasmJs
   * allows only one `runTest` per test method.
   */
  fun test(block: suspend TestScope.() -> Unit) = runTest {
    koinSetUp()
    setUp()
    try {
      block()
    } finally {
      try {
        tearDown()
      } finally {
        koinTearDown()
      }
    }
  }

  /** Starts Koin with test modules. Use directly only for UI tests that cannot use [test]. */
  protected fun koinSetUp() {
    stopKoin()
    startKoin { modules(*initKoinModules(), initTestModule()) }
    MOVE_ANIMATION_DURATION_SETTING.setValue(Duration.ZERO)
    ToastRendererForTests.clear()
    navigator.lastRoute = null
  }

  /** Stops Koin and resets state. Use directly only for UI tests that cannot use [test]. */
  protected fun koinTearDown() {
    ALL_SETTINGS_ITEMS.forEach { it.reset() }
    stopKoin()
    ToastRendererForTests.clear()
  }

  fun initTestModule(): Module {
    return module {
      single<Settings> { TestSettings() }
      single<DatabaseQueryManager> { TestDatabases.empty() }
      single<DailyActivityStore> { InMemoryDailyActivityStore() }
      single<ToastRenderer> { ToastRendererForTests }
      // Unconfined so the settings outbox enqueue runs inline instead of outliving the test on
      // another thread. Never cancelled: ALL_SETTINGS_ITEMS cache their Koin injections forever, so
      // a cancelled scope would make every later test silently skip markDirty.
      single<CoroutineScope>(named(SETTINGS_SYNC_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
      // An in memory explorer cache and an always failing engine, so no test hits Lichess.
      single { offlineExplorerHttpClient() }
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
      single {
        val tokenStore: OAuthTokenStore = get()
        LichessExplorerClient(httpClient = get(), tokenProvider = { tokenStore.getToken() })
      }
      single { InMemoryExplorerCache() }
      single { CachedExplorer(get(), get<InMemoryExplorerCache>()) }
    }
  }

  private fun offlineExplorerHttpClient(): HttpClient =
    HttpClient(
      MockEngine { _ -> respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
    ) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

  /**
   * Provides [LocalNavigator] and the [AppTheme] locals to [block]; without them, reading a Kinetic
   * theme local crashes.
   */
  @OptIn(KoinInternalApi::class)
  @Composable
  fun InitializeApp(block: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigator provides navigator) { AppTheme { block() } }
  }
}
