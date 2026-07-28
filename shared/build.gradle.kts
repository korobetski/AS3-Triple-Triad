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
}

kotlin {
    jvmToolchain(17)

    // Declares the Android target *and* configures it — there is no separate `android {}`
    // block under this plugin, and no `compileOptions` either: `jvmToolchain(17)` sets the
    // bytecode level for every JVM target, and this module has no Java source.
    androidLibrary {
        namespace = "com.tripletriad.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Host-side unit tests are opt-in here, unlike under `com.android.library`. Without
        // this the 77 `commonTest` tests would quietly stop running on Android and only
        // `desktopTest` would be left — green CI, a third of the coverage gone.
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
            // `api` so :androidApp and :desktopApp can compose against the same
            // Compose artifacts without re-declaring them.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            // Card data is read through Compose resources, which is also the
            // mechanism the real migration needs for the 263 card images.
            api(compose.components.resources)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
