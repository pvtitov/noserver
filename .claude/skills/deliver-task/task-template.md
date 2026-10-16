# <Task title>

Status: interviewing
Approval: pending
Base commit: <git rev-parse HEAD at task start>
Created: <YYYY-MM-DD>

<!--
Machine-checked by .claude/hooks/task_doc_guard.py. Keep these formats exactly:
  Status:    interviewing | planning | awaiting-approval | in-progress | in-review | done | blocked | abandoned
  Approval:  pending | approved <YYYY-MM-DD> — "<the user's approving words>"
  Criteria:  "- [ ] **AC-n** ..." (unchecked), "- [x]" (verified, needs Evidence), "- [~]" (descoped, needs Descoped)
             with indented "- Verify:", "- Evidence:", "- Descoped:" lines
  Plan rows: "| Pn | item | covers | status |", status = todo | in-progress | done | descoped | blocked
  Review:    each round ends with "Verdict: approved | changes-requested | pending"
"Status: done" is rejected unless every criterion is verified with evidence (or descoped with
the user's approval), every plan item is done/descoped, no open question remains, and the
latest review verdict is approved.
-->

## Request

<The user's original request, verbatim.>

## Requirements

- R1: <what must be true, in user-observable terms>

## Out of scope

- <explicit non-goals agreed with the user>

## Assumptions

- <assumption> — confirmed by <user | task-owner> on <YYYY-MM-DD>

## Open questions

- [ ] Q1: <question> — <tick and record the answer, who gave it, and the date when resolved>

## Acceptance criteria

- [ ] **AC-1** <observable, unambiguous statement>
  - Verify: `<command that fails if the criterion is not met>` | manual: <step> by <who>
  - Evidence:

## Plan

| # | Item | Covers | Status |
|---|------|--------|--------|
| P1 | <small, independently verifiable step> | AC-1 | todo |

## Decisions log

- <YYYY-MM-DD> — <decision> — by <user | task-owner> — <why>

## Scope changes

<!-- Every change to requirements, criteria, or plan scope after approval, with the user's approving words. -->

## Verification log

- <YYYY-MM-DD> `<command>` → <PASS/FAIL, key output>

## Review log

### Round 1

<findings summary and how each was resolved>

Verdict: pending

## Outstanding issues

- <known issues, deferred findings, unverifiable criteria — with reasons>
