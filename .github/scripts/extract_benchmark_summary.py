"""
Extracts a unified benchmark summary from the merged benchmark-results artifact.

The artifact contains two kinds of result files:
- Macrobenchmark `benchmarkData.json` files (an object with a `benchmarks` list,
  each entry holding `metrics` with min/median/max and `sampledMetrics` with
  percentile distributions). All macro metrics measure time, so lower is better.
- kotlinx-benchmark / JMH JSON reports (a list of objects with `benchmark`,
  `mode` and `primaryMetric`). Direction depends on the JMH mode: throughput
  means higher is better, all time-based modes mean lower is better.

Both are flattened into a single `metrics` map keyed by a stable string
(`macro/<Class>.<method>/<metric>` or `micro/<Class>.<method>`), each entry
carrying its value, unit and direction so the comparison script needs no
knowledge of either source format.

The optional `--prune-dir/--prune-days` pair deletes baseline files whose
filename date prefix (YYYY-MM-DDTHH-MM) is older than the cutoff, keeping the
baseline branch bounded.
"""

import argparse
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Dict, Optional

SCHEMA_VERSION = 1

# Percentiles kept from Macrobenchmark sampled metrics (e.g. frameDurationCpuMs).
SAMPLED_PERCENTILES = ("P50", "P90", "P99")

# JMH modes where a higher score is better; every other mode measures time.
HIGHER_IS_BETTER_MODES = {"thrpt", "Throughput"}


def short_name(fully_qualified: str) -> str:
    """Keeps only the last two dot-separated segments (Class.method)."""
    return ".".join(fully_qualified.split(".")[-2:])


def collect_macro_metrics(macro_dir: Path) -> Dict[str, dict]:
    """Parses every Macrobenchmark JSON file found under the given directory."""
    metrics: Dict[str, dict] = {}
    for path in sorted(macro_dir.rglob("*.json")):
        try:
            data = json.loads(path.read_text())
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if not isinstance(data, dict) or "benchmarks" not in data:
            continue
        for benchmark in data["benchmarks"]:
            simple_class = benchmark.get("className", "").split(".")[-1]
            bench_key = f"{simple_class}.{benchmark.get('name', '')}"
            for metric_name, stats in benchmark.get("metrics", {}).items():
                if "median" not in stats:
                    continue
                key = f"macro/{bench_key}/{metric_name}_median"
                metrics[key] = make_macro_entry(metric_name, stats["median"])
            for metric_name, stats in benchmark.get("sampledMetrics", {}).items():
                for percentile in SAMPLED_PERCENTILES:
                    if percentile not in stats:
                        continue
                    key = f"macro/{bench_key}/{metric_name}_{percentile}"
                    metrics[key] = make_macro_entry(metric_name, stats[percentile])
    return metrics


def make_macro_entry(metric_name: str, value: float) -> dict:
    unit = "ms" if metric_name.endswith("Ms") else ""
    return {"value": value, "unit": unit, "direction": "lower"}


def collect_micro_metrics(micro_dir: Path) -> Dict[str, dict]:
    """Parses every JMH JSON report found under the given directory."""
    metrics: Dict[str, dict] = {}
    for path in sorted(micro_dir.rglob("*.json")):
        try:
            data = json.loads(path.read_text())
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if not isinstance(data, list):
            continue
        for entry in data:
            if not isinstance(entry, dict) or "benchmark" not in entry:
                continue
            primary = entry.get("primaryMetric", {})
            if "score" not in primary:
                continue
            mode = entry.get("mode", "")
            direction = "higher" if mode in HIGHER_IS_BETTER_MODES else "lower"
            key = f"micro/{short_name(entry['benchmark'])}"
            metrics[key] = {
                "value": primary["score"],
                "error": primary.get("scoreError"),
                "unit": primary.get("scoreUnit", ""),
                "direction": direction,
            }
    return metrics


def prune_old_runs(runs_dir: Path, max_age_days: int, now: datetime) -> None:
    """Deletes run files whose filename date prefix is older than the cutoff."""
    cutoff = now - timedelta(days=max_age_days)
    for path in runs_dir.glob("*.json"):
        timestamp = parse_filename_date(path.name)
        if timestamp is not None and timestamp < cutoff:
            print(f"Pruning baseline entry older than {max_age_days} days: {path.name}")
            path.unlink()


def parse_filename_date(filename: str) -> Optional[datetime]:
    try:
        return datetime.strptime(filename[:16], "%Y-%m-%dT%H-%M").replace(
            tzinfo=timezone.utc
        )
    except ValueError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--macro-dir", type=Path, required=True)
    parser.add_argument("--micro-dir", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--date", required=True, help="ISO 8601 UTC run date")
    parser.add_argument("--runner", default="unknown")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--prune-dir", type=Path, default=None)
    parser.add_argument("--prune-days", type=int, default=60)
    args = parser.parse_args()

    macro = collect_macro_metrics(args.macro_dir) if args.macro_dir.is_dir() else {}
    micro = collect_micro_metrics(args.micro_dir) if args.micro_dir.is_dir() else {}
    if not macro and not micro:
        print("No benchmark results found in either directory.", file=sys.stderr)
        return 1

    summary = {
        "schema": SCHEMA_VERSION,
        "commit": args.commit,
        "date": args.date,
        "runner": args.runner,
        "metrics": {**macro, **micro},
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"Wrote {len(macro)} macro and {len(micro)} micro metrics to {args.output}")

    if args.prune_dir is not None and args.prune_dir.is_dir():
        now = datetime.fromisoformat(args.date.replace("Z", "+00:00"))
        prune_old_runs(args.prune_dir, args.prune_days, now)

    return 0


if __name__ == "__main__":
    sys.exit(main())
