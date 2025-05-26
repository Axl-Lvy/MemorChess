# Anki Chess Openings

Roadmap summary for this project.

## Backend

### Todo

- Database management
    - [ ] Find a way to store lines (with a graph)
    - [ ] Deploy the database
    - [ ] Create a database format to export / import from a file
    - [ ] Remove
      the [fallbackToDestructiveMigration](composeApp/src/nonJsMain/kotlin/proj/ankichess/axl/core/data/CustomDatabase.kt)
- Anki method
    - [ ] Implement anki algorithm
    - [ ] Add anki function parameters
- Security
    - [ ] Account management
- Perf
    - [ ] Perf engine (https://www.chessprogramming.org)

### In progress

- Implement chess mechanism
    - [x] Pieces movements
    - [x] FEN parser
    - [x] Move parser
    - [x] Detect checks and mates
    - [ ] Promotions

### Done

## Frontend

### Todo

- Everything

### In progress

### Done
