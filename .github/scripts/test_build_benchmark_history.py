import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from build_benchmark_history import build_history, load_runs, main

MISSING_SHA = "f" * 40


def git(repo: Path, *args: str) -> str:
    env = {
        **os.environ,
        "GIT_AUTHOR_NAME": "Dev",
        "GIT_AUTHOR_EMAIL": "dev@example.com",
        "GIT_COMMITTER_NAME": "Dev",
        "GIT_COMMITTER_EMAIL": "dev@example.com",
    }
    result = subprocess.run(["git", *args], cwd=repo, env=env, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def run(commit: str, date: str, values: dict | None = None, cpu: dict | None = None) -> dict:
    metrics = values if values is not None else {"micro/C.m": {"value": 2.0, "error": 0.1, "unit": "ops/s", "direction": "higher"}}
    summary = {"schema": 1, "commit": commit, "date": date, "runner": "Linux", "metrics": metrics}
    if cpu is not None:
        summary["schema"] = 2
        summary["cpu"] = cpu
    return summary


class HistoryTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        root = Path(self._tmp.name)
        self.repo = root / "repo"
        self.runs_dir = root / "runs"
        self.repo.mkdir()
        self.runs_dir.mkdir()
        git(self.repo, "init", "-q")
        self.sha = {}
        for name, subject in [("c1", "one"), ("c2", "two"), ("c3", "three (#7)"), ("c4", "four")]:
            git(self.repo, "commit", "-q", "--allow-empty", "-m", subject)
            self.sha[name] = git(self.repo, "rev-parse", "HEAD")

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, name: str, summary: dict) -> None:
        (self.runs_dir / name).write_text(json.dumps(summary))

    def history(self) -> dict:
        return build_history(load_runs(self.runs_dir), self.repo, "2026-11-25T03:20:00Z")


class LoadRunsTest(HistoryTest):
    def test_load_runs_sorts_by_date_and_skips_bad_files(self):
        self.write("b.json", run(self.sha["c2"], "2026-11-02T03:00:00Z"))
        self.write("c.json", run(self.sha["c3"], "2026-11-03T03:00:00Z"))
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))
        (self.runs_dir / "bad.json").write_text("not json")
        stderr = io.StringIO()

        with contextlib.redirect_stderr(stderr):
            runs = load_runs(self.runs_dir)

        self.assertEqual([r["date"][:10] for r in runs], ["2026-11-01", "2026-11-02", "2026-11-03"])
        self.assertIn("Skipping", stderr.getvalue())


class BuildHistoryTest(HistoryTest):
    def test_schema_1_run_gets_null_cpu(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))

        history = self.history()

        self.assertEqual(history["runs"][0]["cpu"], {"micro": None, "macro": None})

    def test_schema_2_run_keeps_cpu(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z", cpu={"micro": "EPYC", "macro": None}))

        history = self.history()

        self.assertEqual(history["runs"][0]["cpu"], {"micro": "EPYC", "macro": None})

    def test_first_commit_range_is_itself(self):
        c1 = self.sha["c1"]
        self.write("a.json", run(c1, "2026-11-01T03:00:00Z"))

        history = self.history()

        self.assertEqual(history["commits"][c1]["range"], [{"sha": c1, "subject": "one"}])

    def test_range_spans_unbenchmarked_commits(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))
        self.write("b.json", run(self.sha["c4"], "2026-11-02T03:00:00Z"))

        commit = self.history()["commits"][self.sha["c4"]]

        self.assertEqual([c["sha"] for c in commit["range"]], [self.sha["c4"], self.sha["c3"], self.sha["c2"]])
        self.assertEqual(commit["range"][1]["subject"], "three (#7)")
        self.assertEqual(commit["subject"], "four")

    def test_consecutive_runs_on_same_commit_share_one_entry(self):
        self.write("a.json", run(self.sha["c2"], "2026-11-01T03:00:00Z"))
        self.write("b.json", run(self.sha["c2"], "2026-11-02T03:00:00Z"))

        history = self.history()

        self.assertEqual(len(history["commits"]), 1)
        self.assertEqual(len(history["runs"]), 2)

    def test_missing_commit_gets_empty_subject_and_range(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))
        self.write("b.json", run(MISSING_SHA, "2026-11-02T03:00:00Z"))

        history = self.history()

        self.assertEqual(history["commits"][MISSING_SHA], {"subject": "", "range": []})

    def test_commit_after_missing_previous_gets_itself(self):
        self.write("a.json", run(MISSING_SHA, "2026-11-01T03:00:00Z"))
        self.write("b.json", run(self.sha["c3"], "2026-11-02T03:00:00Z"))

        commit = self.history()["commits"][self.sha["c3"]]

        self.assertEqual(commit["range"], [{"sha": self.sha["c3"], "subject": "three (#7)"}])

    def test_metric_missing_from_some_runs(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))
        self.write("b.json", run(self.sha["c2"], "2026-11-02T03:00:00Z", values={
            "macro/K.n/frameCount_median": {"value": 35.0, "unit": "", "direction": "lower"}}))

        history = self.history()

        self.assertIn("micro/C.m", history["metrics"])
        self.assertNotIn("micro/C.m", history["runs"][1]["values"])

    def test_macro_only_run_passes_through(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z", values={
            "macro/K.n/frameCount_median": {"value": 35.0, "unit": "", "direction": "lower"}}))

        history = self.history()

        self.assertEqual(history["runs"][0]["values"], {"macro/K.n/frameCount_median": [35.0]})
        self.assertEqual(history["metrics"]["macro/K.n/frameCount_median"], {"unit": "", "direction": "lower"})

    def test_error_kept_only_when_present(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z", values={
            "micro/C.m": {"value": 2.0, "error": 0.1, "unit": "ops/s", "direction": "higher"},
            "macro/K.n/frameCount_median": {"value": 35.0, "unit": "", "direction": "lower"},
            "micro/C.nullError": {"value": 3.0, "error": None, "unit": "ops/s", "direction": "higher"}}))

        values = self.history()["runs"][0]["values"]

        self.assertEqual(values["micro/C.m"], [2.0, 0.1])
        self.assertEqual(values["macro/K.n/frameCount_median"], [35.0])
        self.assertEqual(values["micro/C.nullError"], [3.0])

    def test_newest_run_wins_metric_metadata(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z", values={
            "micro/C.m": {"value": 2.0, "unit": "ops/ms", "direction": "higher"}}))
        self.write("b.json", run(self.sha["c2"], "2026-11-02T03:00:00Z", values={
            "micro/C.m": {"value": 2.0, "unit": "ops/s", "direction": "higher"}}))

        history = self.history()

        self.assertEqual(history["metrics"]["micro/C.m"]["unit"], "ops/s")

    def test_zero_runs(self):
        history = self.history()

        self.assertEqual((history["runs"], history["commits"], history["metrics"]), ([], {}, {}))
        self.assertEqual(history["schema"], 1)
        self.assertEqual(history["generated"], "2026-11-25T03:20:00Z")

    def test_one_run(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))

        history = self.history()

        self.assertEqual(len(history["commits"]), 1)
        self.assertEqual(history["runs"][0]["commit"], self.sha["c1"])
        self.assertEqual(history["runs"][0]["date"], "2026-11-01T03:00:00Z")


class MainTest(HistoryTest):
    def test_main_writes_compact_json(self):
        self.write("a.json", run(self.sha["c1"], "2026-11-01T03:00:00Z"))
        output = self.runs_dir.parent / "out" / "history.json"

        with contextlib.redirect_stdout(io.StringIO()):
            code = main(["--runs-dir", str(self.runs_dir), "--repo", str(self.repo), "--output", str(output)])

        text = output.read_text()
        self.assertEqual(code, 0)
        self.assertNotIn('": ', text)
        self.assertEqual(json.loads(text)["schema"], 1)
        self.assertRegex(json.loads(text)["generated"], r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$")


if __name__ == "__main__":
    unittest.main()
