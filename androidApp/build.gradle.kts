// No `org.jetbrains.kotlin.android` here: since AGP 9.0 the Android plugin brings Kotlin
// itself, and applying the standalone plugin on top of it now fails the build outright
// ("no longer required for Kotlin support since AGP 9.0" — issuetracker 438678642). It was
// still listed until the `android.builtInKotlin=false` shim came out of `gradle.properties`,
// which had been suppressing exactly this. `:shared` is unaffected: there the Kotlin
// Multiplatform plugin owns the Kotlin setup, not AGP.
import java.util.zip.ZipFile
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

/**
 * The Compose resources, laid out as assets by `:shared:androidComposeAssets`.
 *
 * Declared as a dependency rather than read out of the other project's build directory, so the
 * artifact carries its producing task and `mergeDebugAssets` cannot run before it has written.
 * `:shared` explains why this is done by hand at all: the Compose plugin's own wiring is inert
 * under AGP 9's KMP library plugin, and the APK shipped with an empty `assets/`.
 */
val composeAssetsDependencies = configurations.dependencyScope("composeAssets")
val sharedComposeAssets = configurations.resolvable("sharedComposeAssetsPath") {
    extendsFrom(composeAssetsDependencies.get())
}

dependencies {
    add(
        composeAssetsDependencies.name,
        project(mapOf("path" to ":shared", "configuration" to "androidComposeAssetsElements")),
    )
}

/** Restates the resolved assets as this project's own output, for [addGeneratedSourceDirectory]. */
abstract class ComposeAssets : DefaultTask() {
    @get:InputFiles
    abstract val source: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun copy() {
        files.sync {
            from(source)
            into(outputDirectory)
        }
    }
}

// `addGeneratedSourceDirectory` and not `sourceSets.main.assets.srcDir(configuration)`: the latter
// compiles, resolves, and silently leaves the sync task out of the graph — a clean build packaged
// an APK with no resources again, which is the bug this whole arrangement exists to fix. The
// Variant API is the supported way to hand AGP a directory some task writes, and it takes the
// task provider rather than a path, so the ordering is not a matter of luck.
androidComponents.onVariants { variant ->
    val assets = tasks.register<ComposeAssets>(
        "composeAssetsFor${variant.name.replaceFirstChar(Char::uppercaseChar)}",
    ) {
        source.from(sharedComposeAssets)
    }
    variant.sources.assets?.addGeneratedSourceDirectory(assets, ComposeAssets::outputDirectory)
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

/** Mirrors `packageOfResClass` in `:shared`; the two have to agree or the runtime finds nothing. */
val resPackage = "tripletriad.shared.generated.resources"

/**
 * Fails if the debug APK ships without its Compose resources.
 *
 * The app crashed on launch with `MissingResourceException` because the APK carried a single
 * asset and no locale bundle, and nothing in the build had an opinion about that — the 520 desktop
 * tests read the same resources from a different, working pipeline, so they stayed green. This
 * reads the packaged APK, which is the only place the question is actually settled.
 */
val verifyComposeAssets = tasks.register("verifyComposeAssets") {
    group = "verification"
    description = "Checks the debug APK actually contains the Compose resources."

    val apk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
    dependsOn("assembleDebug")
    inputs.file(apk)

    doLast {
        val entries = ZipFile(apk.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        val locale = "assets/composeResources/$resPackage/files/locales/tto-en_US.json"
        check(locale in entries) {
            "The debug APK has no Compose resources — expected $locale. " +
                "See :shared:androidComposeAssets."
        }
    }
}

tasks.named("check") { dependsOn(verifyComposeAssets) }

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
}
