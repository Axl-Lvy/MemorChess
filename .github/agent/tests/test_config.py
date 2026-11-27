import tempfile
import unittest
from pathlib import Path

from agentrun.config import ConfigError, load_config

REPO_TOML = Path(__file__).resolve().parent.parent / "pipeline.toml"


def write_config(dir_: Path, text: str) -> Path:
    for name in ("triage", "implement", "review", "precheck"):
        (dir_ / f"{name}.md").write_text(f"{name} prompt")
    path = dir_ / "pipeline.toml"
    path.write_text(text)
    return path


MINIMAL = """
java_min = 21
fix_rounds = 2
attempts = 2
brief_gate = false
llm_base_url = "http://127.0.0.1:4000"
gradle_args = ["--offline"]
deny_paths = [".github/**"]
build_files = ["**/*.gradle.kts"]
stray_globs = ["*.orig"]
sandbox_ro_binds = []

[steps.triage]
harness = "{triage_harness}"
model = "m"
prompt = "triage.md"
max_turns = 10
timeout_s = 60
retries = 1
enabled = {triage_enabled}

[steps.implement]
harness = "claude-code"
model = "m"
prompt = "implement.md"
max_turns = 10
timeout_s = 60
retries = 1
enabled = true

[steps.review]
harness = "claude-code"
model = "m"
prompt = "{review_prompt}"
max_turns = 10
timeout_s = 60
retries = 1
enabled = true

[modules.shared]
test_task = ":shared:jvmTest"
compile_task = ":shared:compileKotlinJvm"
format_task = ":shared:ktfmtCheck"
results_dir = "shared/build/test-results/jvmTest"
src_dirs = ["shared/src"]
"""


def minimal(triage_harness="claude-code", triage_enabled="true", review_prompt="review.md"):
    return (
        MINIMAL.replace("{triage_harness}", triage_harness)
        .replace("{triage_enabled}", triage_enabled)
        .replace("{review_prompt}", review_prompt)
    )


class ConfigTest(unittest.TestCase):
    def test_loads_repo_pipeline_toml(self):
        cfg = load_config(REPO_TOML)

        self.assertFalse(cfg.steps["precheck"].enabled)
        self.assertEqual(cfg.fix_rounds, 2)
        self.assertEqual(cfg.attempts, 2)
        self.assertEqual(cfg.java_min, 21)
        self.assertFalse(cfg.brief_gate)
        self.assertEqual(cfg.modules["composeApp"].test_task, ":composeApp:jvmTest")
        self.assertIn(".github/**", cfg.deny_paths)

    def test_unknown_harness_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = write_config(Path(d), minimal(triage_harness="nope"))

            with self.assertRaises(ConfigError) as ctx:
                load_config(path)

        self.assertIn("triage", str(ctx.exception))
        self.assertIn("nope", str(ctx.exception))

    def test_missing_prompt_file_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = write_config(Path(d), minimal(review_prompt="missing.md"))

            with self.assertRaises(ConfigError) as ctx:
                load_config(path)

        self.assertIn("missing.md", str(ctx.exception))

    def test_enabled_triage_and_implement_required(self):
        with tempfile.TemporaryDirectory() as d:
            path = write_config(Path(d), minimal(triage_enabled="false"))

            with self.assertRaises(ConfigError) as ctx:
                load_config(path)

        self.assertIn("triage", str(ctx.exception))

    def test_source_hash_changes_with_content(self):
        with tempfile.TemporaryDirectory() as d:
            path = write_config(Path(d), minimal())
            first = load_config(path).source_sha256
            path.write_text(minimal().replace("fix_rounds = 2", "fix_rounds = 1"))

            second = load_config(path).source_sha256

        self.assertNotEqual(first, second)


if __name__ == "__main__":
    unittest.main()
