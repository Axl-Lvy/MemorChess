package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings
import kotlin.time.Duration
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.core.config.FeatureFlags
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.pages.navigation.Navigator

/**
 * Base class for tests that need Koin dependency injection.
 *
 * Use [test] for coroutine-based tests (wraps everything in a single `runTest`). For UI tests that
 * need `runComposeUiTest`, use [koinSetUp]/[koinTearDown] directly.
 */
abstract class TestWithKoin : KoinComponent {

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
    FeatureFlags.isAuthEnabled = true
    startKoin { modules(*initKoinModules(), initTestModule()) }
    MOVE_ANIMATION_DURATION_SETTING.setValue(Duration.ZERO)
    ToastRendererForTests.clear()
  }

  /** Stops Koin and resets state. Use directly only for UI tests that cannot use [test]. */
  protected fun koinTearDown() {
    stopKoin()
    FeatureFlags.isAuthEnabled = false
    ToastRendererForTests.clear()
  }

  fun initTestModule(): Module {
    return module {
      single<Settings> { TestSettings() }
      single<DatabaseQueryManager>(named("local")) { TestDatabaseQueryManager.empty() }
      single<ToastRenderer> { ToastRendererForTests }
      single<Navigator> { RememberLastRouteNavigator() }
    }
  }

  @OptIn(KoinInternalApi::class)
  @Composable
  fun InitializeApp(block: @Composable () -> Unit) {
    block()
  }
}
