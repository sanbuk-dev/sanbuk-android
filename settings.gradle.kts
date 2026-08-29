// The publisher SDK is two layers on purpose (Mobile-Publisher-SDK-Design.md §13.2):
//
//   core — plain Kotlin/JVM. Everything that is not a pixel: the wire format,
//          the offline queue, the frequency counters, install identity, the
//          decision of when a view counts. No Android types anywhere, so it
//          compiles and tests on any JDK — and so the Unity, Flutter and React
//          Native shells later share ONE implementation instead of four.
//
//   sdk  — the Android library: views, Custom Tabs, viewability, storage.
//          A shell over core, added once the Android SDK is in the picture.
//
// The split is not ceremony. A rule that lives in a shell has to be written
// again for every shell, and they update at different speeds, so they diverge.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "sanbuk-android"

include(":core")
include(":sdk")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}
