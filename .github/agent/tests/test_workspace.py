import json
import os
import tempfile
import unittest
from pathlib import Path

from agentrun.brief import parse_brief
from agentrun.workspace import (
    apply_and_commit,
    capture_patch,
    changed_entries,
    check_changes,
    fresh_clone,
    restore_commit,
)
from tests.helpers import BOARD_SRC, brief_dict, git, make_repo, repo_config


def plant_hook(git_dir: Path, name: str, marker: Path) -> None:
    hooks = git_dir / "hooks"
    hooks.mkdir(parents=True, exist_ok=True)
    hook = hooks / name
    hook.write_text(f"#!/bin/sh\ntouch {marker}\n")
    hook.chmod(0o755)


class WorkspaceTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.repo = make_repo(self.root)
        self.base = git(self.repo, "rev-parse", "HEAD")
        self.cfg = repo_config()
        self.brief = parse_brief(json.dumps(brief_dict()))

    def tearDown(self):
        self._tmp.cleanup()

    def scratch(self) -> Path:
        return fresh_clone(self.repo, self.base, self.root / "scratch")

    def problems(self, scratch: Path, brief=None) -> list[str]:
        capture_patch(scratch, self.base)
        return check_changes(changed_entries(scratch, self.base), self.cfg, brief or self.brief)

    def test_clone_has_no_hooks(self):
        marker = self.root / "marker"
        plant_hook(self.repo / ".git", "post-checkout", marker)

        clone = fresh_clone(self.repo, self.base, self.root / "clone")

        self.assertFalse(marker.exists())
        self.assertEqual(git(clone, "rev-parse", "HEAD"), self.base)
        self.assertEqual(git(clone, "config", "core.hooksPath"), "/dev/null")

    def test_scratch_hook_not_run_by_capture(self):
        scratch = self.scratch()
        marker = self.root / "marker"
        evil_hooks = self.root / "evil-hooks"
        plant_hook(evil_hooks.parent / "evil", "pre-commit", marker)
        git(scratch, "config", "core.hooksPath", str(evil_hooks.parent / "evil" / "hooks"))
        git(scratch, "config", "core.fsmonitor", f"touch {marker}")
        (scratch / BOARD_SRC).write_text("package proj\n// changed\n")

        patch = capture_patch(scratch, self.base)

        self.assertIn("// changed", patch)
        self.assertFalse(marker.exists())

    def test_deny_listed_path_rejected(self):
        scratch = self.scratch()
        (scratch / ".github" / "workflows").mkdir(parents=True)
        (scratch / ".github" / "workflows" / "x.yml").write_text("on: push\n")

        errors = self.problems(scratch)

        self.assertTrue(any(".github/workflows/x.yml" in e for e in errors), errors)

    def test_rename_into_denied_path_rejected(self):
        scratch = self.scratch()
        (scratch / ".github").mkdir()
        os.rename(scratch / BOARD_SRC, scratch / ".github" / "Board.kt")

        errors = self.problems(scratch)

        entries = changed_entries(scratch, self.base)
        self.assertIn((BOARD_SRC, ".github/Board.kt"), [(e.old_path, e.new_path) for e in entries])
        self.assertTrue(any(".github/Board.kt" in e for e in errors), errors)

    def test_build_file_needs_brief_permission(self):
        scratch = self.scratch()
        (scratch / "shared" / "build.gradle.kts").write_text("// changed\n")

        self.assertTrue(any("build file" in e for e in self.problems(scratch)))
        allowed = parse_brief(json.dumps({**brief_dict(), "build_files_allowed": True}))
        self.assertEqual(self.problems(scratch, allowed), [])

    def test_stray_file_rejected(self):
        scratch = self.scratch()
        (scratch / "shared" / "Board.kt.orig").write_text("x")

        errors = self.problems(scratch)

        self.assertTrue(any("Board.kt.orig" in e for e in errors), errors)

    def test_gitattributes_and_symlink_rejected(self):
        scratch = self.scratch()
        (scratch / "shared" / ".gitattributes").write_text("* filter=evil\n")
        os.symlink("/etc/passwd", scratch / "shared" / "link.kt")

        errors = self.problems(scratch)

        self.assertTrue(any("shared/.gitattributes" in e for e in errors), errors)
        self.assertTrue(any("symbolic link" in e for e in errors), errors)

    def test_quoted_non_ascii_path_still_denied(self):
        scratch = self.scratch()
        (scratch / ".github" / "workflows").mkdir(parents=True)
        (scratch / ".github" / "workflows" / "na\u00efve.yml").write_text("on: push\n")

        errors = self.problems(scratch)

        self.assertTrue(any("na\u00efve.yml" in e and "deny" in e for e in errors), errors)

    def test_retargeted_symlink_rejected(self):
        os.symlink("Board.kt", self.repo / "shared" / "link.kt")
        git(self.repo, "add", "-A")
        git(self.repo, "commit", "-q", "-m", "link")
        self.base = git(self.repo, "rev-parse", "HEAD")
        scratch = self.scratch()
        (scratch / "shared" / "link.kt").unlink()
        os.symlink("/etc/passwd", scratch / "shared" / "link.kt")

        self.assertTrue(any("symbolic link" in e for e in self.problems(scratch)))

    def test_gitlink_rejected(self):
        scratch = self.scratch()
        nested = scratch / "shared" / "nested"
        nested.mkdir()
        git(nested, "init", "-q")
        git(nested, "-c", "user.email=a@b", "-c", "user.name=a", "commit", "-q", "--allow-empty", "-m", "x")

        self.assertTrue(any("submodule" in e for e in self.problems(scratch)))

    def test_binary_and_deletion_apply(self):
        scratch = self.scratch()
        (scratch / "shared" / "icon.png").write_bytes(bytes(range(256)))
        (scratch / BOARD_SRC).unlink()
        patch = capture_patch(scratch, self.base)
        clone = fresh_clone(self.repo, self.base, self.root / "trusted")

        sha = apply_and_commit(clone, patch, "agent: attempt 1")

        self.assertEqual(git(clone, "rev-parse", "HEAD"), sha)
        self.assertEqual((clone / "shared" / "icon.png").read_bytes(), bytes(range(256)))
        self.assertFalse((clone / BOARD_SRC).exists())

    def test_restore_commit_tree_equals_target(self):
        clone = fresh_clone(self.repo, self.base, self.root / "trusted")
        scratch = self.scratch()
        (scratch / BOARD_SRC).write_text("package proj\n// v1\n")
        first = apply_and_commit(clone, capture_patch(scratch, self.base), "v1")
        scratch2 = fresh_clone(clone, first, self.root / "scratch2")
        (scratch2 / BOARD_SRC).write_text("package proj\n// v2\n")
        second = apply_and_commit(clone, capture_patch(scratch2, first), "v2")

        restored = restore_commit(clone, first, "restore v1")

        self.assertEqual(git(clone, "rev-parse", f"{restored}^{{tree}}"), git(clone, "rev-parse", f"{first}^{{tree}}"))
        self.assertEqual(git(clone, "rev-parse", f"{restored}~1"), second)


if __name__ == "__main__":
    unittest.main()
