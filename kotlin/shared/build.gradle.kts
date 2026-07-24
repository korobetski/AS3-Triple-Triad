plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
