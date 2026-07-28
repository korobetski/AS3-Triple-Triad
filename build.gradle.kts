plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // Resolved here, applied to every module below, so a new module cannot be added
    // without static analysis.
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

val ktlintPluginId = libs.plugins.ktlint.get().pluginId
val detektPluginId = libs.plugins.detekt.get().pluginId
// Resolved against this file, so it stays correct in every module: `allprojects` runs the
// block below with each subproject as receiver, and a bare relative path there would look
// for `androidApp/detekt.yml` and so on.
val detektConfigFile = rootProject.file("detekt/detekt.yml")

allprojects {
    apply(plugin = ktlintPluginId)
    apply(plugin = detektPluginId)

    // Formatting rules live in .editorconfig, which the IDE reads as well, so the
    // plugin needs no configuration beyond skipping generated sources.
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            // Compose resource accessors are generated into build/; they are not ours
            // to format.
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(detektConfigFile)
        // detekt defaults to the JVM `main`/`test` source sets, which in a KMP module
        // are empty — point it at every source set instead.
        source.setFrom(files("src"))
        parallel = true
    }
}
