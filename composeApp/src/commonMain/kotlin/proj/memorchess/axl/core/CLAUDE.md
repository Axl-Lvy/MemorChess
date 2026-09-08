# Core layer (`core/`)

Platform-agnostic logic for the board, the opening graph, and spaced repetition. Persistence sits behind an interface. There is no `SyncEngine` yet, but every node/edge write is already soft-deleted and stamped with the sync fields (`isDeleted`, `updatedAt`, `originDevice`, `deviceSeq`), and queued in a per-key outbox.

- **`engine/`** — chess engine wrapper (`chess-core` library). Everything except `evaluation/` also lives in `:shared`, under the same package name, so `:server` can reuse it. `evaluation/` stays here because it's the only user of `stockfish-multiplatform`.
- **`graph/`** — the opening tree, keyed by `PositionKey`. **`TreeStore` is the single mutation chokepoint** — never mutate the graph anywhere else. `DeleteMode.SOFT` is the default (a tombstone may need to reach another device); `HARD` is for local-only cleanup. `eraseAll` wipes everything unconditionally, bypassing `DeleteMode`.
- **`interactions/`** — game interaction controllers (free exploration, spaced-repetition training, read-only repertoire browsing), built on `TreeStore`/`TrainingScheduler`.
- **`data/`** — the persistence seam. `DatabaseQueryManager` has Room (`nonJsMain`), IndexedDB (`wasmJsMain`), and in-memory (tests) implementations; only `TreeStore` and those implementations may touch its node/move surface. Every write that changes a node or move queues its own outbox entry in the same transaction as the row, so a crash between the two can never drop one relative to the other. `PositionKey` lives in `:shared`.
- **`data/repertoire/`** — the downloadable repertoire catalog. `reportInstall` is fire-and-forget and never throws.
- **`scheduling/`** — `Fsrs6SchedulingAlgorithm` is the active `SchedulingAlgorithm`.
- **`streak/`** — `StreakTracker` is local-only, rolling over at local midnight; no cross-device sync.
- **`config/`** — settings never go through `DatabaseQueryManager` for their value, but share the same outbox as nodes/edges (`DirtyKey.SettingKey`).
- **`sync/`** — `DeviceIdentity` is the per-install id plus the monotonic `deviceSeq` every synced row is stamped with. No `SyncEngine` here yet.
- **`auth/`** — Lichess OAuth over PKCE.
- **`pgn/`** — PGN import pipeline, layered on `graph/`. `PgnParser`, `PgnGame`, `PgnParseException` also live in `:shared`, because the server validates uploaded PGN with the same parser the client validates a download with.
