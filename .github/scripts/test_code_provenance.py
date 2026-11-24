import json
import os
import subprocess
import xml.etree.ElementTree as ET
import tempfile
import unittest
from datetime import date
from pathlib import Path

from code_provenance import (
    Era,
    FileReport,
    Line,
    build_tree,
    collect,
    era_for,
    is_code_line,
    percentages,
    render_badge,
    write_site,
)


def git(repo: Path, *args: str, day: str = "2025-01-01", author: str = "Dev") -> None:
    env = {
        **os.environ,
        "GIT_AUTHOR_DATE": f"{day}T12:00:00Z",
        "GIT_COMMITTER_DATE": f"{day}T12:00:00Z",
        "GIT_AUTHOR_NAME": author,
        "GIT_AUTHOR_EMAIL": "dev@example.com",
        "GIT_COMMITTER_NAME": author,
        "GIT_COMMITTER_EMAIL": "dev@example.com",
    }
    subprocess.run(["git", *args], cwd=repo, env=env, check=True, capture_output=True)


def commit(repo: Path, files: dict[str, bytes | str], day: str, author: str = "Dev") -> None:
    for path, content in files.items():
        target = repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content if isinstance(content, bytes) else content.encode())
    git(repo, "add", "-A")
    git(repo, "commit", "-q", "-m", f"change on {day}", day=day, author=author)


class RepoTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self._tmp.name)
        git(self.repo, "init", "-q")

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def report(self, path: str):
        return next(r for r in collect(self.repo) if r.path == path)


class ClassificationTest(unittest.TestCase):
    def test_era_boundaries(self):
        self.assertEqual(era_for(date(2025, 8, 8)), Era.HUMAN)
        self.assertEqual(era_for(date(2025, 8, 9)), Era.COPILOT)
        self.assertEqual(era_for(date(2026, 3, 6)), Era.COPILOT)
        self.assertEqual(era_for(date(2026, 3, 7)), Era.CLAUDE)

    def test_is_code_line(self):
        for text in ["", "   ", "// x", "/* x", " * x", "# x", "-- x"]:
            self.assertFalse(is_code_line(text), text)
        for text in ["val x = 1", "  }", "fun a() // trailing"]:
            self.assertTrue(is_code_line(text), text)


class CollectTest(RepoTest):
    def test_collect_classifies_by_author_date(self):
        commit(self.repo, {"A.kt": "val a = 1\nval b = 2\n"}, "2025-01-01")
        commit(self.repo, {"A.kt": "val a = 1\nval b = 2\nval c = 3\n"}, "2025-09-01")
        commit(self.repo, {"A.kt": "val a = 1\nval b = 2\nval c = 3\nval d = 4\n"}, "2026-04-01")

        counts = self.report("A.kt").counts

        self.assertEqual(counts, {Era.HUMAN: 2, Era.COPILOT: 1, Era.CLAUDE: 1})

    def test_collect_skips_comments_blanks_and_bots(self):
        commit(self.repo, {"B.kt": "// c\n\nval a = 1\n"}, "2025-01-01")
        commit(self.repo, {"B.kt": "// c\n\nval a = 1\nval b = 2\n"}, "2026-04-01", author="renovate[bot]")

        eras = [line.era for line in self.report("B.kt").lines]

        self.assertEqual(eras, [None, None, Era.HUMAN, None])

    def test_collect_scope(self):
        commit(
            self.repo,
            {
                "A.kt": "val a = 1\n",
                "README.md": "# hi\n",
                "gradlew": "exec java\n",
                "gradle/wrapper/x.kts": "val w = 1\n",
                "s.sql": "SELECT 1;\n",
            },
            "2025-01-01",
        )

        paths = [r.path for r in collect(self.repo)]

        self.assertEqual(paths, ["A.kt", "s.sql"])

    def test_collect_counts_uncommitted_lines_as_claude(self):
        commit(self.repo, {"A.kt": "val a = 1\n"}, "2025-01-01")
        (self.repo / "A.kt").write_text("val a = 1\nval b = 2\n")

        last = self.report("A.kt").lines[-1]

        self.assertEqual(last.era, Era.CLAUDE)

    def test_collect_survives_non_utf8(self):
        commit(self.repo, {"C.kt": b'val s = "\xff"\n'}, "2025-01-01")

        counts = self.report("C.kt").counts

        self.assertEqual(counts[Era.HUMAN], 1)

    def test_collect_honours_blame_ignore_revs(self):
        commit(self.repo, {"A.kt": "val a = 1\n"}, "2025-01-01")
        commit(self.repo, {"A.kt": "val a = 2\n"}, "2026-04-01")
        rev = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.repo, check=True, capture_output=True, text=True
        ).stdout
        commit(self.repo, {".git-blame-ignore-revs": rev}, "2026-04-02")

        counts = self.report("A.kt").counts

        self.assertEqual(counts[Era.HUMAN], 1)


class ReportTest(unittest.TestCase):
    FILES = [
        FileReport("a/x.kt", [Line(1, "x", Era.HUMAN)]),
        FileReport("a/b/y.kt", [Line(1, "y", Era.CLAUDE), Line(2, "", None)]),
    ]

    def test_percentages_sum_to_100(self):
        self.assertEqual(
            percentages({Era.HUMAN: 1, Era.COPILOT: 1, Era.CLAUDE: 1}),
            {Era.HUMAN: 34, Era.COPILOT: 33, Era.CLAUDE: 33},
        )
        self.assertEqual(
            sum(percentages({Era.HUMAN: 3830, Era.COPILOT: 1281, Era.CLAUDE: 51873}).values()), 100
        )

    def test_percentages_of_zero(self):
        result = percentages({Era.HUMAN: 0, Era.COPILOT: 0, Era.CLAUDE: 0})

        self.assertEqual(result, {Era.HUMAN: 0, Era.COPILOT: 0, Era.CLAUDE: 0})

    def test_build_tree_aggregates_folders(self):
        tree = build_tree(self.FILES)

        self.assertEqual(tree["counts"], {"human": 1, "copilot": 0, "claude": 1})
        a = tree["children"][0]
        self.assertEqual((a["name"], a["path"]), ("a", "a"))
        self.assertEqual(a["counts"], {"human": 1, "copilot": 0, "claude": 1})
        b = next(child for child in a["children"] if child["name"] == "b")
        self.assertEqual(b["children"], [{"name": "y.kt", "path": "a/b/y.kt", "file": True,
                                          "counts": {"human": 0, "copilot": 0, "claude": 1}}])

    def test_render_badge(self):
        svg = render_badge({Era.HUMAN: 7, Era.COPILOT: 2, Era.CLAUDE: 91})

        ET.fromstring(svg)
        self.assertIn("code provenance", svg)
        for text in ["7%", "2%", "91%"]:
            self.assertIn(text, svg)

    def test_render_badge_of_zero(self):
        svg = render_badge({Era.HUMAN: 0, Era.COPILOT: 0, Era.CLAUDE: 0})

        self.assertEqual(svg.count(">0%<"), 3)

    def test_write_site(self):
        with tempfile.TemporaryDirectory() as tmp:
            out, template = Path(tmp) / "site", Path(tmp) / "page.html"
            template.write_text("<script>const DATA = /*PROVENANCE_DATA*/;</script>")

            write_site(self.FILES, out, template)

            self.assertTrue((out / "badge.svg").is_file())
            rows = json.loads((out / "files/a/b/y.kt.json").read_text())
            self.assertEqual(rows, [[1, "y", "claude"], [2, "", None]])
            page = (out / "index.html").read_text()
            self.assertNotIn("/*PROVENANCE_DATA*/", page)
            data = json.loads(page.removeprefix("<script>const DATA = ").removesuffix(";</script>"))
            self.assertEqual(set(data), {"tree", "icons", "generated"})
            self.assertEqual(set(data["icons"]), {"human", "copilot", "claude"})

    def test_write_site_escapes_script_end(self):
        files = [FileReport("</script>.kt", [Line(1, "x", Era.HUMAN)])]
        with tempfile.TemporaryDirectory() as tmp:
            out, template = Path(tmp) / "site", Path(tmp) / "page.html"
            template.write_text("<script>/*PROVENANCE_DATA*/</script>")

            write_site(files, out, template)

            self.assertEqual((out / "index.html").read_text().count("</script>"), 1)


if __name__ == "__main__":
    unittest.main()
