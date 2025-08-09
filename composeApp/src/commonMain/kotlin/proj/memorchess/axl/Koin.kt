package proj.memorchess.axl

import io.github.jan.supabase.SupabaseClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import proj.memorchess.axl.core.data.CompositeDatabase
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.LocalDatabaseHolder
import proj.memorchess.axl.core.data.online.auth.AuthManager
import proj.memorchess.axl.core.data.online.auth.BasicAuthManager
import proj.memorchess.axl.core.data.online.createSupabaseClient
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager

/**
 * Initializes koin modules
 *
 * @return An array of all koin modules
 */
fun initKoinModules(): Array<Module> {
  val dataModule = module {
    single<SupabaseClient> { createSupabaseClient() }
    singleOf(::BasicAuthManager) bind AuthManager::class
    single<DatabaseQueryManager>(named("local")) { LocalDatabaseHolder.getDatabase() }
    single<SupabaseQueryManager> { SupabaseQueryManager(get(), get()) }
    single<DatabaseQueryManager> { CompositeDatabase(get(), get(named("local"))) }
    single<DatabaseSynchronizer> { DatabaseSynchronizer(get(), get(), get(named("local"))) }
  }
  return arrayOf(dataModule)
}
