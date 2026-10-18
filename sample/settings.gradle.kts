pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "noserver-sample"
include(":app")

// Builds against the library source on this branch rather than a published tag (the published
// tags predate the 2.0 API). The substitution is explicit because the library project sets no
// `group`, so Gradle can't match the coordinates to it on its own.
includeBuild("..") {
    dependencySubstitution {
        substitute(module("com.github.pvtitov:noserver")).using(project(":"))
    }
}
