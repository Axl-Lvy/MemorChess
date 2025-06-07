# MemorChess Roadmap

Roadmap summary for this project. This document lists future developments, sorted by feature.

## Performance Improvements

### Perf engine (https://www.chessprogramming.org)

The chess engine can be improved to allow fast move searching and evaluation.

Status: Not started

### Database

- [ ] JS database.
- [ ] Have an online database per user.
- [ ] Allow users to import/export their own database. The format should be compatible with every kind of database.
- [x] Store graphs in the database.

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
- [ ] Different application themes.

### Implementation details

- [ ] Improve testing with [assertk](https://github.com/willowtreeapps/assertk)
- [ ] Analyze code coverage
