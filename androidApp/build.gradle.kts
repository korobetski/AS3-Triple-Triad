// No `org.jetbrains.kotlin.android` here: since AGP 9.0 the Android plugin brings Kotlin
// itself, and applying the standalone plugin on top of it now fails the build outright
// ("no longer required for Kotlin support since AGP 9.0" — issuetracker 438678642). It was
// still listed until the `android.builtInKotlin=false` shim came out of `gradle.properties`,
// which had been suppressing exactly this. `:shared` is unaffected: there the Kotlin
// Multiplatform plugin owns the Kotlin setup, not AGP.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.tripletriad.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tripletriad.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
}
