---
name: deliver-task
description: Gated delivery workflow for a code change — requirements interview, numbered plan with acceptance criteria, explicit user approval, incremental implementation via the implementer subagent, tests, independent code review loop, task-owner acceptance, and an evidence-backed report. State lives in docs/tasks/<slug>.md.
argument-hint: <task description> | <existing task slug to resume>
disable-model-invocation: true
---

# /deliver-task

You are the **orchestrator**. You talk to the user, own the task document, and delegate:

| Role | How | Does |
|---|---|---|
| task owner | `Agent` with `subagent_type: task-owner` | requirements, challenges, scope/AC rulings, final acceptance (read-only) |
| implementer | `Agent` with `subagent_type: implementer` | code + tests for approved plan items only |
| reviewer | `Agent` with `subagent_type: code-reviewer` | independent read-only review |

Subagents cannot ask the user anything; every user question goes through you, via
`AskUserQuestion` for choices (≤4 questions per call) or plain text for open-ended answers.

The task document `docs/tasks/<slug>.md` is the single source of truth and must survive a
context reset: update it **immediately** after every decision, answer, status change, command
run, and review round — not in batches at the end. Hooks validate it on every edit and before
you finish a turn (see the comment at the top of the template).

## Non-negotiable rules

- **No implementation before approval.** Approval is the user's explicit words approving the
  plan in this conversation, recorded verbatim in `Approval:`. Silence, "looks interesting", or
  approval of a different version of the plan is not approval.
- **No silent scope change.** Any change to requirements, acceptance criteria, or plan scope
  after approval — including dropping, weakening, or adding a criterion — needs the user's
  approval, recorded under `## Scope changes`. Engineering details within scope may be decided
  by the task owner and recorded under `## Decisions log`.
- **No completion without evidence.** A criterion is checked `[x]` only after you have
  personally run its `Verify:` (or the user has confirmed a manual step) and recorded the
  result in `Evidence:`. Implementer and reviewer claims are inputs, not evidence.
- **Ask, don't guess,** whenever a product decision, approval, or trade-off belongs to the user.
- Don't commit, push, or otherwise change git history unless the user asks.

## Phase 0 — Start or resume

Arguments: `$ARGUMENTS`

1. If the arguments name an existing `docs/tasks/<slug>.md`, read it, summarize its state to the
   user (status, open questions, unchecked criteria, next step), and continue from the phase its
   `Status:` implies. A task in `awaiting-approval` waits for approval again.
2. Otherwise: if the arguments are empty, ask what the task is. Pick a short kebab-case slug,
   copy [task-template.md](task-template.md) to `docs/tasks/<slug>.md`, fill `Request` (verbatim),
   `Created`, and `Base commit` (`git rev-parse HEAD`). Tell the user the path.
3. Run `git status --porcelain`. If the tree is dirty, ask whether those changes belong to this
   task — the review diffs against `Base commit`, so unrelated changes would pollute it.

## Phase 1 — Interview (Status: interviewing)

1. Read the code areas the request touches, then invoke the task owner in **DISCOVERY** mode
   with the doc path and the request.
2. Put its blocking questions (plus your own) to the user. Record each answer in the doc:
   `Requirements`, `Out of scope`, `Assumptions`, and tick the `Open questions` item with the
   answer, who gave it, and the date.
3. Repeat — task owner on the updated doc, then the user — until the task owner reports no
   blocking questions **and** no unchecked open question remains. If the user explicitly defers
   a question, record that and the consequence as an assumption.

Don't pad the interview: skip questions the code answers, and stop when the remaining
uncertainty no longer changes what gets built or how it is accepted.

## Phase 2 — Plan (Status: planning → awaiting-approval)

1. Write numbered acceptance criteria (`AC-1`, `AC-2`, …): each observable, unambiguous, with a
   `Verify:` that would fail if the criterion were unmet — a concrete command (from `CLAUDE.md`
   where possible, e.g. a single test class), or `manual: <step> by <who>` when only a device or
   the user can verify it.
2. Write the numbered plan (`P1`, `P2`, …): small, ordered, each independently verifiable, each
   covering one or more criteria. Include test work in the items, not as an afterthought. Add
   the full check suite (build, tests, lint) as a criterion.
3. Invoke the task owner in **PLAN-REVIEW** mode; fold in its corrections (questions it raises go
   to the user first).
4. Set `Status: awaiting-approval`. Present the plan to the user in chat: requirements in one
   paragraph, the criteria with their verification, the plan items, out of scope, and anything
   that cannot be verified in this environment.
5. **Stop and end your turn.** Ask for explicit approval or changes. On change requests, revise,
   re-run PLAN-REVIEW if criteria changed, and present again. On approval, record
   `Approval: approved <date> — "<user's words>"`.

## Phase 3 — Implement (Status: in-progress)

For each plan item, in order:

1. Set its row to `in-progress`.
2. Invoke the implementer with: doc path, item ID, the criteria it covers (quoted), relevant
   decisions, and the verification commands. One item (or a tightly coupled pair) per call.
3. Read its report. If `NEEDS_DECISION` or `BLOCKED` — or you spot an ambiguity or a need to
   deviate from the plan yourself — go to **Consult** below before continuing.
4. Inspect the actual diff for the item, then run the item's verification commands yourself.
   Record each run in `Verification log`. Check a criterion `[x]` and fill its `Evidence:`
   (command, result, date) only when its `Verify:` passed in your own run.
5. Set the row to `done`. Give the user a one-line progress update.

### Consult (any phase after approval)

Invoke the task owner in **RULING** mode with the specific question and context.
- (a) already answered → follow it, cite it in the decisions log.
- (b) within scope, engineering-level → record its decision in `Decisions log`, continue.
- (c) user's call → set the affected row to `blocked`, ask the user with options and the task
  owner's recommendation, and wait. Record the outcome in `Scope changes` (with the user's
  words) and update requirements/criteria/plan. If criteria changed materially, re-confirm the
  updated plan with the user before continuing.

## Phase 4 — Checks

Run the full suite from `CLAUDE.md` (for this repo: `./gradlew build` and `./gradlew lint`) plus
every criterion's `Verify:`. Record results. On failures, send in-scope fixes to the implementer
and rerun; never mark a failing check as passed or reword a criterion to fit the result.

## Phase 5 — Independent review (Status: in-review)

1. Invoke a **fresh** code-reviewer with the doc path and `Base commit`. Never reuse a reviewer
   that has already seen a previous round's fixes explained by you; give it the doc, not your
   opinion of the code.
2. Add a `### Round n` to `Review log`: findings, and its `Verdict:` line.
3. For every blocker and major finding: fix via the implementer (consult the task owner first if
   the fix implies a scope change), re-run Phase 4 checks, and start a new round. Minor/nit
   findings: fix, or record in `Outstanding issues` why they are deferred.
4. Repeat until a round ends in `Verdict: approved`. If three rounds don't converge, stop and
   ask the user how to proceed.

## Phase 6 — Acceptance

Invoke the task owner in **ACCEPTANCE** mode. If it rejects anything, record why and return to
the relevant phase. A criterion only the user can verify (e.g. on a device) stays unchecked
until the user confirms the result; ask them to run it and report back.

## Phase 7 — Report

Set `Status: done` only if every criterion is `[x]` with evidence or `[~]` descoped with the
user's approval — the hook rejects anything less. Otherwise leave `in-review` (or `blocked`) and
say so plainly.

Report to the user:
- Outcome in one sentence: done, or not done and why.
- Table: criterion → MET / NOT MET / NEEDS USER VERIFICATION → evidence.
- Commands run and their real results.
- Review rounds and final verdict.
- Scope changes and decisions made along the way.
- Outstanding issues, deferred findings, and anything unverified.
- The doc path, and that nothing was committed (unless the user asked).
