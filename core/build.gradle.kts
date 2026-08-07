// The game, without a way to show it.
//
// ### Why this module exists
//
// The Phase 5 design has the server verify a match by **replaying it with the real engine** rather
// than with a second implementation of the rules. That is what makes it impossible for a client
// and the server to disagree about who won — and it is only true if there is exactly one engine,
// linkable from both.
//
// So the constraint on this module is negative and absolute: **no Compose, no UI, no resource
// bundle, no platform I/O.** `commonMain` here imports `kotlin` and `kotlinx` and nothing else.
// The two functions that read the catalogs out of the Compose resource bundle stayed in `:shared`
// (see `CatalogLoaders.kt`); the parsers they call are here.
//
// See docs/migration/09-PHASE-5-NETWORK.md § Two prerequisites in the existing code.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("jacoco")
    id("maven-publish")
}

group = "com.tripletriad"
// The server consumes this from a local Maven repository until there is somewhere to publish it.
// A SNAPSHOT rather than a release, so `publishToMavenLocal` overwrites instead of being cached.
version = "0.1.0-SNAPSHOT"

kotlin {
    // 17, matching `:shared`. The server runs 21 and consumes this happily; the reverse would not
    // work, so the library is the one that has to stay lower.
    jvmToolchain(17)

    android {
        namespace = "com.tripletriad.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Same reason as in `:shared`: without it there is no `androidHostTest` source set, and
        // the common tests would run once on the desktop JVM instead of twice.
        withHostTestBuilder {}
    }
    jvm("desktop")

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "core"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: `GameSave`, `Card` and the rest are `@Serializable` and
            // their consumers — `:shared` and the server — serialise them directly.
            api(libs.kotlinx.serialization.json)
            // `CardRepository` guards its cache with `sync.Mutex`.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Coverage
//
// The same shape as `:shared`'s, and for the same reasons — see that file for why JaCoCo rather
// than Kover, and why the desktop target alone is enough. The thresholds are higher here because
// this module is pure logic with no UI: there is nothing in it that a test cannot reach.
// ---------------------------------------------------------------------------------------

val desktopTestTask = tasks.named<Test>("desktopTest")

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "HTML + XML coverage for the desktop target, from :core:desktopTest."
    dependsOn(desktopTestTask)
    executionData(
        desktopTestTask.map { test ->
            test.extensions.getByType<JacocoTaskExtension>().destinationFile!!
        },
    )
    classDirectories.setFrom(
        kotlin.targets.getByName("desktop")
            .compilations.getByName("main")
            .output.classesDirs
            .asFileTree,
    )
    sourceDirectories.setFrom(files("src/commonMain/kotlin"))

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

tasks.register<JacocoCoverageVerification>("coverageVerify") {
    group = "verification"
    description = "Fails if desktop coverage drops well below what it was."
    val report = tasks.named<JacocoReport>("coverageReport")
    dependsOn(report)
    executionData(report.map { it.executionData })
    classDirectories.setFrom(report.map { it.classDirectories })
    sourceDirectories.setFrom(report.map { it.sourceDirectories })

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("coverageVerify") }
