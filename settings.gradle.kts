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

        // **First** among the two that can serve `com.tripletriad:core`, and deliberately so. A
        // copy is only here because somebody ran `./gradlew publishToMavenLocal` in the `tto-core`
        // repository, which is an explicit act with one purpose: trying an engine change against
        // this app before it is released. Ordering the published copy first would silently defeat
        // that — Gradle takes the first repository that answers.
        //
        // The mirror image is the trap: a local install that is no longer wanted keeps shadowing
        // the real artifact until it is removed with `rm -rf ~/.m2/repository/com/tripletriad`.
        // CI has no local repository, so neither applies there.
        mavenLocal {
            content { includeGroup("com.tripletriad") }
        }

        // The rules engine, published from the `tto-core` repository. It used to be `:core` in this
        // build — see the note on the dependency in `shared/build.gradle.kts` for why it left.
        //
        // GitHub Packages requires authentication **even for a public package**: an anonymous
        // request gets a 401, not a 200. So every machine that builds this app needs a GitHub
        // username and a token carrying `read:packages`, from, in order:
        //
        //   ~/.gradle/gradle.properties   gpr.user / gpr.key
        //   the environment               GITHUB_ACTOR / GITHUB_TOKEN   (CI)
        //
        // Never from a file inside this repository.
        maven {
            name = "tto-core"
            url = uri("https://maven.pkg.github.com/korobetski/tto-core")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
            content {
                // Scoped to the one group it serves, so that every dependency which missed Maven
                // Central does not also query GitHub Packages and log a 401 on the way past.
                includeGroup("com.tripletriad")
            }
        }
    }
}

include(":shared")
include(":androidApp")
include(":desktopApp")
