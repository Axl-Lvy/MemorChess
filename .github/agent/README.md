# Agent step runner

Turns one issue into a reviewed, checks-green change: **triage → implement → trusted checks →
review → repair**, with a regression guard. Every LLM step's harness, model and prompt is
chosen in `pipeline.toml`, so trying a new model or harness is a config change.

This is phase P1 of the agent pipeline: it runs **offline** (issue JSON in, a directory of
results out) and makes no GitHub calls. The GitHub Actions workflow that wires it to issue
labels comes later and calls `run.py` the same way.

## Run it

```sh
gh issue view 447 --json number,title,body,comments \
  --jq '{number,title,body,comments:[.comments[].body]}' > /tmp/issue-447.json

JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
AGENT_GRADLE_HOME=/tmp/agent-gradle-home \
python3 -I .github/agent/run.py --issue /tmp/issue-447.json --repo . \
  --base "$(git rev-parse origin/master)" --out /tmp/agent-447 \
  --set implement.model=gemma4-26b          # optional, repeatable
```

Exit code 0 means handed off, needs info, or brief ready; 1 means failed; 2 means a config or
preflight error (nothing ran). Everything lands in `--out`:

| Path | What |
|---|---|
| `run.json` | outcome, best commit, approved, every stage's check report, every step's harness, model, prompt hash, turns, tokens, wall time |
| `brief.json` / `questions.json` | triage's output |
| `stages/NN-<stage>.patch` | the diff each implementation stage produced |
| `work/trusted` | the clone holding the result; `best_sha` is the commit to hand off |
| `work/<stage>/` | the scratch tree, harness logs (`*.stream.jsonl`, outside what the agent can write), Gradle logs |
| `traceback.txt` | only when the run crashed; `run.json` then names the stage and the error |

## Configuration

`pipeline.toml`, overridable per run with `--set key=value`:

| Setting | Meaning |
|---|---|
| `steps.<step>.harness` | `claude-code`, `chat` (one JSON chat completion, no tools), or `fake` (tests) |
| `steps.<step>.model` | LiteLLM model name |
| `steps.<step>.prompt` | prompt template, relative to `pipeline.toml`; placeholders `{issue}`, `{brief}`, `{diff}`, `{checks}`, `{repo_map}`, `{modules}` |
| `steps.<step>.max_turns`, `timeout_s`, `retries` | limits per call; `retries` = extra tries on missing or invalid output |
| `steps.<step>.enabled` | `review` can be disabled (no-reviewer baseline); `triage` and `implement` cannot. `precheck` is not wired into the loop yet |
| `fix_rounds`, `attempts` | repair rounds per attempt, and fresh attempts from the base |
| `brief_gate` | stop after the brief (outcome `brief_ready`) |
| `llm_base_url` | OpenAI/Anthropic-compatible endpoint the harnesses talk to |
| `modules.<m>` | Gradle compile, format and test tasks and the JUnit results dir per module |
| `deny_paths`, `build_files`, `stray_globs` | patch rules; build files need `build_files_allowed` in the brief |
| `java_min` | the run refuses to start on an older JDK |
| `sandbox_ro_binds` | extra read-only paths inside the sandbox (e.g. a harness's install dir) |

## What is trusted and what is not

- **Harness steps** run in a bubblewrap sandbox that starts empty: `/usr`, `/etc`, the JDK and
  their own scratch tree only; the implementer also gets a fresh copy of the Gradle home.
  Triage and review get no Gradle home. They hold no GitHub token or key, and they can write
  only `$AGENT_OUT` and their own home; the runner reads their outputs without following links.
- **Trusted code** (this package) never runs anything the agent wrote outside the sandbox. It
  reads the agent's work as a patch, rejects deny-listed paths, build files (unless the brief
  allows them), `.gitattributes`, `.gitmodules`, symlinks and stray files, and applies the rest
  to a separate clone whose git directory the sandbox never sees.
- **Gradle** (compile, ktfmt, tests) runs `--offline`, without daemons, inside the sandbox, in a
  disposable checkout of the commit under test with its own fresh Gradle home; agent-written
  test results are read without following links.
- **Feedback ranking:** trusted check results outrank the LLM reviewer; review findings that
  contradict a check, or target a file the brief forbids, are dropped and listed in `run.json`.

Known gap in P1: the sandbox shares the host network (the harness needs the model endpoint on
localhost). Egress is restricted in CI, outside the sandbox.

## Host requirements

- **bubblewrap** with unprivileged user namespaces. Ubuntu 24.04+ blocks them through AppArmor;
  allow bwrap only:

  ```sh
  sudo tee /etc/apparmor.d/bwrap <<'EOF'
  abi <abi/4.0>,
  include <tunables/global>
  profile bwrap /usr/bin/bwrap flags=(unconfined) {
    userns,
    include if exists <local/bwrap>
  }
  EOF
  sudo apparmor_parser -r /etc/apparmor.d/bwrap
  ```

  The run checks `bwrap`, the sandboxed JDK, the Gradle home and the harness binary before any
  model call, and refuses to start if one is missing.
- **JDK 21 or later** in `JAVA_HOME` (`chess-core` needs 21; a missing `JAVA_HOME` silently
  falls back to whatever `java` is on `PATH`, which is how Java 17 sneaks in).
- **A warmed Gradle home template** in `AGENT_GRADLE_HOME`: Gradle runs offline, so the wrapper
  distribution (and the toolchain JDKs, Node and Yarn the build uses) must already be there.
  The template is never mounted: each implement round and each check run gets a fresh copy
  (`cp -a --reflink=auto`), deleted afterwards, so nothing an agent writes there persists.
  `GRADLE_RO_DEP_CACHE` can point at a read-only dependency cache (for example
  `~/.gradle/caches`). A minimal template:

  ```sh
  T=/srv/agent-gradle-home; mkdir -p $T/wrapper/dists
  cp -a ~/.gradle/wrapper/dists/gradle-9.6.1-bin $T/wrapper/dists/
  cp -a ~/.gradle/jdks ~/.gradle/nodejs ~/.gradle/yarn $T/
  ```

  Preflight refuses to start when the wrapper distribution named in
  `gradle/wrapper/gradle-wrapper.properties` is missing from it.
- **The harness on the host**, e.g. Claude Code for `claude-code`: `AGENT_CLAUDE_BIN`, else
  `~/.local/bin/claude`, resolved through symlinks; its directory is mounted read-only. It is
  never resumed: each repair round restarts it with the brief, the current diff and the feedback.

## Tests

```sh
python3 -m unittest discover -s .github/agent -t .github/agent -p 'test_*.py'
```

No test needs Gradle, a model or the network: a fake `gradlew` and a scripted adapter stand in.
