#!/bin/bash
# SessionStart hook: install the plugins this repo expects.
#
# `.claude/settings.json` only *enables* a plugin; it never fetches one. On
# Claude Code on the web the container is rebuilt from scratch for every
# session, so without this hook `superpowers` is enabled but absent, and the
# skills CLAUDE.md refers to (for example `using-git-worktrees`) do not exist.
#
# Local checkouts keep whatever the developer installed themselves, so this
# only runs in remote sessions.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

MARKETPLACE="claude-plugins-official"
PLUGIN="superpowers@${MARKETPLACE}"

# Remote containers ship the official marketplace pre-registered; add it if a
# future image stops doing so. Failure here is not fatal: the install below
# reports the real problem.
if ! claude plugin marketplace list 2>/dev/null | grep -q "${MARKETPLACE}"; then
  claude plugin marketplace add "anthropics/${MARKETPLACE}" || true
fi

# Idempotent: a second run exits 0 with "already installed".
claude plugin install --yes "${PLUGIN}"
