"""Builds the bubblewrap sandbox that harness steps and agent-written code run in.

The sandbox starts from an empty root: only /usr, /etc and the listed binds are visible.
The network is shared (the offline cell reaches Ollama on localhost); egress is restricted
outside the sandbox in CI.
"""

import shutil
import subprocess
from dataclasses import dataclass, field
from typing import Callable

APPARMOR_HINT = (
    "bwrap cannot create user namespaces; install the AppArmor profile for /usr/bin/bwrap "
    "described in .github/agent/README.md"
)


class PreflightError(Exception):
    """The host cannot run the pipeline safely (missing sandbox, wrong JDK, ...)."""


@dataclass(frozen=True)
class Sandbox:
    """What a sandboxed process may see: read-only and writable paths, env and working dir."""

    ro_binds: tuple[str, ...]
    rw_binds: tuple[str, ...]
    env: dict[str, str] = field(default_factory=dict)
    workdir: str = "/"


def bwrap_argv(sb: Sandbox, argv: list[str]) -> list[str]:
    """Wraps `argv` so it runs inside the sandbox described by `sb`."""
    out = [
        "bwrap",
        "--unshare-all",
        "--share-net",
        "--die-with-parent",
        "--new-session",
        "--clearenv",
        "--ro-bind", "/usr", "/usr",
        "--symlink", "usr/bin", "/bin",
        "--symlink", "usr/sbin", "/sbin",
        "--symlink", "usr/lib", "/lib",
        "--symlink", "usr/lib64", "/lib64",
        "--ro-bind", "/etc", "/etc",
        "--proc", "/proc",
        "--dev", "/dev",
        "--tmpfs", "/tmp",
    ]
    for path in sb.ro_binds:
        out += ["--ro-bind", path, path]
    for path in sb.rw_binds:
        out += ["--bind", path, path]
    for key, value in sb.env.items():
        out += ["--setenv", key, value]
    out += ["--chdir", sb.workdir, "--", *argv]
    return out


def preflight(
    which: Callable[[str], str | None] = shutil.which,
    run: Callable[..., subprocess.CompletedProcess] = subprocess.run,
) -> None:
    """Fails unless bwrap exists and can actually create a sandbox on this host."""
    if which("bwrap") is None:
        raise PreflightError("bwrap is not installed (apt install bubblewrap)")
    result = run(["bwrap", "--ro-bind", "/", "/", "true"], capture_output=True, text=True)
    if result.returncode != 0:
        raise PreflightError(f"{APPARMOR_HINT}: {result.stderr.strip()}")
