import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from agentrun.brief import parse_brief, test_key
from agentrun.checks import parse_junit_dir, preflight_java, run_checks
from agentrun.sandbox import PreflightError, Sandbox
from tests.test_sandbox import bwrap_works
from agentrun.workspace import apply_and_commit, capture_patch, fresh_clone
from tests.helpers import BOARD_SRC, BOARD_TEST, FILES, FIXTURES, brief_dict, git, make_repo, repo_config

NEW_TEST_BODY = (
    "package proj\n\nclass TestBoard {\n"
    "  @Test fun mapsSquare() { squareToTile(1) shouldBe 1 }\n"
    "  @Test fun `draws dots on legal squares`() { hints(squareToTile(12)) shouldBe setOf(20, 28) }\n}\n"
)
NO_SANDBOX = lambda sb, argv: argv  # noqa: E731


class ChecksTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        files = dict(FILES)
        files["gradlew"] = (FIXTURES / "fake_gradlew.sh").read_text()
        self.repo = make_repo(self.root, files)
        (self.repo / "gradlew").chmod(0o755)
        git(self.repo, "update-index", "--chmod=+x", "gradlew")
        git(self.repo, "commit", "-q", "-m", "exec")
        self.base = git(self.repo, "rev-parse", "HEAD")
        self.cfg = repo_config()
        self.brief = parse_brief(json.dumps(brief_dict()))
        self.junit = self.root / "junit"
        self.junit.mkdir()
        self.log = self.root / "gradle.log"
        os.environ["FAKE_GRADLE_LOG"] = str(self.log)
        os.environ["FAKE_JUNIT_SRC"] = str(self.junit)
        os.environ.pop("FAKE_GRADLE_FAIL", None)

    def tearDown(self):
        for k in ("FAKE_GRADLE_LOG", "FAKE_JUNIT_SRC", "FAKE_GRADLE_FAIL"):
            os.environ.pop(k, None)
        self._tmp.cleanup()

    def junit_cases(self, cases: list[tuple[str, str, bool]]) -> None:
        body = "".join(
            f'<testcase name="{n}" classname="{c}">' + ("" if ok else "<failure>x</failure>") + "</testcase>"
            for c, n, ok in cases
        )
        (self.junit / "TEST-x.xml").write_text(f"<testsuite>{body}</testsuite>")

    def change(self, edits: dict[str, str | None]) -> Path:
        """Applies `edits` (path -> content, None deletes) as an agent patch; returns the clone."""
        scratch = fresh_clone(self.repo, self.base, self.root / "scratch")
        for path, content in edits.items():
            if content is None:
                (scratch / path).unlink()
            else:
                (scratch / path).parent.mkdir(parents=True, exist_ok=True)
                (scratch / path).write_text(content)
        clone = fresh_clone(self.repo, self.base, self.root / "clone")
        apply_and_commit(clone, capture_patch(scratch, self.base), "agent")
        return clone

    def run_on(self, clone: Path, brief=None, wrap=NO_SANDBOX):
        sb = Sandbox(ro_binds=(), rw_binds=(), env={}, workdir=str(clone))
        return run_checks(clone, self.base, brief or self.brief, self.cfg, sb, wrap=wrap)

    def item(self, report, name):
        return next(i for i in report.items if i.name == name)

    def good_change(self):
        return self.change({
            BOARD_TEST: NEW_TEST_BODY,
            BOARD_SRC: FILES[BOARD_SRC] + "fun hints(tile: Int): Set<Int> = setOf(20, 28)\n",
        })

    def test_normalize_matches_all_styles(self):
        passed, failed = parse_junit_dir(FIXTURES / "junit")

        self.assertIn(test_key("TestBoard", "draws dots on legal squares"), passed)
        self.assertIn(test_key("TestBoard", "maps square"), passed)
        self.assertIn(test_key("TestBoard", "keeps contrast above threshold"), failed)
        self.assertIn(test_key("TestBoardKotest", "hint dots respect orientation"), passed)
        self.assertNotIn(test_key("TestBoard", "skipped one"), passed | failed)

    def test_real_kmp_names_with_target_suffix_match(self):
        passed, _ = parse_junit_dir(FIXTURES / "junit-real")

        self.assertIn(test_key("TestBoard", "board renders upright when not inverted"), passed)
        self.assertEqual(test_key("TestBoard", "x[jvm]"), test_key("TestBoard", "x"))
        self.assertEqual(test_key("TestBoard", "x[wasmJs, browser]"), test_key("TestBoard", "x()"))

    def test_green_change_passes_everything(self):
        clone = self.good_change()
        self.junit_cases([("proj.TestBoard", "`draws dots on legal squares`()", True), ("proj.TestBoard", "mapsSquare", True)])

        report = self.run_on(clone)

        self.assertTrue(report.green, [(i.name, i.detail) for i in report.items if not i.ok])
        self.assertEqual([i.name for i in report.items], [
            "compile", "format", "tests", "acceptance", "expected_values", "helpers_used",
            "tests_not_weakened", "stray_files",
        ])
        self.assertEqual(report.score, 8 + 2)

    def test_acceptance_missing_vs_failing(self):
        clone = self.good_change()
        self.junit_cases([("proj.TestBoard", "drawsDotsOnLegalSquares", False)])
        failing = self.item(self.run_on(clone), "acceptance")
        self.junit_cases([("proj.TestBoard", "somethingElse", True)])

        missing = self.item(self.run_on(clone), "acceptance")

        self.assertFalse(failing.ok)
        self.assertIn("failing", failing.detail)
        self.assertFalse(missing.ok)
        self.assertIn("missing", missing.detail)

    def test_expected_value_missing_flagged(self):
        clone = self.change({BOARD_TEST: NEW_TEST_BODY.replace("setOf(20, 28) }", "setOf(1) }")})
        self.junit_cases([("proj.TestBoard", "drawsDotsOnLegalSquares", True)])

        item = self.item(self.run_on(clone), "expected_values")

        self.assertFalse(item.ok)
        self.assertIn("draws dots on legal squares", item.detail)

    def test_lowered_threshold_flagged(self):
        weakened = FILES[BOARD_TEST].replace("shouldBe 1 }", "shouldBe 0 }")
        clone = self.change({BOARD_TEST: weakened})

        item = self.item(self.run_on(clone), "tests_not_weakened")

        self.assertFalse(item.ok)
        self.assertIn(BOARD_TEST, item.detail)

    def test_moved_assertion_not_flagged(self):
        moved = FILES[BOARD_TEST].replace("class TestBoard {\n", "class TestBoard {\n  // moved\n")
        clone = self.change({BOARD_TEST: moved})

        self.assertTrue(self.item(self.run_on(clone), "tests_not_weakened").ok)

    def test_ignore_annotation_flagged(self):
        clone = self.change({BOARD_TEST: FILES[BOARD_TEST].replace("  @Test", "  @Ignore @Test")})

        self.assertFalse(self.item(self.run_on(clone), "tests_not_weakened").ok)

    def test_deleted_test_file_flagged(self):
        clone = self.change({BOARD_TEST: None})

        self.assertFalse(self.item(self.run_on(clone), "tests_not_weakened").ok)

    def test_allow_test_changes_exempts(self):
        clone = self.change({BOARD_TEST: FILES[BOARD_TEST].replace("shouldBe 1 }", "shouldBe 0 }")})
        brief = parse_brief(json.dumps({**brief_dict(), "allow_test_changes": [BOARD_TEST]}))

        self.assertTrue(self.item(self.run_on(clone, brief), "tests_not_weakened").ok)

    def test_helper_not_used_flagged(self):
        clone = self.change({BOARD_TEST: NEW_TEST_BODY.replace("hints(squareToTile(12))", "hints(12)")})

        item = self.item(self.run_on(clone), "helpers_used")

        self.assertFalse(item.ok)
        self.assertIn("squareToTile", item.detail)

    def test_compile_failure_skips_tests(self):
        clone = self.good_change()
        os.environ["FAKE_GRADLE_FAIL"] = ":shared:compileKotlinJvm"

        report = self.run_on(clone)

        self.assertFalse(self.item(report, "compile").ok)
        self.assertIn("Unresolved reference", self.item(report, "compile").detail)
        self.assertFalse(self.item(report, "tests").ok)
        self.assertFalse(report.green)

    def test_test_task_gets_filters_and_offline(self):
        clone = self.good_change()

        self.run_on(clone)

        calls = self.log.read_text().splitlines()
        test_call = next(c for c in calls if ":shared:jvmTest" in c)
        self.assertIn("--offline", test_call)
        self.assertIn(":shared:jvmTest --tests *.TestBoard", test_call)

    def test_gradle_runs_in_sandbox(self):
        clone = self.good_change()
        seen = []

        def recording_wrap(sb, argv):
            seen.append((sb, argv))
            return argv

        self.run_on(clone, wrap=recording_wrap)

        self.assertTrue(seen)
        self.assertTrue(all(argv[0] == "./gradlew" and "--offline" in argv for _, argv in seen))
        self.assertTrue(all(sb.workdir != str(clone) and sb.workdir in sb.rw_binds for sb, _ in seen))
        self.assertTrue(all(str(clone) not in sb.rw_binds for sb, _ in seen))

    @unittest.skipUnless(bwrap_works(), "bwrap not usable on this host")
    def test_checks_run_in_a_bound_disposable_checkout(self):
        clone = self.good_change()
        self.junit_cases([("proj.TestBoard", "`draws dots on legal squares`()[jvm]", True), ("proj.TestBoard", "mapsSquare", True)])
        sb = Sandbox(ro_binds=(str(self.junit),), rw_binds=(), env={"FAKE_JUNIT_SRC": str(self.junit), "PATH": "/usr/bin:/bin"}, workdir="/")

        report = run_checks(clone, self.base, self.brief, self.cfg, sb)

        self.assertTrue(report.green, [(i.name, i.detail) for i in report.failures()])
        self.assertFalse((clone / "shared" / "build").exists())

    def test_symlinked_results_are_not_read(self):
        clone = self.good_change()
        outside = self.root / "outside.xml"
        outside.write_text('<testsuite><testcase name="drawsDotsOnLegalSquares" classname="proj.TestBoard"/></testsuite>')
        os.environ.pop("FAKE_JUNIT_SRC")
        os.environ["FAKE_JUNIT_LINK"] = str(outside)
        try:
            report = self.run_on(clone)
        finally:
            os.environ.pop("FAKE_JUNIT_LINK")

        self.assertFalse(self.item(report, "acceptance").ok)

    def test_preflight_rejects_java_17(self):
        old = lambda *a, **k: SimpleNamespace(returncode=0, stderr='openjdk version "17.0.12" 2024-07-16\n', stdout="")
        new = lambda *a, **k: SimpleNamespace(returncode=0, stderr='openjdk version "21.0.5" 2024-10-15 LTS\n', stdout="")

        with self.assertRaises(PreflightError) as ctx:
            preflight_java("java", 21, run=old)

        self.assertIn("17", str(ctx.exception))
        preflight_java("java", 21, run=new)


if __name__ == "__main__":
    unittest.main()
