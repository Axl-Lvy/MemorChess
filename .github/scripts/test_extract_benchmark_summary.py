import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from extract_benchmark_summary import main, read_cpu


class ReadCpuTest(unittest.TestCase):
    def test_read_cpu_returns_stripped_model(self):
        with tempfile.TemporaryDirectory() as tmp:
            nested = Path(tmp, "nested")
            nested.mkdir()
            (nested / "runner-cpu.txt").write_text("  AMD EPYC 7763 64-Core Processor\n")

            cpu = read_cpu(Path(tmp))

            self.assertEqual(cpu, "AMD EPYC 7763 64-Core Processor")

    def test_read_cpu_missing_returns_none(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(read_cpu(Path(tmp)))

    def test_read_cpu_blank_returns_none(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "runner-cpu.txt").write_text("\n")

            self.assertIsNone(read_cpu(Path(tmp)))


class MainTest(unittest.TestCase):
    def run_main(self, *args: str) -> int:
        with mock.patch.object(sys, "argv", ["extract_benchmark_summary.py", *args]), contextlib.redirect_stdout(io.StringIO()):
            return main()

    def test_main_writes_schema_2_with_cpu(self):
        with tempfile.TemporaryDirectory() as tmp:
            micro = Path(tmp, "micro")
            micro.mkdir()
            (micro / "main.json").write_text(
                json.dumps(
                    [
                        {
                            "benchmark": "a.b.C.m",
                            "mode": "thrpt",
                            "primaryMetric": {"score": 2.0, "scoreError": 0.1, "scoreUnit": "ops/s"},
                        }
                    ]
                )
            )
            (micro / "runner-cpu.txt").write_text("X")
            output = Path(tmp, "summary.json")

            code = self.run_main(
                "--macro-dir", str(Path(tmp, "absent")),
                "--micro-dir", str(micro),
                "--commit", "abc",
                "--date", "2026-11-25T03:00:00Z",
                "--output", str(output),
            )

            summary = json.loads(output.read_text())
            self.assertEqual(code, 0)
            self.assertEqual(summary["schema"], 2)
            self.assertEqual(summary["cpu"], {"micro": "X", "macro": None})
            self.assertEqual(summary["metrics"]["micro/C.m"]["value"], 2.0)

    def test_main_rejects_prune_arguments(self):
        with self.assertRaises(SystemExit), mock.patch("sys.stderr"):
            self.run_main(
                "--macro-dir", "m",
                "--micro-dir", "u",
                "--commit", "abc",
                "--date", "2026-11-25T03:00:00Z",
                "--output", "o.json",
                "--prune-dir", "x",
            )


if __name__ == "__main__":
    unittest.main()
