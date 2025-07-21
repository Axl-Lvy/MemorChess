package proj.memorchess.axl.test_util

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.initKoinModules

abstract class TestWithKoin : KoinComponent {
  @BeforeTest
  fun setupKoin() {
    startKoin { modules(*initKoinModules(), initTestModule()) }
  }

  @AfterTest
  fun tearDownKoin() {
    stopKoin()
  }

  fun initTestModule(): Module {
    return module { singleOf(::NoOpAuthManager) bind AuthManager::class }
  }
}
