import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read only from sample/local.properties (the sample build's root, not the library's) so a
// missing key is a real, testable state; absent keys become empty strings so fresh clones
// still build.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

// The root local.properties stores the client ID quoted (`= "…"`), and Properties keeps the
// quotes; copied verbatim they would end up doubled in the generated BuildConfig and break it.
fun localProperty(name: String): String =
    localProperties.getProperty(name).orEmpty().trim().removeSurrounding("\"")

android {
    namespace = "com.github.pvtitov.noserver.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.pvtitov.noserver.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperty("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "NOSERVER_API_KEY", "\"${localProperty("NOSERVER_API_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes.addAll(listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

base {
    archivesName = "noserver-sample"
}

dependencies {
    implementation("com.github.pvtitov:noserver")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
}
