"""Loads and validates pipeline.toml: which harness, model and prompt each step uses."""

import hashlib
import tomllib
from dataclasses import dataclass
from pathlib import Path

KNOWN_HARNESSES = frozenset({"fake", "chat", "claude-code"})
REQUIRED_STEPS = ("triage", "implement")
OPTIONAL_STEPS = ("review", "precheck")


class ConfigError(Exception):
    """The config file is missing a field, has a wrong type, or names something unknown."""


@dataclass(frozen=True)
class StepConfig:
    """One LLM step: the harness that runs it, the model, the prompt template and its limits."""

    name: str
    harness: str
    model: str
    prompt: Path
    max_turns: int
    timeout_s: int
    retries: int
    enabled: bool


@dataclass(frozen=True)
class ModuleConfig:
    """One Gradle module the trusted checks know how to compile, format-check and test."""

    name: str
    test_task: str
    compile_task: str
    format_task: str
    results_dir: str
    src_dirs: tuple[str, ...]


@dataclass(frozen=True)
class Config:
    """The whole pipeline configuration, plus the hash of the file it came from."""

    steps: dict[str, StepConfig]
    modules: dict[str, ModuleConfig]
    java_min: int
    gradle_args: tuple[str, ...]
    deny_paths: tuple[str, ...]
    build_files: tuple[str, ...]
    stray_globs: tuple[str, ...]
    fix_rounds: int
    attempts: int
    brief_gate: bool
    llm_base_url: str
    sandbox_ro_binds: tuple[str, ...]
    source_sha256: str


def _field(table: dict, key: str, kind: type, where: str):
    if key not in table:
        raise ConfigError(f"{where}: missing '{key}'")
    value = table[key]
    if kind is int and isinstance(value, bool) or not isinstance(value, kind):
        raise ConfigError(f"{where}: '{key}' must be {kind.__name__}")
    return value


def _strings(table: dict, key: str, where: str) -> tuple[str, ...]:
    values = _field(table, key, list, where)
    if not all(isinstance(v, str) for v in values):
        raise ConfigError(f"{where}: '{key}' must be a list of strings")
    return tuple(values)


def _step(name: str, table: dict, base: Path) -> StepConfig:
    where = f"steps.{name}"
    harness = _field(table, "harness", str, where)
    if harness not in KNOWN_HARNESSES:
        raise ConfigError(f"{where}: unknown harness '{harness}' (known: {sorted(KNOWN_HARNESSES)})")
    prompt = base / _field(table, "prompt", str, where)
    if not prompt.is_file():
        raise ConfigError(f"{where}: prompt file {prompt} does not exist")
    return StepConfig(
        name=name,
        harness=harness,
        model=_field(table, "model", str, where),
        prompt=prompt,
        max_turns=_field(table, "max_turns", int, where),
        timeout_s=_field(table, "timeout_s", int, where),
        retries=_field(table, "retries", int, where),
        enabled=_field(table, "enabled", bool, where),
    )


def _module(name: str, table: dict) -> ModuleConfig:
    where = f"modules.{name}"
    return ModuleConfig(
        name=name,
        test_task=_field(table, "test_task", str, where),
        compile_task=_field(table, "compile_task", str, where),
        format_task=_field(table, "format_task", str, where),
        results_dir=_field(table, "results_dir", str, where),
        src_dirs=_strings(table, "src_dirs", where),
    )


def build_config(raw: dict, base: Path, source_sha256: str) -> Config:
    """Validates an already-parsed TOML table; prompt paths resolve against `base`."""
    steps_table = _field(raw, "steps", dict, "config")
    unknown = set(steps_table) - set(REQUIRED_STEPS) - set(OPTIONAL_STEPS)
    if unknown:
        raise ConfigError(f"config: unknown steps {sorted(unknown)}")
    steps = {name: _step(name, table, base) for name, table in steps_table.items()}
    for name in REQUIRED_STEPS:
        if name not in steps or not steps[name].enabled:
            raise ConfigError(f"steps.{name}: required and must be enabled")
    modules = {name: _module(name, t) for name, t in _field(raw, "modules", dict, "config").items()}
    if not modules:
        raise ConfigError("config: at least one module is required")
    return Config(
        steps=steps,
        modules=modules,
        java_min=_field(raw, "java_min", int, "config"),
        gradle_args=_strings(raw, "gradle_args", "config"),
        deny_paths=_strings(raw, "deny_paths", "config"),
        build_files=_strings(raw, "build_files", "config"),
        stray_globs=_strings(raw, "stray_globs", "config"),
        fix_rounds=_field(raw, "fix_rounds", int, "config"),
        attempts=_field(raw, "attempts", int, "config"),
        brief_gate=_field(raw, "brief_gate", bool, "config"),
        llm_base_url=_field(raw, "llm_base_url", str, "config"),
        sandbox_ro_binds=_strings(raw, "sandbox_ro_binds", "config"),
        source_sha256=source_sha256,
    )


def load_config(path: Path) -> Config:
    """Reads and validates the pipeline config at `path`."""
    data = path.read_bytes()
    try:
        raw = tomllib.loads(data.decode())
    except tomllib.TOMLDecodeError as e:
        raise ConfigError(f"{path}: {e}") from e
    return build_config(raw, path.parent, hashlib.sha256(data).hexdigest())
