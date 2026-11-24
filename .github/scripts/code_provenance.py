"""Builds the code provenance report: the share of code lines written in the Human, Copilot and Claude eras."""

import re
import subprocess
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import date, datetime, timezone
from enum import Enum
from pathlib import Path

COPILOT_START = date(2025, 8, 9)
CLAUDE_START = date(2026, 3, 7)

SOURCE_SUFFIXES = {".kt", ".kts", ".swift", ".js", ".py", ".sh", ".sql"}
COMMENT_PREFIXES = ("//", "/*", "*", "#", "--")
IGNORE_REVS_FILE = ".git-blame-ignore-revs"
_HEADER = re.compile(rb"^[0-9a-f]{40} \d+ (\d+)")


class Era(str, Enum):
    """Who wrote a line, judged by the date of the commit that last touched it."""

    HUMAN = "human"
    COPILOT = "copilot"
    CLAUDE = "claude"


@dataclass(frozen=True)
class Line:
    """One source line; `era` is None when the line is not counted."""

    number: int
    text: str
    era: Era | None


@dataclass(frozen=True)
class FileReport:
    """The classified lines of one source file."""

    path: str
    lines: list[Line]

    @property
    def counts(self) -> dict[Era, int]:
        """Counted lines per era."""
        tally = Counter(line.era for line in self.lines if line.era is not None)
        return {era: tally[era] for era in Era}


def era_for(day: date) -> Era:
    """Returns the era a commit made on `day` belongs to."""
    if day < COPILOT_START:
        return Era.HUMAN
    if day < CLAUDE_START:
        return Era.COPILOT
    return Era.CLAUDE


def is_code_line(text: str) -> bool:
    """Tells whether a line holds code rather than nothing or only a comment."""
    stripped = text.strip()
    return bool(stripped) and not stripped.startswith(COMMENT_PREFIXES)


def collect(repo: Path) -> list[FileReport]:
    """Blames every tracked source file of `repo`, sorted by path. Runs one `git blame` per file."""
    with ThreadPoolExecutor(max_workers=8) as pool:
        return list(pool.map(lambda path: _blame(repo, path), _source_files(repo)))


def _source_files(repo: Path) -> list[str]:
    listing = subprocess.run(["git", "ls-files", "-z"], cwd=repo, check=True, capture_output=True).stdout
    paths = (p for p in listing.decode().split("\0") if p)
    return sorted(
        p
        for p in paths
        if Path(p).suffix in SOURCE_SUFFIXES
        and not p.startswith("gradle/wrapper/")
        and (repo / p).is_file()
        and not (repo / p).is_symlink()
    )


def _blame(repo: Path, path: str) -> FileReport:
    command = ["git", "blame", "-w", "-M", "-C", "--line-porcelain"]
    if (repo / IGNORE_REVS_FILE).is_file():
        command += ["--ignore-revs-file", IGNORE_REVS_FILE]
    output = subprocess.run([*command, "--", path], cwd=repo, check=True, capture_output=True).stdout
    lines = []
    number, author, day = 0, "", date.min
    for raw in output.split(b"\n"):
        if raw.startswith(b"\t"):
            text = raw[1:].decode("utf-8", errors="replace")
            counted = is_code_line(text) and "[bot]" not in author
            lines.append(Line(number, text, era_for(day) if counted else None))
        elif header := _HEADER.match(raw):
            number = int(header.group(1))
        elif raw.startswith(b"author "):
            author = raw[7:].decode("utf-8", errors="replace")
        elif raw.startswith(b"author-time "):
            day = datetime.fromtimestamp(int(raw[12:]), timezone.utc).date()
    return FileReport(path, lines)
