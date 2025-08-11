package proj.memorchess.axl.test_util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.russhwolf.settings.Settings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.koin.compose.LocalKoinApplication
import org.koin.compose.LocalKoinScope
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.initKoinModules

interface TestWithKoin : KoinComponent {

  @BeforeTest
  fun setUp() {
    startKoin { modules(*initKoinModules(), initTestModule()) }
  }

  @AfterTest
  fun tearDown() {
    stopKoin()
  }

  fun initTestModule(): Module {
    return module {
      single<Settings> { TestSettings() }
      single<DatabaseQueryManager>(named("local")) { TestDatabaseQueryManager.empty() }
    }
  }

  @OptIn(KoinInternalApi::class)
  @Composable
  fun initializeApp(block: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalKoinScope provides KoinPlatformTools.defaultContext().get().scopeRegistry.rootScope,
      LocalKoinApplication provides KoinPlatformTools.defaultContext().get(),
    ) {
      block()
    }
  }
}
