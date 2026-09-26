---
name: next-task
description: Runs one task from docs/tasks/ end to end — branch, developer, CI, review — and opens a PR. Optional argument: task number.
disable-model-invocation: true
---
Run exactly one task through the full loop. Follow the Git workflow in CLAUDE.md.
Wait for each subagent to finish before starting the next step.

## 1. Pick the task
- `git fetch origin`.gi
- `gh pr list --state open`. If any open PR (including drafts) belongs to a task/
  branch, stop and report it: tasks run sequentially and it must be resolved first.
- Read docs/tasks/README.md from origin/ai-modernization
  (`git show origin/ai-modernization:docs/tasks/README.md`).
- If a task number was given ($ARGUMENTS), use it. Otherwise take the first task with
  status TODO whose dependencies are all DONE. If none qualifies, stop and report why.
- Create the branch: `git switch -c task/<NNN>-<slug> origin/ai-modernization`.

## 2. Implement
Run the developer subagent with the task path. If it reports BLOCKED, go to step 6
(escalation).

## 3. CI
Run the ci-verifier subagent.
- PASS: go to step 4.
- FAIL: resume the SAME developer subagent (SendMessage to its ID) with the full
  ci-verifier report, then run ci-verifier again. At most 3 CI rounds in total.
- INFRA_ERROR, or CI still failing after 3 rounds: go to step 6.

## 4. Review
Run the change-reviewer subagent. Tell it the iteration number, the CI result and run
URL, and from iteration 2 on, the previous review.
- PASS: go to step 5.
- CHANGES_REQUESTED: resume the SAME developer subagent with the review, then go back
  to step 3. At most 3 review iterations in total.
- ESCALATED, or still CHANGES_REQUESTED after 3 iterations: go to step 6.

## 5. Finish
- Save the last review verbatim as docs/tasks/<NNN>-<slug>/review.md.
- In docs/tasks/README.md set this task's status to DONE.
- Commit: `docs(task-NNN): review and status`.
- Run ci-verifier once more so CI is green on the last commit.
- Open the PR: `gh pr create --base ai-modernization --title "TASK-NNN: <title>"`
  with a body containing:
  - summary of the change
  - acceptance criteria table from the review
  - behavior changes
  - "Human checks on device" as a checklist
  - CI run URL and number of CI rounds and review iterations used
- Report the PR URL.

## 6. Escalation
- Save the latest review (if any) as docs/tasks/<NNN>-<slug>/review.md; do not change
  the task status in README.md.
- Commit and push what exists so far.
- Open a DRAFT PR (`gh pr create --draft ...`) whose body explains exactly where and
  why the loop stopped, what the human needs to decide or fix, and links the evidence.
- Report the PR URL and the reason.