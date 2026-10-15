# noserver

A small Android library ("serverless" data sync/sharing SDK) providing Google-Drive-backed
storage for a single JSON file per user. Extracted from the
["Simple Wish List"](https://github.com/pvtitov/swl) app, which is its first consumer.

Public API: `NoServer.configure(googleWebClientId, apiKey)` (call once, e.g. from
`Application.onCreate`, with the consumer's own Google Cloud OAuth web client ID and API key) and `GoogleDriveRepository<T>` (generic
Drive-backed store for a single JSON file — `login`/`download`/`upload`/`getShareToken`/
`download(shareToken)`/`logout`), plus `NoServerException` and the manifest-declared
`NoServerActivity` used for the Credential Manager sign-in flow. See the KDoc on
`GoogleDriveRepository` for usage.

Sharing works by token: `getShareToken()` returns an opaque token for the user's file, the app
stores and delivers it however it likes (the library keeps no friend list), and another user
reads that file with `download(shareToken)`.

## Status

Published via [JitPack](https://jitpack.io) at `com.github.pvtitov:noserver`. Current tags:
`1.0.0`, `1.0.1` (the latest; `1.0.0` predates the `NoServer.configure(...)` runtime
configuration and shouldn't be used). There is no CI — `./gradlew build` before tagging a new
release is a manual step.

### Why root = the library (no submodule)

Unlike the original monorepo (which nested this as an `app`/`noserver` two-module project),
this repo's root project *is* the library — the root `build.gradle.kts` applies
`com.android.library` directly, no `include(...)` needed in `settings.gradle.kts`. This avoids
JitPack's module-selection ambiguity for multi-module repos entirely, which is a common source
of JitPack build failures for otherwise-correct projects.

### Releasing a new version

```
git tag 1.0.2
git push origin 1.0.2
```

JitPack builds on demand from tags (a branch build is also possible, as `<branch>-SNAPSHOT`, but
a tag is the stable, reproducible choice for a real dependency version). Visiting
`https://jitpack.io/#pvtitov/noserver/1.0.2` triggers the first build of a tag; subsequent
resolutions are served from JitPack's cache. If the build fails, that page shows the full log.

### Versioning: how the version number actually gets set

The `MavenPublication` in `build.gradle.kts` deliberately has no hardcoded `version = "..."`.
Gradle publications default to `project.version`, and the project's `version` in turn is read
from the `version` Gradle property (`providers.gradleProperty("version")`), falling back to
`0.0.0-local` if none is passed:

```kotlin
version = providers.gradleProperty("version").getOrElse("0.0.0-local")
```

- **JitPack** invokes the build with `-Pversion=<tag>`, so a release always gets the tag's exact
  version — hardcoding a version here would silently override whichever tag JitPack is actually
  building.
- **`./gradlew publishToMavenLocal`** with no `-Pversion=` now produces `0.0.0-local` instead of
  Gradle's default `unspecified`, so a stray local install can't be mistaken for a real release.
  Pass `-Pversion=1.0.2` to install a specific version locally, e.g. to preview a release before
  tagging it: `./gradlew -Pversion=1.0.2 publishToMavenLocal`.

**JitPack and `publishToMavenLocal` are two entirely independent Maven repositories that can go
out of sync** — one is JitPack's own remote repo (built from a pushed tag, on JitPack's infra),
the other is `~/.m2/repository` on your machine (built from whatever's on disk, with whatever
version you pass). Publishing locally doesn't publish to JitPack and vice versa; there's no
mechanism keeping them in agreement, so treat `publishToMavenLocal` purely as a local dev
convenience — for iterating on a change in a consuming app before tagging and pushing — never as
a substitute release channel. **JitPack is the only channel real consumers should use**; keep
using `publishToMavenLocal` only for that local-iteration workflow, and always pass an explicit,
obviously-non-release `-Pversion=` (or rely on the `0.0.0-local` default) when you do.

## Consuming it

Add the JitPack repository and the dependency:

```kotlin
// settings.gradle(.kts)
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle(.kts)
dependencies {
    implementation("com.github.pvtitov:noserver:1.0.1") // or whatever tag you want
}
```

### Consumer requirements

- `android.useAndroidX=true` must be set in the consumer's `gradle.properties` — this library
  transitively pulls in AndroidX (`androidx.core`, `androidx.credentials`, `androidx.startup`,
  etc., via Credential Manager/Drive auth and app initialization). Every modern Android project
  already has this.
- No core library desugaring needed — the library doesn't use any API that requires it.
- The consumer must call `NoServer.configure(googleWebClientId, apiKey)` with its own Google
  Cloud project's OAuth web client ID and API key before using `GoogleDriveRepository` (e.g. from
  `Application.onCreate`). Neither can be baked into the library at build time: both are tied to
  a specific app's package name and signing certificate, so values compiled into the published
  AAR could never work across different consuming apps.
- **The API key** is one key per app, created once by the app's developer (end users never see
  it). It's needed because, under the `drive.file` scope, a user's own sign-in can't read files
  another account created, even public ones; `download(shareToken)` reads them with the key
  instead. In the same Cloud project as the OAuth client: enable the Google Drive API, then
  *APIs & Services → Credentials → Create credentials → API key*, and restrict it:
  - **API restrictions:** Google Drive API only.
  - **Application restrictions:** Android apps, with the app's package name and the SHA-1 of
    every certificate it's signed with (debug, upload, and the Play App Signing key). The library
    sends the `X-Android-Package`/`X-Android-Cert` headers this check needs.

  The key ships inside the APK and can be extracted, and the Android restriction can be spoofed.
  The worst case is someone using up the app's Drive API quota: the key can only read files that
  are already readable by link.
- No `Application` subclass is required from the consumer — the library gets an application
  `Context` itself via [App Startup](https://developer.android.com/topic/libraries/app-startup),
  so it won't conflict with a consumer's own `Application` subclass at manifest-merge time.

### Data handling

What the library does with user data, for the consuming app's own privacy policy and Google Play
Data safety form. This is technical documentation, not a privacy policy: the library's author
operates no server, analytics, or crash reporting, and receives nothing. The only permission the
library declares is `INTERNET`.

- **`GoogleDriveRepository`** talks directly from the device to Google:
  - Signs the user in through Android Credential Manager (Google ID token) and requests only
    the `drive.file` scope (access to files the app itself created or opened), not the user's
    whole Drive.
  - Creates, reads, and overwrites one JSON file, named by the app, in the user's Drive. The
    file carries a private app property (`noserver` = the file name, visible only to the app's
    own Google Cloud project) that the library uses to find it again. Moving the file to the
    trash counts as deleting it: the library stops using it immediately, and the next `login()`
    starts a new file from the app's initial data.
  - `login()` makes that file readable by **anyone who has its link** (Drive's "anyone with the
    link" reader permission, not listed in search), and restores that setting if the user turned
    it off in Drive. The share token from `getShareToken()` is the file's Drive ID, so anyone
    holding a token can read the file, and tokens can be passed on. There is no revocation: a
    token stops working only if the owner deletes or trashes the file, after which `login()`
    creates a new file with a new token. Drive keeps a trashed file readable by its link until
    the trash is emptied, so the owner's next `login()` also removes the link permission from
    trashed files; until that `login()`, an old token still reads the trashed file. No notification e-mails are sent and no per-user permissions are
    created.
  - `download(shareToken)` reads another account's file by its token. The user must be signed
    in, but this request carries no user credential: it's sent with the app's API key plus the
    app's package name and signing-certificate SHA-1 (see the API key requirement above).
  - `login()` reads the signed-in account's e-mail address and returns it to the app.
  - The signed-in Drive client is kept in memory only, until `logout()` (which also clears
    Credential Manager state) or process death.
- **`ManualRepository`** reads or writes a file the user picks in the system file picker (Storage
  Access Framework). No network, and no persisted URI permission is kept afterwards.
- **No storage of its own:** no SharedPreferences, databases, or files beyond the Drive file
  and user-picked files above.
- **No security of its own:** data is stored as plain JSON, with only the protection Google
  Drive or the picked location gives it. Anyone with access to the account, a share token, or
  the device can read it.
- **Logcat:** the library deliberately doesn't log file content or the e-mail addresses passed to
  or returned from the API, nor share tokens, nor the URIs of picked files or the intents it
  launches (a share token can still appear inside a logged Drive exception, as part of the request URL). It does log
  method names, success flags, the app-chosen Drive file name, and full exceptions from Google,
  Credential Manager, and file I/O (whose messages the library doesn't control). These logs are not stripped in release builds; a
  consumer that wants them gone can strip `android.util.Log` calls with R8
  (`-assumenosideeffects`).

### The consumer's own legal/compliance obligations

This library adds no security or privacy behavior beyond what Google Drive itself provides (see
"Data handling" above), it comes with no warranty (see `LICENSE`), and it is not affiliated with
Google. Using it does **not** satisfy any of the following — they are the consuming app's own
responsibility, as the party that creates the Google Cloud project and OAuth client ID passed to
`NoServer.configure(...)`:

- **Its own privacy policy**, describing (among other things) that it uses Google Drive via this
  library, per the [Google API Services User Data
  Policy](https://developers.google.com/terms/api-services-user-data-policy) and the
  [OAuth consent screen requirements](https://support.google.com/cloud/answer/13464321).
- **OAuth consent screen setup.** This library requests only the `drive.file` scope, which
  Google classifies as *non-sensitive* (see the [Drive API auth
  guide](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)), so no
  restricted-scope verification or CASA security assessment is needed. The consent screen still
  needs the usual app information, and brand verification if it shows the app's logo.
- **Google Workspace accounts:** an organization's admin can forbid sharing files with "anyone
  with the link". For such users `login()` fails with `NoServerException.Failed`, and their
  data can't be shared.
- **"Sign in with Google" branding**, only if the consumer builds its own custom sign-in button
  instead of relying on Credential Manager's own bottom sheet (which this library uses, and which
  is already Google-branded) — see the [branding
  guidelines](https://developers.google.com/identity/branding-guidelines).
- **Terms of service for its own app.** Not required by Google, but advisable.

## Local development

```
./gradlew build          # compile + unit test
./gradlew test            # unit tests only
./gradlew publishToMavenLocal    # install to ~/.m2 for consuming from another local project (see Versioning above)
```

No build-time secret is required — for local manual testing, call
`NoServer.configure(googleWebClientId, apiKey)` from the consuming app with an OAuth web client
ID and API key from the Google Cloud Console (e.g. the values the wishlist app keeps in its own
`local.properties`).

When testing on a real account, first remove the app's access at
[myaccount.google.com/permissions](https://myaccount.google.com/permissions) if that account
ever granted the same Cloud project a broader Drive scope (e.g. full `drive` from 1.x): Google
adds previously granted scopes to new tokens, so reads that `drive.file` alone wouldn't allow
would still succeed.

## Legal

The library has no privacy policy or terms of service of its own: it is not an app or a hosted
service, it collects nothing, and the MIT License's warranty disclaimer and liability limitation
cover the library itself. The privacy policy and terms that Google, app stores, and the law
require belong to the consuming app; see "Data handling" and "The consumer's own
legal/compliance obligations" above. If this project ever ships its own app, OAuth client ID, or
backend, that app or service will need its own policy.

- [Google APIs Terms of Service](https://developers.google.com/terms)
- [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)
- [Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Google branding guidelines](https://developers.google.com/identity/branding-guidelines)

## License

MIT — see `LICENSE`.
