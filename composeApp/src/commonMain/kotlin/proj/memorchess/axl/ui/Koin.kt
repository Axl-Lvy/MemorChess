package proj.memorchess.axl.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import org.koin.core.module.Module
import org.koin.dsl.module
import proj.memorchess.axl.ui.pages.navigation.DelegateNavigator
import proj.memorchess.axl.ui.pages.navigation.Navigator

@Composable
fun initComposableModules(): Array<Module> {
  val navController = rememberNavController()
  val navigationModule = module { single<Navigator> { DelegateNavigator(navController) } }

  return arrayOf(navigationModule)
}
