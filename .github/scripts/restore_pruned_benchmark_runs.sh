#!/usr/bin/env bash
# Restores every run summary ever deleted from runs/ on a benchmark-data checkout, taking
# each file from the parent of the commit that deleted it. Writes files only: review,
# commit and push by hand.
#
# Usage: restore_pruned_benchmark_runs.sh <benchmark-data checkout>
set -euo pipefail

checkout="${1:?usage: $0 <benchmark-data checkout>}"
cd "$checkout"

restored=0
while IFS= read -r path; do
  [ -n "$path" ] || continue
  [ -e "$path" ] && continue
  deleting_commit="$(git log --diff-filter=D --format=%H -1 -- "$path")"
  git show "${deleting_commit}^:${path}" > "$path"
  echo "restored $path"
  restored=$((restored + 1))
done < <(git log --diff-filter=D --name-only --format= -- runs/ | sort -u)

echo "$restored run file(s) restored"
