# noserver

A small Android library ("serverless" data sync/sharing SDK) providing Google-Drive-backed
storage for a single JSON file per user, plus a manual export/import fallback. Extracted from
the ["Simple Wish List"](https://github.com/pvtitov/swl) app, which is its first consumer.

Public API: `GoogleDriveRepository<T>` (generic Drive-backed store for a single JSON file —
`download`/`upload`/`addFriend`/`getMyLogin`/`initialize`/`logout`), plus the internal
`JsonUtils` helper and the manifest-declared `NoServerActivity`/`NoServerApplication` used for
the Credential Manager sign-in flow. See the KDoc on `GoogleDriveRepository` for usage.

## Status: not yet published — setup left to do

This repo was extracted and structured to publish via [JitPack](https://jitpack.io), but
**nothing has actually been pushed to GitHub or tagged yet**. Consumers referencing
`com.github.pvtitov:noserver:1.0.0` (e.g. the wishlist app) will fail to resolve until the
steps below are done.

### To do

1. **Create the GitHub repo.** Must be named `noserver` under the `pvtitov` account/org, and
   must be **public** — JitPack only builds from public repos for free (private repos need a
   paid JitPack plan + auth token). If a different name or owner is used, the consumer
   coordinate (`com.github.pvtitov:noserver:...` in the wishlist repo) needs to change to
   match, since JitPack derives `groupId` from `com.github.<owner>` and `artifactId` from the
   repo name.
2. **Push this repo:**
   ```
   git remote add origin https://github.com/pvtitov/noserver.git
   git push -u origin main
   ```
3. **Tag a release and push the tag** — JitPack builds on demand from tags, not branches (a
   branch build is possible too, as `<branch>-SNAPSHOT`, but a tag is the stable, reproducible
   choice for a real dependency version):
   ```
   git tag 1.0.0
   git push origin 1.0.0
   ```
4. **Trigger and verify the JitPack build.** Visit
   `https://jitpack.io/#pvtitov/noserver/1.0.0` and click "Get it" — this triggers the first
   build (subsequent resolutions are cached). Confirm it goes green; if it fails, JitPack shows
   the full build log for that tag.
5. **Confirm the consumer actually resolves it** — in the wishlist repo, run
   `./gradlew :app:assembleDebug` (already configured to use `https://jitpack.io` +
   `com.github.pvtitov:noserver:1.0.0`; see that repo's `CLAUDE.md`). It's expected to fail
   with an unresolved-dependency error until steps 1–4 are done.
6. **Optional:** add a GitHub Actions workflow here running `./gradlew build` on push/PR — this
   repo currently has no CI, same gap the original monorepo had.

### Why root = the library (no submodule)

Unlike the original monorepo (which nested this as an `app`/`noserver` two-module project),
this repo's root project *is* the library — `build.gradle.kts` at the top level applies
`com.android.library` directly, no `include(...)` needed in `settings.gradle.kts`. This avoids
JitPack's module-selection ambiguity for multi-module repos entirely, which is a common source
of JitPack build failures for otherwise-correct projects.

### Why the publication has no explicit `version`

`noserver/build.gradle.kts`'s `MavenPublication` deliberately does **not** set `version = ...`.
Gradle defaults a publication's version to `project.version`, and JitPack builds by invoking
Gradle with `-Pversion=<tag>` — so the published artifact's version tracks whichever tag JitPack
is building. Hardcoding a version string here would silently override that and publish every
tag as the same fixed version. Verified locally: `./gradlew -Pversion=1.0.0 publishToMavenLocal`
correctly produces `com.github.pvtitov:noserver:1.0.0`.

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
    implementation("com.github.pvtitov:noserver:1.0.0") // or whatever tag you want
}
```

### Consumer requirements

- `android.useAndroidX=true` must be set in the consumer's `gradle.properties` — this library
  transitively pulls in AndroidX (`androidx.core`, `androidx.credentials`, `androidx.lifecycle`,
  etc., via Credential Manager/Drive auth). Every modern Android project already has this.
- No core library desugaring needed — the library doesn't use any API that requires it.
- No `GOOGLE_WEB_CLIENT_ID` setup needed in the consumer — it's read once at this library's own
  build/publish time (see `local.properties`, gitignored, not committed) and baked into the
  published AAR's compiled `BuildConfig`.

## Local development

```
./gradlew build          # compile + unit test
./gradlew test            # unit tests only
./gradlew publishToMavenLocal    # install to ~/.m2 for consuming from another local project
```

Requires `GOOGLE_WEB_CLIENT_ID` set in a local, gitignored `local.properties` (for Google
Sign-In / Drive API auth) — get this from the Google Cloud Console OAuth client credentials for
this project, the same value the wishlist app's own `local.properties` used before extraction.

## License

MIT — see `LICENSE`.
