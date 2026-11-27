"""Runs the agent pipeline offline on one issue.

    python3 -I .github/agent/run.py --issue issue.json --repo . --base <sha> --out /tmp/run \
        [--config .github/agent/pipeline.toml] [--set implement.model=gemma4-26b ...]

Exit codes: 0 handed off / needs info / brief ready, 1 failed, 2 config or preflight error.
"""

import argparse
import dataclasses
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from agentrun.adapters import HARNESSES  # noqa: E402
from agentrun.adapters.claude_code import claude_binary  # noqa: E402
from agentrun.checks import preflight_java  # noqa: E402
from agentrun.config import KNOWN_HARNESSES, REQUIRED_STEPS, Config, ConfigError, load_config  # noqa: E402
from agentrun.loop import load_issue, run_pipeline  # noqa: E402
from agentrun.sandbox import PreflightError, Sandbox, bwrap_argv, preflight  # noqa: E402

AGENT_DIR = Path(__file__).resolve().parent
STEP_FIELDS = {"harness": str, "model": str, "prompt": Path, "max_turns": int, "timeout_s": int, "retries": int, "enabled": bool}
TOP_FIELDS = {"fix_rounds": int, "attempts": int, "brief_gate": bool, "llm_base_url": str}


def _coerce(key: str, kind: type, value: str, base: Path):
    if kind is bool:
        if value.lower() not in ("true", "false"):
            raise ConfigError(f"--set {key}: expected true or false, got {value!r}")
        return value.lower() == "true"
    if kind is int:
        try:
            return int(value)
        except ValueError as e:
            raise ConfigError(f"--set {key}: expected an integer, got {value!r}") from e
    if kind is Path:
        path = (base / value).resolve()
        if not path.is_file():
            raise ConfigError(f"--set {key}: prompt file {path} does not exist")
        return path
    return value


def apply_overrides(cfg: Config, overrides: list[str], base: Path = AGENT_DIR) -> Config:
    """Applies `step.field=value` and top-level `field=value` overrides, validated like the file."""
    steps = dict(cfg.steps)
    top = {}
    for item in overrides:
        key, sep, value = item.partition("=")
        if not sep:
            raise ConfigError(f"--set {item}: expected key=value")
        step, dot, field = key.partition(".")
        if not dot:
            if key not in TOP_FIELDS:
                raise ConfigError(f"--set {key}: unknown setting (known: {sorted(TOP_FIELDS)})")
            top[key] = _coerce(key, TOP_FIELDS[key], value, base)
            continue
        if step not in steps or field not in STEP_FIELDS:
            raise ConfigError(f"--set {key}: unknown step or field")
        new_value = _coerce(key, STEP_FIELDS[field], value, base)
        if field == "harness" and new_value not in KNOWN_HARNESSES:
            raise ConfigError(f"--set {key}: unknown harness {new_value!r}")
        steps[step] = dataclasses.replace(steps[step], **{field: new_value})
    for name in REQUIRED_STEPS:
        if not steps[name].enabled:
            raise ConfigError(f"--set: step {name} must stay enabled")
    return dataclasses.replace(cfg, steps=steps, **top)


def preflight_gradle_home(template: str | None, repo: Path) -> None:
    """Gradle runs offline, so the template home must already hold the wrapper distribution."""
    if not template:
        raise PreflightError("set AGENT_GRADLE_HOME to a warmed Gradle home template (see README)")
    props = (repo / "gradle" / "wrapper" / "gradle-wrapper.properties").read_text()
    match = re.search(r"distributions/(gradle-[^/]+?)\.zip", props)
    dist = match.group(1) if match else None
    if not dist or not (Path(template) / "wrapper" / "dists" / dist).is_dir():
        raise PreflightError(f"{template} has no wrapper/dists/{dist}; warm it first (see README)")


def preflight_harnesses(cfg: Config) -> None:
    """Every enabled step's harness binary must exist on this host."""
    if any(s.enabled and s.harness == "claude-code" for s in cfg.steps.values()):
        binary = claude_binary()
        if not binary.is_file():
            raise PreflightError(f"Claude Code not found at {binary} (set AGENT_CLAUDE_BIN)")


def preflight_sandbox_java(java_min: int) -> None:
    """Checks the Java the sandboxed builds will run, not the host's default."""
    home = os.environ.get("JAVA_HOME")
    sb = Sandbox(
        ro_binds=(home,) if home else (),
        rw_binds=(),
        env={"PATH": f"{home}/bin:/usr/bin:/bin" if home else "/usr/bin:/bin", **({"JAVA_HOME": home} if home else {})},
    )
    preflight_java("java", java_min, run=lambda argv, **kw: subprocess.run(bwrap_argv(sb, argv), **kw))


def main(argv: list[str], preflights=None, harnesses=HARNESSES) -> int:
    """Parses arguments, checks the host, runs the pipeline, returns the exit code."""
    parser = argparse.ArgumentParser(description="Run the MemorChess agent pipeline offline on one issue.")
    parser.add_argument("--issue", type=Path, required=True, help="issue JSON: number, title, body, comments")
    parser.add_argument("--repo", type=Path, required=True, help="repository to clone from")
    parser.add_argument("--base", required=True, help="commit the change starts from")
    parser.add_argument("--out", type=Path, required=True, help="directory for every output")
    parser.add_argument("--config", type=Path, default=AGENT_DIR / "pipeline.toml")
    parser.add_argument("--set", action="append", default=[], metavar="KEY=VALUE",
                        help="override a setting, e.g. implement.model=gemma4-26b or review.enabled=false")
    args = parser.parse_args(argv)
    try:
        cfg = apply_overrides(load_config(args.config), args.set, base=args.config.parent)
        checks = preflights if preflights is not None else [
            preflight,
            lambda: preflight_sandbox_java(cfg.java_min),
            lambda: preflight_gradle_home(os.environ.get("AGENT_GRADLE_HOME"), args.repo),
            lambda: preflight_harnesses(cfg),
        ]
        for check in checks:
            check()
    except (ConfigError, PreflightError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    adapters = {name: harnesses[step.harness](cfg.llm_base_url) for name, step in cfg.steps.items() if step.enabled}
    record = run_pipeline(load_issue(args.issue), args.repo.resolve(), args.base, cfg, args.out.resolve(), adapters)
    print(f"{record.outcome}" + (f" ({record.failed_stage})" if record.failed_stage else "")
          + (f" best={record.best_sha} approved={record.approved}" if record.best_sha else "")
          + f" — {args.out / 'run.json'}")
    return 1 if record.outcome == "failed" else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
