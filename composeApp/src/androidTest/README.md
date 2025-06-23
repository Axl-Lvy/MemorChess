# AnkiChess Test Framework

## Overview

This project uses a custom UI testing framework designed for efficient testing of the MemorChess application. The framework is built on top of Jetpack Compose's testing utilities and provides a structured approach to creating and running UI tests.

## Key Components

### TestRunner

The [`TestRunner`](composeApp/src/androidTest/kotlin/proj/memorchess/axl/TestRunner.kt) class is the main entry point for running tests. It:

- Manages the lifecycle of test execution
- Collects and runs tests from all registered test factories
- Handles test setup and teardown
- Provides utilities for database reset and initialization
- Reports test results (success/failure)

The TestRunner is designed to run all tests in a single application instance to improve test execution speed. Instead of recreating the app for each test (which is time-consuming), the framework:

1. Reuses the same app instance across tests
2. Resets the app state between tests as needed
3. Only resets the database when explicitly required by a test factory

### TestFactories

Test factories are classes that produce test functions. Each factory:

- Extends [`TestRunner`](composeApp/src/androidTest/kotlin/proj/memorchess/axl/AUiTestFactory.kt)
- Provides a list of test functions via `createTests()`
- Defines setup logic in `beforeEach()`
- Specifies whether database reset is needed via `needsDatabaseReset()`

## Why This Approach?

The framework is designed to optimize test execution speed while maintaining test reliability:

1. **Performance**: Creating a new app instance for each test is slow. By reusing the same instance and only resetting what's necessary, tests run much faster.
2. **Isolation**: Despite sharing an app instance, tests remain isolated through careful state management.
3. **Maintainability**: The factory pattern makes it easy to organize tests by feature area.
4. **Extensibility**: Adding new tests is as simple as creating a new factory or adding methods to an existing one.

## Creating Tests

To create new tests:

1. Create a new class extending `AUiTestFactory`
2. Implement the required methods:
   - `createTests()`: Return a list of test functions
   - `beforeEach()`: Set up the test environment
   - `needsDatabaseReset()`: Return true if the database should be reset before tests
3. Add your test factory to the `testFactories` list in `TestRunner`

## Running Tests

Tests can be run in several ways:

1. **Run all tests**: Execute the `runTests()` method in `TestRunner`
2. **Run a specific test class**: Use the `runSingleTestClass()` method (modify it to use your test factory)
3. **Run a single test**: Use the `runSingleTest()` method (modify it to call your specific test)

## Utilities

The framework provides several utilities to simplify test writing:

- Navigation helpers (`goToExplore()`, `goToTraining()`, etc.)
- Board interaction methods (`clickOnTile()`, `playMove()`, etc.)
- Assertion helpers (`assertTileContainsPiece()`, `assertNodeWithTagExists()`, etc.)
- Database access methods (`getAllPositions()`, `getPosition()`, etc.)
- Waiting utilities (`Awaitility.awaitUntilTrue()`)
