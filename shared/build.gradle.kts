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

/** Where the generated `Res` class lives, and the folder the Android assets need to mirror. */
val resPackage = "tripletriad.shared.generated.resources"

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
    //
    // **`iosX64` is gone, and not by choice.** Compose Multiplatform stopped publishing the Intel
    // simulator target at 1.11.0 — `runtime`, `foundation`, `ui` and `material3` have no
    // `-iosx64` artifact from that release on — so declaring it here fails dependency resolution
    // for the whole `appleMain` source set rather than only for that one target. The device target
    // (`iosArm64`) and the simulator every Apple Silicon machine actually runs
    // (`iosSimulatorArm64`) are unaffected, and CI's iOS job already tests the latter.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` so the app modules and the UI keep seeing `Card`, `MatchState` and the rest
            // under their own names. The extraction moved where they live, not what they are.
            //
            // A published artifact rather than `project(":core")` since the engine moved to the
            // `tto-core` repository. Both this module and the server now resolve the same bytes
            // from the same coordinate, which is the property the extraction existed for and which
            // a project dependency here could only approximate: the server was reading whatever a
            // developer had last published into their own `~/.m2`, so "one engine" held only as
            // long as somebody kept publishing.
            //
            // The cost is that an engine change is now two repositories and a version bump. See
            // `tto-core`'s README for the loop that makes that bearable — `publishToMavenLocal`,
            // which `settings.gradle.kts` prefers over the published copy on purpose.
            api(libs.tripletriad.core)
            // `api` so :androidApp and :desktopApp can compose against the same
            // Compose artifacts without re-declaring them.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            // Card data is read through Compose resources, which is also the
            // mechanism the real migration needs for the 263 card images.
            api(libs.compose.components.resources)
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
            // What a Ktor engine throws when the host is unreachable, which is what the tests that
            // simulate one have to throw.
            implementation(libs.kotlinx.io.core)
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
            implementation(libs.compose.ui.test)
        }
    }
}

// Pinned explicitly: the default package is derived from the Android namespace, so
// without this the generated `Res` class would silently move if the namespace ever
// changed.
compose.resources {
    packageOfResClass = resPackage
    generateResClass = auto
}

// ---------------------------------------------------------------------------------------
// Android assets, by hand, because the Compose plugin cannot wire them under AGP 9's KMP
// library plugin.
//
// ### The symptom
//
// `MissingResourceException: Missing resource with path: composeResources/…/tto-en_US.json`
// on launch, on device. Not one file — **the APK contained no Compose resource at all**: no
// locale, no `cards.json`, no `npcs.json`, no artwork. The app cannot start without its
// strings, so it crashed in `rememberStrings` before drawing a frame.
//
// ### The cause
//
// The Compose plugin registers `copyAndroidMainComposeResourcesToAndroidAssets` to feed the
// Android variant's asset pipeline, and under `com.android.kotlin.multiplatform.library` it
// never configures that task's `outputDirectory` — running it directly fails with "property
// 'outputDirectory' doesn't have a configured value". Nothing depends on it either, so the
// app built green and shipped an APK with an empty `assets/`. The desktop and iOS targets
// have their own assemble tasks and were never affected, which is why every one of the 520
// desktop tests passed against resources the phone did not have.
//
// ### The fix
//
// Do what the missing wiring would have done: take the prepared resources — the same tree
// `prepareComposeResourcesTaskForCommonMain` produces for every other target — and lay it out
// the way `Res.readBytes` looks for it, under `composeResources/<packageOfResClass>/`.
// [androidApp] adds the result as an asset directory.
//
// Delete this when the plugin configures its own task. `:androidApp:verifyComposeAssets` is what
// will say so: it reads the built APK, so it passes whoever fills the assets — this task or a
// fixed plugin — and fails if nobody does.
// ---------------------------------------------------------------------------------------

val androidComposeAssets = tasks.register<Sync>("androidComposeAssets") {
    group = "compose resources"
    description = "Lays the Compose resources out as Android assets for :androidApp."

    // The prepared task's output directory *is* the `composeResources` folder — its contents are
    // `files/`, `font/` and the rest. What the runtime wants is that same tree one level deeper,
    // under `composeResources/<packageOfResClass>/`, and adding that level is all this task does.
    from(tasks.named("prepareComposeResourcesTaskForCommonMain")) {
        into("composeResources/$resPackage")
    }
    into(layout.buildDirectory.dir("androidComposeAssets"))
}

// Handed to :androidApp as a dependency rather than reached for across build directories: the
// artifact carries its producing task with it, so the app's asset merge cannot run before this
// has written.
configurations.consumable("androidComposeAssetsElements") {
    outgoing.artifact(layout.buildDirectory.dir("androidComposeAssets")) {
        builtBy(androidComposeAssets)
    }
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
