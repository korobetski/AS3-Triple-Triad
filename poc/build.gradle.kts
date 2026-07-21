// Root project build configuration for Triple Triad PoC
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.9.22" apply false
    id("com.android.application") version "8.2.2" apply false
}

tasks.register("clean", Delete::class) {
    delete.set(layout.buildDirectory)
}
