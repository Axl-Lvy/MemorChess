"""Clones, patches and commits, done so that nothing the agent wrote runs in the trusted process.

Every clone keeps its git data in a sibling `<dest>.gitdir` directory that is never mounted
into the sandbox, so an agent cannot plant hooks, filters or config the trusted side would run.
"""

import re
import subprocess
import tempfile
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


_DIFF_HEADER = re.compile(r"^diff --git a/(.+) b/(.+)$")


def patch_paths(patch: str) -> list[tuple[str | None, str | None]]:
    """(old path, new path) for every file in `patch`; None marks a created or deleted side."""
    files: list[list[str | None]] = []
    for line in patch.splitlines():
        header = _DIFF_HEADER.match(line)
        if header:
            files.append([header.group(1), header.group(2)])
        elif not files:
            continue
        elif line.startswith("new file mode"):
            files[-1][0] = None
        elif line.startswith("deleted file mode"):
            files[-1][1] = None
        elif line.startswith("rename from "):
            files[-1][0] = line[len("rename from "):]
        elif line.startswith("rename to "):
            files[-1][1] = line[len("rename to "):]
    return [(old, new) for old, new in files]


def check_patch(patch: str, cfg: Config, brief: Brief) -> list[str]:
    """Problems that stop a patch from being applied: denied paths, build files, strays, links."""
    errors = []
    for old, new in patch_paths(patch):
        for path in {p for p in (old, new) if p}:
            if matches_any(path, cfg.deny_paths + ALWAYS_DENIED):
                errors.append(f"{path}: deny-listed path")
            elif not brief.build_files_allowed and matches_any(path, cfg.build_files):
                errors.append(f"{path}: build file changed but the brief does not allow it")
        if new and matches_any(Path(new).name, cfg.stray_globs):
            errors.append(f"{new}: stray file")
    if re.search(r"^(new file mode|new mode) 120000", patch, re.MULTILINE):
        errors.append("patch creates a symbolic link")
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
