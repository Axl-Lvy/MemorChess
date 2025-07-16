# Contributing to MemorChess ♟️

Thank you for your interest in contributing to MemorChess! 🚀 Please follow these guidelines to help us maintain a high-quality codebase and smooth development process.

The project roadmap can be found on [Notion](https://www.notion.so/MemorChess-20b6a98d8ad280f189bbeb49f6bbf043).

## ✨ Code Formatting

- We use [ktfmt](https://github.com/facebook/ktfmt) for Kotlin code formatting.
- Before committing, ensure your code is formatted by running:
  ```sh
  ./gradlew ktfmtCheck
  ```
- Our pre-commit hook will automatically check formatting and run tests on the `master` branch.
- Sonar is used to ensure code quality.

## 🤝 Pull Requests

- All changes must be submitted via a pull request (PR).
- When opening a PR, make sure to:
  - ✅ Check the boxes in the PR template to launch the GitHub actions. They are required for the PR to be merged.
  - 📝 The PR title **must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification** (e.g., `feat(module): add new feature`). PRs with non-conventional titles will not pass CI checks.

## 🧪 Running Tests

- Run both desktop and Android tests before submitting your PR:
  ```sh
  ./gradlew desktopTest connectedAndroidTest
  ```

## 🔐 Secrets Management

- The project uses a secrets generation system to handle sensitive information like API keys.
- Secrets are defined in `SecretsTemplate.kt` and generated at build time from values in `local.properties`.
- To add a new secret:
  1. Add the property to `SecretsTemplate.kt` with a default value of `NOT_FOUND`
  2. Add the actual value to your `local.properties` file using UPPER_SNAKE_CASE format
  3. The build system will automatically convert UPPER_SNAKE_CASE to camelCase in the generated `Secrets.kt`
- Example:
  ```kotlin
  // In SecretsTemplate.kt
  open val myApiKey = NOT_FOUND

  // In local.properties
  MY_API_KEY=your_actual_api_key_here
  ```

## 🎉 Thanks for Contributing!

<p align="center">
  <img src="https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif" alt="Thank You" />
</p>

We appreciate your contributions! If you have any questions, feel free to open an issue or ask in the PR. 🏆
