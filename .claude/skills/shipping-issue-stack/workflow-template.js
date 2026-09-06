// Adaptable Workflow script for the shipping-issue-stack skill.
// Fill in ISSUES (in stack order, bottom first) and re-check REPO /
// SONAR_PROJECT_KEY before running. Phase 0 (brainstorming) already happened
// in chat — each issue's `plan` field below is that phase's output.

export const meta = {
  name: 'ship-issue-stack',
  description: 'Spec, implement, review, stack and CI-babysit a batch of issues',
  phases: [
    { title: 'Spec' },
    { title: 'Implement' },
    { title: 'Review' },
    { title: 'Stack & PRs' },
    { title: 'Babysit CI' },
  ],
}

const REPO = 'Axl-Lvy/MemorChess'
const SONAR_PROJECT_KEY = 'Axl-Lvy_MemorChess'

// Stack order: index 0 is the bottom (based on master), each next one is
// based on the previous. `plan` is the concrete decisions from Phase 0
// brainstorming — paste them in, don't leave the issue to reinterpret them.
const ISSUES = [
  { number: 0, branch: 'feat/example-bottom', plan: 'Fill in from Phase 0 brainstorming.' },
  { number: 0, branch: 'fix/example-top', plan: 'Fill in from Phase 0 brainstorming.' },
]

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

const STACK_SCHEMA = {
  type: 'object',
  properties: {
    prUrls: { type: 'array', items: { type: 'string' } },
    notes: { type: 'string' },
  },
  required: ['notes'],
}

const REPO_RULES = `
Repo conventions (read CLAUDE.md and any layer-map CLAUDE.md under the touched directory if unsure):
- ktfmt (Google style); run "./gradlew ktfmtFormat" before you're done.
- KDoc on every public declaration and non-trivial @Composable, one or two lines, symmetric with neighbours, never restating the signature.
- Kotlin style: prefer val, avoid !! and lateinit, sealed classes for state.
- ui/** has no coverage safety net — think through every branch/state yourself.
- Never force-push, never rebase/amend already-pushed commits.
`

function specPrompt(issue) {
  return `Write a concrete implementation spec for GitHub issue #${issue.number} in the MemorChess repo (${REPO}).

Read the issue: gh issue view ${issue.number} --json body,title --repo ${REPO}

Decisions already made for you (do not re-litigate these, apply them):
${issue.plan}

Produce a spec covering: exact files touched, the exact change in each, edge cases to handle, and a short test plan (what a TDD red/green cycle looks like for this). Do not write code yet — read the current state of the files you'll touch so the spec is accurate, but this step only produces the spec text.

Return the spec via the structured schema.`
}

function specReviewerPrompt(issue, spec) {
  return `Review this implementation spec for GitHub issue #${issue.number} (${REPO}) against the issue's acceptance criteria and this repo's conventions (CLAUDE.md).

Issue: gh issue view ${issue.number} --json body,title --repo ${REPO}

Spec to review:
${spec}

Report concrete gaps only: missing files, acceptance criteria not addressed, a decision that contradicts the Phase-0 plan, vague/unverifiable steps. Don't nitpick style. Return the structured findings schema — empty array means the spec is ready to implement from.`
}

function specFixerPrompt(issue, spec, findings) {
  return `Revise this implementation spec for GitHub issue #${issue.number} (${REPO}) to address the findings below. Do not touch any code — this is a spec-text revision only.

Current spec:
${spec}

Findings to address:
${JSON.stringify(findings, null, 2)}

Return the revised spec via the structured schema.`
}

function implementerPrompt(issue, spec) {
  return `Implement GitHub issue #${issue.number} in the MemorChess repo from the spec below, using TDD (red, green, refactor).

You are in a git worktree that may be stale. First:
  git fetch origin
  git checkout -B ${issue.branch} origin/master

Spec:
${spec}

${REPO_RULES}
Run "./gradlew ktfmtFormat", then compile/test the relevant target.

Commit with a Conventional Commit subject matching the issue's intent, and a body ending with "Closes #${issue.number}".

After your final commit, run "git switch --detach" to release the branch name. Do not push, do not open a PR, do not delete the worktree.

Return the structured schema: worktreePath, branch, a summary, and whether build/tests passed.`
}

function reviewerPrompt(issue, branch, worktreePath, roundLabel) {
  return `Review the ${roundLabel} draft of GitHub issue #${issue.number}'s implementation in the MemorChess repo, as a skeptical senior reviewer.

Work lives at ${worktreePath} on branch ${branch} (HEAD is detached there — expected, don't check it out).

Run: cd ${worktreePath} && git diff origin/master...${branch}

Check correctness, acceptance-criteria gaps (gh issue view ${issue.number} --json body --repo ${REPO}), missed spots, formatting/KDoc, anything that breaks the build. Don't nitpick explicit spec/issue decisions. Return the structured findings schema — empty means clean.`
}

function fixerPrompt(issue, branch, worktreePath, findings) {
  return `Fix review findings on GitHub issue #${issue.number}'s implementation (MemorChess repo).

Work in ${worktreePath}. First: cd ${worktreePath} && git checkout ${branch} (re-attaches; it was detached).

Findings:
${JSON.stringify(findings, null, 2)}

${REPO_RULES}
Run "./gradlew ktfmtFormat" and re-verify. Commit as a new commit on ${branch} (never amend/rebase). Then "git switch --detach" again.

Return a summary of what you changed.`
}

async function specAndImplement(issue) {
  let spec = (await agent(specPrompt(issue), { label: `spec-${issue.number}`, phase: 'Spec', schema: SPEC_SCHEMA }))?.spec
  if (!spec) throw new Error(`Spec writer for #${issue.number} failed.`)

  for (let round = 1; round <= 2; round++) {
    const review = await agent(specReviewerPrompt(issue, spec), {
      label: `spec-review-${issue.number}-r${round}`, phase: 'Spec', model: 'fable', schema: REVIEW_SCHEMA,
    })
    const findings = review?.findings || []
    if (findings.length === 0) { log(`#${issue.number}: spec clean.`); break }
    log(`#${issue.number}: spec review round ${round} found ${findings.length} gap(s).`)
    if (round === 2) { log(`#${issue.number}: spec review cap reached, proceeding with unresolved gaps.`); break }
    const fixed = await agent(specFixerPrompt(issue, spec, findings), { label: `spec-fix-${issue.number}-r${round}`, phase: 'Spec', schema: SPEC_SCHEMA })
    if (fixed?.spec) spec = fixed.spec
  }

  const impl = await agent(implementerPrompt(issue, spec), {
    label: `implement-${issue.number}`, phase: 'Implement', isolation: 'worktree', schema: IMPLEMENT_SCHEMA,
  })
  if (!impl) throw new Error(`Implementer for #${issue.number} failed.`)
  log(`#${issue.number} implemented on ${impl.branch}. Build passed: ${impl.buildPassed}`)

  for (let round = 1; round <= 2; round++) {
    const review = await agent(reviewerPrompt(issue, impl.branch, impl.worktreePath, round === 1 ? 'first' : 'second'), {
      label: `review-${issue.number}-r${round}`, phase: 'Review', model: 'fable', schema: REVIEW_SCHEMA,
    })
    const findings = review?.findings || []
    if (findings.length === 0) { log(`#${issue.number}: review round ${round} clean.`); break }
    log(`#${issue.number}: review round ${round} found ${findings.length} finding(s).`)
    if (round === 2) { log(`#${issue.number}: review cap reached, unresolved: ${JSON.stringify(findings)}`); break }
    await agent(fixerPrompt(issue, impl.branch, impl.worktreePath, findings), {
      label: `fix-${issue.number}-r${round}`, phase: 'Review', schema: FIX_SCHEMA,
    })
  }

  return { number: issue.number, branch: impl.branch, worktreePath: impl.worktreePath }
}

function harmonizerPrompt(results) {
  const chain = results.map((r) => `${r.branch} (worktree ${r.worktreePath})`).join(' -> ')
  return `You are the harmonizer for a stack of independently-implemented branches in the MemorChess repo (${REPO}), bottom to top: ${chain}

All branches are currently detached in their own worktrees. In your own fresh worktree:
1. git fetch origin
2. For each branch from the second one up, rebase it onto the one below (in order): git checkout <branch-n>; git rebase <branch-n-1>. These should be conflict-free if the issues touch disjoint files. If a rebase conflicts, STOP — report it, do not resolve destructively (nothing has been pushed yet, so aborting is always safe).
3. gh stack init --base master ${results.map((r) => r.branch).join(' ')}
4. gh stack submit --auto (falls back to plain "gh pr create --base <parent-branch>" chains if this exits 9 — stacks unavailable).
5. For every PR, "gh pr edit <number> --title ... --body ...": Conventional Commit title, body starting with the exact content of .github/pull_request_template.md, then a description, then "Closes #<issue>".
6. Confirm with "gh stack view --json" (or "gh pr list" on the fallback path).

Return the structured schema: every PR URL in stack order, plus notes on anything unusual.`
}

function monitorPrompt(issueNumber, prNumber, branch, syncFromBranch) {
  const sync = syncFromBranch
    ? `First sync with the latest state of ${syncFromBranch} (it may have new fix commits since this branch was created on top of it):
  git merge origin/${syncFromBranch} --no-edit
On conflict, STOP and return status "issues" with a single failure {source:"merge-conflict", detail:"..."}. If the merge brought in commits, push normally (never --force): git push origin ${branch}

`
    : ''
  return `Monitor GitHub PR #${prNumber} (branch ${branch}, issue #${issueNumber}) in ${REPO} until fully green: every required CI check passing and zero open SonarQube issues.

You are in a fresh git worktree. Set up:
  git fetch origin
  git checkout -B ${branch} origin/${branch}

${sync}Then:
1. Poll "gh pr checks ${prNumber} --repo ${REPO}" (sleep ~45s between polls) until every non-skipping check is final. Read the LATEST run for each check name — an old failed run superseded by a newer pass on the same check is not live. Cap at ~15 minutes; if still not final, return status "pending".
2. For any real failure, find the cause via "gh run view <run-id> --log-failed --repo ${REPO}". Record {source: check name, detail: root cause}.
3. Once CI is pass/skip, check SonarQube (MCP tools, project key "${SONAR_PROJECT_KEY}", scoped to this PR's branch) for open issues. Record each as {source:"sonar", detail:"<rule> <file>:<line> — <message>"}.
4. If zero failures and zero Sonar issues: "gh pr ready ${prNumber} --repo ${REPO}", return status "clean".
5. Otherwise return status "issues" with the full failures array. Don't fix anything yourself.`
}

function ciFixerPrompt(prNumber, branch, failures) {
  return `Fix CI/SonarQube failures on GitHub PR #${prNumber} (branch ${branch}) in the MemorChess repo (${REPO}).

You are in a fresh git worktree. Set up:
  git fetch origin
  git checkout -B ${branch} origin/${branch}

Failures:
${JSON.stringify(failures, null, 2)}

${REPO_RULES}
Run "./gradlew ktfmtFormat", make the minimal correct fix per failure, re-verify with the relevant Gradle task. Commit as new commit(s) on ${branch} (never amend/rebase/force). Then: git push origin ${branch} (plain push).

Return a summary of what you changed for each failure.`
}

async function babysit(issueNumber, prNumber, branch, syncFromBranch) {
  for (let round = 1; round <= 3; round++) {
    const monitor = await agent(monitorPrompt(issueNumber, prNumber, branch, syncFromBranch), {
      label: `monitor-${prNumber}-r${round}`, phase: 'Babysit CI', model: 'haiku', schema: MONITOR_SCHEMA,
    })
    if (!monitor) return { prNumber, status: 'error' }
    if (monitor.status === 'clean') { log(`PR #${prNumber}: green, ready for review.`); return { prNumber, status: 'clean' } }
    if (monitor.status === 'pending') {
      log(`PR #${prNumber}: still pending (round ${round}).`)
      if (round === 3) return { prNumber, status: 'pending' }
      continue
    }
    log(`PR #${prNumber}: ${monitor.failures.length} failure(s), round ${round}.`)
    if (round === 3) return { prNumber, status: 'issues', failures: monitor.failures }
    await agent(ciFixerPrompt(prNumber, branch, monitor.failures), {
      label: `ci-fix-${prNumber}-r${round}`, phase: 'Babysit CI', schema: FIX_SCHEMA,
    })
  }
  return { prNumber, status: 'unresolved' }
}

phase('Spec')
log(`Spec -> implement -> review for ${ISSUES.length} issue(s), in parallel...`)
const implResults = (await parallel(ISSUES.map((issue) => () => specAndImplement(issue)))).filter(Boolean)
if (implResults.length !== ISSUES.length) throw new Error('One or more issue pipelines failed — stopping before stacking.')

phase('Stack & PRs')
const stacked = await agent(harmonizerPrompt(implResults), { label: 'harmonizer', phase: 'Stack & PRs', model: 'haiku', schema: STACK_SCHEMA })
if (!stacked) throw new Error('Harmonizer failed.')

// Map issue -> PR number from stacked.prUrls (same order as implResults) before babysitting.
const prNumbers = stacked.prUrls.map((u) => Number(u.split('/').pop()))

phase('Babysit CI')
const babysat = await parallel(
  implResults.map((r, i) => () => babysit(r.number, prNumbers[i], r.branch, i === 0 ? null : implResults[i - 1].branch)),
)

return { prUrls: stacked.prUrls, notes: stacked.notes, babysat }
