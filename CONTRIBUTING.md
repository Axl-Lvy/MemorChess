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
  ./gradlew desktopTest :androidApp:connectedCheck
  ```

## 🔐 Credentials

- The project has no secrets-generation system. Any credential required at runtime (e.g. the live
  Lichess Opening Explorer test uses `LICHESS_API_TOKEN`) is read straight from the process
  environment via `System.getenv`.
- Locally, export the variable in your shell or run configuration before running the affected task.
- In CI, the variables are injected from GitHub Actions secrets of the same name.

## 🎉 Thanks for Contributing!

<p align="center">
  <img src="https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif" alt="Thank You" />
</p>

We appreciate your contributions! If you have any questions, feel free to open an issue or ask in the PR. 🏆
