---
name: shipping-issue-stack
description: Use when the user wants to batch-implement several GitHub issues as a linear stack of reviewed, CI-clean pull requests, or asks to run issues through a spec/review/implement/harmonize pipeline.
---

# Shipping an issue stack

## Overview

Turns a batch of related GitHub issues into a stack of small, reviewed PRs. Two
phases: brainstorm each issue with the human partner in this session, then run
a multi-agent Workflow (spec → spec review → TDD implement → code review →
open PRs → CI/Sonar babysit). Requires `ultracode` on or the user explicitly
asking for a workflow (see the Workflow tool's own opt-in rule) — this skill
does not itself authorize orchestration.

**REQUIRED BACKGROUND:** superpowers:brainstorming (phase 0),
superpowers:test-driven-development (the implement step), workflow-authoring
(script mechanics).

**Token discipline matters here as much as correctness.** A full TDD
implementer redo costs hundreds of thousands of tokens; the failure modes
below are not theoretical — every one of them has actually happened and
wasted a redo. Read "Resume-safety rules" before writing or editing a
workflow script for this skill, not after something breaks.

## When to use

- The user names 2+ GitHub issues to implement together as a reviewed PR stack.
- The user asks to "spec these out and ship them", "run the issues through
  the pipeline", or similarly wants coordinated multi-agent delivery instead
  of one issue at a time.
- **Not** for a single ad-hoc change — use normal TDD directly.
- **Not** for issues that aren't independently reviewable as separate PRs —
  decompose first (see brainstorming's scope-check).

## Always confirm before starting

This skill's trigger conditions above are an exception to the normal
"invoke without asking" rule: because running it means committing to a
multi-hour, multi-agent pipeline that opens real PRs, always ask the user to
confirm before doing anything else — before Phase 0, before invoking
superpowers:brainstorming, before any exploration beyond what's needed to
name the issues. State in one or two sentences what running this skill will
do (brainstorm each issue, then spec/implement/review/open/babysit them as a
PR stack) and wait for an explicit yes. A message that merely mentions
several issues in passing is not that confirmation — only proceed once the
user has said to run this process specifically.

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

- **The real dependency shape — decide this now, not after implementation.**
  Most batches are one of two shapes:
  - **Independent** — the issues touch disjoint code and have no real
    dependency on each other. Each is based directly on `master`, implemented
    and reviewed fully in parallel, and opened as its own PR against `master`.
    No chain, no rebasing, nothing to harmonize.
  - **One foundation + N dependents** — one issue lands shared
    infrastructure (a token system, a shared component) the rest build on.
    Implement and land that one first (see "Foundation-first" below), then
    fan the rest out **directly against the foundation branch** — one level,
    not a rebase chain. Each dependent PR's base is the foundation branch;
    GitHub retargets them to `master` automatically once it merges.
  A genuine multi-level chain (issue C really does build on issue B's actual
  changes, not just a review-convenience ordering) is rare. If it applies,
  give C that exact branch as its `baseRef` **from the start** — never
  retrofit a chain by rebasing an already-implemented, already-pushed branch
  onto another one after the fact (see "Never rebase a pushed branch" below
  for why that specific move broke a real PR mid-session).
- branch names
- if there's a foundation issue: **land it first, sequentially**, before
  fanning out the rest. This costs some wall-clock but means the dependent
  issues implement against a real, finished foundation instead of a moving
  target — cheap insurance against needing to re-sync everything later.
- **the model each role should run.** Set this once, before Phase 1 starts,
  for every issue. If the user asks to change it mid-run after some issues
  have already implemented, see "Resume-safety rules" — the fix is never to
  edit the shared pipeline function.

Get explicit approval before launching the workflow — this is
superpowers:brainstorming's hard gate, it does not relax because the next
step is "just running a workflow."

## GitHub access: MCP tools remotely, `gh` locally — detect, don't assume

This skill runs in two different environments and the GitHub access path is
**not** the same in both:

- **Remote (this harness, claude.ai/code)**: `gh` and any direct GitHub
  REST/GraphQL access are not available to the session (confirmed by direct
  testing, not something to rediscover per run). Only the GitHub MCP tools
  work here.
- **Local (Claude Code CLI on a developer machine)**: `gh` is installed and
  authenticated and works normally. The GitHub MCP server may or may not be
  attached in a given local setup — don't assume it is.

So every prompt below (spec writer, reviewer, PR opener, monitor) must tell
its agent to **detect which is available, not hard-code one**:

1. Try `ToolSearch("select:mcp__github__issue_read,mcp__github__pull_request_read,mcp__github__create_pull_request,mcp__github__update_pull_request")`
   first. If the MCP tools resolve, use them: `issue_read` / `pull_request_read`
   (methods `get`, `get_diff`, `get_check_runs`, `get_comments`, ...),
   `create_pull_request`, `update_pull_request`.
2. If `ToolSearch` finds nothing (no GitHub MCP server in this session), fall
   back to the `gh` CLI directly: `gh issue view`, `gh pr create`, `gh pr
   checks`, `gh pr view --json ...`, `gh api ...`.

Never write a prompt that hard-codes only one path — a script written for a
local run with only `gh` will fail outright the next time it's run remotely,
and vice versa. Plain `git` over HTTPS (fetch/checkout/commit/push) works
normally in both environments regardless.

## Phase 1 — Spec → implement → review (per issue)

Author a Workflow script from `workflow-template.js` in this skill's
directory. Per issue, the pipeline is:

1. **Spec** (model set in Phase 0 — inherit session unless told otherwise):
   reads the issue and the Phase-0 decisions, writes a concrete
   implementation spec (files touched, exact changes, edge cases, test
   plan). Returns spec text.
2. **Spec review, once** (`opus`): checks the spec against the issue's
   acceptance criteria and repo conventions (CLAUDE.md, layer-map CLAUDE.mds).
   Findings → one fix pass on the spec text (not code). No loop, no
   re-review after the fix — write, review, fix, move on.
3. **Implement** (`isolation: 'worktree'`): TDD from the approved spec. First
   commands must re-pin the branch explicitly (`git fetch origin && git
   checkout -B <branch> <baseRef>`) since a fresh worktree can start from a
   stale ambient branch. **Before ever running `git add -A` or committing,
   run `git status --short` and read it** — a reused worktree can carry
   stray staged or modified files left uncommitted by an earlier agent in an
   earlier run of this same workflow; blindly staging them has twice caused
   real damage in production runs of this skill (once silently deleting 150
   lines of regression tests for a bug that was later found to still be
   broken; once nearly reintroducing an already-fixed bug). If `git status`
   shows anything you didn't just edit yourself, diff it, decide whether it's
   safe on its own merits, and only then include or discard it — never fold
   it in by default. Commit, **push immediately** (`git push -u origin
   <branch>` — plain push, this is a brand-new branch so it cannot conflict),
   then `git switch --detach` so the branch name is free for the next agent.
   Do not open a PR yet.
4. **Code review loop** (`opus` reads `git diff <baseRef>...<branch>`;
   writer fixes, re-attaching with `git checkout <branch>` and detaching
   again after committing **and pushing** — same plain push each round).
   Loop, cap 2 rounds — log unresolved findings on cap-out rather than
   looping forever.

Pushing after every commit (not just at the end) is the resilience
mechanism for this whole pipeline: a workflow interruption (spend limit,
container reclaim, a bad edit forcing a stop) can happen at any point, and
with this rule the worst case is losing the one in-flight agent's uncommitted
work — never a fully-implemented-but-unpushed branch sitting only in an
ephemeral worktree. That happened three times before this rule existed; each
time, recovering meant manually finding and pushing local branches by hand
before doing anything else.

Run all issues' pipelines concurrently (`parallel()`) unless Phase 0 decided
on foundation-first (in which case: the foundation issue runs alone, *then*
the rest run concurrently against its finished branch). The main cost driver
here is wall-clock, not machine resources (unlike a full Gradle build per
implementer, spec/review steps are cheap). Only serialize further if you have
a real resource constraint (e.g. two implementers both need to run a full
Gradle build and the machine can't take two concurrent ones).

### Resume-safety rules

These come from confirmed failures, not caution in the abstract — a resumed
Workflow run's cache does **not** key purely on an agent() call's resolved
(prompt, opts) *value*; it behaves like a positional replay across the
recorded call sequence. Two concrete consequences, both observed to cost a
full implementer redo:

- **Never edit a shared prompt-building function, or the code at its call
  site, once any of its `agent()` calls has completed in this run — even a
  change that is a complete no-op for the issues that already succeeded.**
  If a value-preserving edit is made to a function three issues already
  called successfully, resuming can still re-invoke all three from scratch.
  If behavior must change for only *some* issues after others have already
  finished (e.g. the user asks to switch model mid-run), do not add a
  parameter or a conditional to the existing function — write a **new,
  separate function** used only by the not-yet-run issues, and leave every
  byte of the original function and its call sites untouched.
- **Don't restructure the fan-out's control flow after a run has begun**
  (e.g. splitting one `parallel()` of four into two sequential `parallel()`
  calls of two, to make a spend-limit cutoff land more cleanly). This
  changes the recorded call order even for issues whose own code never
  changed, and has broken their cache too. If a spend-limit cutoff is a real
  risk, the fix is the push-immediately rule above (the git deliverable
  survives regardless of what the workflow's own agent-cache does on
  resume), not restructuring the script.
- **Early-warning sign, not just a post-mortem one:** if a resume shows an
  agent starting a step (spec write, spec review, `implement-N`) for an
  issue whose corresponding branch is *already pushed and known-good*, stop
  the run immediately and check `git log`/`git diff` on that branch before
  letting it continue — don't assume a re-run of an already-succeeded step
  is harmless. Confirm first whether it's about to redo real work; if so,
  the branch is already safe (it's pushed), so there is no urgency to let
  the redo finish — diagnose, then decide.

## Phase 2 — Open the PRs

By the time this phase starts, every branch is **already pushed** (Phase 1's
push-immediately rule) and **already based on the right commit** (Phase 0
decided the real dependency shape up front, so nothing needs retrofitting).
This phase does no git operations at all — it only opens PRs.

One agent, per issue (or one agent for the whole batch — either is fine,
there's no shared mutable git state left to serialize on):

- Open the PR via the GitHub MCP `create_pull_request` tool (load via
  `ToolSearch` first): `base` is `master` for the foundation issue (or for
  any genuinely independent issue), or the foundation branch for a
  dependent issue in the one-level-fan-out shape.
- `title`: the issue's Conventional Commits title. A `validate-pr-title` CI
  job (or equivalent) may enforce this format — match it exactly.
- `body`: start with the **exact content** of
  `.github/pull_request_template.md`, then a description, then
  `Closes #<issue>`. If Phase 0/1 surfaced any disclosure the issue's
  acceptance criteria explicitly required stating in the PR (a scope
  decision, a known limitation) — carry it into the body verbatim; a review
  finding that says "state this in the PR body" is not satisfied by a code
  comment alone.
- Open as **draft** — Phase 3 verifies CI/Sonar before marking any PR ready.

**Never force-push, never rebase an already-pushed branch, no exceptions**
(repo rule). Since nothing in this phase pushes or rebases, this should
never come up — if you find yourself reaching for `git rebase` here, stop:
it means Phase 0's dependency-shape decision was wrong or skipped, and the
fix is to redo that decision, not to rebase a pushed branch to match it.

## Phase 3 — CI/Sonar babysitting

One monitor per PR, in parallel (subagent model is a judgment call — cheap
polling suits a lighter model, but this phase makes real fix/no-fix
decisions, so don't default to the cheapest model without thinking about
it):

- Poll the PR's check runs via the GitHub MCP `pull_request_read`
  (`get_check_runs`) tool (sleep ~45s between polls, cap ~15 min) until
  every non-skipped check is pass/fail. Read the *latest* run for each check
  name — a check that failed on an old run and has since been superseded by
  a newer pass on the same PR is not live.
- **Check SonarQube by reading the SonarCloud bot's own PR comment**
  (`pull_request_read` → `get_comments`, filter for the
  `sonarqubecloud[bot]` author), not by querying the raw SonarQube/SonarCloud
  REST API directly. A direct API fetch (e.g. via a generic web-fetch tool)
  can be served from a cache keyed by URL for several minutes — querying it
  right after pushing a fix has returned a stale pre-fix issue list at least
  once in production use of this skill. The bot's PR comment is reposted
  fresh on every analysis and is the reliable source. If a finding needs
  detail beyond the comment's summary/count, that's the point to fall back
  to the API — not as the first check.
- **Every PR above the bottom of a one-level fan-out must re-sync whenever
  the foundation branch moves *for any reason*, not just during this
  phase's initial pass** — including a fix pushed to the foundation branch
  *after* the stack's PRs were already opened (e.g. a Sonar finding fixed
  post-hoc). Re-sync: `git merge origin/<foundation-branch> --no-edit`, then
  a plain `git push` if that brought in new commits. Never rebase a pushed
  branch — merge is the only safe way to pull a lower branch's fix into a
  higher one post-push. Treat "the foundation branch just got a new commit"
  as an event that invalidates every dependent PR's babysitting state until
  each has re-synced and re-verified, whether that commit landed during the
  original CI-red loop or was discovered later while babysitting Sonar.
- On any real CI failure, spawn a fixer with the concrete failure detail
  (the actual check-run output or log, fetched via the MCP tools — never a
  vague "fix CI"). On a real Sonar finding, spawn a fixer with the actual
  rule/file/line/message from the bot's comment (or the API if more detail
  was needed). Loop back to monitoring after each fix, cap 3 rounds.
- A fix for one Sonar rule can trade into a different one (e.g. reducing
  cognitive complexity by extracting a function can push that function's
  parameter count over a separate limit) — re-check after every fix rather
  than assuming the specific finding you addressed was the only one there
  ever would be.
- When clean (CI green, zero open Sonar issues): mark the PR ready via
  `update_pull_request` (`draft: false`).

## Models

| Role | Model |
|---|---|
| Spec writer, implementer, fixer (spec or code) | Decided in Phase 0 — inherit session unless the user asked otherwise |
| Spec reviewer, code reviewer | `opus` |
| PR-opener, CI/Sonar babysitter | Cheap model is fine for polling; use judgment for the fix/no-fix decisions |

## Common mistakes

- Skipping Phase 0 because the issue already lists acceptance criteria —
  this repo's issues deliberately leave some calls open; brainstorm anyway.
- Deciding the dependency shape (independent vs. foundation-first vs. a
  genuine chain) *after* implementation instead of before — retrofitting it
  means rebasing already-pushed branches, which both forbids force-pushing
  and (separately) has actually broken a PR mid-session.
- Editing a shared pipeline function (or restructuring the fan-out's control
  flow) after any issue in the batch has already completed a step — see
  "Resume-safety rules". This is the single most expensive mistake this
  skill can make; it has cost a full implementer redo twice.
- Hard-coding one GitHub access path into a prompt instead of detecting —
  `gh` doesn't work remotely, MCP GitHub tools may not be attached locally.
  Always try the MCP tools first, fall back to `gh` if they don't resolve.
- `git add -A` in a reused worktree without reading `git status` first — has
  silently swept in and committed a stray, unrelated, uncommitted change
  from an earlier run twice, once masking a real unfixed bug.
- Checking Sonar via a raw API fetch instead of the bot's PR comment — the
  API response can be stale-cached right when you need a fresh answer most
  (immediately after pushing a fix).
- Deferring every branch's push to a single end-of-pipeline step instead of
  pushing after each commit — turns any mid-run interruption into a
  scramble to recover local-only work instead of a non-event.
- Letting a dependent PR's babysitting state go stale after the branch below
  it gets *any* new commit, including one discovered well after the stack
  was first opened.
- Reacting to a stale/superseded failed check instead of the latest run for
  that check name.

## See also

- `workflow-template.js` in this directory — an adaptable Workflow script
  covering Phases 1-3 (fill in `ISSUES`, `REPO`, `SONAR_PROJECT_KEY`).
- superpowers:brainstorming, superpowers:test-driven-development,
  workflow-authoring.
