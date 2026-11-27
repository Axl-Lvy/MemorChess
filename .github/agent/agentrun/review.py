"""The reviewer's verdict, and the ranking that keeps contradicted findings from the implementer.

Trusted checks outrank the LLM reviewer: a finding that contradicts a check result, or asks for
a change the brief forbids, is dropped before it reaches the implementer.
"""

import json
import re
from dataclasses import dataclass
from typing import Literal

from agentrun.brief import Brief, matches_any
from agentrun.checks import CheckReport
from agentrun.config import Config

FAIL_CLAIM = re.compile(r"\b(fail|fails|failing|will not pass|won't pass|wont pass|breaks?)\b", re.IGNORECASE)
MENTIONS_TESTS = re.compile(r"\btests?\b", re.IGNORECASE)


class VerdictError(Exception):
    """The reviewer's JSON is malformed or leaves a criterion without a verdict."""


@dataclass(frozen=True)
class Finding:
    """A blocking issue the reviewer raised, optionally anchored to a line, with a fix."""

    file: str | None
    line: int | None
    text: str
    patch: str | None


@dataclass(frozen=True)
class Verdict:
    """Pass or fail for every acceptance criterion, plus blocking findings."""

    criteria: dict[str, Literal["pass", "fail"]]
    reasons: dict[str, str]
    blocking: tuple[Finding, ...]


def parse_verdict(raw: str, brief: Brief) -> Verdict:
    """Parses the reviewer's JSON; every brief criterion must get `pass` or `fail`."""
    try:
        obj = json.loads(raw)
        rows = obj["criteria"]
        blocking = obj.get("blocking") or []
    except (json.JSONDecodeError, KeyError, TypeError, AttributeError) as e:
        raise VerdictError(f"malformed verdict: {e}") from e
    criteria, reasons = {}, {}
    for row in rows:
        if not isinstance(row, dict) or row.get("verdict") not in ("pass", "fail"):
            raise VerdictError(f"criterion verdict must be 'pass' or 'fail': {row}")
        criteria[str(row.get("id"))] = row["verdict"]
        reasons[str(row.get("id"))] = str(row.get("reason") or "")
    missing = [c.id for c in brief.criteria if c.id not in criteria]
    if missing:
        raise VerdictError(f"no verdict for criteria {', '.join(missing)}")
    findings = []
    for f in blocking:
        if not isinstance(f, dict) or not isinstance(f.get("text"), str):
            raise VerdictError(f"blocking finding needs a text: {f}")
        line = f.get("line")
        findings.append(Finding(
            f.get("file") if isinstance(f.get("file"), str) else None,
            line if isinstance(line, int) and not isinstance(line, bool) else None,
            f["text"],
            f.get("patch") if isinstance(f.get("patch"), str) else None,
        ))
    return Verdict(criteria, reasons, tuple(findings))


def approved(verdict: Verdict) -> bool:
    """True when every criterion passes and there is no blocking finding."""
    return all(v == "pass" for v in verdict.criteria.values()) and not verdict.blocking


def _norm(text: str) -> str:
    return re.sub(r"[^a-z0-9]", "", text.lower())


def _contradicts_checks(text: str, report: CheckReport) -> str | None:
    if not FAIL_CLAIM.search(text):
        return None
    norm = _norm(text)
    for key in report.tests_passed:
        name = key.split("#", 1)[1]
        if name and name in norm:
            return f"claims a failure but {name} passed in the trusted checks"
    if report.green and MENTIONS_TESTS.search(text):
        return "claims tests fail but every trusted check passed"
    return None


def rank_feedback(
    verdict: Verdict, report: CheckReport, brief: Brief, cfg: Config
) -> tuple[list[str], list[str]]:
    """Splits review feedback into what the implementer gets and what is dropped (with why)."""
    kept, dropped = [], []
    not_touch = {f.path for f in brief.files_not_to_touch}
    tests_by_criterion = {c.id: c.tests for c in brief.criteria}
    for cid, verdict_value in sorted(verdict.criteria.items()):
        if verdict_value != "fail":
            continue
        reason = verdict.reasons.get(cid, "")
        contradiction = _contradicts_checks(reason, report)
        if contradiction:
            dropped.append(f"{cid}: {reason} — dropped: {contradiction}")
            continue
        note = ""
        mapped = tests_by_criterion.get(cid, ())
        if mapped and all(any(_norm(t.partition('.')[2]) == k.split('#', 1)[1] for k in report.tests_passed) for t in mapped):
            note = " (its tests pass; explain what the tests miss, then fix the code and extend the test)"
        kept.append(f"{cid} fails: {reason}{note}")
    for f in verdict.blocking:
        where = f"{f.file}:{f.line}" if f.file and f.line else (f.file or "general")
        if f.file and (f.file in not_touch or matches_any(f.file, cfg.deny_paths)):
            dropped.append(f"{where}: {f.text} — dropped: {f.file} must not be changed")
            continue
        contradiction = _contradicts_checks(f.text, report)
        if contradiction:
            dropped.append(f"{where}: {f.text} — dropped: {contradiction}")
            continue
        kept.append(f"{where}: {f.text}" + (f"\nSuggested patch:\n{f.patch}" if f.patch else ""))
    return kept, dropped
