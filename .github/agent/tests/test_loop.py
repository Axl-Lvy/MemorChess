import dataclasses
import json
import os
import tempfile
import unittest
from pathlib import Path

from agentrun.adapters.fake import FakeAdapter
from agentrun.loop import Issue, load_issue, run_pipeline
from tests.helpers import (
    BOARD_SRC,
    BOARD_TEST,
    FILES,
    GOOD_SRC,
    NEW_TEST_BODY,
    brief_dict,
    git,
    make_gradle_repo,
    repo_config,
    write_junit,
)

NO_SANDBOX = lambda sb, argv: argv  # noqa: E731
ISSUE = Issue(447, "Show legal move hints", "Dots on legal squares.", ("please",))
PASS = {"criteria": [{"id": "AC1", "verdict": "pass", "reason": "ok"}], "blocking": []}
FAIL = {"criteria": [{"id": "AC1", "verdict": "fail", "reason": "dots drawn on mirrored squares"}], "blocking": []}


def edit(files: dict[str, str | None]):
    """An implementer effect: writes (or deletes) files in its scratch tree."""
    def effect(sb):
        for path, content in files.items():
            target = Path(sb.workdir) / path
            if content is None:
                target.unlink()
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
    return effect


GOOD = edit({BOARD_TEST: NEW_TEST_BODY, BOARD_SRC: GOOD_SRC})
BREAK_TEST = edit({BOARD_TEST: None})


class LoopTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.repo, self.base = make_gradle_repo(self.root)
        self.cfg = repo_config()
        self.junit = self.root / "junit"
        self.junit.mkdir()
        write_junit(self.junit, [("proj.TestBoard", "drawsDotsOnLegalSquares", True), ("proj.TestBoard", "mapsSquare", True)])
        os.environ["FAKE_JUNIT_SRC"] = str(self.junit)
        self.out = self.root / "out"

    def tearDown(self):
        os.environ.pop("FAKE_JUNIT_SRC", None)
        self._tmp.cleanup()

    def run_with(self, triage, implement, review=(), cfg=None):
        adapters = {
            "triage": FakeAdapter(list(triage)),
            "implement": FakeAdapter(list(implement)),
            "review": FakeAdapter(list(review)),
        }
        record = run_pipeline(ISSUE, self.repo, self.base, cfg or self.cfg, self.out, adapters, wrap=NO_SANDBOX)
        return record, adapters

    def test_load_issue(self):
        path = self.root / "issue.json"
        path.write_text(json.dumps({"number": 7, "title": "t", "body": "b", "comments": ["c"]}))

        self.assertEqual(load_issue(path), Issue(7, "t", "b", ("c",)))

    def test_triage_invalid_twice_fails_at_triage(self):
        record, adapters = self.run_with([{"raw": "not json"}, {"output": None}], [])

        self.assertEqual((record.outcome, record.failed_stage), ("failed", "triage"))
        self.assertEqual(adapters["implement"].calls, [])
        self.assertIn("invalid", adapters["triage"].calls[1][1])

    def test_triage_inconsistent_brief_fails(self):
        bad = brief_dict()
        bad["files_not_to_touch"].append({"path": BOARD_TEST, "reason": "x", "new": False})

        record, _ = self.run_with([{"output": bad}, {"output": bad}], [])

        self.assertEqual(record.failed_stage, "triage")

    def test_not_ready_ends_needs_info(self):
        record, adapters = self.run_with([{"output": {**brief_dict(), "ready": False, "questions": ["Which theme?"]}}], [])

        self.assertEqual(record.outcome, "needs_info")
        self.assertEqual(json.loads((self.out / "questions.json").read_text()), ["Which theme?"])
        self.assertEqual(adapters["implement"].calls, [])

    def test_brief_gate_stops_after_brief(self):
        cfg = dataclasses.replace(self.cfg, brief_gate=True)

        record, adapters = self.run_with([{"output": brief_dict()}], [], cfg=cfg)

        self.assertEqual(record.outcome, "brief_ready")
        self.assertTrue((self.out / "brief.json").is_file())
        self.assertEqual(adapters["implement"].calls, [])

    def test_green_and_approved_hands_off(self):
        record, adapters = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}], [{"output": PASS}])

        self.assertEqual(record.outcome, "handoff")
        self.assertTrue(record.approved)
        self.assertEqual(record.best_sha, git(self.out / "work" / "trusted", "rev-parse", "HEAD"))
        self.assertTrue(any(p.name.endswith("implement-a1.patch") for p in (self.out / "stages").iterdir()))

    def test_red_checks_skip_review(self):
        half = edit({BOARD_SRC: GOOD_SRC})
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)

        record, adapters = self.run_with([{"output": brief_dict()}], [{"effect": half}, {"effect": half}], [], cfg=cfg)

        self.assertEqual(adapters["review"].calls, [])
        self.assertEqual((record.outcome, record.failed_stage), ("failed", "implement"))
        feedback = adapters["implement"].calls[1][1]
        self.assertIn("expected_values", feedback)

    def test_rejected_patch_becomes_feedback(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)
        sneaky = edit({".github/workflows/x.yml": "on: push\n"})

        record, adapters = self.run_with([{"output": brief_dict()}], [{"effect": sneaky}, {"effect": GOOD}], [{"output": PASS}], cfg=cfg)

        self.assertIn("deny-listed", adapters["implement"].calls[1][1])
        self.assertEqual(record.outcome, "handoff")
        self.assertFalse((self.out / "work" / "trusted" / ".github").exists())

    def test_regression_rolls_back_to_best(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)

        record, _ = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}, {"effect": BREAK_TEST}], [{"output": FAIL}], cfg=cfg)

        trusted = self.out / "work" / "trusted"
        best_stage = record.stages[0]
        self.assertEqual(record.best_sha, best_stage.sha)
        self.assertEqual(git(trusted, "rev-parse", "HEAD^{tree}"), git(trusted, "rev-parse", f"{best_stage.sha}^{{tree}}"))
        self.assertEqual(record.outcome, "handoff")
        self.assertFalse(record.approved)

    def test_green_round_is_never_rolled_back(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)
        tidy = edit({BOARD_SRC: GOOD_SRC + "// tidied\n"})

        def tweak(sb):
            tidy(sb)
            write_junit(self.junit, [("proj.TestBoard", "drawsDotsOnLegalSquares", True)])

        record, _ = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}, {"effect": tweak}], [{"output": FAIL}, {"output": FAIL}], cfg=cfg)

        trusted = self.out / "work" / "trusted"
        self.assertEqual(record.best_sha, record.stages[1].sha)
        self.assertNotIn("regression guard", git(trusted, "log", "--format=%s"))

    def test_new_attempt_is_not_rolled_back_to_an_earlier_attempt(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=0, attempts=2)
        half = edit({BOARD_SRC: GOOD_SRC})

        record, _ = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}, {"effect": half}], [{"output": FAIL}], cfg=cfg)

        trusted = self.out / "work" / "trusted"
        self.assertNotIn("regression guard", git(trusted, "log", "--format=%s"))
        self.assertEqual(record.best_sha, record.stages[0].sha)
        self.assertEqual(git(trusted, "rev-parse", "HEAD^{tree}"), git(trusted, "rev-parse", f"{record.best_sha}^{{tree}}"))

    def test_non_resuming_adapter_gets_diff_and_feedback(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)
        adapters = {
            "triage": FakeAdapter([{"output": brief_dict()}]),
            "implement": FakeAdapter([{"effect": GOOD}, {"effect": GOOD}], can_resume=False),
            "review": FakeAdapter([{"output": FAIL}, {"output": PASS}]),
        }

        run_pipeline(ISSUE, self.repo, self.base, cfg, self.out, adapters, wrap=NO_SANDBOX)

        kind, prompt = adapters["implement"].calls[1]
        self.assertEqual(kind, "start")
        self.assertIn("mirrored squares", prompt)
        self.assertIn("draws dots on legal squares", prompt)

    def test_resuming_adapter_gets_feedback_only(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)

        _, adapters = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}, {"effect": GOOD}], [{"output": FAIL}, {"output": PASS}], cfg=cfg)

        self.assertEqual(adapters["implement"].calls[1][0], "resume")

    def test_failed_resume_restarts_with_diff_and_feedback(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=1, attempts=1)

        _, adapters = self.run_with(
            [{"output": brief_dict()}],
            [{"effect": GOOD}, {"status": "error", "turns": 0}, {"effect": GOOD}],
            [{"output": FAIL}, {"output": PASS}],
            cfg=cfg,
        )

        kinds = [k for k, _ in adapters["implement"].calls]
        self.assertEqual(kinds, ["start", "resume", "start"])
        self.assertIn("mirrored squares", adapters["implement"].calls[2][1])

    def test_claude_code_does_not_resume(self):
        from agentrun.adapters.claude_code import ClaudeCodeAdapter

        self.assertFalse(ClaudeCodeAdapter("http://x").can_resume)

    def test_attempt_two_starts_from_base(self):
        cfg = dataclasses.replace(self.cfg, fix_rounds=0, attempts=2)
        half = edit({BOARD_SRC: GOOD_SRC})
        seen = []

        def check_base(sb):
            seen.append((Path(sb.workdir) / BOARD_SRC).read_text())
            GOOD(sb)

        self.run_with([{"output": brief_dict()}], [{"effect": half}, {"effect": check_base}], [{"output": PASS}], cfg=cfg)

        self.assertEqual(seen, [FILES[BOARD_SRC]])

    def test_review_disabled_hands_off_on_green(self):
        steps = dict(self.cfg.steps)
        steps["review"] = dataclasses.replace(steps["review"], enabled=False)
        cfg = dataclasses.replace(self.cfg, steps=steps)

        record, adapters = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}], [], cfg=cfg)

        self.assertEqual(record.outcome, "handoff")
        self.assertEqual(adapters["review"].calls, [])

    def test_invalid_review_hands_off_unapproved(self):
        record, _ = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}], [{"raw": "?"}, {"raw": "?"}])

        self.assertEqual(record.outcome, "handoff")
        self.assertFalse(record.approved)

    def test_build_sandbox_has_utf8_locale_writable_home_and_no_daemons(self):
        seen = []

        def recording(sb, argv):
            seen.append((sb, argv))
            return argv

        adapters = {
            "triage": FakeAdapter([{"output": brief_dict()}]),
            "implement": FakeAdapter([{"effect": GOOD}]),
            "review": FakeAdapter([{"output": PASS}]),
        }

        run_pipeline(ISSUE, self.repo, self.base, self.cfg, self.out, adapters, wrap=recording)

        sb, argv = seen[0]
        self.assertEqual(sb.env["LANG"], "C.UTF-8")
        self.assertEqual(sb.env["LC_ALL"], "C.UTF-8")
        self.assertIn(sb.env["HOME"], sb.rw_binds)
        self.assertEqual(sb.env["ORG_GRADLE_PROJECT_kotlin.compiler.execution.strategy"], "in-process")
        self.assertIn("-Dorg.gradle.daemon=false", sb.env["GRADLE_OPTS"])
        self.assertIn("--no-daemon", argv)

    def test_gradle_homes_are_fresh_copies_and_triage_review_get_none(self):
        template = self.root / "gradle-template"
        (template / "wrapper").mkdir(parents=True)
        (template / "wrapper" / "marker").write_text("pristine")
        os.environ["AGENT_GRADLE_HOME"] = str(template)
        seen = {"triage": [], "implement": [], "review": [], "checks": []}

        def capture(kind, also=None):
            def effect(sb):
                seen[kind].append(sb)
                if also:
                    also(sb)
            return effect

        def poison(sb):
            (Path(sb.env["GRADLE_USER_HOME"]) / "init.d").mkdir(exist_ok=True)
            (Path(sb.env["GRADLE_USER_HOME"]) / "init.d" / "evil.gradle").write_text("x")
            GOOD(sb)

        def recording(sb, argv):
            seen["checks"].append(sb)
            gh = Path(sb.env["GRADLE_USER_HOME"])
            self.assertTrue((gh / "wrapper" / "marker").is_file())
            self.assertFalse((gh / "init.d").exists())
            return argv

        adapters = {
            "triage": FakeAdapter([{"output": brief_dict(), "effect": capture("triage")}]),
            "implement": FakeAdapter([{"effect": capture("implement", poison)}]),
            "review": FakeAdapter([{"output": PASS, "effect": capture("review")}]),
        }
        try:
            run_pipeline(ISSUE, self.repo, self.base, self.cfg, self.out, adapters, wrap=recording)
        finally:
            del os.environ["AGENT_GRADLE_HOME"]

        for sb in seen["triage"] + seen["review"]:
            self.assertNotIn("GRADLE_USER_HOME", sb.env)
        homes = [sb.env["GRADLE_USER_HOME"] for sb in seen["implement"] + seen["checks"]]
        self.assertTrue(seen["checks"])
        self.assertNotIn(seen["implement"][0].env["GRADLE_USER_HOME"], [sb.env["GRADLE_USER_HOME"] for sb in seen["checks"]])
        for sb in seen["implement"] + seen["checks"] + seen["triage"] + seen["review"]:
            self.assertNotIn(str(template), sb.rw_binds)
        self.assertFalse((template / "init.d").exists())
        self.assertTrue(all(h for h in homes))

    def test_handoff_not_approved_when_only_dropped_findings_remain(self):
        contradicted = {"criteria": [{"id": "AC1", "verdict": "pass", "reason": "ok"}], "blocking": [
            {"file": None, "line": None, "text": "drawsDotsOnLegalSquares will fail", "patch": None}]}

        record, _ = self.run_with([{"output": brief_dict()}], [{"effect": GOOD}], [{"output": contradicted}])

        self.assertEqual(record.outcome, "handoff")
        self.assertFalse(record.approved)
        self.assertEqual(len(record.dropped_feedback), 1)

    def test_run_json_records_config_and_prompt_hashes(self):
        self.run_with([{"output": brief_dict()}], [{"effect": GOOD}], [{"output": PASS}])

        data = json.loads((self.out / "run.json").read_text())
        self.assertEqual(data["config_sha256"], self.cfg.source_sha256)
        self.assertEqual(set(data["prompts_sha256"]), {"triage", "implement", "review"})
        self.assertEqual(data["steps"][0]["step"], "triage")
        self.assertEqual(data["steps"][0]["model"], "qwen3.6-apex")
        self.assertTrue(all("report" in s for s in data["stages"]))


if __name__ == "__main__":
    unittest.main()
