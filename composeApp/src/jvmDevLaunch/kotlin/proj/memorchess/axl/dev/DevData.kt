package proj.memorchess.axl.dev

import java.io.File
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.koin.core.Koin
import proj.memorchess.axl.core.config.MAX_NEW_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.MAX_TOTAL_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.pgn.PgnImporter
import proj.memorchess.axl.core.scheduling.Fsrs6SchedulingAlgorithm

private const val SEED_RANDOM = 42

private const val SEEDED_MARKER_FILE = "seeded"

/** Records which dev server run this data was synced with; `devReseed` compares it. */
private const val SERVER_GENERATION_FILE = "server-generation"

/** What [prepareDevData] found in the worktree's dev database. */
internal enum class DevDataState {
  /** The database already holds data; nothing was written. */
  ALREADY_SEEDED,

  /** The database was empty and has just been seeded. */
  SEEDED,

  /** The database is empty although it was seeded before: Room dropped it on a schema change. */
  WIPED_BY_SCHEMA_CHANGE,
}

/** Seeds the dev database reached through [koin] if it is empty, and reports what it found. */
internal suspend fun prepareDevData(dataDir: File, koin: Koin, now: Instant): DevDataState {
  val database = koin.get<DatabaseQueryManager>()
  if (database.getNodesPage(null, 1).nodes.isNotEmpty()) return DevDataState.ALREADY_SEEDED
  val marker = File(dataDir, SEEDED_MARKER_FILE)
  if (marker.exists()) return DevDataState.WIPED_BY_SCHEMA_CHANGE
  val tagStore = koin.get<RepertoireTagStore>()
  val treeStore = koin.get<TreeStore>()
  DevSeeder(
      tagStore = tagStore,
      importer = PgnImporter(treeStore, tagStore),
      treeStore = treeStore,
      database = database,
      activityStore = koin.get(),
      simulator =
        SeedSimulator(
          algorithm = Fsrs6SchedulingAlgorithm(enableFuzz = { false }, enableShortTerm = { true }),
          random = Random(SEED_RANDOM),
          timeZone = TimeZone.currentSystemDefault(),
          maxNewPerDay = MAX_NEW_MOVES_PER_DAY_SETTING.getValue(),
          maxTotalPerDay = MAX_TOTAL_MOVES_PER_DAY_SETTING.getValue(),
        ),
    )
    .seed(now)
  dataDir.mkdirs()
  marker.writeText(now.toString())
  return DevDataState.SEEDED
}

/** Records [session]'s server generation in [dataDir], so `devReseed` can spot a restart. */
internal fun recordServerGeneration(dataDir: File, session: DevSession) {
  dataDir.mkdirs()
  File(dataDir, SERVER_GENERATION_FILE).writeText(session.generation.value)
}

/** The dev window title, naming the worktree that owns `<worktree>/composeApp/build/dev-data`. */
internal fun devWindowTitle(dataDir: File): String {
  val worktree = dataDir.absoluteFile.parentFile?.parentFile?.parentFile?.name ?: "?"
  return "Memor Chess (dev: $worktree)"
}
