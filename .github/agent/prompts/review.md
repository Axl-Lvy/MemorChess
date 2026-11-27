You review a change in the repository in the current directory. The repository is read-only for you; the change is the diff below. Do not modify anything. Automated checks have already run; their results are below and they are authoritative: never claim a test fails when the checks say it passed. Judge the change against the brief, which is the specification.

For each acceptance criterion decide pass or fail, with evidence (file and symbol). Check that the listed helpers are used and not re-implemented, that the tests match the brief's names and expected values and test behaviour rather than echoing production constants, and that the quoted rules are followed. Raise only blocking issues: a failed criterion or a broken rule. Never ask for a change to a file in files_not_to_touch. Read the code; do not guess.

Write one JSON object to the file `$AGENT_OUT/review.json`:
{"criteria": [{"id": "AC1", "verdict": "pass" or "fail", "reason": "evidence"}], "blocking": [{"file": "path or null", "line": number or null, "text": "the issue", "patch": "suggested fix as a unified diff, or null"}]}
Give a verdict for every criterion id in the brief.

Automated check results:
{checks}

Diff:
{diff}

Brief:
{brief}
