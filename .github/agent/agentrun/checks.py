"""Trusted checks: Gradle compile, format and tests in the sandbox, plus checks on the diff
that catch what small models do when they cannot make a test pass honestly."""

import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, replace
from fnmatch import fnmatch
from pathlib import Path
from typing import Callable

from agentrun.adapters.base import run_bounded
from agentrun.brief import Brief, test_key
from agentrun.config import Config
from agentrun.sandbox import PreflightError, Sandbox, bwrap_argv
from agentrun.workspace import GitError, _git, fresh_clone, head, read_regular_file

GRADLE_TIMEOUT_S = 1800
ASSERTION = re.compile(r"\b(shouldBe|should\w*|assert\w*|expect\w*)\b")
DISABLING = re.compile(r"@Ignore\b|@Disabled\b|\bxtest\b|\bxshould\b|enabled\s*=\s*false")
ERROR_LINE = re.compile(r"^(e: |error:|> |FAILURE|.*FAILED)")


@dataclass
class CheckItem:
    """One named check, whether it passed, and what to tell the implementer if not."""

    name: str
    ok: bool
    detail: str


@dataclass
class CheckReport:
    """All checks for one commit, with the test keys that passed and failed."""

    items: list[CheckItem]
    tests_passed: frozenset[str]
    tests_failed: frozenset[str]

    @property
    def green(self) -> bool:
        """True when every check passed."""
        return all(i.ok for i in self.items)

    @property
    def score(self) -> int:
        """Checks passed plus tests passed; the regression guard compares commits by this."""
        return sum(i.ok for i in self.items) + len(self.tests_passed)

    def failures(self) -> list[CheckItem]:
        """The checks that did not pass."""
        return [i for i in self.items if not i.ok]


def preflight_java(java_bin: str, java_min: int, run: Callable = subprocess.run) -> None:
    """Fails unless `java_bin` reports a major version of at least `java_min`."""
    result = run([java_bin, "-version"], capture_output=True, text=True)
    match = re.search(r'version "(\d+)(?:\.(\d+))?', (result.stderr or "") + (result.stdout or ""))
    if result.returncode != 0 or not match:
        raise PreflightError(f"cannot read the Java version from {java_bin}")
    major = int(match.group(1))
    if major == 1 and match.group(2):
        major = int(match.group(2))
    if major < java_min:
        raise PreflightError(f"Java {major} found at {java_bin}; the build needs Java {java_min} or later")


def parse_junit_dir(d: Path, root_dir: Path | None = None) -> tuple[set[str], set[str]]:
    """Test keys that passed and that failed, from the JUnit XML files under `d`.

    Agent-written code produced these files, so symlinks are never followed, `d` must resolve
    inside `root_dir`, and only regular files of bounded size are read.
    """
    passed, failed = set(), set()
    if not d.is_dir() or d.is_symlink():
        return passed, failed
    if root_dir is not None and not d.resolve().is_relative_to(root_dir.resolve()):
        return passed, failed
    files = []
    for dirpath, _dirs, names in os.walk(d, followlinks=False):
        files += [Path(dirpath) / n for n in names if n.endswith(".xml")]
    for xml in sorted(files):
        content = read_regular_file(xml, max_bytes=20_000_000)
        if content is None:
            continue
        try:
            root = ET.fromstring(content)
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            key = test_key(case.get("classname", ""), case.get("name", ""))
            if case.find("skipped") is not None:
                continue
            if case.find("failure") is not None or case.find("error") is not None:
                failed.add(key)
            else:
                passed.add(key)
    return passed, failed


def is_test_path(path: str) -> bool:
    """True for files under a test source set (`test`, `commonTest`, `jvmTest`, ...)."""
    dirs = path.split("/")[:-1]
    return any(seg == "test" or seg.endswith("Test") for seg in dirs)


def _changes(clone: Path, base: str) -> list[tuple[str, str]]:
    out = _git(clone, "diff", "--name-status", "--no-renames", base, "HEAD")
    return [tuple(line.split("\t", 1)) for line in out.splitlines() if "\t" in line]


def _diff_lines(clone: Path, base: str) -> dict[str, tuple[list[str], list[str]]]:
    """Per file: (added lines, removed lines), from a zero-context diff."""
    out = _git(clone, "diff", "--unified=0", "--no-renames", base, "HEAD")
    files: dict[str, tuple[list[str], list[str]]] = {}
    current = None
    for line in out.splitlines():
        if line.startswith("+++ "):
            current = line[6:] if line.startswith("+++ b/") else None
            if current is not None:
                files.setdefault(current, ([], []))
        elif line.startswith("--- "):
            old = line[6:] if line.startswith("--- a/") else None
            if old is not None:
                files.setdefault(old, ([], []))
                current = old
        elif current and line.startswith("+"):
            files[current][0].append(line[1:])
        elif current and line.startswith("-"):
            files[current][1].append(line[1:])
    return files


def _module_of(path: str, cfg: Config) -> str | None:
    first = path.split("/", 1)[0]
    return first if first in cfg.modules else None


def _gradle(
    clone: Path, tasks: list[str], cfg: Config, sb: Sandbox, wrap, runner, log_dir: Path, label: str,
    checkout: Path,
) -> tuple[bool, str]:
    clone = checkout
    argv = wrap(replace(sb, workdir=str(clone)), ["./gradlew", *cfg.gradle_args, *tasks])
    log = log_dir / f"gradle-{label}.log"
    code, _ = runner(argv, GRADLE_TIMEOUT_S, log, cwd=clone)
    text = log.read_text(errors="replace") if log.exists() else ""
    if code == 0:
        return True, ""
    if code is None:
        return False, f"gradle {label} timed out after {GRADLE_TIMEOUT_S} s"
    errors = [l for l in text.splitlines() if ERROR_LINE.match(l)]
    return False, "\n".join((errors or text.splitlines())[-25:])


def run_checks(
    clone: Path,
    base: str,
    brief: Brief,
    cfg: Config,
    sb: Sandbox,
    wrap: Callable[[Sandbox, list[str]], list[str]] = bwrap_argv,
    runner: Callable = run_bounded,
    log_dir: Path | None = None,
) -> CheckReport:
    """Runs every trusted check on the clone's HEAD against `base`; Gradle logs go to `log_dir`.

    Gradle runs in a disposable checkout of HEAD, the only writable tree in its sandbox, so
    agent-written tests never write to the trusted clone. `sb` must already carry a fresh
    Gradle home.
    """
    scratch_root = Path(tempfile.mkdtemp(prefix="agent-checkout-"))
    try:
        return _run_checks(clone, base, brief, cfg, sb, wrap, runner, log_dir, scratch_root)
    finally:
        shutil.rmtree(scratch_root, ignore_errors=True)


def _run_checks(clone, base, brief, cfg, sb, wrap, runner, log_dir, scratch_root) -> CheckReport:
    checkout = fresh_clone(clone, head(clone), scratch_root / "checkout")
    sb = replace(sb, rw_binds=sb.rw_binds + (str(checkout),))
    changes = _changes(clone, base)
    diff = _diff_lines(clone, base)
    touched = sorted({m for _, p in changes if (m := _module_of(p, cfg))}) or sorted(cfg.modules)
    keep_logs = log_dir is not None
    log_dir = log_dir or Path(tempfile.mkdtemp(prefix="agent-checks-"))
    items: list[CheckItem] = []

    ok, detail = _gradle(clone, [cfg.modules[m].compile_task for m in touched], cfg, sb, wrap, runner, log_dir, "compile", checkout)
    items.append(CheckItem("compile", ok, detail))
    compiled = ok
    ok, detail = _gradle(clone, [cfg.modules[m].format_task for m in touched], cfg, sb, wrap, runner, log_dir, "format", checkout)
    items.append(CheckItem("format", ok, detail or ("" if ok else "run ktfmtFormat")))

    test_modules: dict[str, set[str]] = {}
    for status, path in changes:
        module = _module_of(path, cfg)
        if module and status != "D" and is_test_path(path) and path.endswith(".kt"):
            test_modules.setdefault(module, set()).add(Path(path).stem)
    for t in brief.tests:
        test_modules.setdefault(t.module, set()).add(t.test_class.rsplit(".", 1)[-1])
    if not compiled:
        items.append(CheckItem("tests", False, "not run: compile failed"))
    else:
        tasks: list[str] = []
        for module, classes in sorted(test_modules.items()):
            tasks.append(cfg.modules[module].test_task)
            for cls in sorted(classes):
                tasks += ["--tests", f"*.{cls}"]
        ok, detail = _gradle(clone, tasks, cfg, sb, wrap, runner, log_dir, "tests", checkout) if tasks else (True, "")
        items.append(CheckItem("tests", ok, detail))
    passed, failed = set(), set()
    for module in test_modules:
        p, f = parse_junit_dir(checkout / cfg.modules[module].results_dir, root_dir=checkout)
        passed |= p
        failed |= f
    failed -= passed

    missing = [f"{t.test_class}.{t.name}" for t in brief.tests if test_key(t.test_class, t.name) not in passed | failed]
    failing = [f"{t.test_class}.{t.name}" for t in brief.tests if test_key(t.test_class, t.name) in failed]
    detail = "; ".join(filter(None, [
        f"missing: {', '.join(missing)}" if missing else "",
        f"failing: {', '.join(failing)}" if failing else "",
    ]))
    items.append(CheckItem("acceptance", not missing and not failing, detail))

    absent = []
    for t in brief.tests:
        try:
            text = _git(clone, "show", f"HEAD:{t.file}")
        except GitError:
            text = ""
        if t.expected not in text:
            absent.append(f"{t.test_class}.{t.name} must assert the expected value {t.expected!r} in {t.file}")
    items.append(CheckItem("expected_values", not absent, "\n".join(absent)))

    added = [line for a, _ in diff.values() for line in a]
    unused = [h.symbol for h in brief.helpers_to_reuse
              if not any(re.search(rf"\b{re.escape(h.symbol)}\b", line) for line in added)]
    items.append(CheckItem(
        "helpers_used", not unused,
        f"reuse the existing helpers instead of re-implementing them: {', '.join(unused)}" if unused else "",
    ))

    weakened = []
    for status, path in changes:
        if not is_test_path(path) or path in brief.allow_test_changes:
            continue
        if status == "D":
            weakened.append(f"{path}: test file deleted")
            continue
        add, removed = diff.get(path, ([], []))
        re_added = {" ".join(l.split()) for l in add}
        lost = [l.strip() for l in removed if ASSERTION.search(l) and " ".join(l.split()) not in re_added]
        disabled = [l.strip() for l in add if DISABLING.search(l)]
        weakened += [f"{path}: assertion removed or changed: {l}" for l in lost]
        weakened += [f"{path}: test disabled: {l}" for l in disabled]
    items.append(CheckItem("tests_not_weakened", not weakened, "\n".join(weakened)))

    strays = [p for status, p in changes if status == "A" and any(fnmatch(Path(p).name, g) for g in cfg.stray_globs)]
    items.append(CheckItem("stray_files", not strays, ", ".join(strays)))
    if not keep_logs:
        shutil.rmtree(log_dir, ignore_errors=True)
    return CheckReport(items, frozenset(passed), frozenset(failed))
