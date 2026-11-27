import json
import unittest

from agentrun.brief import parse_brief, test_key
from agentrun.checks import CheckItem, CheckReport
from agentrun.review import VerdictError, approved, parse_verdict, rank_feedback
from tests.helpers import THEME_SRC, brief_dict, repo_config

GREEN = CheckReport(
    [CheckItem("tests", True, "")],
    frozenset({test_key("TestBoard", "draws dots on legal squares")}),
    frozenset(),
)


def verdict_json(criteria=None, blocking=None) -> str:
    return json.dumps({
        "criteria": criteria if criteria is not None else [{"id": "AC1", "verdict": "pass", "reason": "ok"}],
        "blocking": blocking or [],
    })


class ReviewTest(unittest.TestCase):
    def setUp(self):
        self.brief = parse_brief(json.dumps(brief_dict()))
        self.cfg = repo_config()

    def test_missing_criterion_verdict_rejected(self):
        with self.assertRaises(VerdictError) as ctx:
            parse_verdict(verdict_json(criteria=[]), self.brief)

        self.assertIn("AC1", str(ctx.exception))

    def test_bad_verdict_value_rejected(self):
        with self.assertRaises(VerdictError):
            parse_verdict(verdict_json(criteria=[{"id": "AC1", "verdict": "maybe", "reason": ""}]), self.brief)

    def test_claim_contradicting_green_test_dropped(self):
        verdict = parse_verdict(verdict_json(blocking=[
            {"file": None, "line": None, "text": "drawsDotsOnLegalSquares will fail because tiles are mirrored", "patch": None},
        ]), self.brief)

        kept, dropped = rank_feedback(verdict, GREEN, self.brief, self.cfg)

        self.assertEqual(kept, [])
        self.assertEqual(len(dropped), 1)
        self.assertIn("passed", dropped[0])

    def test_finding_on_not_to_touch_dropped(self):
        verdict = parse_verdict(verdict_json(blocking=[
            {"file": THEME_SRC, "line": 3, "text": "raise the contrast here", "patch": None},
        ]), self.brief)

        kept, dropped = rank_feedback(verdict, GREEN, self.brief, self.cfg)

        self.assertEqual(kept, [])
        self.assertIn(THEME_SRC, dropped[0])

    def test_fail_with_green_tests_kept_with_note(self):
        verdict = parse_verdict(verdict_json(criteria=[
            {"id": "AC1", "verdict": "fail", "reason": "dots drawn on mirrored squares"},
        ]), self.brief)

        kept, dropped = rank_feedback(verdict, GREEN, self.brief, self.cfg)

        self.assertEqual(dropped, [])
        self.assertEqual(len(kept), 1)
        self.assertIn("mirrored", kept[0])
        self.assertIn("tests pass", kept[0])

    def test_genuine_blocking_finding_kept_with_patch(self):
        verdict = parse_verdict(verdict_json(blocking=[
            {"file": "shared/src/commonMain/kotlin/proj/Board.kt", "line": 4, "text": "hints play moves on the live engine", "patch": "- engine.play(m)\n+ engine.copy().play(m)"},
        ]), self.brief)

        kept, _ = rank_feedback(verdict, GREEN, self.brief, self.cfg)

        self.assertEqual(len(kept), 1)
        self.assertIn("Board.kt:4", kept[0])
        self.assertIn("engine.copy()", kept[0])

    def test_approved_only_when_all_pass(self):
        ok = parse_verdict(verdict_json(), self.brief)
        failing = parse_verdict(verdict_json(criteria=[{"id": "AC1", "verdict": "fail", "reason": "x"}]), self.brief)
        blocked = parse_verdict(verdict_json(blocking=[{"file": None, "line": None, "text": "real bug", "patch": None}]), self.brief)

        self.assertTrue(approved(ok))
        self.assertFalse(approved(failing))
        self.assertFalse(approved(blocked))


if __name__ == "__main__":
    unittest.main()
