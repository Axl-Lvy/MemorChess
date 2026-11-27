import json
import os
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

from agentrun.adapters import HARNESSES
from agentrun.adapters.base import run_bounded
from agentrun.adapters.chat import ChatAdapter
from agentrun.adapters.claude_code import ClaudeCodeAdapter, parse_stream
from agentrun.adapters.fake import FakeAdapter
from agentrun.config import KNOWN_HARNESSES, StepConfig
from agentrun.sandbox import Sandbox
from tests.helpers import FIXTURES


def step(name="triage", model="qwen3.6-apex", timeout_s=30) -> StepConfig:
    return StepConfig(name, "fake", model, Path("p.md"), 10, timeout_s, 1, True)


SB = Sandbox(ro_binds=(), rw_binds=(), env={}, workdir="/")


class AdaptersTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.out = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_registry_matches_config(self):
        self.assertEqual(set(HARNESSES), set(KNOWN_HARNESSES))

    def test_timeout_kills_process_group(self):
        pid_file = self.out / "child.pid"
        argv = ["sh", "-c", f"sleep 30 & echo $! > {pid_file}; wait"]

        code, wall = run_bounded(argv, timeout_s=1, log=self.out / "log")

        self.assertIsNone(code)
        self.assertLess(wall, 10)
        child = int(pid_file.read_text())
        time.sleep(0.2)
        with self.assertRaises(ProcessLookupError):
            os.kill(child, 0)

    def test_run_bounded_returns_exit_code_and_logs(self):
        code, _ = run_bounded(["sh", "-c", "echo hello; exit 3"], timeout_s=10, log=self.out / "log")

        self.assertEqual(code, 3)
        self.assertIn("hello", (self.out / "log").read_text())

    def test_fake_replays_script(self):
        fake = FakeAdapter([{"output": {"a": 1}}, {"output": None}, {"status": "timeout"}])

        first = fake.start("p", step(), SB, self.out / "1")
        second = fake.start("p", step(), SB, self.out / "2")
        third = fake.resume("s", "fb", step(), SB, self.out / "3")

        self.assertEqual(json.loads(first.output_file.read_text()), {"a": 1})
        self.assertIsNone(second.output_file)
        self.assertEqual(third.status, "timeout")
        self.assertEqual(fake.calls[2], ("resume", "fb"))

    def test_chat_writes_content(self):
        seen = {}

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                seen["path"] = self.path
                seen["body"] = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                body = json.dumps({
                    "choices": [{"message": {"content": "{\"enough_info\": true}"}}],
                    "usage": {"prompt_tokens": 11, "completion_tokens": 5},
                }).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *a):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            adapter = ChatAdapter(f"http://127.0.0.1:{server.server_port}")
            result = adapter.start("question", step("precheck", model="jev"), SB, self.out)
        finally:
            server.shutdown()
            server.server_close()

        self.assertEqual(result.status, "ok")
        self.assertEqual(json.loads(result.output_file.read_text()), {"enough_info": True})
        self.assertEqual(seen["path"], "/v1/chat/completions")
        self.assertEqual(seen["body"]["model"], "jev")
        self.assertEqual(seen["body"]["response_format"], {"type": "json_object"})
        self.assertEqual((result.tokens_in, result.tokens_out), (11, 5))

    def test_claude_code_argv(self):
        adapter = ClaudeCodeAdapter("http://127.0.0.1:4000")

        argv = adapter.build_argv("do it", step("implement"), SB, self.out, resume=None)

        self.assertEqual(argv[0], "bwrap")
        inner = argv[argv.index("--") + 1:]
        self.assertTrue(inner[0].endswith("claude") or "/claude/" in inner[0], inner[0])
        self.assertEqual(inner[1], "-p")
        self.assertIn("--max-turns", inner)
        self.assertEqual(inner[inner.index("--model") + 1], "qwen3.6-apex")
        envs = {argv[i + 1]: argv[i + 2] for i, a in enumerate(argv) if a == "--setenv"}
        self.assertEqual(envs["ANTHROPIC_BASE_URL"], "http://127.0.0.1:4000")
        self.assertTrue(envs["HOME"].startswith(str(self.out)))
        for key, value in envs.items():
            self.assertNotIn("GH_TOKEN", key)
            self.assertNotIn("APP_PRIVATE_KEY", key)
        binds = [argv[i + 1] for i, a in enumerate(argv) if a == "--bind"]
        self.assertNotIn(str(self.out), binds)
        self.assertIn(str(self.out / "agent"), binds)
        self.assertIn(str(self.out / "home"), binds)
        self.assertEqual(envs["AGENT_OUT"], str(self.out / "agent"))

    def test_claude_code_uses_configured_binary_and_small_model(self):
        binary = self.out / "versions" / "2.1.332"
        binary.parent.mkdir()
        binary.write_text("")
        os.environ["AGENT_CLAUDE_BIN"] = str(binary)
        try:
            argv = ClaudeCodeAdapter("http://localhost:11434").build_argv("x", step(model="spike-apex"), SB, self.out, resume=None)
        finally:
            del os.environ["AGENT_CLAUDE_BIN"]

        inner = argv[argv.index("--") + 1:]
        envs = {argv[i + 1]: argv[i + 2] for i, a in enumerate(argv) if a == "--setenv"}
        self.assertEqual(inner[0], str(binary))
        self.assertIn(str(binary.parent), [argv[i + 1] for i, a in enumerate(argv) if a == "--ro-bind"])
        self.assertEqual(envs["ANTHROPIC_DEFAULT_HAIKU_MODEL"], "spike-apex")
        self.assertEqual(envs["ANTHROPIC_SMALL_FAST_MODEL"], "spike-apex")

    def test_missing_binary_does_not_mount_the_working_directory(self):
        os.environ["AGENT_CLAUDE_BIN"] = str(self.out / "nope" / "claude")
        try:
            argv = ClaudeCodeAdapter("http://x").build_argv("x", step(), SB, self.out, resume=None)
        finally:
            del os.environ["AGENT_CLAUDE_BIN"]

        ro = [argv[i + 1] for i, a in enumerate(argv) if a == "--ro-bind"]
        self.assertNotIn(os.getcwd(), ro)
        self.assertNotIn(str(self.out / "nope"), ro)

    def test_claude_code_resume_argv(self):
        adapter = ClaudeCodeAdapter("http://127.0.0.1:4000")

        argv = adapter.build_argv("feedback", step(), SB, self.out, resume="sess-1")

        inner = argv[argv.index("--") + 1:]
        self.assertEqual(inner[inner.index("--resume") + 1], "sess-1")

    def test_claude_code_parses_result(self):
        parsed = parse_stream((FIXTURES / "claude_stream.jsonl").read_text())

        self.assertEqual(parsed, ("3f6c1d2e-aaaa-bbbb-cccc-000000000001", 17, 172000, 3100))

    def test_parse_stream_without_result(self):
        self.assertEqual(parse_stream("not json\n{\"type\":\"assistant\"}\n"), (None, None, None, None))


if __name__ == "__main__":
    unittest.main()
