# Core layer

- Every node/edge write is a soft delete or upsert stamped with `isDeleted`, `updatedAt`, `originDevice`, `deviceSeq`, and queues its outbox entry in the same transaction as the row. `eraseAll` is the only hard wipe.
- `TreeStore` is the only path for local graph mutation; remote writes land through `SyncApplier`. `NodeCache` is the only mutator of the in-memory `OpeningTree`.
- Only `TreeStore`, `SyncApplier`, `RepertoireTagStore`, `TrainableProjection`, `HolderCleanup` (read only), and the `DatabaseQueryManager` implementations touch the seam's node/move surface. Settings are the exception: `ConfigItem` calls `markDirty` directly, since a setting has no row to write through.
- **Move holders**: a move is held by the user (a manual hold, `ownIsGood`, synced as `EdgeSyncRow`) and by installed repertoires (a tag carrying the author's `isGood`); it exists while something holds it. `isGood` is the effective marking (own > good > bad, `effectiveMarking`), derived locally and never synced. Only a manual hold change marks an `EdgeKey` dirty, so a move never held manually has no `EdgeSyncRow`. A tag with a `null` marking records membership and holds nothing.
  - Saving in `LinesExplorer` holds the whole path; `TreeStore.install` adds install holds only.
  - `TreeStore.uninstall` and a replacing `install` remove a repertoire's holds and delete what `HolderCleanup` plans: unheld moves, then the cascade from positions left with no incoming move. An unheld move is deleted locally and never pushed; deleted positions are pushed.
  - `SyncApplier` locally deletes a position that a pulled holder change leaves with no live move.
- Most of `engine/` and the PGN parser live in `:shared` under the same package names, so `:server` can reuse them. `engine/evaluation/` stays here because it is the only user of `stockfish-multiplatform`.
- `StreakTracker` is deliberately local-only; streaks do not sync.
