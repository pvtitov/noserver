# Sample app demonstrating Drive storage and sharing

Status: in-review
Approval: approved 2026-10-16 — "Approved"
Base commit: b5333e5115b4a6d3bb05e01a87f2334bc0df2f30
Created: 2026-10-16

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

create minimalistic sample app inside this project to demonstrate that this lib works as expected with Google Drive including case with 2 and more users when one creates data and shares with another

## Requirements

- R1: A minimal Android sample app in a standalone Gradle build under `sample/` (own `settings.gradle.kts`), consuming the library from source via `includeBuild("..")` with dependency substitution of `com.github.pvtitov:noserver`. The root build, its published coordinates, and JitPack are unchanged (no `include(...)` in root `settings.gradle.kts`, `jitpack.yml` untouched).
- R2: applicationId `com.github.pvtitov.noserver.sample`. The sample reads `GOOGLE_WEB_CLIENT_ID` and `NOSERVER_API_KEY` from gitignored `local.properties` into `BuildConfig` and calls `NoServer.configure` in its `Application.onCreate`. With either value missing it still builds, and shows a clear "not configured" message instead of crashing.
- R3: Own-data flow: Log in (shows the returned e-mail), show own note, edit + Upload, Download again, Log out. Each action shows its result.
- R4: Sharing flow: the user's share token is shown as selectable text with a Copy button (clipboard) and a Share button (system share sheet). A paste field + "Read shared" button displays another user's note via `download(shareToken)`.
- R5: Multi-user scenario demonstrable on two devices/emulators (acceptance path) and also via logout/login account switching on one device: A writes and gets token; B reads A's note; A edits and uploads; B re-reads and sees the change; a third account C (if available) reads too.
- R6: Every `NoServerException` subtype and every null/false result is shown as a readable status line, never a crash or silent hang. One call at a time: action buttons are disabled while a call runs (the library keeps a single pending action).
- R7: The sample logs no share tokens, e-mails, or note content (consistent with README "Data handling" logging claims, which stay unchanged).
- R8: README gets a "Sample app" section: how to build/run it, the `local.properties` keys, the Cloud setup (Android OAuth client + API key restricted to the sample package and debug SHA-1), the manual multi-user test script, and token-transfer routes (emulator host-clipboard sync, share sheet, `adb shell input text`). README also says `sample/local.properties` is gitignored, to copy `GOOGLE_WEB_CLIENT_ID` from the root `local.properties` into it, and that the API key's Android restriction needs `com.github.pvtitov.noserver.sample` + debug SHA-1 (a new key, or the existing key with the sample package added). CLAUDE.md commands list gets the sample build command, and "What this is" notes that `sample/` is a separate Gradle build (not included by root `settings.gradle.kts`) consuming the library via `includeBuild`.
- R9: Root `./gradlew build` and `./gradlew lint` still pass; the sample's `assembleDebug` and `lint` pass.

## Out of scope

- Fixing library bugs/limitations found by the sample (e.g. `logout()` returning before the Drive client is cleared, single pending action, back-press gap) — recorded under Outstanding issues (user, 2026-10-16).
- `ManualRepository` demo; garbage-token and trashed-file demo cases; QR codes (user chose no extras, 2026-10-16).
- Persisting tokens or a friend list in the sample.
- Publishing the sample (Play/JitPack), release signing, a privacy policy for the sample.
- Adding a version catalog, CI, or ktlint/detekt to the repo; automated end-to-end tests against real Drive; Compose/instrumented UI tests (can't get past real Credential Manager sign-in).

## Assumptions

- The user owns the Google Cloud project behind the existing `GOOGLE_WEB_CLIENT_ID`, with Drive API enabled, and will add an Android OAuth client and a restricted API key for `com.github.pvtitov.noserver.sample` + debug SHA-1, plus test users if the consent screen is in Testing — confirmed by user (chose manual test script with this setup) on 2026-10-16
- 2-3 personal (non-Workspace) Google accounts on Play-Services emulators/devices are available — task-owner, 2026-10-16
- Debug signing only — task-owner, 2026-10-16
- Unreleased 2.0 behavior on `main` is what is demonstrated — task-owner, 2026-10-16
- No automated tests for the sample beyond build + lint: its logic is thin glue over `GoogleDriveRepository` (a final class), and a fake would need an interface added only for tests — task-owner rec. (Q8), presented to user in plan

## Open questions

- [x] Q1: Where does the sample live? (a) standalone Gradle build in `sample/` with `includeBuild("..")` — root/JitPack untouched (task-owner rec.); (b) `include(":sample")` in root settings; (c) restructure into :library + :sample — **Answer: (a) standalone `sample/` build with includeBuild — user, 2026-10-16**
- [x] Q2: How is the multi-user case exercised? (a) two devices/emulators; (b) one device, logout()+login() switch (may hit logout() race: suspend logout returns before drive is cleared); (c) both, (a) as acceptance path (rec.). Minimum scenario: A writes, shares token; B reads; A edits, B re-reads and sees change; C reads too. Negative cases? — **Answer: (c) support both; two devices is the acceptance path — user, 2026-10-16** (negative cases tracked as Q9)
- [x] Q3: How does the token travel? (a) copy + paste field; (b) share sheet + clipboard + paste field (rec.); (c) QR — **Answer: token as selectable text + Copy button + Share button; reader has paste field + 'Read shared'. README documents emulator host-clipboard sync and `adb shell input text` routes. User asked 'which clipboard' — clarified each device has its own; emulator clipboard syncs with host — user, 2026-10-16**
- [x] Q9: Extra demo cases beyond core scenario: garbage token → NoAccess; trashed file → token behavior; (Q4) ManualRepository — **Answer: none — user, 2026-10-16**
- [x] Q4: Demonstrate ManualRepository too? (a) no (rec.); (b) yes — **Answer: no — user, 2026-10-16**
- [x] Q5: Library bugs found by the sample: (a) out of scope, record as outstanding (rec.); (b) fix in this task — **Answer: (a) record as outstanding — user, 2026-10-16**
- [x] Q6: Manual verification on device by the user with a written test script, recorded as evidence (rec.), or close on build+lint only? applicationId `com.github.pvtitov.noserver.sample`? — **Answer: user runs a manual test script, results recorded as evidence; applicationId `com.github.pvtitov.noserver.sample` — user, 2026-10-16**
- [x] Q7 (non-blocking): add "Sample app" section to README/CLAUDE.md with setup? (rec. yes) — **Taken as task-owner rec. into R8; user confirms with plan approval — 2026-10-16**
- [x] Q8 (non-blocking): automated tests for the sample? (rec. none beyond build+lint, optional ViewModel JVM test with a fake) — **No automated tests (see Assumptions); user confirms with plan approval — 2026-10-16**
- [x] Q10: Sample lint crashes (StackOverflowError) on a single project nested inside the library's directory; a two-project layout (`sample/` + `sample/app`) fixes it (confirmed on a scratch copy). (a) switch to `sample/app` layout (task-owner rec.); (b) keep layout, descope AC-4; (c) move sample outside repo — **Answer: (a) switch to `sample/` + `sample/app` — user, 2026-10-16**

## Acceptance criteria

- [x] **AC-1** The library build is untouched: the only changed or new paths since the base commit are under `sample/`, plus `README.md`, `CLAUDE.md`, and this task doc; root build + lint pass.
  - Verify: `test -z "$( { git diff --name-only b5333e5; git ls-files --others --exclude-standard; } | grep -vE '^(sample/|README\.md$|CLAUDE\.md$|docs/tasks/sample-app\.md$)' )" && ./gradlew build && ./gradlew lint`
  - Evidence: 2026-10-16, orchestrator: allow-list path check → PATHS-OK (changed: README.md, CLAUDE.md; new: sample/, task doc); `./gradlew build` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL
- [x] **AC-2** `sample/` is a standalone Gradle build (root project `noserver-sample` at `sample/` with the app in subproject `:app` at `sample/app` — two projects because lint crashes with a StackOverflowError on a single project nested in the library's directory — own wrapper, explicit `dependencySubstitution` because the library sets no `group`) whose debug app builds against the library **source** via `includeBuild("..")`, with applicationId `com.github.pvtitov.noserver.sample`.
  - Verify: `./gradlew -p sample assembleDebug && ./gradlew -p sample :app:dependencies --configuration debugRuntimeClasspath | grep -E 'com\.github\.pvtitov:noserver.* -> project ' && ! ./gradlew -p sample :app:dependencies --configuration debugRuntimeClasspath | grep -E 'com\.github\.pvtitov:noserver.*(FAILED|:[0-9])' && "$ANDROID_HOME/build-tools/36.1.0/aapt2" dump packagename sample/app/build/outputs/apk/debug/noserver-sample-debug.apk | grep -qx com.github.pvtitov.noserver.sample && sample/gradlew -p sample help`
  - Evidence: 2026-10-16, orchestrator, after Q10 layout: `./gradlew -p sample assembleDebug` PASS; `:app:dependencies` → `+--- com.github.pvtitov:noserver -> project :noserver`; negative grep no match; `aapt2 dump packagename sample/app/build/outputs/apk/debug/noserver-sample-debug.apk` → `com.github.pvtitov.noserver.sample`; `sample/gradlew -p sample help` PASS
- [x] **AC-3** With both keys, or only `NOSERVER_API_KEY`, missing from `sample/local.properties`, the sample still builds, launches without crashing, and shows "NoServer is not configured" instead of the actions.
  - Verify: run by orchestrator on emulator-5554, twice: (1) `sample/local.properties` with neither key, (2) with only `GOOGLE_WEB_CLIENT_ID`. Each run: `ANDROID_SERIAL=emulator-5554 ./gradlew -p sample installDebug && adb -s emulator-5554 logcat -c && adb -s emulator-5554 shell am start -W -n com.github.pvtitov.noserver.sample/.MainActivity && sleep 5 && adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5554 shell cat /sdcard/ui.xml | grep -q "NoServer is not configured" && test -n "$(adb -s emulator-5554 shell pidof com.github.pvtitov.noserver.sample)" && test -z "$(adb -s emulator-5554 logcat -d -b crash)"`. Restore real keys afterwards.
  - Evidence: 2026-10-16, orchestrator on emulator-5554: run 1 (no sample/local.properties) and run 2 (only GOOGLE_WEB_CLIENT_ID, grep -c NOSERVER_API_KEY = 0): each `installDebug` OK, `am start -W` Status ok, uiautomator dump contains `text="NoServer is not configured"`, pidof non-empty after 5 s, crash buffer empty → RUN PASS ×2. Temporary sample/local.properties removed afterwards (none existed before)
- [x] **AC-4** The sample passes Android Lint.
  - Verify: `./gradlew -p sample lint --console=plain` passes and its output shows `:app:lint` executed
  - Evidence: 2026-10-16, orchestrator: `./gradlew -p sample lint --console=plain` → `> Task :app:lintDebug`, `> Task :app:lint`, BUILD SUCCESSFUL; report 0 errors, 9 warnings (OldTargetApi, 4× GradleDependency, 2× TrustAllX509TrustManager inside google-http-client jar, DataExtractionRules, MissingApplicationIcon)
- [x] **AC-5** The sample never logs (no tokens, e-mails, or note content can reach logcat from sample code).
  - Verify: `test -d sample/app/src && ! grep -rnE '(^|[^A-Za-z])Log\.|android\.util\.Log|Timber|print(ln)?\(|System\.(out|err)|printStackTrace' sample/app/src`
  - Evidence: 2026-10-16, orchestrator: `test -d sample/app/src && ! grep -rnE ... sample/app/src` → AC5-PASS (no matches)
- [ ] **AC-6** The screen offers Log in (shows returned e-mail), own note view/edit, Upload, Download, Log out; the share token as selectable text with Copy and Share buttons; a paste field with "Read shared" showing the other user's note. Every action reports its outcome (success, null/false, or the `NoServerException` subtype) in a status line, and all action buttons are disabled while a call runs.
  - Verify: `for s in NoGoogleAccount Cancelled NoAccess Failed; do grep -rq "NoServerException\.$s" sample/app/src || { echo "missing $s"; false; }; done`; code review by orchestrator and reviewer (each subtype mapped; busy flag gates every button; `NoServer.configure` called in `Application.onCreate` only when both keys are present; token comes from `getShareToken()` after `login()`); manual: walkthrough on a device by user during the AC-7 run
  - Evidence:
- [ ] **AC-7** Multi-user on two devices/emulators: A logs in, edits + uploads a note, copies/shares its token; B logs in with another account, pastes the token, "Read shared" shows A's note; A edits + uploads again; B re-reads and sees the change; (if a third account is available) C reads it too.
  - Verify: manual: README "Sample app" test script on two emulators by user, reporting each step's result
  - Evidence:
- [ ] **AC-8** One-device account switching: after Log out, Log in with account B shows B's e-mail and B's own note (not A's), and B can read A's token. If this fails because of the known library `logout()` race, the failure is recorded under Outstanding issues and the criterion is descoped only with the user's approval.
  - Verify: manual: README script's one-device section on one emulator by user
  - Evidence:
- [x] **AC-9** README has a "Sample app" section covering build/run, the `local.properties` keys, Cloud setup (Android OAuth client + API key restricted to `com.github.pvtitov.noserver.sample` and the debug SHA-1, Testing-mode test users, revoking old grants), token-transfer routes (emulator clipboard sync, share sheet, `adb shell input text`), and the manual test script for AC-7/AC-8; README "Data handling" is unchanged; CLAUDE.md lists the sample build command.
  - Verify: `grep -n "^## Sample app" README.md && grep -n "NOSERVER_API_KEY" README.md && grep -n "adb shell input text" README.md && grep -n "\-p sample" CLAUDE.md && grep -n "sample/" CLAUDE.md` and `diff <(git show b5333e5:README.md | awk '/^### Data handling/{f=1;print;next} /^##/{f=0} f') <(awk '/^### Data handling/{f=1;print;next} /^##/{f=0} f' README.md)` prints nothing
  - Evidence: 2026-10-16, orchestrator: greps → README:218 `## Sample app`, README:243 `NOSERVER_API_KEY`, README:275 `adb shell input text`, CLAUDE.md:17-18 `-p sample`, CLAUDE.md:7 `sample/`; Data-handling diff empty → DATA-HANDLING-UNCHANGED. Content read by orchestrator: build/run, keys, Cloud setup, token routes, AC-7/AC-8 script all present

## Plan

| # | Item | Covers | Status |
|---|------|--------|--------|
| P1 | `sample/` build skeleton: `settings.gradle.kts` with `includeBuild("..")` + substitution of `com.github.pvtitov:noserver`, own Gradle wrapper (copied from root), app `build.gradle.kts` (Kotlin DSL, hardcoded versions, Compose, serialization plugin), `BuildConfig` fields from `sample/local.properties`, `Application` calling `NoServer.configure` only when both keys are present, manifest; placeholder "NoServer is not configured" screen in `.MainActivity`. Explicit `dependencySubstitution` (library sets no `group`); AGP 8.12.3 / Kotlin 2.2.21 exactly (AGP rejects mixed versions in a composite) + compose plugin 2.2.21; `rootProject.name = "noserver-sample"`; `sample/gradle.properties` with `android.useAndroidX=true`; `sample/.gitignore` with `.kotlin`; keys from `sample/local.properties` only; `compilerOptions` not deprecated `kotlinOptions` | AC-1, AC-2, AC-3 | done |
| P2 | `MainViewModel` (single immutable `StateFlow` UI state, busy flag, one call at a time, maps every `NoServerException` subtype/null/false to a status line) + Compose screen with all actions, selectable token, Copy (ClipboardManager) and Share (ACTION_SEND chooser), paste field + Read shared; no logging | AC-3, AC-4, AC-5, AC-6 | done |
| P3 | README "Sample app" section (setup, keys, Cloud registration, token-transfer routes, manual test script for two-device and one-device runs) + CLAUDE.md commands line | AC-9 | done |
| P4 | Full checks: root `./gradlew build` + `./gradlew lint`, sample `assembleDebug` + `lint`, AC-1..AC-5 verifications and the emulator "not configured" launch | AC-1, AC-2, AC-3, AC-4, AC-5 | done |
| P5 | User's Cloud setup + manual run of the README script on devices; record results | AC-6, AC-7, AC-8 | in-progress |

## Decisions log

- 2026-10-16 — Sample build strips surrounding double quotes from `local.properties` values, and README shows the unquoted form: the root `local.properties` stores `GOOGLE_WEB_CLIENT_ID = "…"` with quotes, which `Properties` keeps and which would break the generated `BuildConfig` if copied verbatim — by orchestrator (engineering detail within R2/R8)

- 2026-10-16 — Supersedes the single-project decision: `sample/settings.gradle.kts` keeps `rootProject.name`, wrapper, `includeBuild` block, plus `include(":app")`; root `sample/build.gradle.kts` declares plugins `apply false` only; `sample/app/build.gradle.kts` reads keys from `rootProject.file("local.properties")` (still `sample/local.properties`) — by task-owner (RULING), layout by user (Q10)

- 2026-10-16 — P1 deviation accepted: `org.gradle.jvmargs=-Xmx2048m` in `sample/gradle.properties` (dex merge OOM'd on the 512 MB default heap); inside `sample/`, within scope — by orchestrator

- 2026-10-16 — [SUPERSEDED by Q10, see below/Scope changes] Sample is a single Gradle project at `sample/` named `noserver-sample`; keys only from `sample/local.properties` (no fallback to root, so AC-3's keys-absent run is possible); `sample/.gitignore` with `.kotlin`; wrapper pinned to Gradle 9.0.0 — by task-owner (PLAN-REVIEW) — smallest layout, fixed APK path, testable not-configured state

- 2026-10-16 — Sample builds against library source, never JitPack/mavenLocal (published tags predate 2.0 API) — by task-owner — tests what is on main
- 2026-10-16 — Credentials from gitignored local.properties (`GOOGLE_WEB_CLIENT_ID`, new `NOSERVER_API_KEY`) into BuildConfig; missing values → app builds and shows "not configured" — by task-owner — fresh clones must keep building
- 2026-10-16 — One Activity + one ViewModel + immutable StateFlow state, one tiny @Serializable note model; Kotlin DSL; hardcoded versions (no catalog, matching repo) — by task-owner — fits app size and repo conventions

## Scope changes

<!-- Every change to requirements, criteria, or plan scope after approval, with the user's approving words. -->

- 2026-10-16 — Sample layout changed from single project at `sample/` to root `sample/` + app subproject `:app` at `sample/app` (lint 31.12.3 overflows its stack on a single project nested in the library's directory). AC-2 text and Verify (`:app:dependencies`, APK at `sample/app/build/...`), AC-4 Verify (must show `:app:lint` ran), AC-5 and AC-6 Verify paths (`sample/app/src`) updated accordingly; AC-4 otherwise unchanged. User chose "Switch to sample/app (Recommended)" (Q10).

## Verification log

- 2026-10-16 (after Round 1 fixes) code read by orchestrator (logIn fetches token before download; readShared clears sharedNote first; clip marked sensitive API 24+); AC-1 path check → PASS; `./gradlew build lint` → BUILD SUCCESSFUL; `./gradlew -p sample assembleDebug lint` → `:app:lint`, BUILD SUCCESSFUL, 0 errors / 9 warnings; AC-5 → PASS; AC-6 grep → PASS; Data-handling diff empty; AC-3 no-keys emulator rerun → PASS

- 2026-10-16 (P3) quote-stripping: implementer built with `GOOGLE_WEB_CLIENT_ID = "abc"` → BuildConfig `"abc"` (implementer's run; temp file removed — I confirmed no sample/local.properties remains)
- 2026-10-16 (P4) `./gradlew build` → BUILD SUCCESSFUL; `./gradlew lint` → BUILD SUCCESSFUL; AC-1 path check → PASS; AC-9 greps + Data-handling diff → PASS; `./gradlew -p sample assembleDebug lint --rerun-tasks` → `:app:lint`, BUILD SUCCESSFUL

- 2026-10-16 (after Q10 layout) AC-2 commands → PASS (see AC-2 evidence); `./gradlew -p sample lint --console=plain` → PASS, `:app:lint` ran, 0 errors / 9 warnings; AC-5 grep → PASS; AC-6 grep loop on `sample/app/src` → PASS (AC-6 still needs code review + user walkthrough)
- 2026-10-16 AC-3 emulator runs (no keys; client ID only) → PASS ×2; screenshot shows "NoServer is not configured" + hint naming both keys

- 2026-10-16 (after P2) `./gradlew -p sample lint` → FAIL, same StackOverflowError; tried lintConfig + sample/lint.xml, checkDependencies true/false, lintDebug, root lint.xml (scratch) — no effect. Two-project layout in scratch copy → `:app:lint` BUILD SUCCESSFUL (implementer report, not yet my own run)
- 2026-10-16 (after P2) AC-5 grep on `sample/src` → PASS; AC-6 grep loop → PASS (all four subtypes)

- 2026-10-16 (after P1) AC-1 path allow-list check → PASS (only `sample/` and the task doc are new)
- 2026-10-16 (after P1) `./gradlew -p sample assembleDebug` → PASS; dependency report `+--- com.github.pvtitov:noserver -> project :noserver`; negative grep (FAILED/version) → no match; `aapt2 dump packagename` → `com.github.pvtitov.noserver.sample`; `sample/gradlew -p sample help` → PASS
- 2026-10-16 (after P1) `./gradlew -p sample lint` → FAIL: lint-internal `StackOverflowError` in `ConfigurationHierarchy$ProjectPlaceholderConfiguration.isEnabled` (`:lintReportDebug`). Implementer reports an identical copy outside the repo passes lint, so the trigger is `sample/` being nested inside the included library build's directory. Being worked in P2.

## Review log

### Round 1

Fresh code-reviewer, 2026-10-16. No blockers/majors; no scope creep; no security problems. AC-1/2/4/5/9 MET, AC-3 MET at code level (emulator run by orchestrator), AC-6 code part MET, AC-7/8 need user.
1. [minor] `MainViewModel.logIn` returns early when `download()` is null, so `getShareToken()` never runs and the message says "log in first" right after login → fix: always fetch the token; distinct "doesn't decode as a note" outcome — sent to implementer
2. [minor] `readShared` failure leaves the previous shared note on screen → fix: clear `sharedNote` at call start — sent to implementer
3. [minor] Outstanding issues empty though Out of scope says library limits are recorded there; back-press on NoServerActivity leaves sample stuck on "running…" → recorded below by orchestrator; README line added via implementer
4. [nit] clipboard clip lacks `EXTRA_IS_SENSITIVE` → fix (cheap) — sent to implementer
5. [nit, unconfirmed] keys-only `sample/local.properties` without ANDROID_HOME → "SDK location not found" → README mentions `sdk.dir`/ANDROID_HOME — sent to implementer
6. [nit] `outcome_no_file` wording also shown for decode failures on Download → same fix as 1

Verdict: approved

### Round 2

Fresh code-reviewer on the full change after Round 1 fixes, 2026-10-16. Round 1 fixes confirmed. No findings above nit; AC-1/2/4/5/9 MET, AC-3 and AC-6 code parts MET, AC-6 walkthrough/AC-7/AC-8 need user.
1. [nit, unconfirmed] IS_SENSITIVE clip might not sync to the host clipboard on some emulators, which would break README's emulator Copy route → deferred: checked during the user's AC-7 run (fallback: Share, `adb shell input text`, or drop the flag)
2. [nit] local.properties values go into a BuildConfig string literal unescaped → deferred: real client IDs/API keys contain no `"` or `\`
3. [nit] "Read shared" with empty field makes a Drive call → deferred: outcome is still reported (NoAccess for the local format check), R6 holds
4. [nit] superseded single-project decision not marked → fixed by orchestrator

Verdict: approved

## Outstanding issues

- Round 2 deferred nits: IS_SENSITIVE clip vs. emulator host-clipboard sync (check in AC-7 run); unescaped BuildConfig string values; Read shared enabled with empty field.
- AC-6 (walkthrough), AC-7, AC-8 await the user's device runs (P5).

- Library (out of scope per Q5): `GoogleDriveRepository.logout()` → `GoogleDriveAuthorizationManager.logout()` only starts `NoServerActivity` and returns before `drive` is cleared, so an immediate `login()` may reuse the previous account (may affect AC-8).
- Library (out of scope per Q5): single pending `action` (`TODO: implement action queue`); the sample works around it by running one call at a time.
- Library (out of scope per Q5): a back press on the invisible `NoServerActivity` while a call is pending leaves the caller suspended; in the sample, buttons stay disabled on "running…" until the app is restarted (README notes this).
- Sample lint warnings (0 errors): newer dependency versions available, OldTargetApi, DataExtractionRules, MissingApplicationIcon, TrustAllX509TrustManager inside google-http-client — left as is; versions must match the library / bumps out of scope.
