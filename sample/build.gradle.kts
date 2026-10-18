// The app lives in the :app subproject because Android Lint overflows its stack on a single
// project nested inside the library's directory; this root only pins plugin versions.
plugins {
    // Must match the library's AGP/Kotlin versions exactly: both builds share one classloader
    // in the composite, and AGP rejects differing plugin versions there.
    id("com.android.application") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}
