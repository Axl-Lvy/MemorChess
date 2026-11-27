import json
import os
import tempfile
import unittest
from pathlib import Path

from agentrun.brief import parse_brief
from agentrun.workspace import (
    apply_and_commit,
    capture_patch,
    check_patch,
    fresh_clone,
    patch_paths,
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

        errors = check_patch(capture_patch(scratch, self.base), self.cfg, self.brief)

        self.assertTrue(any(".github/workflows/x.yml" in e for e in errors), errors)

    def test_rename_into_denied_path_rejected(self):
        scratch = self.scratch()
        (scratch / ".github").mkdir()
        os.rename(scratch / BOARD_SRC, scratch / ".github" / "Board.kt")

        patch = capture_patch(scratch, self.base)
        errors = check_patch(patch, self.cfg, self.brief)

        self.assertIn((BOARD_SRC, ".github/Board.kt"), patch_paths(patch))
        self.assertTrue(any(".github/Board.kt" in e for e in errors), errors)

    def test_build_file_needs_brief_permission(self):
        scratch = self.scratch()
        (scratch / "shared" / "build.gradle.kts").write_text("// changed\n")
        patch = capture_patch(scratch, self.base)

        self.assertTrue(any("build file" in e for e in check_patch(patch, self.cfg, self.brief)))
        allowed = parse_brief(json.dumps({**brief_dict(), "build_files_allowed": True}))
        self.assertEqual(check_patch(patch, self.cfg, allowed), [])

    def test_stray_file_rejected(self):
        scratch = self.scratch()
        (scratch / "shared" / "Board.kt.orig").write_text("x")

        errors = check_patch(capture_patch(scratch, self.base), self.cfg, self.brief)

        self.assertTrue(any("Board.kt.orig" in e for e in errors), errors)

    def test_gitattributes_and_symlink_rejected(self):
        scratch = self.scratch()
        (scratch / "shared" / ".gitattributes").write_text("* filter=evil\n")
        os.symlink("/etc/passwd", scratch / "shared" / "link.kt")

        errors = check_patch(capture_patch(scratch, self.base), self.cfg, self.brief)

        self.assertTrue(any("shared/.gitattributes" in e for e in errors), errors)
        self.assertTrue(any("symbolic link" in e for e in errors), errors)

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
