"""A scripted adapter for tests: replays queued results without running any model."""

import json
from pathlib import Path
from typing import Callable

from agentrun.adapters.base import StepResult, output_path
from agentrun.config import StepConfig
from agentrun.sandbox import Sandbox


class FakeAdapter:
    """Each call pops the next script entry.

    An entry may set `output` (JSON-serialisable, or None for no file), `raw` (text written
    as-is), `status`, `session`, and `effect` (a callable run with the sandbox, used to edit
    the scratch tree the way an implementer would).
    """

    can_resume = True

    def __init__(self, script: list[dict], can_resume: bool = True):
        self.script = list(script)
        self.can_resume = can_resume
        self.calls: list[tuple[str, str]] = []

    def _play(self, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        entry = self.script.pop(0) if self.script else {"output": None}
        out_dir.mkdir(parents=True, exist_ok=True)
        effect: Callable[[Sandbox], None] | None = entry.get("effect")
        if effect:
            effect(sb)
        target = output_path(out_dir, step)
        if "raw" in entry:
            target.write_text(entry["raw"])
        elif entry.get("output") is not None:
            target.write_text(json.dumps(entry["output"]))
        log = out_dir / f"{step.name}.log"
        log.write_text("fake")
        return StepResult(
            status=entry.get("status", "ok"),
            session=entry.get("session", "fake-session"),
            output_file=target if target.exists() else None,
            turns=entry.get("turns", 1),
            tokens_in=0,
            tokens_out=0,
            wall_s=0.0,
            log=log,
        )

    def start(self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        self.calls.append(("start", prompt))
        return self._play(step, sb, out_dir)

    def resume(self, session: str, feedback: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        self.calls.append(("resume", feedback))
        return self._play(step, sb, out_dir)
