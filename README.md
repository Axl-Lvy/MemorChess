# Benchmark baseline data

Machine-written branch. The `record-baseline` job of the Performance benchmarks
workflow appends one JSON summary per master benchmark run under `runs/`,
named `<YYYY-MM-DDTHH-MM>_<short sha>.json`. Entries older than 60 days are
pruned automatically.

PR benchmark runs (`benchmark-pr.yml`) read the most recent entries here to
compute their comparison baseline. Do not edit this branch by hand.
