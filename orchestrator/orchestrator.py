import json
import re
import subprocess
import tomllib
import argparse
from pathlib import Path


PROJECT_PATH = Path(__file__).resolve().parents[1]
ORIGINAL_PROJECT_PATH = PROJECT_PATH.parent / "smart-flight"
AGENTS_PATH = PROJECT_PATH / "agents"
WORKFLOW_FILE = PROJECT_PATH / ".ai" / "workflow.json"

MAX_REVIEW_ITERATIONS = 3


def resume_workflow():
    workflow = load_workflow()

    if workflow is None:
        raise RuntimeError("Cannot resume: workflow.json does not exist.")

    stage = workflow["stage"]

    resumable_stages = {
        "ESCALATED",
        "DEVELOPER_FIX",
        "READY_FOR_REVIEW",
        "REVIEWING",
        "READY_FOR_FEATURE_PUSH",
    }

    if stage not in resumable_stages:
        raise RuntimeError(
            f"Cannot resume workflow from stage: {stage}."
        )

    task_id = workflow["task_id"]

    print()
    print(f"=== RESUMING WORKFLOW: {task_id} ===")


    if stage == "ESCALATED":
        feedback = workflow.get("review_feedback")

        if not feedback:
            raise RuntimeError(
                "Cannot resume escalated workflow: "
                "review_feedback is missing."
            )

        print()
        print("=" * 80)
        print("WORKFLOW ESCALATED")
        print("=" * 80)
        print()
        print(f"Task: {task_id}")
        print()
        print("Final reviewer feedback:")
        print("-" * 80)
        print(feedback)
        print("-" * 80)
        print()

        answer = input(
            "Start another Developer -> Reviewer cycle? [y/N]: "
        ).strip().lower()

        if answer != "y":
            print()
            print("Workflow remains ESCALATED.")
            return

        next_iteration = workflow["review_iteration"] + 1

        print()
        print(
            f"Starting new review cycle with "
            f"review iteration {next_iteration}."
        )

        update_workflow(
            stage="DEVELOPER_FIX",
            review_iteration=next_iteration,
            review_attempt=1,
            review_result=None,
            review_feedback=feedback,
        )
    else:
        print(f"Resuming from stage: {stage}")

    run_workflow()


def load_agent_config(agent_name):
    config_path = AGENTS_PATH / f"{agent_name}.toml"

    with config_path.open("rb") as file:
        return tomllib.load(file)


def load_workflow():
    if not WORKFLOW_FILE.exists():
        return None

    with WORKFLOW_FILE.open("r", encoding="utf-8") as file:
        return json.load(file)


def save_workflow(workflow):
    temporary_file = WORKFLOW_FILE.with_suffix(".tmp")

    with temporary_file.open("w", encoding="utf-8") as file:
        json.dump(
            workflow,
            file,
            indent=2,
        )
        file.write("\n")

    temporary_file.replace(WORKFLOW_FILE)


def update_workflow(**updates):
    workflow = load_workflow()

    if workflow is None:
        workflow = {}

    workflow.update(updates)

    save_workflow(workflow)

    return workflow


def run_agent(agent_name, workflow_prompt):
    config = load_agent_config(agent_name)

    prompt = f"""
{config["developer_instructions"]}

---

CURRENT WORKFLOW CONTEXT

{workflow_prompt}
"""

    command = [
        "codex",
        "exec",
        "--model",
        config["model"],
        "--sandbox",
        config["sandbox_mode"],
        "-C",
        str(PROJECT_PATH),
        "--add-dir",
        str(ORIGINAL_PROJECT_PATH),
        "-c",
        f'model_reasoning_effort="{config["model_reasoning_effort"]}"',
        prompt,
    ]

    print(f"\nStarting {agent_name}...")
    print("-" * 80)

    result = subprocess.run(
        command,
        text=True,
        capture_output=True,
    )

    if result.returncode != 0:
        raise RuntimeError(
            f"{agent_name} failed with exit code "
            f"{result.returncode}:\n\n"
            f"{result.stderr}"
        )

    print(result.stdout)

    return result.stdout


def run_git(*args):
    result = subprocess.run(
        ["git", *args],
        cwd=PROJECT_PATH,
        text=True,
        capture_output=True,
    )

    if result.returncode != 0:
        raise RuntimeError(
            f"Git command failed:\n"
            f"git {' '.join(args)}\n\n"
            f"{result.stderr}"
        )

    return result.stdout.strip()


def current_branch():
    return run_git("branch", "--show-current")


def push_branch(branch):
    print(f"\nPushing branch: {branch}")
    print("-" * 80)

    run_git("push", "-u", "origin", branch)

    print(f"Branch {branch} pushed successfully.")


def push_main():
    branch = current_branch()

    if branch != "main":
        raise RuntimeError(
            f"Expected current branch to be main, "
            f"but found: {branch}"
        )

    print("\nPushing main...")
    print("-" * 80)

    run_git("push", "origin", "main")

    print("main pushed successfully.")


def extract_task_id(text):
    matches = re.findall(r"\bTASK-\d+\b", text)

    if not matches:
        raise RuntimeError(
            "Unable to determine TASK-XXX from Architect output."
        )

    return matches[-1]


def extract_developer_branch(text, task_id):
    expected_branch = f"feat/{task_id}"

    if expected_branch in text:
        return expected_branch

    raise RuntimeError(
        f"Developer did not report expected branch "
        f"{expected_branch}."
    )


def extract_review_result(text):
    match = re.search(
        r"(?m)^RESULT:\s*(PASS|CHANGES_REQUESTED|ESCALATED)\s*$",
        text.upper(),
    )

    if not match:
        raise RuntimeError(
            "Reviewer output does not contain a valid result.\n\n"
            "Expected one of:\n"
            "RESULT: PASS\n"
            "RESULT: CHANGES_REQUESTED\n"
            "RESULT: ESCALATED"
        )

    return match.group(1)


def extract_review_feedback(text):
    match = re.search(
        r"(?ms)^FEEDBACK:\s*(.*)$",
        text,
    )

    if not match:
        return ""

    return match.group(1).strip()


def review_artifact_path(task_id, iteration):
    return (
        PROJECT_PATH
        / ".ai"
        / "reviews"
        / f"{task_id}-iteration-{iteration}.md"
    )


def read_review_artifact(task_id, iteration):
    path = review_artifact_path(task_id, iteration)

    if not path.exists():
        return None

    return path.read_text(encoding="utf-8")


def extract_review_result_from_artifact(text):
    match = re.search(
        r"(?mis)^##\s*Result\s*$\s*"
        r"(PASS|CHANGES_REQUESTED|ESCALATED)\s*$",
        text,
    )

    if not match:
        return None

    return match.group(1).upper()


def extract_blocking_findings_from_artifact(text):
    match = re.search(
        r"(?ms)^##\s*Blocking Findings\s*$"
        r"(.*?)"
        r"(?=^##\s*Non-Blocking Findings\s*$)",
        text,
    )

    if match:
        return match.group(1).strip()

    return text.strip()


def recover_review_result(workflow):
    task_id = workflow["task_id"]
    iteration = workflow["review_iteration"]

    artifact = read_review_artifact(
        task_id,
        iteration,
    )

    if artifact is None:
        return None

    result = extract_review_result_from_artifact(artifact)

    if result is None:
        return None

    feedback = extract_blocking_findings_from_artifact(
        artifact
    )

    return result, feedback


def run_architect():
    response = run_agent(
        "architect",
        f"""
Inspect the current Smart Flight project state and determine the next
logical implementation task.

Review:

- existing .ai/tasks
- existing .ai/designs
- existing .ai/reviews
- the current rewritten application
- the original Smart Flight application

The original application is:

{ORIGINAL_PROJECT_PATH}

The rewritten application is:

{PROJECT_PATH}

Create the next TASK-XXX specification under:

.ai/tasks/

Follow all requirements defined in your Architect instructions.

Do not implement the task.

Do not modify application source code.

Do not modify tests.

Do not modify Gradle configuration.

Do not create a branch.

Before finishing:

1. Review the task.
2. Check git status.
3. Check git diff.
4. Commit ONLY the task specification.

Use:

docs(task): add TASK-XXX specification

Do not push.

At the end of your response use exactly this format:

TASK_ID: TASK-XXX
TASK_FILE: .ai/tasks/TASK-XXX.md
COMMIT: <commit hash>

Then provide a short summary of important decisions and open questions.
        """,
    )

    task_id = extract_task_id(response)

    return task_id, response


def run_designer(task_id):
    response = run_agent(
        "designer",
        f"""
You are now working on:

Task: {task_id}

Read:

.ai/tasks/{task_id}.md

This task is the authoritative description of WHAT must be implemented.

Create the corresponding design:

.ai/designs/{task_id}.md

Use the task, existing Smart Flight architecture and, where necessary,
the original application as input.

The design should describe HOW the task should be implemented without
unnecessarily prescribing implementation details.

Do not implement application code.

Do not modify tests.

Do not modify Gradle configuration.

Work on the current main branch.

Before finishing:

1. Review the design.
2. Check git status.
3. Check git diff.
4. Commit ONLY the design specification.

Use:

docs(design): add {task_id} design

Do not push.

At the end of your response use exactly this format:

TASK_ID: {task_id}
DESIGN_FILE: .ai/designs/{task_id}.md
COMMIT: <commit hash>

Then provide a short summary of important design decisions and open questions.
        """,
    )

    return response


def run_developer(
    task_id,
    review_feedback=None,
    review_iteration=0,
):
    branch = f"feat/{task_id}"

    if review_feedback:
        workflow_context = f"""
This is a review-fix iteration.

Task:

{task_id}

Continue working on the EXISTING feature branch:

{branch}

Do NOT create a new branch.

The previous Reviewer returned:

CHANGES_REQUESTED

Reviewer feedback:

---------------- REVIEW FEEDBACK ----------------

{review_feedback}

-------------- END REVIEW FEEDBACK --------------

Address all actionable BLOCKING_IMPLEMENTATION findings.

Do not make unrelated changes.

After applying the fixes:

1. Run relevant tests and verification where possible.
2. Inspect git status.
3. Inspect git diff.
4. Commit the fixes.
5. Do not push.

This is review iteration:

{review_iteration}

At the end report:

TASK_ID: {task_id}
BRANCH: {branch}
COMMIT: <commit hash>
IMPLEMENTATION_STATUS: COMPLETE
"""
    else:
        workflow_context = f"""
This is the initial implementation of:

{task_id}

Read:

.ai/tasks/{task_id}.md

and:

.ai/designs/{task_id}.md

The task defines WHAT must be implemented.

The design defines HOW the UI/UX and implementation should behave.

Before modifying any implementation files:

1. Check the current branch.
2. Create:

   {branch}

   from the current base branch.
3. Switch to that branch.
4. Verify that the current branch is exactly:

   {branch}

Only then modify application source code, tests or other implementation
files.

Implement the task.

Add appropriate tests.

Do not push.

Before finishing:

1. Run relevant tests and verification where possible.
2. Inspect git status.
3. Inspect git diff.
4. Commit the implementation.

At the end report:

TASK_ID: {task_id}
BRANCH: {branch}
COMMIT: <commit hash>
IMPLEMENTATION_STATUS: COMPLETE
"""

    response = run_agent(
        "developer",
        workflow_context,
    )

    reported_branch = extract_developer_branch(
        response,
        task_id,
    )

    actual_branch = current_branch()

    if actual_branch != branch:
        raise RuntimeError(
            f"Developer reported branch {branch}, "
            f"but current Git branch is {actual_branch}."
        )

    return reported_branch, response


def run_reviewer(
    task_id,
    branch,
    iteration,
):
    response = run_agent(
        "reviewer",
        f"""
Review:

Task: {task_id}

Task specification:

.ai/tasks/{task_id}.md

Design specification:

.ai/designs/{task_id}.md

Developer feature branch:

{branch}

This is review iteration:

{iteration}

Review the implementation independently.

Check:

1. Architect task requirements.
2. Designer requirements.
3. Existing project architecture and conventions.
4. Tests.
5. Obvious correctness issues.

Do not modify application source code.

Do not fix issues yourself.

The orchestrator controls the maximum number of review iterations.

Do not decide whether another iteration is allowed.

Create the review artifact:

.ai/reviews/{task_id}-iteration-{iteration}.md

The artifact must contain the complete review.

Commit ONLY that review artifact.

Use:

docs(review): add {task_id} review iteration {iteration}

Do not push.

Do not create a PR.

IMPORTANT:

At the end of your response, provide the workflow result in EXACTLY
this format:

RESULT: PASS

or:

RESULT: CHANGES_REQUESTED

or:

RESULT: ESCALATED

Immediately after that provide:

FEEDBACK:

<concise actionable feedback for the Developer>

For PASS, write:

FEEDBACK:
None.

For ESCALATED, explain what requires Architect, Designer or human
intervention.

The RESULT and FEEDBACK are the communication protocol with the
orchestrator.
        """,
    )

    result = extract_review_result(response)
    feedback = extract_review_feedback(response)

    return result, feedback


def start_new_workflow():
    print("\nNo active workflow found.")
    print("Starting new workflow with Architect.")

    task_id, architect_response = run_architect()

    update_workflow(
        task_id=task_id,
        stage="READY_FOR_DESIGNER",
        branch=None,
        review_iteration=0,
        review_attempt=0,
        review_result=None,
        review_feedback=None,
    )

    return task_id


def handle_ready_for_designer(workflow):
    task_id = workflow["task_id"]

    print(
        f"\n=== DESIGNER: {task_id} ==="
    )

    run_designer(task_id)

    update_workflow(
        task_id=task_id,
        stage="READY_FOR_DEVELOPER",
        branch=None,
        review_iteration=0,
    )


def handle_ready_for_developer(workflow):
    task_id = workflow["task_id"]

    print(
        f"\n=== DEVELOPER: {task_id} ==="
    )

    branch, _ = run_developer(
        task_id=task_id,
    )

    update_workflow(
        task_id=task_id,
        stage="READY_FOR_REVIEW",
        branch=branch,
        review_iteration=1,
        review_attempt=1,
        review_result=None,
        review_feedback=None,
    )


def _apply_review_result(workflow, result, feedback):
    task_id = workflow["task_id"]
    iteration = workflow["review_iteration"]
    attempt = workflow.get("review_attempt", 1)

    print(f"Reviewer result: {result}")

    if result == "PASS":
        update_workflow(
            stage="READY_FOR_FEATURE_PUSH",
            review_result="PASS",
            review_feedback=None,
        )
        return

    if result == "ESCALATED":
        update_workflow(
            stage="ESCALATED",
            review_result="ESCALATED",
            review_feedback=feedback,
        )

        print()
        print(f"Workflow escalated for {task_id}.")
        print(f"Reason: {feedback}")
        return

    if result == "CHANGES_REQUESTED":
        if attempt >= MAX_REVIEW_ITERATIONS:
            update_workflow(
                stage="ESCALATED",
                review_result="CHANGES_REQUESTED",
                review_feedback=feedback,
            )

            print()
            print(
                f"Maximum review iterations "
                f"({MAX_REVIEW_ITERATIONS}) reached for {task_id}."
            )
            print("Workflow escalated.")
            print(f"Final reviewer feedback: {feedback}")
            return

        update_workflow(
            stage="DEVELOPER_FIX",
            review_result="CHANGES_REQUESTED",
            review_feedback=feedback,
            review_attempt=attempt + 1,
            review_iteration=iteration + 1,
        )
        return

    raise RuntimeError(f"Unknown reviewer result: {result}")


def handle_ready_for_review(workflow):
    task_id = workflow["task_id"]
    branch = workflow["branch"]
    iteration = workflow["review_iteration"]

    print()
    print(
        f"=== REVIEWER: {task_id} "
        f"(iteration {workflow.get('review_attempt', 1)}/"
        f"{MAX_REVIEW_ITERATIONS}, artifact {iteration}) ==="
    )

    update_workflow(
        stage="REVIEWING",
        review_result=None,
        review_feedback=None,
    )

    result, feedback = run_reviewer(
        task_id=task_id,
        branch=branch,
        iteration=iteration,
    )

    # Persist the reviewer result before doing any further workflow logic.
    update_workflow(
        stage="REVIEWING",
        review_result=result,
        review_feedback=feedback,
    )

    _apply_review_result(
        load_workflow(),
        result,
        feedback,
    )


def handle_reviewing(workflow):
    result = workflow.get("review_result")
    feedback = workflow.get("review_feedback", "")

    if result:
        _apply_review_result(
            workflow,
            result,
            feedback,
        )
        return

    print()
    print(
        "Reviewer result is not persisted. "
        "Checking for an existing review artifact..."
    )

    recovered = recover_review_result(workflow)

    if recovered:
        result, feedback = recovered

        print()
        print(
            f"Recovered reviewer result from review artifact: "
            f"{result}"
        )

        print()
        print("Recovered reviewer feedback:")
        print("-" * 80)
        print(feedback)
        print("-" * 80)

        # Persist the recovered result and feedback before continuing.
        update_workflow(
            stage="REVIEWING",
            review_result=result,
            review_feedback=feedback,
        )

        _apply_review_result(
            load_workflow(),
            result,
            feedback,
        )
        return

    print()
    print(
        "No completed review artifact found. "
        "Re-running reviewer."
    )

    handle_ready_for_review(workflow)


def handle_developer_fix(workflow):
    task_id = workflow["task_id"]
    branch = workflow["branch"]
    feedback = workflow.get("review_feedback")
    iteration = workflow["review_iteration"]

    print()
    print(
        f"=== DEVELOPER FIX: {task_id} "
        f"(review attempt {workflow.get('review_attempt', 1)}) ==="
    )

    if not feedback:
        previous_iteration = max(1, iteration - 1)

        print()
        print(
            f"review_feedback is missing. "
            f"Trying to recover feedback from review artifact "
            f"iteration {previous_iteration}..."
        )

        artifact = read_review_artifact(
            task_id,
            previous_iteration,
        )

        if artifact:
            feedback = extract_blocking_findings_from_artifact(
                artifact
            )

            if feedback:
                print()
                print(
                    "Recovered review feedback from artifact."
                )

                update_workflow(
                    review_feedback=feedback,
                )

        if not feedback:
            raise RuntimeError(
                f"Developer fix requested for {task_id}, "
                "but review_feedback is missing and "
                "could not be recovered from the previous "
                "review artifact."
            )

    current = current_branch()

    if current != branch:
        raise RuntimeError(
            f"Developer fix expected branch {branch}, "
            f"but current branch is {current}"
        )

    run_developer(
        task_id=task_id,
        review_feedback=feedback,
        review_iteration=iteration,
    )

    update_workflow(
        stage="READY_FOR_REVIEW",
        review_result=None,
        review_feedback=None,
    )


def handle_ready_for_feature_push(workflow):
    task_id = workflow["task_id"]
    branch = workflow["branch"]

    print(
        f"\n=== PUSH FEATURE BRANCH: {task_id} ==="
    )

    actual_branch = current_branch()

    if actual_branch != branch:
        raise RuntimeError(
            f"Expected current branch {branch}, "
            f"but found {actual_branch}."
        )

    push_branch(branch)

    update_workflow(
        task_id=task_id,
        stage="WAITING_FOR_MERGE",
        branch=branch,
        review_iteration=workflow["review_iteration"],
        review_result="PASS",
        review_feedback=None,
    )

    print()
    print("=" * 80)
    print(f"{task_id} WAITING FOR MERGE")
    print("=" * 80)
    print()
    print(f"Feature branch pushed: {branch}")
    print("Create and merge the GitHub PR.")
    print("The orchestrator will not start another task.")


def run_workflow():
    while True:
        workflow = load_workflow()

        if workflow is None or workflow["stage"] == "COMPLETED":
            start_new_workflow()
            continue

        stage = workflow["stage"]

        print()
        print(f"Current stage: {stage}")

        if stage == "READY_FOR_DESIGNER":
            handle_ready_for_designer(workflow)

        elif stage == "READY_FOR_DEVELOPER":
            handle_ready_for_developer(workflow)

        elif stage == "READY_FOR_REVIEW":
            handle_ready_for_review(workflow)

        elif stage == "REVIEWING":
            handle_reviewing(workflow)

        elif stage == "DEVELOPER_FIX":
            handle_developer_fix(workflow)

        elif stage == "READY_FOR_FEATURE_PUSH":
            handle_ready_for_feature_push(workflow)
        
        elif stage == "WAITING_FOR_MERGE":
            print()
            print("Workflow is waiting for the GitHub PR to be merged.")
            print("Stopping orchestrator.")
            return

        elif stage == "ESCALATED":
            print()
            print("Workflow escalated.")
            print(
                f"Feedback: "
                f"{workflow.get('review_feedback')}"
            )
            return

        else:
            raise RuntimeError(
                f"Unknown workflow stage: {stage}"
            )


def main():
    parser = argparse.ArgumentParser(
        description="Smart Flight Autonomous Orchestrator"
    )

    parser.add_argument(
        "--resume",
        action="store_true",
        help="Resume an interrupted or escalated workflow",
    )

    args = parser.parse_args()

    print()
    print("========================================")
    print("Smart Flight Autonomous Orchestrator")
    print("========================================")

    if args.resume:
        resume_workflow()
    else:
        run_workflow()


if __name__ == "__main__":
    main()