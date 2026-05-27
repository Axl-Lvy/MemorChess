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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import proj.memorchess.axl.core.auth.LICHESS_REDIRECT_URI
import proj.memorchess.axl.core.auth.LichessOAuthClient
import proj.memorchess.axl.core.auth.LichessSignInController
import proj.memorchess.axl.core.auth.OAuthLauncher
import proj.memorchess.axl.core.auth.OAuthTokenStore
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.explorer.LichessExplorerClient
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.pages.navigation.LocalNavigator
import proj.memorchess.axl.ui.theme.AppTheme

/**
 * Base class for tests that need Koin dependency injection.
 *
 * Use [test] for coroutine-based tests (wraps everything in a single `runTest`). For UI tests that
 * need `runComposeUiTest`, use [koinSetUp]/[koinTearDown] directly.
 */
abstract class TestWithKoin : KoinComponent {

  /**
   * Recording navigator exposed to the composition tree under [LocalNavigator]. Tests that want to
   * assert which destination was navigated to read this directly.
   */
  protected val navigator: RememberLastRouteNavigator = RememberLastRouteNavigator()

  /** Override to add suspend setup logic within [test]'s single coroutine scope. */
  open suspend fun setUp() {}

  /** Override to add suspend teardown logic within [test]'s single coroutine scope. */
  open suspend fun tearDown() {}

  /**
   * Runs a test within a single [runTest], handling Koin lifecycle and calling [setUp]/[tearDown].
   *
   * This avoids the wasmJs limitation where only one `runTest` can be called per test method.
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
    stopKoin()
    ToastRendererForTests.clear()
  }

  fun initTestModule(): Module {
    return module {
      single<Settings> { TestSettings() }
      single<DatabaseQueryManager> { TestDatabaseQueryManager.empty() }
      single<ToastRenderer> { ToastRendererForTests }
      // Explorer overrides: an in memory cache and a MockEngine that always errors so no test
      // accidentally hits the real Lichess service. Tests that exercise the explorer rebuild
      // these on their own.
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
   * Wraps a test's composable content so that descendants can resolve [LocalNavigator] and the
   * Kinetic theme locals ([LocalKineticPalette], [LocalKineticTypography]). Production code
   * provides both from [proj.memorchess.axl.ui.App]; tests that render fragments in isolation need
   * them too — without [AppTheme] any Composable that reads
   * [proj.memorchess.axl.ui.theme.LocalKineticTypography] crashes at the `staticCompositionLocalOf`
   * default.
   */
  @OptIn(KoinInternalApi::class)
  @Composable
  fun InitializeApp(block: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigator provides navigator) { AppTheme { block() } }
  }
}
