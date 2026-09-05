---
name: shipping-issue-stack
description: Use when the user wants to batch-implement several GitHub issues as a linear stack of reviewed, CI-clean pull requests, or asks to run issues through a spec/review/implement/harmonize pipeline.
---

# Shipping an issue stack

## Overview

Turns a batch of related GitHub issues into a stack of small, reviewed PRs. Two
phases: brainstorm each issue with the human partner in this session, then run
a multi-agent Workflow (spec → spec review → TDD implement → code review →
harmonize into a stack → CI/Sonar babysit). Requires `ultracode` on or the
user explicitly asking for a workflow (see the Workflow tool's own opt-in
rule) — this skill does not itself authorize orchestration.

**REQUIRED BACKGROUND:** superpowers:brainstorming (phase 0),
superpowers:test-driven-development (the implement step), workflow-authoring
(script mechanics), gh-stack (stack maintenance).

## When to use

- The user names 2+ GitHub issues to implement together as a reviewed PR stack.
- The user asks to "spec these out and ship them", "run the issues through
  the pipeline", or similarly wants coordinated multi-agent delivery instead
  of one issue at a time.
- **Not** for a single ad-hoc change — use normal TDD directly.
- **Not** for issues that aren't independently reviewable as separate PRs —
  decompose first (see brainstorming's scope-check).

## Phase 0 — Brainstorm (this session, with the human partner)

For **each** issue in scope, invoke superpowers:brainstorming to pin down the
concrete plan: exact files, exact token/API/naming decisions, anything the
issue text leaves open. Do this even when the issue already lists acceptance
criteria — issues in this repo deliberately leave some judgment calls for the
implementer (precedent: #277/#278 left the mono-font fate, the unknown-state
palette token, and a corner-radius choice all open despite having acceptance
criteria). The decided plan per issue becomes the spec-writer's brief in
Phase 1.

Also confirm with the user, explicitly:
- the final issue list and stack order (bottom → top; use dependency order if
  one exists, otherwise pick one and say why)
- branch names
- parallel vs. sequential implementation (default recommendation: **parallel**,
  each branch off `master` independently — faster, and the harmonizer step
  composes the stack regardless of implementation order; go sequential only
  when branches will conflict on the same files)

Get explicit approval before launching the workflow — this is
superpowers:brainstorming's hard gate, it does not relax because the next
step is "just running a workflow."

## Phase 1 — Spec → implement → review (per issue, in parallel)

Author a Workflow script from `workflow-template.js` in this skill's
directory. Per issue, the pipeline is:

1. **Spec** (Sonnet — inherit session model, don't override): reads the issue
   and the Phase-0 decisions, writes a concrete implementation spec (files
   touched, exact changes, edge cases, test plan). Returns spec text.
2. **Spec review loop** (`fable`): checks the spec against the issue's
   acceptance criteria and repo conventions (CLAUDE.md, layer-map CLAUDE.mds).
   Findings → Sonnet revises the spec (not code). Loop, cap 2 rounds.
3. **Implement** (Sonnet, `isolation: 'worktree'`): TDD from the approved
   spec. First commands must re-pin the branch explicitly (`git fetch origin
   && git checkout -B <branch> origin/master`) since a fresh worktree can
   start from a stale ambient branch. Commit, then `git switch --detach` so
   the branch is free for the next agent to touch. Do not push or open a PR
   yet.
4. **Code review loop** (`fable` reads `git diff origin/master...<branch>`;
   Sonnet fixes, re-attaching with `git checkout <branch>` and detaching
   again after committing). Loop, cap 2 rounds — log unresolved findings on
   cap-out rather than looping forever.

Run all issues' pipelines concurrently (`parallel()`), not sequentially — the
main cost driver here is wall-clock, not machine resources (unlike a full
Gradle build per implementer, spec/review steps are cheap). Only serialize if
you have a real resource constraint (e.g. two implementers both need to run a
full Gradle build and the machine can't take two concurrent ones).

## Phase 2 — Harmonize into a stack

One `haiku` agent, once every issue's Phase-1 pipeline is clean:

- Rebase each branch onto the one below it, in stack order (`git checkout
  <branch-n> && git rebase <branch-n-1>`). This is safe pre-push since
  nothing has been force-anything yet. If two branches touch overlapping
  files and the rebase conflicts, STOP — report it, don't resolve
  destructively.
- `gh stack init --base master <bottom> ... <top>` (adopts the existing
  local branches), then `gh stack submit --auto`. If that exits 9 (stacked
  PRs unavailable on this repo), fall back to plain PRs with explicit
  `--base <parent-branch>` chaining.
- Fix up every PR with `gh pr edit`: `--auto` humanizes multi-commit branch
  names into non-conventional titles and skips the PR template. Titles must
  follow Conventional Commits; bodies must start with
  `.github/pull_request_template.md`'s content, then a description, then
  `Closes #N`.

## Phase 3 — CI/Sonar babysitting

One `haiku` monitor per PR, in parallel:

- Polls `gh pr checks <PR>` (sleep ~45s between polls, cap ~15 min) until
  every non-skipped check is pass/fail, then checks SonarQube (MCP tools,
  project key from `build.gradle.kts`'s `sonar.projectKey`) for open issues
  on that PR.
- **Every PR above the bottom of the stack must sync first**: `git merge
  origin/<branch-below> --no-edit`, then a plain `git push` if that brought
  in new commits. Never rebase a pushed branch here — this repo forbids
  force pushes; merge is the only safe way to pull a lower branch's fix
  into a higher one post-push.
- On any real failure (CI or Sonar), spawn a Sonnet fixer with the concrete
  failure detail (`gh run view --log-failed` output, or the Sonar issue
  list) — never a vague "fix CI". Loop back to monitoring after each fix,
  cap 3 rounds.
- When clean: `gh pr ready <PR>`.

Distinguish real failures from stale ones: a check that failed on an old run
before a retitle/reformat, and has since been superseded by a newer run on
the same PR, is not a live problem — read the *latest* run for that check
name, not just whatever `gh pr checks` shows first.

## Phase 4 — Stack maintenance

If a later change reorders, adds, or removes a branch — as opposed to just
adding a fix commit to an existing branch — don't hand-rebase. Use `gh stack
rebase` / `gh stack sync` (see the gh-stack skill) so the base-branch chain
and the GitHub Stack object stay consistent. Never force-push; if something
in `gh stack`'s own mechanics genuinely requires rewriting already-pushed
history, stop and ask the user first (repo rule, no exceptions).

## Models

| Role | Model |
|---|---|
| Spec writer, implementer, fixer (spec or code) | Sonnet — inherit session, don't override |
| Spec reviewer, code reviewer | `fable` |
| Harmonizer, CI/Sonar babysitter | `haiku` |

## Common mistakes

- Skipping Phase 0 because the issue already lists acceptance criteria —
  this repo's issues deliberately leave some calls open; brainstorm anyway.
- Defaulting to sequential implementation "to be safe" — parallel is
  faster and the harmonizer composes the stack regardless; only serialize
  for a genuine resource conflict.
- Treating `gh stack submit --auto`'s output as done — it leaves PRs as
  drafts with humanized titles and no template. Always `gh pr edit` after,
  and `gh pr ready` only once CI/Sonar are actually clean.
- Rebasing a branch that's already been pushed instead of merging — this
  repo forbids force pushes categorically.
- Letting a dependent PR's CI run stale after the branch below it gets a
  fix — merge the lower branch in before re-checking the upper one.
- Reacting to a stale/superseded failed check instead of the latest run
  for that check name.

## See also

- `workflow-template.js` in this directory — an adaptable Workflow script
  covering Phases 1-3 (fill in `ISSUES`, `REPO`, `SONAR_PROJECT_KEY`).
- superpowers:brainstorming, superpowers:test-driven-development,
  workflow-authoring, gh-stack.
