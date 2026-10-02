# noserver

A small Android library ("serverless" data sync/sharing SDK) providing Google-Drive-backed
storage for a single JSON file per user. Extracted from the
["Simple Wish List"](https://github.com/pvtitov/swl) app, which is its first consumer.

Public API: `NoServer.configure(googleWebClientId)` (call once, e.g. from `Application.onCreate`,
with the consumer's own Google Cloud OAuth web client ID) and `GoogleDriveRepository<T>` (generic
Drive-backed store for a single JSON file — `download`/`upload`/`addFriend`/`getMyLogin`/
`initialize`/`logout`), plus the internal `JsonUtils` helper and the manifest-declared
`NoServerActivity` used for the Credential Manager sign-in flow. See the KDoc on
`GoogleDriveRepository` for usage.

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
- The consumer must call `NoServer.configure(googleWebClientId)` with its own Google Cloud OAuth
  web client ID before using `GoogleDriveRepository` (e.g. from `Application.onCreate`). This
  can't be baked into the library at build time: an OAuth web client ID is tied to a specific
  app's package name and signing certificate, so a single ID compiled into the published AAR
  could never work across different consuming apps.
- No `Application` subclass is required from the consumer — the library gets an application
  `Context` itself via [App Startup](https://developer.android.com/topic/libraries/app-startup),
  so it won't conflict with a consumer's own `Application` subclass at manifest-merge time.

### The consumer's own legal/compliance obligations

This library adds no security or privacy behavior beyond what Google Drive itself provides (see
[`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) and [`TERMS_OF_SERVICE.md`](TERMS_OF_SERVICE.md)), and
it is not affiliated with Google. Using it does **not** satisfy any of the following — they are
the consuming app's own responsibility, as the party that creates the Google Cloud project and
OAuth client ID passed to `NoServer.configure(...)`:

- **Its own privacy policy**, describing (among other things) that it uses Google Drive via this
  library, per the [Google API Services User Data
  Policy](https://developers.google.com/terms/api-services-user-data-policy) and the
  [OAuth consent screen requirements](https://support.google.com/cloud/answer/13464321).
- **OAuth consent screen verification.** This library requests the broad `drive` scope (Google
  classifies it as *restricted*; see the [Drive API auth
  guide](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)), which
  needs Google's brand + scope verification, and potentially an annual [CASA security
  assessment](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification)
  if the consumer's own backend (if any) can access that data — the library itself never sends
  data through a server, only device-to-Drive.
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
`NoServer.configure(googleWebClientId)` from the consuming app with an OAuth web client ID from
the Google Cloud Console (e.g. the same value the wishlist app's own `local.properties` used
before extraction).

## Legal

- [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) and [`TERMS_OF_SERVICE.md`](TERMS_OF_SERVICE.md) —
  this library's own privacy policy and terms, covering the library itself (which collects
  nothing and adds no security of its own). They do not replace a consuming app's own privacy
  policy or terms; see "The consumer's own legal/compliance obligations" above.
- [Google APIs Terms of Service](https://developers.google.com/terms)
- [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)
- [Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Google branding guidelines](https://developers.google.com/identity/branding-guidelines)

## License

MIT — see `LICENSE`.
