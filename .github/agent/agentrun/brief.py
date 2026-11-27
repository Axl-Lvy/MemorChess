"""The triage brief: the contract the implementer works from and the checks verify against."""

import json
import re
from dataclasses import dataclass
from fnmatch import fnmatch
from pathlib import Path
from typing import Literal

from agentrun.config import Config

TITLE_RE = re.compile(
    r"^(feat|fix|docs|test|ci|refactor|perf|chore|data|dev|ui|build)(\([^)]+\))?!?: .+"
)


class BriefError(Exception):
    """The brief JSON is malformed; `errors` lists every problem found."""

    def __init__(self, errors: list[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


@dataclass(frozen=True)
class FileRef:
    """A repo path with the reason triage gave for it; `new` marks a file to be created."""

    path: str
    reason: str
    new: bool


@dataclass(frozen=True)
class Helper:
    """An existing symbol the implementer must reuse instead of re-implementing."""

    symbol: str
    path: str


@dataclass(frozen=True)
class TestSpec:
    """An acceptance test the implementer must write, with its expected value spelled out."""

    __test__ = False

    module: str
    file: str
    test_class: str
    name: str
    input: str
    expected: str


@dataclass(frozen=True)
class Criterion:
    """A checkable acceptance criterion and the tests that prove it."""

    id: str
    text: str
    tests: tuple[str, ...]


@dataclass(frozen=True)
class Brief:
    """Triage's output: either questions for the owner, or the full implementation contract."""

    ready: bool
    questions: tuple[str, ...]
    analysis: str
    title: str
    goal: str
    criteria: tuple[Criterion, ...]
    files_to_touch: tuple[FileRef, ...]
    files_not_to_touch: tuple[FileRef, ...]
    helpers_to_reuse: tuple[Helper, ...]
    build_files_allowed: bool
    allow_test_changes: tuple[str, ...]
    rules: tuple[str, ...]
    tests: tuple[TestSpec, ...]
    screenshots: bool
    ci_tier: Literal["cheap", "full"]


def test_key(test_class: str, name: str) -> str:
    """Normalises a test identity so camelCase, snake_case, backticks and spaces all match.

    A trailing KMP target suffix such as `[jvm]` and a trailing `()` are ignored.
    """
    simple_class = re.sub(r"\[[^\]]*\]$", "", test_class.strip()).rsplit(".", 1)[-1]
    bare = re.sub(r"(\(\))?(\[[^\]]*\])?$", "", name.strip())
    bare = re.sub(r"(\(\))$", "", bare)
    norm = lambda s: re.sub(r"[^a-z0-9]", "", s.lower())  # noqa: E731
    return f"{norm(simple_class)}#{norm(bare)}"


test_key.__test__ = False


class _Reader:
    def __init__(self):
        self.errors: list[str] = []

    def get(self, obj: dict, key: str, kind: type, where: str, default=None):
        if not isinstance(obj, dict) or key not in obj:
            self.errors.append(f"{where}: missing '{key}'")
            return default
        value = obj[key]
        if not isinstance(value, kind) or (kind is not bool and isinstance(value, bool)):
            self.errors.append(f"{where}: '{key}' must be {kind.__name__}")
            return default
        return value

    def items(self, obj: dict, key: str, where: str) -> list:
        return self.get(obj, key, list, where, default=[]) or []


def _file_refs(r: _Reader, obj: dict, key: str) -> tuple[FileRef, ...]:
    refs = []
    for i, item in enumerate(r.items(obj, key, "brief")):
        where = f"{key}[{i}]"
        refs.append(
            FileRef(
                path=r.get(item, "path", str, where, ""),
                reason=r.get(item, "reason", str, where, ""),
                new=r.get(item, "new", bool, where, False),
            )
        )
    return tuple(refs)


def parse_brief(raw: str) -> Brief:
    """Parses triage's JSON into a Brief; raises BriefError listing every malformed field."""
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError as e:
        raise BriefError([f"not JSON: {e}"]) from e
    if not isinstance(obj, dict):
        raise BriefError(["brief must be a JSON object"])
    r = _Reader()
    criteria = tuple(
        Criterion(
            id=r.get(c, "id", str, f"acceptance_criteria[{i}]", ""),
            text=r.get(c, "text", str, f"acceptance_criteria[{i}]", ""),
            tests=tuple(r.items(c, "tests", f"acceptance_criteria[{i}]")),
        )
        for i, c in enumerate(r.items(obj, "acceptance_criteria", "brief"))
    )
    tests = tuple(
        TestSpec(
            module=r.get(t, "module", str, f"tests[{i}]", ""),
            file=r.get(t, "file", str, f"tests[{i}]", ""),
            test_class=r.get(t, "class", str, f"tests[{i}]", ""),
            name=r.get(t, "name", str, f"tests[{i}]", ""),
            input=r.get(t, "input", str, f"tests[{i}]", ""),
            expected=r.get(t, "expected", str, f"tests[{i}]", ""),
        )
        for i, t in enumerate(r.items(obj, "tests", "brief"))
    )
    helpers = tuple(
        Helper(
            symbol=r.get(h, "symbol", str, f"helpers_to_reuse[{i}]", ""),
            path=r.get(h, "path", str, f"helpers_to_reuse[{i}]", ""),
        )
        for i, h in enumerate(r.items(obj, "helpers_to_reuse", "brief"))
    )
    ci_tier = r.get(obj, "ci_tier", str, "brief", "cheap")
    if ci_tier not in ("cheap", "full"):
        r.errors.append("brief: 'ci_tier' must be 'cheap' or 'full'")
    brief = Brief(
        ready=r.get(obj, "ready", bool, "brief", False),
        questions=tuple(r.items(obj, "questions", "brief")),
        analysis=r.get(obj, "analysis", str, "brief", ""),
        title=r.get(obj, "title", str, "brief", ""),
        goal=r.get(obj, "goal", str, "brief", ""),
        criteria=criteria,
        files_to_touch=_file_refs(r, obj, "files_to_touch"),
        files_not_to_touch=_file_refs(r, obj, "files_not_to_touch"),
        helpers_to_reuse=helpers,
        build_files_allowed=r.get(obj, "build_files_allowed", bool, "brief", False),
        allow_test_changes=tuple(r.items(obj, "allow_test_changes", "brief")),
        rules=tuple(r.items(obj, "rules", "brief")),
        tests=tests,
        screenshots=r.get(obj, "screenshots", bool, "brief", False),
        ci_tier=ci_tier,
    )
    if r.errors:
        raise BriefError(r.errors)
    return brief


def matches_any(path: str, globs: tuple[str, ...]) -> bool:
    """True when `path` matches a glob; `dir/**` also matches everything under `dir/`."""
    for g in globs:
        if fnmatch(path, g) or (g.endswith("/**") and path.startswith(g[:-2])):
            return True
        if g.startswith("**/") and fnmatch(path, g[3:]):
            return True
    return False


def validate_brief(brief: Brief, repo: Path, cfg: Config) -> list[str]:
    """Checks a parsed brief against the repo and itself; returns every problem (empty = valid)."""
    if not brief.ready:
        return [] if brief.questions else ["brief: not ready but asks no question"]
    errors = []
    if not TITLE_RE.match(brief.title):
        errors.append(f"title '{brief.title}' is not a Conventional Commit with a repo type")
    if "[skip-tests]" in brief.title.lower():
        errors.append("title must not contain [skip-tests]")
    touch = {f.path for f in brief.files_to_touch}
    not_touch = {f.path for f in brief.files_not_to_touch}
    for ref in brief.files_to_touch:
        target = repo / ref.path
        if ref.new and not target.parent.is_dir():
            errors.append(f"files_to_touch: parent directory of new file {ref.path} does not exist")
        if not ref.new and not target.is_file():
            errors.append(f"files_to_touch: {ref.path} does not exist")
        if matches_any(ref.path, cfg.deny_paths):
            errors.append(f"files_to_touch: {ref.path} is deny-listed")
        if not brief.build_files_allowed and matches_any(ref.path, cfg.build_files):
            errors.append(f"files_to_touch: {ref.path} is a build file and build_files_allowed is false")
    for ref in brief.files_not_to_touch:
        if not (repo / ref.path).exists():
            errors.append(f"files_not_to_touch: {ref.path} does not exist")
    for path in sorted(touch & not_touch):
        errors.append(f"{path} is both in files_to_touch and files not to touch")
    for h in brief.helpers_to_reuse:
        source = repo / h.path
        text = source.read_text(errors="replace") if source.is_file() else ""
        if not re.search(rf"\b{re.escape(h.symbol)}\b", text):
            errors.append(f"helpers_to_reuse: symbol {h.symbol} not found in {h.path}")
    keys = set()
    for t in brief.tests:
        keys.add(test_key(t.test_class, t.name))
        if not t.expected.strip():
            errors.append(f"tests: {t.test_class}.{t.name} has no written-out expected value")
        if t.file in not_touch:
            errors.append(f"tests: {t.file} holds a required test but is in files not to touch")
        elif t.file not in touch:
            errors.append(f"tests: {t.file} holds a required test but is not in files_to_touch")
        if t.module not in cfg.modules:
            errors.append(f"tests: module '{t.module}' is not configured")
    for c in brief.criteria:
        for ref in c.tests:
            cls, _, name = ref.partition(".")
            if test_key(cls, name) not in keys:
                errors.append(f"acceptance_criteria {c.id}: test {ref} is not in tests")
    return errors
