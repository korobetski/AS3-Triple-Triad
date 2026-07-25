plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
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
        val desktopTest by getting
        desktopTest.dependencies {
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

android {
    namespace = "com.tripletriad.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
