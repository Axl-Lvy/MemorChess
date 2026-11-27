"""Clones, patches and commits, done so that nothing the agent wrote runs in the trusted process.

Every clone keeps its git data in a sibling `<dest>.gitdir` directory that is never mounted
into the sandbox, so an agent cannot plant hooks, filters or config the trusted side would run.
"""

import os
import stat
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from agentrun.brief import Brief, matches_any
from agentrun.config import Config

SAFE = ["-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "core.symlinks=true"]
IDENTITY = ["-c", "user.name=agent-runner", "-c", "user.email=agent-runner@localhost"]
ALWAYS_DENIED = (".gitattributes", "**/.gitattributes", ".gitmodules")


class GitError(Exception):
    """A git command failed; the message carries its stderr."""


def gitdir_of(worktree: Path) -> Path:
    """The git directory that belongs to `worktree`, kept outside it."""
    return worktree.parent / f"{worktree.name}.gitdir"


def _git(worktree: Path, *args: str, input_text: str | None = None) -> str:
    argv = ["git", f"--git-dir={gitdir_of(worktree)}", f"--work-tree={worktree}", *SAFE, *args]
    result = subprocess.run(argv, capture_output=True, text=True, input=input_text, cwd=worktree)
    if result.returncode != 0:
        raise GitError(f"git {' '.join(args)}: {result.stderr.strip()}")
    return result.stdout


def fresh_clone(source_repo: Path, base: str, dest: Path) -> Path:
    """Clones `source_repo` at `base` into `dest`, with hooks and fsmonitor disabled."""
    gitdir = gitdir_of(dest)
    subprocess.run(
        ["git", *SAFE, "clone", "-q", "--no-local", "--no-checkout",
         f"--separate-git-dir={gitdir}", str(source_repo), str(dest)],
        check=True, capture_output=True, text=True,
    )
    _git(dest, "config", "core.hooksPath", "/dev/null")
    _git(dest, "config", "core.fsmonitor", "false")
    _git(dest, "checkout", "-q", "--detach", base)
    return dest


def head(worktree: Path) -> str:
    """The commit `worktree` is at."""
    return _git(worktree, "rev-parse", "HEAD").strip()


def capture_patch(scratch: Path, base: str) -> str:
    """Everything the agent changed in `scratch` since `base`, as a binary-safe git diff."""
    _git(scratch, "add", "-A")
    return _git(scratch, "diff", "--cached", "--binary", "--find-renames", base)


@dataclass(frozen=True)
class Entry:
    """One changed file as git records it: modes, status and both paths (None when absent)."""

    old_mode: str
    new_mode: str
    status: str
    old_path: str | None
    new_path: str | None


def changed_entries(scratch: Path, base: str) -> list[Entry]:
    """What `capture_patch` staged, read from git's NUL-separated raw diff (no quoting issues)."""
    fields = _git(scratch, "diff", "--cached", "--raw", "-z", "-M", base).split("\0")
    entries, i = [], 0
    while i < len(fields) and fields[i]:
        meta = fields[i].lstrip(":").split()
        old_mode, new_mode, status = meta[0], meta[1], meta[4]
        if status[0] in "RC":
            old, new, i = fields[i + 1], fields[i + 2], i + 3
        else:
            old = new = fields[i + 1]
            i += 2
        entries.append(Entry(
            old_mode, new_mode, status[0],
            None if status[0] == "A" else old,
            None if status[0] == "D" else new,
        ))
    return entries


def check_changes(entries: list[Entry], cfg: Config, brief: Brief) -> list[str]:
    """Problems that stop a change from being applied: denied paths, build files, strays, links."""
    errors = []
    for e in entries:
        for path in {p for p in (e.old_path, e.new_path) if p}:
            if matches_any(path, cfg.deny_paths + ALWAYS_DENIED):
                errors.append(f"{path}: deny-listed path")
            elif not brief.build_files_allowed and matches_any(path, cfg.build_files):
                errors.append(f"{path}: build file changed but the brief does not allow it")
        if e.new_path and matches_any(Path(e.new_path).name, cfg.stray_globs):
            errors.append(f"{e.new_path}: stray file")
        modes = {e.old_mode, e.new_mode}
        if "120000" in modes:
            errors.append(f"{e.new_path or e.old_path}: symbolic links cannot be added, changed or removed")
        if "160000" in modes:
            errors.append(f"{e.new_path or e.old_path}: submodules (nested git repositories) are not allowed")
    return errors


def apply_and_commit(clone: Path, patch: str, message: str) -> str:
    """Applies `patch` to the trusted clone and commits it; returns the new commit."""
    with tempfile.NamedTemporaryFile("w", suffix=".patch", delete=False) as f:
        f.write(patch)
    try:
        _git(clone, "apply", "--index", "--binary", f.name)
    finally:
        Path(f.name).unlink()
    _git(clone, *IDENTITY, "commit", "-q", "--allow-empty", "-m", message)
    return head(clone)


def restore_commit(clone: Path, target_sha: str, message: str) -> str:
    """Adds one commit whose tree equals `target_sha`'s, without rewriting history."""
    _git(clone, "read-tree", "--reset", "-u", target_sha)
    _git(clone, *IDENTITY, "commit", "-q", "--allow-empty", "-m", message)
    return head(clone)


def read_regular_file(path: Path, max_bytes: int = 1_000_000) -> bytes | None:
    """Reads an agent-written file without following symlinks, blocking on FIFOs or reading
    without bound; returns None unless it is a regular file of at most `max_bytes`."""
    try:
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except OSError:
        return None
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_size > max_bytes:
            return None
        with os.fdopen(fd, "rb", closefd=False) as f:
            return f.read(max_bytes + 1)[:max_bytes]
    finally:
        os.close(fd)
