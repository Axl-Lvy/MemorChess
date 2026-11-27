import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from agentrun.config import ConfigError
from agentrun.sandbox import PreflightError
from tests.helpers import AGENT_DIR, repo_config

spec = importlib.util.spec_from_file_location("agent_run_cli", AGENT_DIR / "run.py")
cli = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cli)


class CliTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.issue = self.root / "issue.json"
        self.issue.write_text(json.dumps({"number": 1, "title": "t", "body": "b", "comments": []}))

    def tearDown(self):
        self._tmp.cleanup()

    def argv(self, *extra: str) -> list[str]:
        return ["--issue", str(self.issue), "--repo", str(self.root), "--base", "HEAD", "--out", str(self.root / "out"), *extra]

    def test_set_override_changes_model(self):
        cfg = cli.apply_overrides(repo_config(), ["implement.model=gemma4-26b", "review.enabled=false", "fix_rounds=1"])

        self.assertEqual(cfg.steps["implement"].model, "gemma4-26b")
        self.assertFalse(cfg.steps["review"].enabled)
        self.assertEqual(cfg.fix_rounds, 1)
        self.assertEqual(cfg.steps["triage"].model, "qwen3.6-apex")

    def test_set_override_validates_like_the_file(self):
        with self.assertRaises(ConfigError):
            cli.apply_overrides(repo_config(), ["triage.harness=nope"])
        with self.assertRaises(ConfigError):
            cli.apply_overrides(repo_config(), ["implement.max_turns=lots"])

    def test_bad_set_key_exits_2(self):
        self.assertEqual(cli.main(self.argv("--set", "nope.model=x"), preflights=[], harnesses={}), 2)

    def test_preflight_failure_exits_2_before_any_adapter_call(self):
        built = []

        def failing():
            raise PreflightError("no bwrap")

        harnesses = {name: (lambda url, n=name: built.append(n)) for name in ("claude-code", "chat", "fake")}

        code = cli.main(self.argv(), preflights=[failing], harnesses=harnesses)

        self.assertEqual(code, 2)
        self.assertEqual(built, [])


if __name__ == "__main__":
    unittest.main()
