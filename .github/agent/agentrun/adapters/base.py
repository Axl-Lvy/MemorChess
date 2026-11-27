"""The contract every harness adapter implements, and the bounded process runner they share."""

import os
import signal
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Protocol

from agentrun.config import StepConfig
from agentrun.sandbox import Sandbox


@dataclass
class StepResult:
    """What one harness invocation produced; `output_file` is set only if the agent wrote it."""

    status: Literal["ok", "timeout", "error"]
    session: str | None
    output_file: Path | None
    turns: int | None
    tokens_in: int | None
    tokens_out: int | None
    wall_s: float
    log: Path


class Adapter(Protocol):
    """Runs one LLM step with a given harness; outputs land in `out_dir/<step>.json`."""

    can_resume: bool

    def start(self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult: ...

    def resume(
        self, session: str, feedback: str, step: StepConfig, sb: Sandbox, out_dir: Path
    ) -> StepResult: ...


def agent_dir(out_dir: Path) -> Path:
    """The only part of `out_dir` the agent can write (`$AGENT_OUT`); logs stay outside it."""
    return out_dir / "agent"


def output_path(out_dir: Path, step: StepConfig) -> Path:
    """Where the agent is told to write its JSON for `step`."""
    return agent_dir(out_dir) / f"{step.name}.json"


def run_bounded(
    argv: list[str], timeout_s: int, log: Path, cwd: Path | None = None
) -> tuple[int | None, float]:
    """Runs `argv` in its own process group with output to `log`; kills the group on timeout.

    Returns the exit code (None on timeout) and the wall time in seconds.
    """
    log.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    with log.open("wb") as sink:
        proc = subprocess.Popen(
            argv,
            stdout=sink,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
            cwd=cwd,
        )
        try:
            code = proc.wait(timeout=timeout_s)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            proc.wait()
            code = None
    return code, time.monotonic() - started
