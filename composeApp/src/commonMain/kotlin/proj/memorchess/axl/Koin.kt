package proj.memorchess.axl

import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import proj.memorchess.axl.core.config.getPlatformSpecificSettings
import proj.memorchess.axl.core.data.CompositeDatabase
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.getPlatformSpecificLocalDatabase
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.createSupabaseClient
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.DatabaseUploader
import proj.memorchess.axl.core.data.online.database.SupabaseBookQueryManager
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.graph.nodes.BookBasedNodeCache
import proj.memorchess.axl.core.graph.nodes.DbBasedNodeCache
import proj.memorchess.axl.core.graph.nodes.IsolatedBookNode
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.graph.nodes.PersonalNode
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
    single<DatabaseQueryManager>(named("local")) { getPlatformSpecificLocalDatabase() }
    single<SupabaseQueryManager> { SupabaseQueryManager(get(), get()) }
    single<SupabaseBookQueryManager> { SupabaseBookQueryManager(get(), get()) }
    single<DatabaseSynchronizer> { DatabaseSynchronizer(get(), get(), get(named("local"))) }
    single<DatabaseUploader> { DatabaseUploader(get(), get()) }
    single<DatabaseQueryManager> { CompositeDatabase(get(), get(named("local")), get()) }
    single<Settings> { getPlatformSpecificSettings() }
  }

  val nodeModule = module {
    singleOf(::DbBasedNodeCache)
    single<NodeManager<PersonalNode>> { NodeManager(::PersonalNode, DbBasedNodeCache()) }
    single<MutableMap<Long, NodeManager<IsolatedBookNode>>> { mutableMapOf() }
    factory(named("book")) { (bookId: Long) ->
      val cache: MutableMap<Long, NodeManager<IsolatedBookNode>> = get()
      cache.getOrPut(bookId) {
        NodeManager(
          nodeConstructor = { position, previousAndNextMoves, previous, next ->
            IsolatedBookNode(bookId, position, previousAndNextMoves, previous, next)
          },
          BookBasedNodeCache(bookId),
        )
      }
    }
  }

  val otherModule = module { single<ToastRenderer> { getPlatformSpecificToastRenderer() } }

  return arrayOf(authModule, dataModule, nodeModule, otherModule)
}
