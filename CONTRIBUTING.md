# Contributing to MemorChess ♟️

Thank you for your interest in contributing to MemorChess! 🚀 Please follow these guidelines to help us maintain a high-quality codebase and smooth development process.

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

## 🎉 Thanks for Contributing!

<p align="center">
  <img src="https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif" alt="Thank You" />
</p>

We appreciate your contributions! If you have any questions, feel free to open an issue or ask in the PR. 🏆
