// Adaptable Workflow script for the shipping-issue-stack skill.
// Fill in ISSUES (Phase 0's decided dependency shape — see below), REPO and
// SONAR_PROJECT_KEY before running. Phase 0 (brainstorming) already happened
// in chat — each issue's `plan` field below is that phase's output.
//
// RESUME SAFETY — read before editing this file after a run has started:
// Once ANY agent() call below has completed successfully in this run, do not
// edit the function that produced it or its call site — not even a
// value-preserving change. The resume cache behaves like a positional replay
// of the recorded call sequence, not a pure content hash: a no-op edit to a
// shared function has been observed to force a full redo of every issue that
// already succeeded through it. If behavior must change for a subset of
// issues mid-run (e.g. a different model), write a NEW, separate function for
// just those issues and leave the original untouched — see
// specAndImplementAlt below for the pattern.

export const meta = {
  name: 'ship-issue-stack',
  description: 'Spec, implement, review, open PRs and CI-babysit a batch of issues',
  phases: [
    { title: 'Chain' }, // sequential, one issue at a time (omit if the batch is Independent)
    { title: 'Independent' }, // parallel fan-out for a genuinely independent batch (omit if Chain)
    { title: 'Open PRs' },
    { title: 'Babysit CI' },
  ],
}

const REPO_OWNER = 'owner'
const REPO_NAME = 'repo'
const REPO = `${REPO_OWNER}/${REPO_NAME}`
const SONAR_PROJECT_KEY = 'owner_repo'

// DEPENDENCY SHAPE — decided in Phase 0, not inferred here. Two shapes:
//
// (a) INDEPENDENT: every issue's baseRef is 'origin/master'. Everything runs
//     in one parallel() batch, every PR bases on master. Use only when the
//     issues genuinely touch disjoint code and review order does not matter.
//
// (b) CHAIN (the default for any related batch): ISSUES is the exact review
//     order. ISSUES[0]'s baseRef is 'origin/master'; every other entry's
//     baseRef is documentation only ('PREVIOUS_BRANCH' — the real value is
//     always the previous entry's actual pushed branch, resolved at runtime
//     below since implementation is strictly sequential). Each issue is
//     implemented and reviewed only after the previous one is pushed. This
//     is a genuine linear stack (the same shape gh-stack manages), not a
//     one-level fan-out where every dependent points at the same foundation
//     branch — never collapse the chain back into a fan-out to save
//     wall-clock; that just moves the rebasing work onto a human later.
const ISSUES = [
  { number: 0, branch: 'feat/example-first', baseRef: 'origin/master', plan: 'Fill in from Phase 0.' },
  { number: 0, branch: 'feat/example-second', baseRef: 'PREVIOUS_BRANCH', plan: 'Fill in from Phase 0.' },
  { number: 0, branch: 'feat/example-third', baseRef: 'PREVIOUS_BRANCH', plan: 'Fill in from Phase 0.' },
]
// true for shape (b) CHAIN (the default). false for shape (a) INDEPENDENT.
const IS_CHAIN = true

const SPEC_SCHEMA = {
  type: 'object',
  properties: { spec: { type: 'string' } },
  required: ['spec'],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          summary: { type: 'string' },
          severity: { type: 'string' },
        },
        required: ['file', 'summary'],
      },
    },
  },
  required: ['findings'],
}

const IMPLEMENT_SCHEMA = {
  type: 'object',
  properties: {
    worktreePath: { type: 'string' },
    branch: { type: 'string' },
    summary: { type: 'string' },
    buildPassed: { type: 'boolean' },
    verification: { type: 'string' },
  },
  required: ['worktreePath', 'branch', 'summary', 'buildPassed'],
}

const FIX_SCHEMA = {
  type: 'object',
  properties: { summary: { type: 'string' } },
  required: ['summary'],
}

const MONITOR_SCHEMA = {
  type: 'object',
  properties: {
    status: { type: 'string', enum: ['clean', 'issues', 'pending'] },
    failures: {
      type: 'array',
      items: {
        type: 'object',
        properties: { source: { type: 'string' }, detail: { type: 'string' } },
        required: ['source', 'detail'],
      },
    },
  },
  required: ['status', 'failures'],
}

const PR_SCHEMA = {
  type: 'object',
  properties: { prUrl: { type: 'string' }, notes: { type: 'string' } },
  required: ['prUrl'],
}

// GitHub access differs by environment — DETECT, don't hard-code. Remotely
// (this harness) gh does not work, only the MCP tools do. Locally (Claude
// Code CLI on a dev machine) gh works fine but the GitHub MCP server may not
// be attached. Every prompt that touches GitHub gets this block so it picks
// the right path wherever it runs.
const GH_RULES = `
GitHub access — this may be running remotely (where the \`gh\` CLI and any
direct GitHub REST/GraphQL call DO NOT WORK) or locally (where \`gh\` works
normally but the GitHub MCP tools may not be attached). Detect which applies:
  1. Try: ToolSearch("select:mcp__github__issue_read,mcp__github__pull_request_read,mcp__github__create_pull_request,mcp__github__update_pull_request")
  2. If those resolve, use them (issue_read/get, pull_request_read with method
     get_check_runs / get_comments / get_diff, create_pull_request,
     update_pull_request draft:false, ...).
  3. If ToolSearch finds nothing, fall back to the gh CLI directly (gh issue
     view, gh pr create, gh pr checks, gh pr view --json ..., gh api ...).
Never hard-code only one path. Plain git over HTTPS (fetch, checkout, commit,
push) works normally either way — only the GitHub API surface differs.
`

const REPO_RULES = `
Repo conventions (read CLAUDE.md and any layer-map CLAUDE.md under the touched directory if unsure):
- ktfmt (Google style); run "./gradlew ktfmtFormat" before you're done.
- KDoc on every public declaration and non-trivial @Composable, one or two lines, symmetric with neighbours, never restating the signature.
- Kotlin style: prefer val, avoid !! and lateinit, sealed classes for state.
- ui/** has no coverage safety net — think through every branch/state yourself.
- Never force-push, never rebase/amend already-pushed commits.

BEFORE EVER RUNNING "git add -A" OR COMMITTING: run "git status --short" and read it.
A reused worktree can carry stray staged or modified files an earlier agent left
uncommitted in an earlier run of this same workflow. Blindly staging them has
twice caused real damage in production use of this skill — once silently
deleting real regression tests, once nearly reintroducing an already-fixed
bug. If status shows anything you did not just edit yourself, diff it
("git diff" / "git diff --cached") and judge it on its own merits before
including or discarding it. Never assume a reused worktree starts clean.
`

function issueBrief(issue) {
  return `GitHub issue #${issue.number} in ${REPO}.

Decisions already made for you (do not re-litigate these, apply them):
${issue.plan}
`
}

function specPrompt(issue) {
  return `Write a concrete implementation spec for the work below.

${issueBrief(issue)}
Read the actual issue for its title/body/acceptance criteria (pull_request_read's sibling, issue_read, method get) before writing the spec.
${GH_RULES}
Produce a spec covering: exact files touched, the exact change in each, edge cases to handle, and a short test plan (what a TDD red/green cycle looks like for this). Read the CURRENT state of every file you plan to touch (at ${issue.baseRef}) so the spec is accurate. Do not write code yet — this step only produces the spec text.

Return the spec via the structured schema.`
}

function specReviewerPrompt(issue, spec) {
  return `Review this implementation spec against its issue's acceptance criteria and this repo's conventions (CLAUDE.md).

${issueBrief(issue)}
${GH_RULES}
Spec to review:
${spec}

Report concrete gaps only: missing files, acceptance criteria not addressed, a decision that contradicts the Phase-0 plan, vague/unverifiable steps. Don't nitpick style. Return the structured findings schema — empty array means the spec is ready to implement from.`
}

function specFixerPrompt(issue, spec, findings) {
  return `Revise this implementation spec for issue #${issue.number} to address the findings below. Do not touch any code — this is a spec-text revision only.

Current spec:
${spec}

Findings to address:
${JSON.stringify(findings, null, 2)}

Return the revised spec via the structured schema.`
}

function implementerPrompt(issue, spec, baseRef) {
  return `Implement issue #${issue.number} from the spec below, using TDD (red, green, refactor).

You are in a git worktree that may be stale. First:
  git fetch origin
  git checkout -B ${issue.branch} ${baseRef}
Confirm with "git log --oneline -3" that you are where you expect before writing anything.

Spec:
${spec}

${REPO_RULES}
${GH_RULES}
Run "./gradlew ktfmtFormat", then compile/test the relevant target. Do not claim
success you have not observed — put a real command-output summary in the
"verification" field, and set buildPassed honestly.

Commit with a Conventional Commit subject matching the issue's intent, and a body ending with "Closes #${issue.number}".

Push immediately: git push -u origin ${issue.branch} (plain push — this is a
brand-new branch, it cannot conflict). This is the resilience mechanism for
the whole pipeline: if anything interrupts the run after this point, the
work is already safe on the remote, not stranded in this worktree.

After pushing, run "git switch --detach" so the branch name is free for the next agent. Do not open a PR.

Return the structured schema: worktreePath, branch, a summary, buildPassed, and verification.`
}

function reviewerPrompt(issue, branch, worktreePath, baseRef, roundLabel) {
  return `Review the ${roundLabel} draft of issue #${issue.number}'s implementation, as a skeptical senior reviewer who expects to find real problems.

Work lives at ${worktreePath} on branch ${branch} (HEAD is detached there — expected, don't check it out).

Run: cd ${worktreePath} && git diff ${baseRef}...${branch}

${issueBrief(issue)}
${REPO_RULES}
${GH_RULES}
Check correctness, acceptance-criteria gaps, missed spots, formatting/KDoc, anything that breaks the build. Don't nitpick explicit spec/issue decisions. Return the structured findings schema — empty means clean.`
}

function fixerPrompt(issue, branch, worktreePath, findings) {
  return `Fix review findings on issue #${issue.number}'s implementation.

Work in ${worktreePath}. First: cd ${worktreePath} && git checkout ${branch} (re-attaches; it was detached).

Findings:
${JSON.stringify(findings, null, 2)}

${REPO_RULES}
Run "./gradlew ktfmtFormat" and re-verify. Commit as a new commit on ${branch} (never amend/rebase). Push immediately: git push origin ${branch} (plain push). Then "git switch --detach" again.

Return a summary of what you changed.`
}

async function specAndImplement(issue, baseRef, phaseName) {
  let spec = (await agent(specPrompt(issue), { label: `spec-${issue.number}`, phase: phaseName, schema: SPEC_SCHEMA }))?.spec
  if (!spec) throw new Error(`Spec writer for #${issue.number} failed.`)

  // Spec review is a single pass: write, review once, fix once if needed.
  // No re-review loop — that's the point, keep this cheap.
  const specReview = await agent(specReviewerPrompt(issue, spec), {
    label: `spec-review-${issue.number}`, phase: phaseName, model: 'opus', schema: REVIEW_SCHEMA,
  })
  const specFindings = specReview?.findings || []
  if (specFindings.length === 0) {
    log(`#${issue.number}: spec clean.`)
  } else {
    log(`#${issue.number}: spec review found ${specFindings.length} gap(s), fixing.`)
    const fixed = await agent(specFixerPrompt(issue, spec, specFindings), { label: `spec-fix-${issue.number}`, phase: phaseName, schema: SPEC_SCHEMA })
    if (fixed?.spec) spec = fixed.spec
  }

  const impl = await agent(implementerPrompt(issue, spec, baseRef), {
    label: `implement-${issue.number}`, phase: phaseName, isolation: 'worktree', schema: IMPLEMENT_SCHEMA,
  })
  if (!impl) throw new Error(`Implementer for #${issue.number} failed.`)
  log(`#${issue.number}: implemented + pushed on ${impl.branch}. buildPassed=${impl.buildPassed}`)

  let unresolved = []
  for (let round = 1; round <= 2; round++) {
    const review = await agent(reviewerPrompt(issue, impl.branch, impl.worktreePath, baseRef, round === 1 ? 'first' : 'second'), {
      label: `review-${issue.number}-r${round}`, phase: phaseName, model: 'opus', schema: REVIEW_SCHEMA,
    })
    const findings = review?.findings || []
    if (findings.length === 0) { log(`#${issue.number}: review round ${round} clean.`); unresolved = []; break }
    log(`#${issue.number}: review round ${round} found ${findings.length} finding(s).`)
    if (round === 2) { unresolved = findings; log(`#${issue.number}: review cap reached, unresolved: ${JSON.stringify(findings)}`); break }
    await agent(fixerPrompt(issue, impl.branch, impl.worktreePath, findings), {
      label: `fix-${issue.number}-r${round}`, phase: phaseName, schema: FIX_SCHEMA,
    })
  }

  return { number: issue.number, branch: impl.branch, title: issue.title, unresolved, summary: impl.summary }
}

// If the user asks to change model/behavior for only the NOT-YET-RUN issues
// partway through a run, do NOT parametrize specAndImplement above — copy it
// here instead, change what's needed, and route only the remaining issues
// through this copy. This keeps the original function's call sites
// byte-for-byte untouched so already-completed issues' cache stays valid.
//
// async function specAndImplementAlt(issue, baseRef, phaseName) { ... }

function prOpenerPrompt(result, baseRefLabel, chainNote) {
  return `Open a pull request for issue #${result.number} (${REPO}). The branch
${result.branch} is ALREADY PUSHED and ALREADY based on the right commit —
this task does no git operations at all, only opens the PR.

${GH_RULES}
Use create_pull_request: owner "${REPO_OWNER}", repo "${REPO_NAME}", head "${result.branch}",
base "${baseRefLabel}", draft: true.
  - title: the issue's Conventional Commits title (a validate-pr-title CI
    check may enforce this format — match it exactly).
  - body: start with the EXACT content of .github/pull_request_template.md,
    then a description of the change, then "Closes #${result.number}".
    ${chainNote}
    If any review finding said something must be stated in the PR body
    (a scope decision, a known limitation) rather than just a code comment,
    include it verbatim — a code comment alone does not satisfy that finding.

Return the structured schema: prUrl, and notes on anything unusual.`
}

function monitorPrompt(result, prUrl, belowBranch) {
  const sync = belowBranch
    ? `This PR is part of a chain, based on ${belowBranch}. First sync with its
latest state (it may have a new commit since this branch was created,
including a fix pushed to it AFTER this stack's PRs were first opened):
  git fetch origin && git checkout -B ${result.branch} origin/${result.branch}
  git merge origin/${belowBranch} --no-edit
On conflict, STOP and return status "issues" with a single failure
{source:"merge-conflict", detail:"..."}. Never rebase — merge only. If the
merge brought in new commits, push normally (never --force):
  git push origin ${result.branch}

`
    : ''
  return `Monitor pull request ${prUrl} (branch ${result.branch}) until fully
green: every required CI check passing and zero open SonarQube issues.

${sync}${GH_RULES}
1. Poll pull_request_read (method get_check_runs) every ~45s until every
   non-skipped check is final. Read the LATEST run for each check name — a
   check that failed on an old run, since superseded by a newer pass on the
   same PR, is not live. Cap at ~15 minutes; if still not final, return
   status "pending".
2. For any real CI failure, get the actual failing output (job logs via the
   MCP tools). Record {source: check name, detail: root cause}.
3. Once CI is pass/skip, check SonarQube by reading the SonarCloud bot's own
   PR comment (pull_request_read, method get_comments, author
   sonarqubecloud[bot]) — NOT a raw SonarQube/SonarCloud API fetch, which can
   be served stale from a several-minutes cache right when you need a fresh
   answer (e.g. immediately after a fix). Only fall back to the API for
   detail beyond what the comment's summary/count gives you. Record each open
   issue as {source:"sonar", detail:"<rule> <file>:<line> — <message>"}.
4. If zero failures and zero Sonar issues: update_pull_request draft:false,
   return status "clean".
5. Otherwise return status "issues" with the full failures array. Don't fix anything yourself.`
}

function ciFixerPrompt(result, prUrl, failures) {
  return `Fix CI/SonarQube failures on pull request ${prUrl} (branch ${result.branch}).

You are in a fresh git worktree. Set up:
  git fetch origin
  git checkout -B ${result.branch} origin/${result.branch}

Failures:
${JSON.stringify(failures, null, 2)}

${REPO_RULES}
Run "./gradlew ktfmtFormat", make the minimal correct fix per failure, re-verify
with the relevant Gradle task. A fix for one finding can trade into a
different one (e.g. reducing complexity by extracting a function can push
that function's own parameter count over a separate limit) — re-verify
broadly, don't assume the one finding you targeted was the only one there
would ever be. Commit as new commit(s) on ${result.branch} (never
amend/rebase/force). Then: git push origin ${result.branch} (plain push).

Return a summary of what you changed for each failure.`
}

async function babysit(result, prUrl, belowBranch) {
  for (let round = 1; round <= 3; round++) {
    const monitor = await agent(monitorPrompt(result, prUrl, belowBranch), {
      label: `monitor-${result.number}-r${round}`, phase: 'Babysit CI', schema: MONITOR_SCHEMA,
    })
    if (!monitor) return { prUrl, status: 'error' }
    if (monitor.status === 'clean') { log(`${prUrl}: green, marked ready.`); return { prUrl, status: 'clean' } }
    if (monitor.status === 'pending') {
      log(`${prUrl}: still pending (round ${round}).`)
      if (round === 3) return { prUrl, status: 'pending' }
      continue
    }
    log(`${prUrl}: ${monitor.failures.length} failure(s), round ${round}.`)
    if (round === 3) return { prUrl, status: 'issues', failures: monitor.failures }
    await agent(ciFixerPrompt(result, prUrl, monitor.failures), {
      label: `ci-fix-${result.number}-r${round}`, phase: 'Babysit CI', schema: FIX_SCHEMA,
    })
  }
  return { prUrl, status: 'unresolved' }
}

let allResults
if (IS_CHAIN) {
  phase('Chain')
  allResults = []
  let previousBranch = null
  for (const issue of ISSUES) {
    const baseRef = previousBranch ?? issue.baseRef // first issue only: origin/master
    log(`#${issue.number}: implementing against ${baseRef}.`)
    const result = await specAndImplement(issue, baseRef, 'Chain')
    allResults.push(result)
    previousBranch = result.branch
    log(`#${issue.number}: done on ${previousBranch}. Next issue in the chain bases on it.`)
  }
} else {
  phase('Independent')
  allResults = (await parallel(
    ISSUES.map((issue) => () => specAndImplement(issue, issue.baseRef, 'Independent')),
  )).filter(Boolean)
  if (allResults.length !== ISSUES.length) {
    log(`WARNING: only ${allResults.length}/${ISSUES.length} pipelines completed. Opening PRs for what succeeded.`)
  }
}

phase('Open PRs')
const prResults = []
for (let i = 0; i < allResults.length; i++) {
  const result = allResults[i]
  const previous = IS_CHAIN && i > 0 ? allResults[i - 1] : null
  const baseRefLabel = previous ? previous.branch : 'master'
  const chainNote = previous
    ? `Note in the body that this PR is part of a stack and depends on #${previous.number} landing first (base branch ${baseRefLabel}).`
    : IS_CHAIN
      ? `Note in the body that this PR is the base of a stack of dependent PRs.`
      : ''
  const opened = await agent(prOpenerPrompt(result, baseRefLabel, chainNote), {
    label: `open-pr-${result.number}`, phase: 'Open PRs', schema: PR_SCHEMA,
  })
  if (opened?.prUrl) prResults.push({ ...result, prUrl: opened.prUrl, belowBranch: previous ? previous.branch : null })
}

phase('Babysit CI')
const babysat = await parallel(
  prResults.map((r) => () => babysit(r, r.prUrl, r.belowBranch)),
)

return { prUrls: prResults.map((r) => r.prUrl), babysat }
