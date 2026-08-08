// `com.android.kotlin.multiplatform.library` and not `com.android.library`: AGP 9 refuses to
// apply the plain Android library plugin alongside Kotlin Multiplatform at all ("not compatible
// ... since AGP 9.0"). It offers `android.builtInKotlin=false` + `android.newDsl=false` as a
// bypass, but both are themselves deprecated and go in AGP 10, so this is the migration and not
// the bypass. What changed for anyone reading task names: the Android unit tests now run under
// `:shared:testAndroidHostTest`, from an `androidHostTest` source set, where they used to be
// `:shared:testDebugUnitTest` / `androidUnitTest`.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("jacoco")
}

kotlin {
    jvmToolchain(17)

    // Declares the Android target *and* configures it — there is no separate `android {}`
    // block under this plugin, and no `compileOptions` either: `jvmToolchain(17)` sets the
    // bytecode level for every JVM target, and this module has no Java source.
    android {
        namespace = "com.tripletriad.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Host-side unit tests are opt-in here, unlike under `com.android.library`. Without
        // this the `androidHostTest` source set does not exist, so the 432 `commonTest` tests
        // run once instead of twice and only `desktopTest` is left — green CI, and no longer any
        // check that common code behaves the same on Android's runtime as on the desktop JVM.
        //
        // What is *not* lost is coverage: that is measured on the desktop target alone (see the
        // JaCoCo block below), so dropping this would cost a second execution and no percentage.
        // An earlier version of this comment said "a third of the coverage gone", which
        // contradicted the block that explains why one target is enough.
        withHostTestBuilder {}
    }
    jvm("desktop")

    // Declared so the real migration has the targets in place. The Kotlin/Native
    // compilations for these are skipped on non-macOS hosts; building the frameworks
    // requires macOS + Xcode.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` so the app modules and the UI keep seeing `Card`, `MatchState` and the rest
            // under their own names. The extraction moved where they live, not what they are.
            api(project(":core"))
            // `api` so :androidApp and :desktopApp can compose against the same
            // Compose artifacts without re-declaring them.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            // Card data is read through Compose resources, which is also the
            // mechanism the real migration needs for the 263 card images.
            api(compose.components.resources)
            api(libs.compose.backhandler)
            api(libs.kotlinx.serialization.json)
            // `api` so :androidApp and :desktopApp get it for their `Dispatchers.IO` store
            // implementations without re-declaring the same pinned version.
            api(libs.kotlinx.coroutines.core)

            // The client half of Phase 5. `implementation` and not `api`: talking to the server is
            // this module's business, and the app modules see `MatchSubmitter` — declared in
            // `:core` — rather than Ktor. That keeps the transport a detail, which the migration
            // document asks for explicitly so PvP can later be built against an in-memory pair of
            // endpoints instead of a socket.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Answers requests from a lambda, so the network layer is exercised with no socket and
            // no server — on every target, including the ones with no localhost worth the name.
            implementation(libs.ktor.client.mock)
        }

        // One engine per platform, because there is no common one. Ktor's API is multiplatform;
        // its transport cannot be.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // `getByName` and not `val desktopTest by getting`: Gradle 9 deprecated the delegate
        // syntax and removes it in Gradle 10. There is no generated `desktopTest` accessor
        // either, because the source set is named after the custom `jvm("desktop")` target.
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

// Pinned explicitly: the default package is derived from the Android namespace, so
// without this the generated `Res` class would silently move if the namespace ever
// changed.
compose.resources {
    packageOfResClass = "tripletriad.shared.generated.resources"
    generateResClass = auto
}

// ---------------------------------------------------------------------------------------
// Coverage
//
// JaCoCo directly, and **not Kover**, which the migration plan named. Kover 0.9.3 — the
// newest there is; 0.10.0 does not exist — cannot even be applied to this module: it
// aborts with "Kover requires extension with name 'android' for project ':shared' since it
// is recognized as Kotlin/Android project". Under `com.android.kotlin.multiplatform.library`
// there is no project-level `android` extension to find, because the Android configuration
// moved inside `kotlin { android { } }`. Kover has no way to opt out of that detection, so
// the choice is JaCoCo or no coverage at all.
//
// Measured on the **desktop** target only. That is not a shortcut: `commonMain` is the code
// under test, `desktopTest` runs all 432 common tests plus the 97 that need a Compose harness,
// the packaged resource bundle or a nanosecond clock, and the Android host-test run executes the
// same common sources a second time. Instrumenting both would double-count identical lines
// rather than reach new ones.
// ---------------------------------------------------------------------------------------

val desktopTestTask = tasks.named<Test>("desktopTest")

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "HTML + XML coverage for the desktop target, from :shared:desktopTest."
    dependsOn(desktopTestTask)
    executionData(
        desktopTestTask.map { test ->
            test.extensions.getByType<JacocoTaskExtension>().destinationFile!!
        },
    )

    classDirectories.setFrom(
        // The Compose resource accessors (`Res`, `Res.drawable.…`) are generated into
        // build/ and are not ours to cover; counting them would inflate the total with
        // hundreds of trivial generated getters.
        kotlin.targets.getByName("desktop")
            .compilations.getByName("main")
            .output.classesDirs
            .asFileTree
            .matching { exclude("tripletriad/shared/generated/**") },
    )
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/desktopMain/kotlin"))

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

// A floor, not a target. Last measured at 96.8% line / 86.7% branch (2026-08-02), and set well
// under that: the point is to catch a test file being deleted or a whole area going untested, not
// to make every ordinary refactor a coverage negotiation. Raising it to just below the current
// number would make the build fail on noise.
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

// So `./gradlew build` measures coverage rather than leaving it to be remembered. It costs
// one extra `desktopTest` run's worth of instrumentation, and the tests were running anyway.
tasks.named("check") { dependsOn("coverageVerify") }
