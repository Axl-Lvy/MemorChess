import json
import tempfile
import unittest
from pathlib import Path

from agentrun.brief import BriefError, parse_brief, test_key, validate_brief
from tests.helpers import BOARD_TEST, THEME_SRC, brief_dict, make_repo, repo_config


class BriefTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.repo = make_repo(Path(self._tmp.name))
        self.cfg = repo_config()

    def tearDown(self):
        self._tmp.cleanup()

    def errors(self, data: dict) -> list[str]:
        return validate_brief(parse_brief(json.dumps(data)), self.repo, self.cfg)

    def test_valid_brief_passes(self):
        self.assertEqual(self.errors(brief_dict()), [])

    def test_missing_field_reported(self):
        data = brief_dict()
        del data["goal"]

        with self.assertRaises(BriefError) as ctx:
            parse_brief(json.dumps(data))

        self.assertTrue(any("goal" in e for e in ctx.exception.errors))

    def test_test_file_in_not_to_touch_rejected(self):
        data = brief_dict()
        data["files_not_to_touch"].append({"path": BOARD_TEST, "reason": "x", "new": False})

        errors = self.errors(data)

        self.assertTrue(any(BOARD_TEST in e and "not to touch" in e for e in errors), errors)

    def test_test_file_must_be_in_touch_list(self):
        data = brief_dict()
        data["files_to_touch"] = [f for f in data["files_to_touch"] if f["path"] != BOARD_TEST]

        errors = self.errors(data)

        self.assertTrue(any(BOARD_TEST in e for e in errors), errors)

    def test_missing_path_rejected_unless_new(self):
        data = brief_dict()
        data["files_to_touch"].append({"path": "shared/src/Nope.kt", "reason": "x", "new": False})
        data["files_to_touch"].append({"path": "nowhere/dir/New.kt", "reason": "x", "new": True})

        errors = self.errors(data)

        self.assertTrue(any("shared/src/Nope.kt" in e for e in errors), errors)
        self.assertTrue(any("nowhere/dir/New.kt" in e for e in errors), errors)

    def test_missing_helper_symbol_rejected(self):
        data = brief_dict()
        data["helpers_to_reuse"] = [{"symbol": "squareTo", "path": data["helpers_to_reuse"][0]["path"]}]

        errors = self.errors(data)

        self.assertTrue(any("squareTo" in e for e in errors), errors)

    def test_empty_expected_rejected(self):
        data = brief_dict()
        data["tests"][0]["expected"] = "  "

        errors = self.errors(data)

        self.assertTrue(any("expected" in e for e in errors), errors)

    def test_skip_tests_title_rejected(self):
        data = brief_dict()
        data["title"] = "feat(ui): hints [Skip-Tests]"

        errors = self.errors(data)

        self.assertTrue(any("skip-tests" in e.lower() for e in errors), errors)

    def test_non_conventional_title_rejected(self):
        data = brief_dict()
        data["title"] = "Add hints"

        self.assertTrue(any("title" in e for e in self.errors(data)))

    def test_build_file_needs_permission(self):
        data = brief_dict()
        data["files_to_touch"].append({"path": "shared/build.gradle.kts", "reason": "x", "new": False})

        self.assertTrue(any("build file" in e for e in self.errors(data)))
        data["build_files_allowed"] = True
        self.assertEqual(self.errors(data), [])

    def test_deny_listed_path_rejected(self):
        data = brief_dict()
        data["files_to_touch"].append({"path": ".github/workflows/x.yml", "reason": "x", "new": True})

        self.assertTrue(any(".github/workflows/x.yml" in e for e in self.errors(data)))

    def test_not_ready_needs_questions(self):
        data = brief_dict()
        data["ready"] = False

        self.assertTrue(any("question" in e for e in self.errors(data)))
        data["questions"] = ["Which theme?"]
        data["files_to_touch"] = []
        self.assertEqual(self.errors(data), [])

    def test_criterion_unknown_test_rejected(self):
        data = brief_dict()
        data["acceptance_criteria"][0]["tests"] = ["TestBoard.nope"]

        self.assertTrue(any("TestBoard.nope" in e for e in self.errors(data)))

    def test_touch_and_not_touch_overlap_rejected(self):
        data = brief_dict()
        data["files_to_touch"].append({"path": THEME_SRC, "reason": "x", "new": False})

        self.assertTrue(any(THEME_SRC in e for e in self.errors(data)))

    def test_key_normalises(self):
        self.assertEqual(
            test_key("proj.x.TestBoard", "`draws dots on legal squares`"),
            test_key("TestBoard", "drawsDotsOnLegalSquares"),
        )
        self.assertEqual(
            test_key("TestBoard", "draws_dots_on_legal_squares()"),
            test_key("TestBoard", "draws dots on legal squares"),
        )
        self.assertNotEqual(test_key("TestBoard", "a"), test_key("TestOther", "a"))


if __name__ == "__main__":
    unittest.main()
