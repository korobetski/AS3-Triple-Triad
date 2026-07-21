plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
    
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.compose.compiler:compiler:1.5.12")
                implementation("org.jetbrains.compose.foundation:foundation:1.5.12")
                implementation("org.jetbrains.compose.material:material:1.5.12")
                implementation("org.jetbrains.compose.ui:ui:1.5.12")
                implementation("org.jetbrains.compose.animation:animation:1.5.12")
                implementation("org.jetbrains.compose.runtime:runtime:1.5.12")
                implementation("org.jetbrains.compose.ui:ui-graphics:1.5.12")
                implementation("org.jetbrains.compose.ui:ui-text:1.5.12")
                implementation("org.jetbrains.compose.ui:ui-util:1.5.12")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                implementation("org.jetbrains.compose.ui:ui-android:1.5.12")
            }
        }
        
        val iosMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
            }
        }
    }
}
