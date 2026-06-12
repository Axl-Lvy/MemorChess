# repertoire-data

This orphan branch holds the curated opening repertoires that MemorChess downloads at runtime. The app fetches these files through raw.githubusercontent.com, following the same pattern as the benchmark-data branch. The branch contains no application code and never merges into master.

## Layout

```
manifest.json    index of all available repertoires
pgn/             one PGN file per repertoire
validate.py      validation script, also run by CI
```

## Manifest format

`manifest.json` has a top level `schemaVersion` (integer, currently 1) and a `repertoires` list. Each entry describes one repertoire:

| Field | Meaning |
| --- | --- |
| `id` | Unique kebab-case identifier, for example `london-system-white`. |
| `name` | Display name shown to the user. |
| `color` | Side the repertoire is built for, either `white` or `black`. |
| `description` | One or two plain language sentences about the repertoire. |
| `moveCount` | Total number of SAN moves in the file, mainline plus all variations, as counted by `validate.py`. |
| `file` | Path of the PGN file relative to the branch root, for example `pgn/london-system-white.pgn`. |

## PGN conventions

* One game per file, with at least `[Event "..."]`, a `[White "..."]` or `[Black "..."]` header naming the repertoire, and `[Result "*"]`. The movetext ends with `*` because a repertoire has no result.
* The mainline goes 8 to 12 moves deep.
* Moves for the repertoire color are single: each position has exactly one recommended move. Opponent moves branch into variations covering the main replies.
* `{ comments }` are welcome on key moves to explain the idea behind them.
* Every move in every variation must be legal. `validate.py` walks the whole tree and rejects anything python-chess cannot play.

## Contributing

1. Add your PGN file under `pgn/`.
2. Add a matching entry to `manifest.json`. To get the correct `moveCount`, run `python validate.py --print-counts`.
3. Run the validation locally:

   ```sh
   python3 -m venv .venv
   .venv/bin/pip install chess
   .venv/bin/python validate.py
   ```

4. Push to `repertoire-data`. The `validate-repertoires` workflow runs the same script on every push and fails the build on any inconsistency.
