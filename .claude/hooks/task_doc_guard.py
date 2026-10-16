#!/usr/bin/env python3
"""Deterministic guards for the /deliver-task workflow.

Usage: task_doc_guard.py <mode>, hook input JSON on stdin.

  post-edit         PostToolUse(Edit|Write): validate a task doc right after it changes.
  stop              Stop: refuse to end the turn while any task doc is inconsistent
                    (e.g. "Status: done" with an acceptance criterion lacking evidence).
  implementer-gate  PreToolUse(Edit|Write) inside the implementer subagent: no code edits
                    without an approved plan, and no edits to the task docs themselves.
  readonly-bash     PreToolUse(Bash) inside read-only subagents: best-effort denylist of
                    mutating commands. Not a sandbox: it stops accidents, not intent.

The task-doc format these checks rely on is defined in
.claude/skills/deliver-task/task-template.md.
"""
import json
import os
import re
import sys
from pathlib import Path

PROJECT = Path(os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()).resolve()
TASKS = PROJECT / "docs" / "tasks"

STATUSES = {"interviewing", "planning", "awaiting-approval", "in-progress",
            "in-review", "done", "blocked", "abandoned"}
NEEDS_PLAN = {"awaiting-approval", "in-progress", "in-review", "done"}
NEEDS_APPROVAL = {"in-progress", "in-review", "done"}
IMPLEMENTABLE = {"in-progress", "in-review"}
PLAN_STATUSES = {"todo", "in-progress", "done", "descoped", "blocked"}
PLACEHOLDER_EVIDENCE = {"", "-", "tbd", "todo", "n/a", "...", "…"}


def read_input():
    try:
        return json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return {}


def task_docs():
    if not TASKS.is_dir():
        return []
    return sorted(p for p in TASKS.glob("*.md") if p.name != "README.md")


def is_task_doc(path):
    p = Path(path).resolve()
    return p.parent == TASKS.resolve() and p.suffix == ".md" and p.name != "README.md"


def field(text, name):
    m = re.search(rf"^{name}:[ \t]*(.*)$", text, re.M)
    return m.group(1).strip() if m else None


def section(text, title):
    m = re.search(rf"^## {re.escape(title)}[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    return m.group(1) if m else None


def acceptance_criteria(text):
    body = section(text, "Acceptance criteria") or ""
    items = []
    for block in re.split(r"(?m)^(?=- \[[ xX~]\])", body):
        head = re.match(r"- \[([ xX~])\]\s*\*\*(AC-\d+)\*\*", block)
        if not head:
            continue

        def sub(name):
            m = re.search(rf"^\s+- {name}:[ \t]*(.*)$", block, re.M)
            return m.group(1).strip() if m else None

        items.append({"mark": head.group(1).lower(), "id": head.group(2),
                      "verify": sub("Verify"), "evidence": sub("Evidence"),
                      "descoped": sub("Descoped")})
    return items


def plan_rows(text):
    body = section(text, "Plan") or ""
    rows = []
    for line in body.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 2 and re.fullmatch(r"P\d+", cells[0]):
            rows.append((cells[0], cells[-1].lower()))
    return rows


def validate(text):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    problems = []
    status = field(text, "Status")
    approval = field(text, "Approval")

    if status not in STATUSES:
        problems.append(f"'Status:' must be one of {sorted(STATUSES)}, found {status!r}.")
        return problems
    if approval is None or not (approval == "pending" or approval.startswith("approved")):
        problems.append("'Approval:' must be 'pending' or 'approved <date> — \"<user's words>\"'.")
    elif status in NEEDS_APPROVAL and not approval.startswith("approved"):
        problems.append(f"Status is '{status}' but the plan has no recorded user approval.")
    elif approval.startswith("approved") and not re.search(r"\d{4}-\d{2}-\d{2}", approval):
        problems.append("'Approval: approved' must include the date and the user's approving words.")

    acs = acceptance_criteria(text)
    rows = plan_rows(text)

    for ac in acs:
        if ac["mark"] == "~" and not ac["descoped"]:
            problems.append(f"{ac['id']} is marked descoped [~] without a '- Descoped:' line "
                            "citing the user's approval.")

    if status in NEEDS_PLAN:
        if not acs:
            problems.append("No acceptance criteria ('- [ ] **AC-n** ...') in '## Acceptance criteria'.")
        for ac in acs:
            if ac["mark"] != "~" and not ac["verify"]:
                problems.append(f"{ac['id']} has no '- Verify:' line.")
        if not rows:
            problems.append("No plan rows ('| P1 | ... | <status> |') in '## Plan'.")
        for pid, st in rows:
            if st not in PLAN_STATUSES:
                problems.append(f"Plan item {pid} has status {st!r}; use one of {sorted(PLAN_STATUSES)}.")

    if status == "done":
        for ac in acs:
            if ac["mark"] == " ":
                problems.append(f"{ac['id']} is unchecked; 'done' needs every criterion verified or descoped.")
            elif ac["mark"] == "x" and (ac["evidence"] or "").lower() in PLACEHOLDER_EVIDENCE:
                problems.append(f"{ac['id']} is checked but its '- Evidence:' is empty.")
        for pid, st in rows:
            if st not in {"done", "descoped"}:
                problems.append(f"Plan item {pid} is '{st}'; 'done' needs every item done or descoped.")
        if re.search(r"(?m)^- \[ \]", section(text, "Open questions") or ""):
            problems.append("'## Open questions' still has unresolved '- [ ]' items.")
        verdicts = re.findall(r"(?m)^Verdict:[ \t]*(\S+)", section(text, "Review log") or "")
        if not verdicts or verdicts[-1].lower() != "approved":
            problems.append("The latest 'Verdict:' in '## Review log' is not 'approved'.")

    return problems


def report(path, problems):
    rel = Path(path).resolve().relative_to(PROJECT) if PROJECT in Path(path).resolve().parents else path
    return f"{rel}:\n" + "\n".join(f"  - {p}" for p in problems)


def post_edit(data):
    path = (data.get("tool_input") or {}).get("file_path", "")
    if not path or not is_task_doc(path) or not Path(path).is_file():
        return 0
    problems = validate(Path(path).read_text(encoding="utf-8"))
    if problems:
        print("Task document is inconsistent with the /deliver-task rules. Fix it now "
              "(revert the status if the work does not support it):\n" + report(path, problems),
              file=sys.stderr)
        return 2
    return 0


def stop(data):
    # A second Stop in a row means we already blocked once; let the turn end so a doc
    # Claude cannot fix on its own (e.g. waiting for the user) doesn't loop forever.
    if data.get("stop_hook_active"):
        return 0
    failures = []
    for doc in task_docs():
        problems = validate(doc.read_text(encoding="utf-8"))
        if problems:
            failures.append(report(doc, problems))
    if failures:
        print(json.dumps({
            "decision": "block",
            "reason": "Task documents are inconsistent; fix them (or downgrade their Status) "
                      "before finishing, and tell the user what is unverified:\n" + "\n".join(failures),
        }))
    return 0


def implementer_gate(data):
    path = (data.get("tool_input") or {}).get("file_path", "")
    resolved = Path(path).resolve() if path else None
    if resolved and (resolved == TASKS.resolve() or TASKS.resolve() in resolved.parents):
        print("The implementer must not edit task documents; report progress and evidence to the "
              "orchestrator, which owns docs/tasks/.", file=sys.stderr)
        return 2
    if resolved and (PROJECT / ".claude").resolve() in resolved.parents:
        print("The implementer must not edit workflow configuration under .claude/.", file=sys.stderr)
        return 2
    for doc in task_docs():
        text = re.sub(r"<!--.*?-->", "", doc.read_text(encoding="utf-8"), flags=re.S)
        if field(text, "Status") in IMPLEMENTABLE and (field(text, "Approval") or "").startswith("approved"):
            return 0
    print("No task in docs/tasks/ is 'in-progress'/'in-review' with 'Approval: approved'. "
          "Implementation needs a user-approved plan; stop and report back.", file=sys.stderr)
    return 2


MUTATING_BASH = [
    (r"(^|[;&|(]\s*|\bxargs\s+)(rm|rmdir|mv|cp|touch|mkdir|chmod|chown|ln|tee|truncate|dd|install|patch)\b",
     "file-mutating command"),
    (r"\bsed\b[^|;&]*\s-i", "in-place sed"),
    (r"\bperl\b[^|;&]*\s-[a-zA-Z]*i", "in-place perl"),
    (r"\bgit\s+(-C\s+\S+\s+)?(add|commit|push|pull|fetch|reset|checkout|switch|restore|stash|rebase|merge|"
     r"cherry-pick|revert|clean|rm|mv|tag|apply|am|worktree|config|init|gc|prune|update-ref)\b",
     "mutating git command"),
    (r"\bgit\s+(-C\s+\S+\s+)?branch\s+-[dDmMcC]", "mutating git command"),
    (r"\bgradlew?\b[^;&|]*\bpublish", "publishing build task"),
    (r"\b(curl|wget)\b[^|;&]*\s(-o|-O|--output)\b", "download to file"),
]


def readonly_bash(data):
    cmd = (data.get("tool_input") or {}).get("command", "")
    # Discarding output and merging stderr are harmless; any other redirect writes a file.
    scrubbed = re.sub(r"\d*>>?\s*/dev/null|\d*>&\d", "", cmd)
    if re.search(r">", scrubbed):
        print("Read-only agent: output redirection to a file is not allowed.", file=sys.stderr)
        return 2
    for pattern, label in MUTATING_BASH:
        if re.search(pattern, scrubbed):
            print(f"Read-only agent: {label} is not allowed. Report the needed change instead.",
                  file=sys.stderr)
            return 2
    return 0


MODES = {"post-edit": post_edit, "stop": stop,
         "implementer-gate": implementer_gate, "readonly-bash": readonly_bash}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in MODES:
        print(f"usage: {sys.argv[0]} {{{'|'.join(MODES)}}}", file=sys.stderr)
        sys.exit(1)
    sys.exit(MODES[sys.argv[1]](read_input()))
