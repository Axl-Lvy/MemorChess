# Core layer

- Every node/edge write is a soft delete or upsert stamped with `isDeleted`, `updatedAt`, `originDevice`, `deviceSeq`, and queues its outbox entry in the same transaction as the row. `eraseAll` is the only hard wipe.
- `TreeStore` is the only path for local graph mutation; remote writes land through `SyncApplier`. `NodeCache` is the only mutator of the in-memory `OpeningTree`.
- Only `TreeStore`, `SyncApplier`, `RepertoireTagStore`, `TrainableProjection`, and the `DatabaseQueryManager` implementations touch the seam's node/move surface. Settings are the exception: `ConfigItem` calls `markDirty` directly, since a setting has no row to write through.
- Most of `engine/` and the PGN parser live in `:shared` under the same package names, so `:server` can reuse them. `engine/evaluation/` stays here because it is the only user of `stockfish-multiplatform`.
- `StreakTracker` is deliberately local-only; streaks do not sync.
