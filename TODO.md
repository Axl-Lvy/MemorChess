# MemorChess Roadmap

Roadmap summary for this project. This document lists future developments, sorted by feature.

Items marked with **GFI** are "Good First Issues" and are suitable for new contributors.

### Database

- [ ] JS database.
- [ ] Have an online database per user.
- [ ] Allow users to import/export their own database. The format should be compatible with every
  kind of database.
- [x] Store graphs in the database.
- [ ] Import PGN files.

### Training

- [ ] Good and bad moves.
- [ ] Training manager.
- [ ] Store last and next training date for each move.
- [ ] Compute the next training date based on the last training date and the training algorithm.

### Settings

- [ ] Use [Multiplatform-Settings](https://github.com/russhwolf/multiplatform-settings)
- [ ] Add a settings page.
- [ ] Implement a customizable training algorithm.

### UI

- [ ] Choose a design chart.
- [ ] Create customizable components.
    - [ ] **GFI** Highlighted selected piece.
- [ ] Different application themes.
- [ ] Use [voyager](https://github.com/adrielcafe/voyager)

### Implementation details

- [ ] **GFI** Improve testing with [assertk](https://github.com/willowtreeapps/assertk)
- [ ] Analyze code coverage

#### Refactorings

- [ ] **GFI** Remove `IStoredNode` interface and make `StoredNode` be a data class, using
  `PreviousAndNextMoves` as field.
- [ ] **GFI** Remove all the references to `IReloader` in `AInteractionsManager` and its subclasses.

### Perf engine (https://www.chessprogramming.org)

- [ ] The chess engine can be improved to allow fast move searching and evaluation.
