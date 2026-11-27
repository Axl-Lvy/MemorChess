import os
import subprocess
import unittest
from types import SimpleNamespace

from agentrun.sandbox import PreflightError, Sandbox, bwrap_argv, preflight


def bwrap_works() -> bool:
    try:
        preflight()
        return True
    except PreflightError:
        return False


class SandboxTest(unittest.TestCase):
    def sb(self, **kw) -> Sandbox:
        args = dict(ro_binds=("/opt/jdk",), rw_binds=("/w/scratch", "/w/out"), env={"A": "1"}, workdir="/w/scratch")
        args.update(kw)
        return Sandbox(**args)

    def test_argv_has_no_dev_bind_root(self):
        argv = bwrap_argv(self.sb(), ["true"])

        self.assertEqual(argv[0], "bwrap")
        for i, a in enumerate(argv):
            if a in ("--dev-bind", "--bind", "--ro-bind"):
                self.assertNotEqual(argv[i + 1], "/", argv)
        self.assertIn("--unshare-all", argv)
        self.assertIn("--die-with-parent", argv)

    def test_rw_and_ro_binds_in_order(self):
        argv = bwrap_argv(self.sb(), ["echo", "hi"])

        joined = " ".join(argv)
        self.assertIn("--ro-bind /opt/jdk /opt/jdk", joined)
        self.assertIn("--bind /w/scratch /w/scratch", joined)
        self.assertIn("--bind /w/out /w/out", joined)
        self.assertEqual(argv[-3:], ["--", "echo", "hi"])
        self.assertLess(joined.index("--ro-bind /usr"), joined.index("--bind /w/scratch"))

    def test_env_is_cleared_then_set(self):
        argv = bwrap_argv(self.sb(env={"A": "1", "B": "2"}), ["true"])

        clear = argv.index("--clearenv")
        setenvs = [i for i, a in enumerate(argv) if a == "--setenv"]
        self.assertEqual(len(setenvs), 2)
        self.assertTrue(all(i > clear for i in setenvs))
        self.assertEqual(argv[argv.index("--chdir") + 1], "/w/scratch")

    def test_preflight_reports_missing_bwrap(self):
        with self.assertRaises(PreflightError) as ctx:
            preflight(which=lambda _: None)

        self.assertIn("bwrap", str(ctx.exception))

    def test_preflight_reports_userns_blocked(self):
        failing = lambda *a, **k: SimpleNamespace(returncode=1, stderr="setting up uid map: Permission denied")

        with self.assertRaises(PreflightError) as ctx:
            preflight(which=lambda _: "/usr/bin/bwrap", run=failing)

        self.assertIn("AppArmor", str(ctx.exception))

    @unittest.skipUnless(bwrap_works(), "bwrap not usable on this host")
    def test_real_sandbox_runs_and_sees_only_binds(self):
        argv = bwrap_argv(Sandbox(ro_binds=(), rw_binds=(), env={"X": "y"}, workdir="/"), ["sh", "-c", "echo $X; ls /"])

        result = subprocess.run(argv, capture_output=True, text=True)

        self.assertEqual(result.returncode, 0, result.stderr)
        lines = result.stdout.split()
        self.assertEqual(lines[0], "y")
        self.assertNotIn("home", lines)

    @unittest.skipUnless(bwrap_works(), "bwrap not usable on this host")
    def test_real_sandbox_hides_home(self):
        home = os.path.expanduser("~")
        argv = bwrap_argv(Sandbox(ro_binds=(), rw_binds=(), env={}, workdir="/"), ["ls", home])

        result = subprocess.run(argv, capture_output=True)

        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
