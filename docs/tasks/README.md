# Task documents

One file per task delivered with `/deliver-task`, named `<task-slug>.md` and created from
`.claude/skills/deliver-task/task-template.md`. Each holds the requirements, acceptance criteria,
plan status, decisions, verification evidence, and review log for that task. The format is
checked by `.claude/hooks/task_doc_guard.py`, so edit the status lines by hand with care.
