"""The run record: what ran with which config, model and prompt, and how each stage scored."""

import dataclasses
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

from agentrun.adapters.base import StepResult
from agentrun.checks import CheckReport


@dataclass
class Stage:
    """One implementation stage: the commit it produced and its trusted-check report."""

    name: str
    sha: str | None
    report: CheckReport | None
    patch_file: Path


@dataclass
class StepLog:
    """One harness invocation, with the exact harness, model and prompt hash it used."""

    step: str
    harness: str
    model: str
    prompt_sha256: str
    result: StepResult


@dataclass
class RunRecord:
    """The outcome of one pipeline run and everything needed to compare it with others."""

    outcome: Literal["handoff", "needs_info", "brief_ready", "failed"] = "failed"
    failed_stage: str | None = None
    best_sha: str | None = None
    approved: bool = False
    stages: list[Stage] = field(default_factory=list)
    steps: list[StepLog] = field(default_factory=list)
    config_sha256: str = ""
    prompts_sha256: dict[str, str] = field(default_factory=dict)
    dropped_feedback: list[str] = field(default_factory=list)
    error: str | None = None


def _plain(value):
    if isinstance(value, CheckReport):
        return {
            "green": value.green,
            "score": value.score,
            "items": [dataclasses.asdict(i) for i in value.items],
            "tests_passed": sorted(value.tests_passed),
            "tests_failed": sorted(value.tests_failed),
        }
    if dataclasses.is_dataclass(value):
        return {f.name: _plain(getattr(value, f.name)) for f in dataclasses.fields(value)}
    if isinstance(value, (list, tuple, frozenset, set)):
        return [_plain(v) for v in value]
    if isinstance(value, dict):
        return {k: _plain(v) for k, v in value.items()}
    if isinstance(value, Path):
        return str(value)
    return value


def write_record(record: RunRecord, out: Path) -> Path:
    """Writes `record` as `out/run.json` and returns its path."""
    path = out / "run.json"
    path.write_text(json.dumps(_plain(record), indent=2))
    return path
