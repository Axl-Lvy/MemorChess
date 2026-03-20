package proj.memorchess.axl

import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.getPlatformSpecificLocalDatabase
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.createSupabaseClient
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.graph.BookTreeRepository
import proj.memorchess.axl.core.graph.DbTreeRepository
import proj.memorchess.axl.core.graph.OpeningTree
import proj.memorchess.axl.core.graph.TrainingSchedule
import proj.memorchess.axl.core.graph.TreeRepository
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.getPlatformSpecificToastRenderer

/**
 * Initializes koin modules
 *
 * @return An array of all koin modules
 */
fun initKoinModules(): Array<Module> {

  val authModule = module {
    single<SupabaseClient> { createSupabaseClient() }
    singleOf(::AuthManager)
  }

  val dataModule = module {
    single<DatabaseQueryManager> { getPlatformSpecificLocalDatabase() }
    single<SupabaseBookQueryManager> { SupabaseBookQueryManager(get(), get()) }
    single<Settings> { getPlatformSpecificSettings() }
  }

  val nodeModule = module {
    single { OpeningTree() }
    single { TrainingSchedule(get()) }
    single<TreeRepository> { DbTreeRepository(get()) }
    single { NodeManager(get<OpeningTree>(), get<TreeRepository>(), get<TrainingSchedule>()) }
    single<MutableMap<Long, NodeManager>> { mutableMapOf() }
    factory(named("book")) { (bookId: Long) ->
      val cache: MutableMap<Long, NodeManager> = get()
      cache.getOrPut(bookId) {
        NodeManager(
          openingTree = OpeningTree(),
          treeRepository = BookTreeRepository(bookId, get(), get()),
          trainingSchedule = null,
        )
      }
    }
  }

  val otherModule = module { single<ToastRenderer> { getPlatformSpecificToastRenderer() } }

  return arrayOf(authModule, dataModule, nodeModule, otherModule)
}
