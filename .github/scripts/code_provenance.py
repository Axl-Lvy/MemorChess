"""Builds the code provenance report: the share of code lines written in the Human, Copilot and Claude eras."""

import json
import re
import subprocess
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import date, datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Any

COPILOT_START = date(2025, 8, 9)
CLAUDE_START = date(2026, 3, 7)

SOURCE_SUFFIXES = {".kt", ".kts", ".swift", ".js", ".py", ".sh", ".sql"}
COMMENT_PREFIXES = ("//", "/*", "*", "#", "--")
IGNORE_REVS_FILE = ".git-blame-ignore-revs"
DATA_PLACEHOLDER = "/*PROVENANCE_DATA*/"
ROOT_NAME = "MemorChess"
_HEADER = re.compile(rb"^[0-9a-f]{40} \d+ (\d+)")


class Era(str, Enum):
    """Who wrote a line, judged by the date of the commit that last touched it."""

    HUMAN = "human"
    COPILOT = "copilot"
    CLAUDE = "claude"


# Copilot and Claude marks from Simple Icons 15.22.0 (CC0 path data; the marks are their owners' trademarks).
ICONS = {
    Era.HUMAN: '<path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-5.3 0-10 2.7-10 6v2h20v-2c0-3.3-4.7-6-10-6z"/>',
    Era.COPILOT: '<path d="M23.922 16.997C23.061 18.492 18.063 22.02 12 22.02 5.937 22.02.939 18.492.078 16.997A.641.641 0 0 1 0 16.741v-2.869a.883.883 0 0 1 .053-.22c.372-.935 1.347-2.292 2.605-2.656.167-.429.414-1.055.644-1.517a10.098 10.098 0 0 1-.052-1.086c0-1.331.282-2.499 1.132-3.368.397-.406.89-.717 1.474-.952C7.255 2.937 9.248 1.98 11.978 1.98c2.731 0 4.767.957 6.166 2.093.584.235 1.077.546 1.474.952.85.869 1.132 2.037 1.132 3.368 0 .368-.014.733-.052 1.086.23.462.477 1.088.644 1.517 1.258.364 2.233 1.721 2.605 2.656a.841.841 0 0 1 .053.22v2.869a.641.641 0 0 1-.078.256Zm-11.75-5.992h-.344a4.359 4.359 0 0 1-.355.508c-.77.947-1.918 1.492-3.508 1.492-1.725 0-2.989-.359-3.782-1.259a2.137 2.137 0 0 1-.085-.104L4 11.746v6.585c1.435.779 4.514 2.179 8 2.179 3.486 0 6.565-1.4 8-2.179v-6.585l-.098-.104s-.033.045-.085.104c-.793.9-2.057 1.259-3.782 1.259-1.59 0-2.738-.545-3.508-1.492a4.359 4.359 0 0 1-.355-.508Zm2.328 3.25c.549 0 1 .451 1 1v2c0 .549-.451 1-1 1-.549 0-1-.451-1-1v-2c0-.549.451-1 1-1Zm-5 0c.549 0 1 .451 1 1v2c0 .549-.451 1-1 1-.549 0-1-.451-1-1v-2c0-.549.451-1 1-1Zm3.313-6.185c.136 1.057.403 1.913.878 2.497.442.544 1.134.938 2.344.938 1.573 0 2.292-.337 2.657-.751.384-.435.558-1.15.558-2.361 0-1.14-.243-1.847-.705-2.319-.477-.488-1.319-.862-2.824-1.025-1.487-.161-2.192.138-2.533.529-.269.307-.437.808-.438 1.578v.021c0 .265.021.562.063.893Zm-1.626 0c.042-.331.063-.628.063-.894v-.02c-.001-.77-.169-1.271-.438-1.578-.341-.391-1.046-.69-2.533-.529-1.505.163-2.347.537-2.824 1.025-.462.472-.705 1.179-.705 2.319 0 1.211.175 1.926.558 2.361.365.414 1.084.751 2.657.751 1.21 0 1.902-.394 2.344-.938.475-.584.742-1.44.878-2.497Z"/>',
    Era.CLAUDE: '<path d="m4.7144 15.9555 4.7174-2.6471.079-.2307-.079-.1275h-.2307l-.7893-.0486-2.6956-.0729-2.3375-.0971-2.2646-.1214-.5707-.1215-.5343-.7042.0546-.3522.4797-.3218.686.0608 1.5179.1032 2.2767.1578 1.6514.0972 2.4468.255h.3886l.0546-.1579-.1336-.0971-.1032-.0972L6.973 9.8356l-2.55-1.6879-1.3356-.9714-.7225-.4918-.3643-.4614-.1578-1.0078.6557-.7225.8803.0607.2246.0607.8925.686 1.9064 1.4754 2.4893 1.8336.3643.3035.1457-.1032.0182-.0728-.164-.2733-1.3539-2.4467-1.445-2.4893-.6435-1.032-.17-.6194c-.0607-.255-.1032-.4674-.1032-.7285L6.287.1335 6.6997 0l.9957.1336.419.3642.6192 1.4147 1.0018 2.2282 1.5543 3.0296.4553.8985.2429.8318.091.255h.1579v-.1457l.1275-1.706.2368-2.0947.2307-2.6957.0789-.7589.3764-.9107.7468-.4918.5828.2793.4797.686-.0668.4433-.2853 1.8517-.5586 2.9021-.3643 1.9429h.2125l.2429-.2429.9835-1.3053 1.6514-2.0643.7286-.8196.85-.9046.5464-.4311h1.0321l.759 1.1293-.34 1.1657-1.0625 1.3478-.8804 1.1414-1.2628 1.7-.7893 1.36.0729.1093.1882-.0183 2.8535-.607 1.5421-.2794 1.8396-.3157.8318.3886.091.3946-.3278.8075-1.967.4857-2.3072.4614-3.4364.8136-.0425.0304.0486.0607 1.5482.1457.6618.0364h1.621l3.0175.2247.7892.522.4736.6376-.079.4857-1.2142.6193-1.6393-.3886-3.825-.9107-1.3113-.3279h-.1822v.1093l1.0929 1.0686 2.0035 1.8092 2.5075 2.3314.1275.5768-.3218.4554-.34-.0486-2.2039-1.6575-.85-.7468-1.9246-1.621h-.1275v.17l.4432.6496 2.3436 3.5214.1214 1.0807-.17.3521-.6071.2125-.6679-.1214-1.3721-1.9246L14.38 17.959l-1.1414-1.9428-.1397.079-.674 7.2552-.3156.3703-.7286.2793-.6071-.4614-.3218-.7468.3218-1.4753.3886-1.9246.3157-1.53.2853-1.9004.17-.6314-.0121-.0425-.1397.0182-1.4328 1.9672-2.1796 2.9446-1.7243 1.8456-.4128.164-.7164-.3704.0667-.6618.4008-.5889 2.386-3.0357 1.4389-1.882.929-1.0868-.0062-.1579h-.0546l-6.3385 4.1164-1.1293.1457-.4857-.4554.0608-.7467.2307-.2429 1.9064-1.3114Z"/>',
}
COLOURS = {Era.HUMAN: "#2da44e", Era.COPILOT: "#4f7df3", Era.CLAUDE: "#d97757"}

# Verdana 11px advance widths, as used by shields.io, for the characters a badge holds.
_CHAR_WIDTHS = {
    **dict.fromkeys("0123456789", 7.0),
    "%": 11.9,
    " ": 3.9,
    "a": 6.7,
    "c": 5.8,
    "d": 6.9,
    "e": 6.6,
    "n": 7.0,
    "o": 6.7,
    "p": 6.9,
    "r": 4.7,
    "v": 6.3,
}
_FONT = 'fill="#fff" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11"'


@dataclass(frozen=True)
class Line:
    """One source line; `era` is None when the line is not counted."""

    number: int
    text: str
    era: Era | None


@dataclass(frozen=True)
class FileReport:
    """The classified lines of one source file."""

    path: str
    lines: list[Line]

    @property
    def counts(self) -> dict[Era, int]:
        """Counted lines per era."""
        tally = Counter(line.era for line in self.lines if line.era is not None)
        return {era: tally[era] for era in Era}


def era_for(day: date) -> Era:
    """Returns the era a commit made on `day` belongs to."""
    if day < COPILOT_START:
        return Era.HUMAN
    if day < CLAUDE_START:
        return Era.COPILOT
    return Era.CLAUDE


def is_code_line(text: str) -> bool:
    """Tells whether a line holds code rather than nothing or only a comment."""
    stripped = text.strip()
    return bool(stripped) and not stripped.startswith(COMMENT_PREFIXES)


def collect(repo: Path) -> list[FileReport]:
    """Blames every tracked source file of `repo`, sorted by path. Runs one `git blame` per file."""
    with ThreadPoolExecutor(max_workers=8) as pool:
        return list(pool.map(lambda path: _blame(repo, path), _source_files(repo)))


def _source_files(repo: Path) -> list[str]:
    listing = subprocess.run(["git", "ls-files", "-z"], cwd=repo, check=True, capture_output=True).stdout
    paths = (p for p in listing.decode().split("\0") if p)
    return sorted(
        p
        for p in paths
        if Path(p).suffix in SOURCE_SUFFIXES
        and not p.startswith("gradle/wrapper/")
        and (repo / p).is_file()
        and not (repo / p).is_symlink()
    )


def _blame(repo: Path, path: str) -> FileReport:
    command = ["git", "blame", "-w", "-M", "-C", "--line-porcelain"]
    if (repo / IGNORE_REVS_FILE).is_file():
        command += ["--ignore-revs-file", IGNORE_REVS_FILE]
    output = subprocess.run([*command, "--", path], cwd=repo, check=True, capture_output=True).stdout
    lines = []
    number, author, day = 0, "", date.min
    for raw in output.split(b"\n"):
        if raw.startswith(b"\t"):
            text = raw[1:].decode("utf-8", errors="replace")
            counted = is_code_line(text) and "[bot]" not in author
            lines.append(Line(number, text, era_for(day) if counted else None))
        elif header := _HEADER.match(raw):
            number = int(header.group(1))
        elif raw.startswith(b"author "):
            author = raw[7:].decode("utf-8", errors="replace")
        elif raw.startswith(b"author-time "):
            day = datetime.fromtimestamp(int(raw[12:]), timezone.utc).date()
    return FileReport(path, lines)


def percentages(counts: dict[Era, int]) -> dict[Era, int]:
    """Whole percentages per era that sum to 100, or all 0 when nothing is counted."""
    total = sum(counts.values())
    if total == 0:
        return {era: 0 for era in Era}
    exact = {era: 100 * counts[era] / total for era in Era}
    result = {era: int(value) for era, value in exact.items()}
    by_remainder = sorted(Era, key=lambda era: exact[era] - result[era], reverse=True)
    for era in by_remainder[: 100 - sum(result.values())]:
        result[era] += 1
    return result


def build_tree(files: list[FileReport]) -> dict[str, Any]:
    """Nests the files into folders, each node carrying its counts and percentages per era.

    A chain of folders that each hold a single folder collapses into one node named `a/b/c`.
    A file node's `id` is its index in `files`, which names its `files/<id>.json` in the site.
    """
    root: dict[str, Any] = {"name": ROOT_NAME, "path": "", "children": []}
    folders = {"": root}
    for index, report in enumerate(files):
        parts = report.path.split("/")
        parent = root
        for depth in range(1, len(parts)):
            path = "/".join(parts[:depth])
            if path not in folders:
                folders[path] = {"name": parts[depth - 1], "path": path, "children": []}
                parent["children"].append(folders[path])
            parent = folders[path]
        counts = {era.value: n for era, n in report.counts.items()}
        node = {"name": parts[-1], "path": report.path, "file": True, "id": index, "counts": counts}
        parent["children"].append(node)
    root["children"] = [_compact(child) for child in root["children"]]
    _sum_counts(root)
    return root


def _compact(node: dict[str, Any]) -> dict[str, Any]:
    if "children" not in node:
        return node
    while len(node["children"]) == 1 and "children" in node["children"][0]:
        only = node["children"][0]
        node = {**only, "name": f"{node['name']}/{only['name']}"}
    node["children"] = [_compact(child) for child in node["children"]]
    return node


def _sum_counts(node: dict[str, Any]) -> dict[str, int]:
    if "children" in node:
        node["counts"] = {era.value: 0 for era in Era}
        for child in node["children"]:
            for era, n in _sum_counts(child).items():
                node["counts"][era] += n
    shares = percentages({era: node["counts"][era.value] for era in Era})
    node["pct"] = {era.value: percent for era, percent in shares.items()}
    return node["counts"]


def render_badge(counts: dict[Era, int]) -> str:
    """Draws a flat, shields.io-style SVG badge with each era's icon and percentage."""
    label = "code provenance"
    label_width = _text_width(label) + 12
    x = label_width
    segments = []
    for era, percent in percentages(counts).items():
        text = f"{percent}%"
        width = 5 + 12 + 4 + _text_width(text) + 6
        segments.append(
            f'<rect x="{x:.1f}" width="{width:.1f}" height="20" fill="{COLOURS[era]}"/>'
            f'<svg x="{x + 5:.1f}" y="4" width="12" height="12" viewBox="0 0 24 24" fill="#fff">{ICONS[era]}</svg>'
            f'<text x="{x + 21:.1f}" y="14" textLength="{_text_width(text):.1f}" {_FONT}>{text}</text>'
        )
        x += width
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{x:.1f}" height="20" role="img" aria-label="{label}">'
        f"<title>{label}</title>"
        '<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/>'
        '<stop offset="1" stop-opacity=".1"/></linearGradient>'
        f'<clipPath id="r"><rect width="{x:.1f}" height="20" rx="3" fill="#fff"/></clipPath>'
        f'<g clip-path="url(#r)"><rect width="{label_width:.1f}" height="20" fill="#555"/>{"".join(segments)}'
        f'<rect width="{x:.1f}" height="20" fill="url(#s)"/></g>'
        f'<text x="6" y="14" textLength="{_text_width(label):.1f}" {_FONT}>{label}</text>'
        "</svg>"
    )


def _text_width(text: str) -> float:
    return sum(_CHAR_WIDTHS.get(char, 7.0) for char in text)


def write_site(files: list[FileReport], out: Path, template: Path) -> None:
    """Writes `index.html`, one `files/<id>.json` per source file and `badge.svg` into `out`."""
    (out / "files").mkdir(parents=True, exist_ok=True)
    for index, report in enumerate(files):
        target = out / "files" / f"{index}.json"
        rows = [[line.number, line.text, line.era.value if line.era else None] for line in report.lines]
        target.write_text(_to_json(rows), encoding="utf-8")
    tree = build_tree(files)
    data = {"tree": tree, "icons": {era.value: icon for era, icon in ICONS.items()}}
    page = template.read_text(encoding="utf-8").replace(DATA_PLACEHOLDER, _to_json(data).replace("</", "<\\/"))
    out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(page, encoding="utf-8")
    totals = {era: tree["counts"][era.value] for era in Era}
    (out / "badge.svg").write_text(render_badge(totals), encoding="utf-8")


def _to_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def main(argv: list[str]) -> int:
    """Generates the site for the repository in the working directory into `argv[1]`."""
    if len(argv) != 2:
        print(f"usage: {argv[0]} <out-dir>", file=sys.stderr)
        return 2
    write_site(collect(Path.cwd()), Path(argv[1]), Path(__file__).with_name("code_provenance_page.html"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
