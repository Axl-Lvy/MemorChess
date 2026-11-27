"""Runs Claude Code (`claude -p`) against the local LiteLLM endpoint, inside the sandbox."""

import json
import shutil
from dataclasses import replace
from pathlib import Path

from agentrun.adapters.base import StepResult, output_path, run_bounded
from agentrun.config import StepConfig
from agentrun.sandbox import Sandbox, bwrap_argv


def parse_stream(text: str) -> tuple[str | None, int | None, int | None, int | None]:
    """Reads session id, turns, input and output tokens from the last `result` event."""
    result = None
    for line in text.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(event, dict) and event.get("type") == "result":
            result = event
    if result is None:
        return None, None, None, None
    usage = result.get("usage") or {}
    tokens_in = sum(
        usage.get(k) or 0
        for k in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens")
    )
    return result.get("session_id"), result.get("num_turns"), tokens_in, usage.get("output_tokens")


class ClaudeCodeAdapter:
    """One fresh Claude Code home per call; `resume` continues the session with feedback."""

    can_resume = True

    def __init__(self, base_url: str):
        self.base_url = base_url

    def build_argv(
        self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path, resume: str | None
    ) -> list[str]:
        home = out_dir / "home"
        claude = shutil.which("claude") or "claude"
        ro = sb.ro_binds + (str(Path(claude).resolve().parent),) if shutil.which("claude") else sb.ro_binds
        env = {
            **sb.env,
            "HOME": str(home),
            "CLAUDE_CONFIG_DIR": str(home / ".claude"),
            "PATH": f"{Path(claude).resolve().parent}:/usr/bin:/bin",
            "ANTHROPIC_BASE_URL": self.base_url,
            "ANTHROPIC_AUTH_TOKEN": "sk-local-proxy",
            "AGENT_OUT": str(out_dir),
            "DISABLE_AUTOUPDATER": "1",
            "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC": "1",
        }
        inner = [
            "claude", "-p", prompt,
            "--output-format", "stream-json", "--verbose",
            "--max-turns", str(step.max_turns),
            "--model", step.model,
            "--dangerously-skip-permissions",
        ]
        if resume:
            inner += ["--resume", resume]
        sandbox = replace(sb, ro_binds=ro, rw_binds=sb.rw_binds + (str(out_dir),), env=env)
        return bwrap_argv(sandbox, inner)

    def _run(self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path, resume: str | None) -> StepResult:
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "home" / ".claude").mkdir(parents=True, exist_ok=True)
        log = out_dir / f"{step.name}.stream.jsonl"
        code, wall = run_bounded(self.build_argv(prompt, step, sb, out_dir, resume), step.timeout_s, log)
        session, turns, tokens_in, tokens_out = parse_stream(log.read_text(errors="replace"))
        target = output_path(out_dir, step)
        status = "timeout" if code is None else ("ok" if code == 0 else "error")
        return StepResult(
            status, session, target if target.exists() else None, turns, tokens_in, tokens_out, wall, log
        )

    def start(self, prompt: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        return self._run(prompt, step, sb, out_dir, resume=None)

    def resume(self, session: str, feedback: str, step: StepConfig, sb: Sandbox, out_dir: Path) -> StepResult:
        return self._run(feedback, step, sb, out_dir, resume=session)
