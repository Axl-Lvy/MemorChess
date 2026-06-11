"""
Compares a PR benchmark summary against the master baseline window and renders
the result as a markdown comment.

The baseline is the N most recent summary files (produced by
extract_benchmark_summary.py) on the benchmark-data branch. For every metric in
the PR summary the script computes the baseline mean and sample standard
deviation, then classifies the PR value:

- UI macrobenchmarks run on an emulator and are noisy, so the threshold is
  max(2 sigma, 1% of the mean); the 1% floor avoids flagging everything when
  all baseline values happen to be identical (sigma = 0).
- JVM microbenchmarks are far tighter, so a plain 2 sigma band would flag
  ordinary runner variance; the threshold is max(2 sigma, 3% of the mean).

The verdict is advisory only: the comment never blocks a merge. The first line
of the output is an HTML marker so the calling workflow can find and update the
existing comment instead of stacking new ones.
"""

import argparse
import json
import statistics
import sys
from pathlib import Path
from typing import Dict, List, Optional

COMMENT_MARKER = "<!-- benchmark-comparison -->"

# Minimum baseline entries containing a metric before a verdict is attempted.
MIN_BASELINE_SAMPLES = 3

SIGMA_MULTIPLIER = 2.0
MACRO_RELATIVE_FLOOR = 0.01
MICRO_RELATIVE_FLOOR = 0.03

VERDICT_WITHIN = "✅ within noise"
VERDICT_REGRESSION = "⚠️ regression"
VERDICT_IMPROVEMENT = "🟢 improvement"
VERDICT_NO_BASELINE = "➖ no baseline"


def load_baselines(baseline_dir: Path, window: int) -> List[dict]:
    """Loads the most recent baseline summaries, newest first by filename."""
    baselines = []
    for path in sorted(baseline_dir.glob("*.json"), reverse=True)[:window]:
        try:
            data = json.loads(path.read_text())
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if isinstance(data, dict) and "metrics" in data:
            baselines.append(data)
    return baselines


def format_value(value: float) -> str:
    """Formats with enough precision for small ms values and large ops/s ones."""
    if value == 0:
        return "0"
    if abs(value) >= 1000:
        return f"{value:,.0f}"
    if abs(value) >= 1:
        return f"{value:.2f}"
    return f"{value:.4f}"


def relative_floor(key: str) -> float:
    return MACRO_RELATIVE_FLOOR if key.startswith("macro/") else MICRO_RELATIVE_FLOOR


def classify(key: str, entry: dict, values: List[float]) -> dict:
    """Builds one comparison row for a metric present in the PR summary."""
    pr_value = entry["value"]
    if len(values) < MIN_BASELINE_SAMPLES:
        return {
            "pr": format_value(pr_value),
            "baseline": f"n={len(values)}",
            "delta": "n/a",
            "verdict": VERDICT_NO_BASELINE,
        }

    mean = statistics.mean(values)
    sigma = statistics.stdev(values)
    threshold = max(SIGMA_MULTIPLIER * sigma, relative_floor(key) * abs(mean))
    delta = pr_value - mean

    if mean != 0:
        delta_text = f"{delta / abs(mean) * 100:+.1f}%"
    else:
        delta_text = f"{delta:+g} (abs)"

    if abs(delta) <= threshold:
        verdict = VERDICT_WITHIN
    else:
        worse = delta > 0 if entry["direction"] == "lower" else delta < 0
        verdict = VERDICT_REGRESSION if worse else VERDICT_IMPROVEMENT

    return {
        "pr": format_value(pr_value),
        "baseline": f"{format_value(mean)} ±{format_value(sigma)} (n={len(values)})",
        "delta": delta_text,
        "verdict": verdict,
    }


def render_section(
    title: str,
    note: str,
    prefix: str,
    pr_metrics: Dict[str, dict],
    baselines: List[dict],
) -> List[str]:
    keys = sorted(k for k in pr_metrics if k.startswith(prefix))
    if not keys:
        return []
    lines = [f"### {title}", "", note, ""]
    lines.append("| Benchmark | Metric | PR | master | Δ | Verdict |")
    lines.append("|---|---|---|---|---|---|")
    for key in keys:
        entry = pr_metrics[key]
        values = [
            b["metrics"][key]["value"] for b in baselines if key in b["metrics"]
        ]
        row = classify(key, entry, values)
        benchmark, metric = split_key(key)
        unit = f" {entry['unit']}" if entry.get("unit") else ""
        lines.append(
            f"| {benchmark} | {metric}{unit} | {row['pr']} | {row['baseline']} "
            f"| {row['delta']} | {row['verdict']} |"
        )
    lines.append("")
    return lines


def split_key(key: str) -> tuple:
    """Splits a metric key into its benchmark and metric display columns."""
    parts = key.split("/")
    if len(parts) == 3:
        return parts[1], parts[2]
    return parts[1], "score"


def render_comment(
    pr_summary: dict, baselines: List[dict], run_url: Optional[str]
) -> str:
    lines = [COMMENT_MARKER, "## Benchmark comparison", ""]
    if not baselines:
        lines.append(
            "No master baseline data is available yet, so this run only reports "
            "the PR's own numbers. Baselines accumulate from the daily master "
            "benchmark run."
        )
        lines.append("")
    lines += render_section(
        "UI benchmarks (emulator)",
        "Emulator numbers are noisy; only shifts beyond max(2σ, 1%) of the "
        "master baseline are flagged.",
        "macro/",
        pr_summary["metrics"],
        baselines,
    )
    lines += render_section(
        "Core microbenchmarks (JVM)",
        "Shifts beyond max(2σ, 3%) of the master baseline are flagged. "
        "Throughput scores: higher is better.",
        "micro/",
        pr_summary["metrics"],
        baselines,
    )
    lines.append(
        f"_Advisory only, never blocking. Baseline: {len(baselines)} most recent "
        "master runs from the `benchmark-data` branch._"
    )
    if run_url:
        lines.append(f"_Raw results: [workflow run]({run_url})._")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pr-summary", type=Path, required=True)
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--window", type=int, default=10)
    parser.add_argument("--run-url", default=None)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    pr_summary = json.loads(args.pr_summary.read_text())
    baselines = (
        load_baselines(args.baseline_dir, args.window)
        if args.baseline_dir.is_dir()
        else []
    )

    args.output.write_text(render_comment(pr_summary, baselines, args.run_url))
    print(f"Wrote comparison against {len(baselines)} baseline runs to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
