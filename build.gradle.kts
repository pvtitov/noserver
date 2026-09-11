plugins {
    id("com.android.library") version "8.12.3"
    id("org.jetbrains.kotlin.android") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("maven-publish")
}

android {
    namespace = "com.github.pvtitov.noserver"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes.addAll(listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES"))
    }
    publishing {
        singleVariant("release")
    }
    testOptions {
        // GoogleDriveRepository logs via android.util.Log; without this, calling it from a plain
        // JVM unit test (no Robolectric) throws "not mocked" instead of running the real logic.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    // api: consumers' inline reified calls into GoogleDriveRepository/JsonUtils compile
    // kotlinx.serialization.json.Json symbols directly into their own call sites, so it
    // must be on their compile classpath too, not just noserver's.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("androidx.credentials:credentials:1.6.0-beta03")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0-beta03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240509-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

publishing {
    publications {
        // No explicit `version` here on purpose: it defaults to `project.version`, which
        // JitPack sets via `-Pversion=<tag>` when it builds a release. Hardcoding a version
        // here would silently override whatever tag JitPack is actually building.
        register<MavenPublication>("release") {
            groupId = "com.github.pvtitov"
            artifactId = "noserver"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
            }
        }
    }
}
