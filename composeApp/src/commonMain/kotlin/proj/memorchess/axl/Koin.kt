package proj.memorchess.axl

import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.getPlatformSpecificLocalDatabase
import proj.memorchess.axl.core.graph.DbTreeRepository
import proj.memorchess.axl.core.graph.OpeningTree
import proj.memorchess.axl.core.graph.TrainingSchedule
import proj.memorchess.axl.core.graph.TreeRepository
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.getPlatformSpecificToastRenderer

/**
 * Initializes koin modules
 *
 * @return An array of all koin modules
 */
fun initKoinModules(): Array<Module> {

  val dataModule = module {
    single<DatabaseQueryManager> { getPlatformSpecificLocalDatabase() }
    single<Settings> { getPlatformSpecificSettings() }
  }

  val nodeModule = module {
    single { OpeningTree() }
    single { TrainingSchedule(get()) }
    single<TreeRepository> { DbTreeRepository(get()) }
    single { NodeManager(get<OpeningTree>(), get<TreeRepository>(), get<TrainingSchedule>()) }
  }

  val schedulingModule = module { single<SchedulingAlgorithm> { Fsrs6SchedulingAlgorithm() } }

  val otherModule = module { single<ToastRenderer> { getPlatformSpecificToastRenderer() } }

  return arrayOf(dataModule, nodeModule, schedulingModule, otherModule)
}
