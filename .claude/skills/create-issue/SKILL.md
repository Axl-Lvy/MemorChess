---
name: create-issue
description: Create a GitHub issue for MemorChess that mirrors the repo's bug-report / feature-request issue forms. Use when the user asks to open, file, or create a GitHub issue.
---

# Create issue

Open a GitHub issue with `gh issue create`, structuring the body to match the
repo's issue forms in `.github/ISSUE_TEMPLATE/`. The `.yml` forms only apply in
the web UI, so when creating via CLI you must fill the same fields by hand.

## Steps

1. Decide the type from what the user is reporting:
   - **Bug** — something is broken or behaves unexpectedly. Label `bug`.
   - **Feature** — a new idea or improvement. Label `enhancement`.
   If it's ambiguous, ask the user which one.
2. Gather the fields below. If a required field is missing and you can't infer
   it, ask the user a brief question rather than guessing.
3. Create the issue:
   ```sh
   gh issue create --title "<title>" --label "<bug|enhancement>" --body "<body>"
   ```
4. Report the issue URL back to the user.

## Bug body (mirror of `bug_report.yml`)

```markdown
### What happened?
<description of the bug and expected behavior — required>

### Steps to reproduce
1. ...
2. ...

### Platform
<Android | iOS | JVM desktop | WebAssembly (web) — required>

### App version / commit
<version or git commit, if known>

### Logs or screenshots
<relevant logs, stack traces, or screenshots, if any>
```

## Feature body (mirror of `feature_request.yml`)

```markdown
### Problem or motivation
<what problem this solves or the motivation — required>

### Proposed solution
<the feature or behavior you'd like — required>

### Affected platforms
<Android | iOS | JVM desktop | WebAssembly (web) | All platforms>

### Alternatives considered
<alternative solutions or workarounds, if any>
```

## Notes

- Keep the title concise; no Conventional Commits prefix is required for issues
  (that rule is for PR titles only).
- Omit optional sections that are empty rather than leaving placeholder text.
