package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.initKoinModules
import proj.memorchess.axl.ui.components.popup.NO_OP_RENDERER
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.pages.navigation.Navigator

interface TestWithKoin : KoinComponent {

  @BeforeTest
  fun setUp() {
    startKoin { modules(*initKoinModules(), initTestModule()) }
    MOVE_ANIMATION_DURATION_SETTING.setValue(Duration.ZERO)
  }

  @AfterTest
  fun tearDown() {
    stopKoin()
  }

  fun initTestModule(): Module {
    return module {
      single<Settings> { TestSettings() }
      single<DatabaseQueryManager>(named("local")) { TestDatabaseQueryManager.empty() }
      single<ToastRenderer> { NO_OP_RENDERER }
      single<Navigator> { RememberLastRouteNavigator() }
    }
  }

  @OptIn(KoinInternalApi::class)
  @Composable
  fun InitializeApp(block: @Composable () -> Unit) {
    block()
  }
}
