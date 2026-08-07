rootProject.name = "triple-triad-kmp"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// The rules engine and the game data, with no UI. Extracted so the Phase 5 server can replay a
// match with the same engine the client played it with, rather than a second implementation.
include(":core")
include(":shared")
include(":androidApp")
include(":desktopApp")
