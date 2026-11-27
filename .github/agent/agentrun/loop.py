"""The pipeline: triage → implement → trusted checks → review → repair, with a regression guard.

Every model step runs through the adapter its config names; every decision about what reaches
the next step is made here, in trusted code.
"""

import hashlib
import json
import os
import shutil
import subprocess
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Callable

from agentrun.adapters.base import Adapter, StepResult, run_bounded
from agentrun.brief import Brief, BriefError, parse_brief, validate_brief
from agentrun.checks import CheckReport, run_checks
from agentrun.config import Config, StepConfig
from agentrun.metrics import RunRecord, Stage, StepLog, write_record
from agentrun.review import VerdictError, approved, parse_verdict, rank_feedback
from agentrun.sandbox import Sandbox, bwrap_argv
from agentrun.workspace import (
    _git,
    apply_and_commit,
    capture_patch,
    changed_entries,
    check_changes,
    fresh_clone,
    head,
    restore_commit,
)

REPO_MAP_CHARS = 8000


@dataclass(frozen=True)
class Issue:
    """The issue as the pipeline sees it."""

    number: int
    title: str
    body: str
    comments: tuple[str, ...]


def load_issue(path: Path) -> Issue:
    """Reads an issue JSON file with number, title, body and comments."""
    data = json.loads(path.read_text())
    return Issue(int(data["number"]), data["title"], data.get("body") or "", tuple(data.get("comments") or ()))


def render(template: Path, **values: str) -> str:
    """Fills `{name}` placeholders in a prompt template; other braces are left alone."""
    text = template.read_text()
    for key, value in values.items():
        text = text.replace("{" + key + "}", value)
    return text


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _repo_map(tree: Path, dirs: list[str]) -> str:
    listing = subprocess.run(
        ["git", "ls-files", "--", *dirs], cwd=tree, capture_output=True, text=True
    ).stdout if (tree / ".git").exists() else ""
    if not listing:
        listing = "\n".join(
            str(p.relative_to(tree)) for d in dirs for p in sorted((tree / d).rglob("*.kt")) if p.is_file()
        )
    return listing[:REPO_MAP_CHARS]


def _issue_text(issue: Issue) -> str:
    comments = "\n\n".join(f"Comment:\n{c}" for c in issue.comments)
    return f"#{issue.number} {issue.title}\n\n{issue.body}\n\n{comments}".strip()


def _brief_text(brief_json: str, diff: str = "", feedback: str = "") -> dict[str, str]:
    return {"brief": brief_json, "diff": diff, "feedback": feedback}


class _Run:
    def __init__(self, issue, repo, base, cfg, out, adapters, wrap, runner):
        self.issue, self.repo, self.base, self.cfg, self.out = issue, repo, base, cfg, out
        self.adapters, self.wrap, self.runner = adapters, wrap, runner
        self.record = RunRecord(
            config_sha256=cfg.source_sha256,
            prompts_sha256={n: _sha256(s.prompt) for n, s in cfg.steps.items() if s.enabled},
        )
        self.stage_no = 0
        template = os.environ.get("AGENT_GRADLE_HOME")
        self.gradle_template = Path(template) if template else None
        java_home = os.environ.get("JAVA_HOME", "")
        ro = list(cfg.sandbox_ro_binds) + ([java_home] if java_home else [])
        # --clearenv drops the locale: without it the JVM uses ASCII file names and tests with
        # non-ASCII paths fail. Daemons are off: Kotlin compiles in process, nothing outlives a step.
        env = {
            "PATH": f"{java_home}/bin:/usr/bin:/bin" if java_home else "/usr/bin:/bin",
            "LANG": "C.UTF-8",
            "LC_ALL": "C.UTF-8",
            "GRADLE_OPTS": "-Dorg.gradle.daemon=false -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8",
            "ORG_GRADLE_PROJECT_kotlin.compiler.execution.strategy": "in-process",
        }
        if java_home:
            env["JAVA_HOME"] = java_home
        if os.environ.get("GRADLE_RO_DEP_CACHE"):
            env["GRADLE_RO_DEP_CACHE"] = os.environ["GRADLE_RO_DEP_CACHE"]
            ro.append(os.environ["GRADLE_RO_DEP_CACHE"])
        self.base_sb = Sandbox(ro_binds=tuple(ro), rw_binds=(), env=env, workdir="/")

    def build_sandbox(self, dest: Path) -> Sandbox:
        """A sandbox that may run Gradle: a fresh copy of the pristine Gradle home template and
        a writable home, both disposable. The template itself is never mounted."""
        gradle_home, home = dest / "gradle-home", dest / "home"
        shutil.rmtree(dest, ignore_errors=True)
        dest.mkdir(parents=True)
        if self.gradle_template:
            subprocess.run(["cp", "-a", "--reflink=auto", str(self.gradle_template), str(gradle_home)], check=True)
        else:
            gradle_home.mkdir()
        home.mkdir()
        env = {**self.base_sb.env, "GRADLE_USER_HOME": str(gradle_home), "HOME": str(home)}
        return replace(self.base_sb, rw_binds=(str(gradle_home), str(home)), env=env)

    def step(self, name: str, prompt: str | None, sb: Sandbox, out_dir: Path,
             session: str | None = None, feedback: str = "") -> StepResult:
        cfg: StepConfig = self.cfg.steps[name]
        adapter: Adapter = self.adapters[name]
        if session and adapter.can_resume:
            result = adapter.resume(session, feedback, cfg, sb, out_dir)
        else:
            result = adapter.start(prompt or "", cfg, sb, out_dir)
        self.record.steps.append(StepLog(name, cfg.harness, cfg.model, self.record.prompts_sha256.get(name, ""), result))
        return result

    def stage(self, name: str, patch: str, sha: str | None, report: CheckReport | None) -> Stage:
        self.stage_no += 1
        stages = self.out / "stages"
        stages.mkdir(parents=True, exist_ok=True)
        patch_file = stages / f"{self.stage_no:02d}-{name}.patch"
        patch_file.write_text(patch)
        st = Stage(name, sha, report, patch_file)
        self.record.stages.append(st)
        return st

    def triage(self) -> Brief | None:
        cfg = self.cfg.steps["triage"]
        view = fresh_clone(self.repo, self.base, self.out / "work" / "triage-view")
        prompt = render(
            cfg.prompt,
            issue=_issue_text(self.issue),
            modules=", ".join(sorted(self.cfg.modules)),
            repo_map=_repo_map(view, [d for m in self.cfg.modules.values() for d in m.src_dirs]),
        )
        sb = replace(self.base_sb, ro_binds=self.base_sb.ro_binds + (str(view),), workdir=str(view))
        errors: list[str] = []
        for attempt in range(1 + cfg.retries):
            text = prompt if attempt == 0 else (
                f"{prompt}\n\nYour previous output was invalid:\n- " + "\n- ".join(errors)
                + "\nWrite a corrected brief."
            )
            result = self.step("triage", text, sb, self.out / f"triage-{attempt + 1}")
            if result.output_file is None:
                errors = [f"no output file was written ({result.status})"]
                continue
            raw = result.output_file.read_text()
            try:
                brief = parse_brief(raw)
            except BriefError as e:
                errors = e.errors
                continue
            errors = validate_brief(brief, view, self.cfg)
            if not errors:
                (self.out / "brief.json").write_text(raw)
                return brief
        (self.out / "triage-errors.json").write_text(json.dumps(errors, indent=2))
        return None

    def review(self, brief_json: str, brief: Brief, clone: Path, report: CheckReport, out_dir: Path):
        cfg = self.cfg.steps["review"]
        diff = _git(clone, "diff", self.base, "HEAD")
        checks = "\n".join(f"{i.name}: {'ok' if i.ok else 'FAILED'} {i.detail}".strip() for i in report.items)
        prompt = render(cfg.prompt, **_brief_text(brief_json, diff=diff), checks=checks)
        view = fresh_clone(clone, head(clone), out_dir / "view")
        sb = replace(self.base_sb, ro_binds=self.base_sb.ro_binds + (str(view),), workdir=str(view))
        for attempt in range(1 + cfg.retries):
            result = self.step("review", prompt, sb, out_dir / f"try-{attempt + 1}")
            if result.output_file is None:
                continue
            try:
                return parse_verdict(result.output_file.read_text(), brief)
            except VerdictError:
                continue
        return None

    def run(self) -> RunRecord:
        brief = self.triage()
        if brief is None:
            self.record.failed_stage = "triage"
            return self.record
        if not brief.ready:
            (self.out / "questions.json").write_text(json.dumps(list(brief.questions)))
            self.record.outcome = "needs_info"
            return self.record
        if self.cfg.brief_gate:
            self.record.outcome = "brief_ready"
            return self.record
        brief_json = (self.out / "brief.json").read_text()
        clone = fresh_clone(self.repo, self.base, self.out / "work" / "trusted")
        impl_cfg = self.cfg.steps["implement"]
        touched_dirs = sorted({f.path.rsplit("/", 1)[0] for f in brief.files_to_touch})
        best: tuple[int, str] | None = None

        for attempt in range(1, self.cfg.attempts + 1):
            if attempt > 1:
                restore_commit(clone, self.base, f"agent: attempt {attempt} restarts from the base")
            session, feedback = None, ""
            for rnd in range(self.cfg.fix_rounds + 1):
                name = f"implement-a{attempt}" if rnd == 0 else f"round-a{attempt}r{rnd}"
                work = self.out / "work" / name
                start_sha = head(clone)
                scratch = fresh_clone(clone, start_sha, work / "scratch")
                build = self.build_sandbox(work / "build-implement")
                sb = replace(build, rw_binds=build.rw_binds + (str(scratch),), workdir=str(scratch))
                prompt = render(impl_cfg.prompt, **_brief_text(brief_json), repo_map=_repo_map(scratch, touched_dirs))
                if rnd > 0:
                    diff = _git(clone, "diff", self.base, "HEAD")
                    restart_prompt = f"{prompt}\n\nYour current changes:\n{diff}\n\nFix these problems:\n{feedback}"
                resuming = rnd > 0 and bool(session) and self.adapters["implement"].can_resume
                if resuming:
                    result = self.step("implement", None, sb, work / "out", session=session, feedback=feedback)
                    if result.status != "ok" and not result.turns:
                        session, resuming = None, False
                if not resuming:
                    result = self.step("implement", restart_prompt if rnd > 0 else prompt, sb, work / "out-restart" if rnd > 0 else work / "out")
                session = result.session or session
                shutil.rmtree(work / "build-implement", ignore_errors=True)
                patch = capture_patch(scratch, start_sha)
                problems = check_changes(changed_entries(scratch, start_sha), self.cfg, brief)
                if problems:
                    self.stage(name, patch, None, None)
                    feedback = "Your change was rejected and not applied:\n- " + "\n- ".join(problems)
                    continue
                sha = apply_and_commit(clone, patch, f"agent: {name}") if patch.strip() else start_sha
                check_sb = self.build_sandbox(work / "build-checks")
                report = run_checks(clone, self.base, brief, self.cfg, check_sb, self.wrap, self.runner, log_dir=work / "checks")
                shutil.rmtree(work / "build-checks", ignore_errors=True)
                self.stage(name, patch, sha, report)
                if best and report.score < best[0]:
                    restore_commit(clone, best[1], f"agent: regression guard restores {best[1][:7]}")
                    feedback = (
                        "Your last change made the checks worse and was rolled back. It caused:\n- "
                        + "\n- ".join(f"{i.name}: {i.detail}" for i in report.failures())
                        + (f"\n\nStill open from before:\n{feedback}" if feedback else "")
                    )
                    continue
                if report.green and (best is None or report.score >= best[0]):
                    best = (report.score, sha)
                if not report.green:
                    feedback = "Trusted checks failed:\n- " + "\n- ".join(f"{i.name}: {i.detail}" for i in report.failures())
                    continue
                if not self.cfg.steps.get("review") or not self.cfg.steps["review"].enabled:
                    return self.handoff(best, approved_=True)
                verdict = self.review(brief_json, brief, clone, report, work / "review")
                if verdict is None:
                    return self.handoff(best, approved_=False)
                kept, dropped = rank_feedback(verdict, report, brief, self.cfg)
                self.record.dropped_feedback += dropped
                if approved(verdict) or not kept:
                    return self.handoff(best, approved_=True)
                feedback = "Review findings:\n- " + "\n- ".join(kept)
        if best:
            return self.handoff(best, approved_=False)
        self.record.failed_stage = "implement"
        return self.record

    def handoff(self, best: tuple[int, str] | None, approved_: bool) -> RunRecord:
        self.record.outcome = "handoff"
        self.record.best_sha = best[1] if best else None
        self.record.approved = approved_
        return self.record


def run_pipeline(
    issue: Issue,
    repo: Path,
    base: str,
    cfg: Config,
    out: Path,
    adapters: dict[str, Adapter],
    wrap: Callable[[Sandbox, list[str]], list[str]] = bwrap_argv,
    runner: Callable = run_bounded,
) -> RunRecord:
    """Runs the whole pipeline for `issue` on `repo` at `base`; writes everything under `out`."""
    out.mkdir(parents=True, exist_ok=True)
    record = _Run(issue, repo, base, cfg, out, adapters, wrap, runner).run()
    write_record(record, out)
    return record
