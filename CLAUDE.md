# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-module Android **library** (root project applies `com.android.library`; there is no `app` module and no `include(...)` in `settings.gradle.kts`). `sample/` is a separate Gradle build, not included by the root `settings.gradle.kts`, that consumes the library source via `includeBuild("..")`. It has a root project plus `:app` at `sample/app`, two projects because lint crashes with a `StackOverflowError` on a single project nested in the library's directory. README's "Sample app" section covers its keys, Cloud setup, and the manual multi-user test script. The library gives a consuming app a Google-Drive-backed store for one JSON file per user. It is published through JitPack as `com.github.pvtitov:noserver:<tag>` (currently tags `1.0.0`, `1.0.1`). `README.md` covers publishing/consumer setup, including versioning and the consumer's own legal/compliance obligations (privacy policy, OAuth verification) that this library doesn't take on for them. The library deliberately has no privacy policy or ToS of its own (it collects nothing; MIT covers liability). Instead, README's "Data handling" section documents its data flows and logging for consumers. That section makes factual claims about the code, so update it whenever you change what the library accesses, stores, or logs.

## Commands

```
./gradlew build                      # compile + unit tests
./gradlew test                       # unit tests only (JVM, no device needed)
./gradlew testDebugUnitTest --tests "com.github.pvtitov.noserver.JsonUtilsTest"   # one class (append .method for one test)
./gradlew lint                       # Android Lint
./gradlew -Pversion=1.0.2 publishToMavenLocal    # install to ~/.m2 for a local consumer, previewing a version before tagging it
./gradlew -p sample installDebug     # build + install the sample app (separate build in sample/; keys go in sample/local.properties)
./gradlew -p sample lint             # Android Lint for the sample app
```

- `androidTest` (`ExampleInstrumentedTest`) and `ExampleUnitTest` are untouched IDE templates; the real tests are `GoogleDriveRepositoryTest` and `JsonUtilsTest`.
- There is no CI, and no ktlint/detekt/Spotless config. Dependency versions are hardcoded in `build.gradle.kts` (no version catalog).
- The publication in `build.gradle.kts` intentionally sets no hardcoded `version = "..."`. `project.version` is instead read from `providers.gradleProperty("version")`, falling back to `0.0.0-local` if unset. JitPack passes `-Pversion=<tag>` (so a release gets the tag's exact version); a plain `./gradlew publishToMavenLocal` with no `-Pversion=` now gets the obviously-local `0.0.0-local` instead of Gradle's default `unspecified`. **`publishToMavenLocal` and JitPack are independent Maven repositories** (`~/.m2` vs. JitPack's own remote repo) with nothing keeping them in sync — treat local publishing purely as a dev convenience, never as a release channel. JitPack builds with JDK 17 (`jitpack.yml`).

## Architecture

Public surface: `NoServer.configure(googleWebClientId, apiKey)`, `GoogleDriveRepository<T>` and `NoServerException`. Everything else is plumbing for sign-in, and understanding it takes reading several files together.

**Call flow of a repository operation** (`GoogleDriveRepository` → `GoogleDriveAuthorizationManager` → `GoogleDriveAuthenticator` ↔ `NoServerActivity`):

1. Each repository operation goes through the private `runOnDrive { drive -> ... }`, which is `withContext(coroutineContext) { suspendCoroutine { GoogleDriveAuthorizationManager.runWithGoogleDrive({ drive -> continuation.resume(action(drive)) }, continuation::resumeWithException) } }`. The Drive call is a plain blocking lambda, and the coroutine is resumed from inside it.
2. `runWithGoogleDrive` stores the lambda in a single `action` field. If `GoogleDriveAuthenticator.drive` is already set, it runs the lambda immediately. Otherwise it starts the transparent `NoServerActivity` with `AUTHENTICATION_EXTRA_KEY` (via `NoServerApplicationContext.instance`, populated at process start by `NoServerInitializer` through [App Startup](https://developer.android.com/topic/libraries/app-startup) — no consumer `Application` subclass required, see below), and the lambda runs later, after sign-in finishes.
3. `NoServerActivity` dispatches on which intent extra is present (authenticate / authorization-consent intent / logout). `GoogleDriveAuthenticator.authenticate` uses Credential Manager (Google ID token) to get an account, then builds a Drive client with `GoogleAccountCredential` and the `DriveScopes.DRIVE_FILE` scope only. It shows the `GetGoogleIdOption` bottom sheet first, and on `NoCredentialException` falls back to `GetSignInWithGoogleOption`.
4. If the action throws `UserRecoverableAuthIOException` (Drive scope consent is needed), `handleUserRecoverableAuthException` relaunches `NoServerActivity` with `AUTHORIZATION_EXTRA_KEY` carrying the consent intent. `onActivityResult` then calls `authorize()`, which re-runs the stored action. On success the manager launches the activity with `FINISH_EXTRA_KEY`, and it finishes because no known extra matches. That launch is skipped when no `NoServerActivity` is alive (`liveInstances`); otherwise every call made while already signed in would flash the activity over the consumer's UI. The activity has no content and no background dim, so the consumer draws its own progress UI.

**Things that are easy to trip over:**

- Only **one pending `action`** exists (there is a `TODO: implement action queue`). Concurrent repository calls made while unauthenticated overwrite each other, and the earlier call's continuation is never resumed.
- Failures resume the caller with a `NoServerException` (`NoGoogleAccount` / `Cancelled` / `NoAccess` / `Failed`) through the `onError` callback of `runWithGoogleDrive`, so consumers must catch it. An action that throws a `NoServerException` itself (e.g. `NoAccess` for a 404 on a share token) reaches the caller as is; any other exception is wrapped in `Failed`. With no account on the device, sign-in falls back from the `GetGoogleIdOption` bottom sheet to `GetSignInWithGoogleOption`, which offers to add an account. One gap remains: a back press on the invisible `NoServerActivity` while a Drive call is still running leaves the caller suspended.
- `NoServerApplicationContext.instance` is populated by `NoServerInitializer` (an App Startup `Initializer<Context>`, wired up via the `<provider>` entry in the manifest) at process start, not by a forced `Application` subclass — an earlier version required the manifest-declared `NoServerApplication` to be the consumer's actual `Application`, which silently broke (`runWithGoogleDrive` did nothing) for any consumer with their own `Application` subclass, since AGP's manifest merger would need `tools:replace` to resolve the conflict and the replaced-in class wouldn't populate the library's context holder.
- `NoServer.configure(...)` must be called first. `GoogleDriveAuthenticator.serverClientId()` throws `error("NoServer is not configured...")` otherwise (surfaced to the caller as `NoServerException.Failed`). The client ID is deliberately runtime config, not a build-time constant, because it is tied to each consuming app's package name and signing certificate.
- `GoogleDriveRepository.login/download/upload` are `inline reified` only to encode/decode JSON; all Drive work is in non-inline `@PublishedApi internal` helpers (`loginWithInitialJson`, `downloadMyJson`, `downloadSharedJson`, `uploadJson`). Keep it that way: whatever an inline function touches becomes part of consumers' compiled call sites and must stay binary-compatible (`JsonUtils` is `@PublishedApi internal` for this reason; `GoogleDriveAuthorizationManager` is plain `internal` because no inline code touches it). Also why `kotlinx-serialization-json` is an `api` dependency while everything else is `implementation`.
- Sharing (2.0): the user's file is found by an `appProperties` marker (`noserver` = file name, `trashed=false`, youngest first), looked up on every operation. The ID is deliberately not cached: Drive keeps a trashed file readable and writable by ID, and trashing counts as deleting. `login()` keeps the active file shared "anyone with the link, reader", and removes the link permission from trashed marked files (Drive would otherwise serve them by link until the trash is emptied). The share token is just the Drive file ID; `download(shareToken)` is a direct `files.get`. There are no per-user permissions and no revocation.
- `download(shareToken)` still goes through `runOnDrive` (readers must be signed in) but reads with `SharedFileReader`, a second, credential-less `Drive` client using the app's API key: under `drive.file` the user's own token gets a 404 on another account's file even when it's public. That client must send `X-Android-Package`/`X-Android-Cert`, or a key restricted to Android apps answers 403.
- `runOnDrive` runs as a child of the caller (`withContext(dispatcher)` + `suspendCancellableCoroutine`); cancelling the caller calls `GoogleDriveAuthorizationManager.discard` so an action waiting for sign-in never runs. A blocking Drive call already in progress isn't interrupted.
- When testing `drive.file` behavior on a real account, revoke the Cloud project's earlier grants first (myaccount.google.com/permissions): Google adds previously granted scopes (e.g. 1.x's full `drive`) to new tokens.

## Testing approach

`GoogleDriveRepositoryTest` uses MockK: it `mockkObject`s `GoogleDriveAuthorizationManager` so `runWithGoogleDrive` invokes its action synchronously with a mocked `Drive`, and stubs the fluent Drive request chains. `testOptions.unitTests.isReturnDefaultValues = true` is what lets the real code call `android.util.Log` on the plain JVM. The real sign-in/consent flow can't be covered by unit tests; it needs a device with a Google account.

## Engineering rules

- Don't change scope silently: if a request turns out ambiguous or the plan needs to change, say so and ask before proceeding.
- Never report work as done, or a check as passing, without having run the verification and seen the result; state plainly what was not verified.
- Product decisions, public-API changes, and anything touching what README's "Data handling" section claims are the user's call — ask.
- A change isn't finished until `./gradlew build` and `./gradlew lint` pass, or their failures are reported.

## Delivery workflow

`/deliver-task <description | slug>` (`.claude/skills/deliver-task/`) runs the gated flow: interview → plan with acceptance criteria → user approval → implement → checks → independent review → acceptance → report. It delegates to the project subagents in `.claude/agents/` (`task-owner`, `implementer`, `code-reviewer`) and keeps state in `docs/tasks/<slug>.md`. Hooks in `.claude/settings.json` and the agents' frontmatter run `.claude/hooks/task_doc_guard.py` (needs `python3`), which rejects a task document marked `done` without verification evidence and blocks implementer edits until a plan is approved.
