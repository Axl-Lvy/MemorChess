"""Shared test fixtures: a tiny git repo and the repo's own config."""

import json
import subprocess
from pathlib import Path

from agentrun.config import load_config

AGENT_DIR = Path(__file__).resolve().parent.parent
FIXTURES = Path(__file__).resolve().parent / "fixtures"

BOARD_SRC = "shared/src/commonMain/kotlin/proj/Board.kt"
BOARD_TEST = "shared/src/commonTest/kotlin/proj/TestBoard.kt"
THEME_SRC = "composeApp/src/commonMain/kotlin/proj/Theme.kt"

FILES = {
    BOARD_SRC: "package proj\n\nfun squareToTile(square: Int): Int = square\n",
    BOARD_TEST: (
        "package proj\n\nclass TestBoard {\n"
        "  @Test fun mapsSquare() { squareToTile(1) shouldBe 1 }\n}\n"
    ),
    THEME_SRC: "package proj\n\nval contrast = 3.0\n",
    "shared/build.gradle.kts": "// build\n",
}


def git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args], check=True, capture_output=True, text=True
    ).stdout.strip()


def make_repo(root: Path, files: dict[str, str] | None = None) -> Path:
    """Creates a git repo under `root` with `files` committed on `main`; returns its path."""
    repo = root / "repo"
    repo.mkdir()
    git(repo, "init", "-q", "-b", "main")
    git(repo, "config", "user.email", "t@example.com")
    git(repo, "config", "user.name", "t")
    for path, content in (files or FILES).items():
        target = repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", "base")
    return repo


def repo_config():
    return load_config(AGENT_DIR / "pipeline.toml")


def brief_dict() -> dict:
    return json.loads((FIXTURES / "brief_ok.json").read_text())
