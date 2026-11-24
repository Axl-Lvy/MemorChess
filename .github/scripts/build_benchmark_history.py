"""
Builds the history file read by the benchmark history page.

Reads every run summary written by `extract_benchmark_summary.py` (schema 1 or
2) and writes one compact JSON document holding the metric catalogue, every run
sorted by date, and per benchmarked commit its subject plus the commits that
landed since the previous benchmarked commit. The daily job only benchmarks
master's head, so a step in a chart usually spans several commits; the range
lets the page show all of them.

Usage: build_benchmark_history.py --runs-dir DIR --repo CHECKOUT --output FILE
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

HISTORY_SCHEMA = 1


def load_runs(runs_dir: Path) -> list[dict]:
    """Parses every run summary in the directory, sorted by run date."""
    runs = []
    for path in sorted(runs_dir.glob("*.json")):
        try:
            data = json.loads(path.read_text())
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            print(f"Skipping {path.name}: {error}", file=sys.stderr)
            continue
        if not isinstance(data, dict) or "commit" not in data or "date" not in data:
            print(f"Skipping {path.name}: not a run summary", file=sys.stderr)
            continue
        runs.append(data)
    return sorted(runs, key=lambda run: run["date"])


def _git(repo: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["git", "-C", str(repo), *args], check=False, capture_output=True, text=True)


def _log_entries(output: str) -> list[dict]:
    entries = []
    for line in output.splitlines():
        sha, _, subject = line.partition("\t")
        entries.append({"sha": sha, "subject": subject})
    return entries


def _exists(repo: Path, sha: str) -> bool:
    return _git(repo, "cat-file", "-e", f"{sha}^{{commit}}").returncode == 0


def commit_subjects(repo: Path) -> dict[str, str]:
    """Maps every commit reachable from HEAD to its subject line."""
    result = _git(repo, "log", "--format=%H%x09%s", "HEAD")
    return {entry["sha"]: entry["subject"] for entry in _log_entries(result.stdout)}


def commit_range(repo: Path, previous: Optional[str], commit: str) -> list[dict]:
    """Lists the commits since `previous` up to and including `commit`, newest first."""
    if not _exists(repo, commit):
        return []
    if previous is None or not _exists(repo, previous):
        result = _git(repo, "log", "-1", "--format=%H%x09%s", commit)
    else:
        result = _git(repo, "log", "--first-parent", "--format=%H%x09%s", f"{previous}..{commit}")
    return _log_entries(result.stdout)


def _values(metrics: dict) -> dict[str, list[float]]:
    values = {}
    for key, entry in metrics.items():
        error = entry.get("error")
        values[key] = [entry["value"]] if error is None else [entry["value"], error]
    return values


def build_history(runs: list[dict], repo: Path, generated: str) -> dict:
    """Assembles the history document from date-sorted run summaries."""
    subjects = commit_subjects(repo)
    metrics: dict[str, dict] = {}
    commits: dict[str, dict] = {}
    history_runs = []
    previous = None
    for run in runs:
        sha = run["commit"]
        for key, entry in run.get("metrics", {}).items():
            metrics[key] = {"unit": entry.get("unit", ""), "direction": entry.get("direction", "lower")}
        if sha not in commits:
            commits[sha] = {"subject": subjects.get(sha, ""), "range": commit_range(repo, previous, sha)}
            previous = sha
        cpu = run.get("cpu") or {}
        history_runs.append(
            {
                "commit": sha,
                "date": run["date"],
                "cpu": {"micro": cpu.get("micro"), "macro": cpu.get("macro")},
                "values": _values(run.get("metrics", {})),
            }
        )
    return {
        "schema": HISTORY_SCHEMA,
        "generated": generated,
        "metrics": metrics,
        "commits": commits,
        "runs": history_runs,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs-dir", type=Path, required=True)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)

    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    history = build_history(load_runs(args.runs_dir), args.repo, generated)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(history, separators=(",", ":")))
    print(f"Wrote {len(history['runs'])} runs over {len(history['commits'])} commits to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
