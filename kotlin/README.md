# Triple Triad — Kotlin Multiplatform PoC

Minimal proof of concept for the migration described in
[../docs/migration/00-INDEX.md](../docs/migration/00-INDEX.md).

It does exactly one thing: **it shows a Triple Triad card, and tapping the card
flips it around its vertical axis and hands it to the other side** (blue ⇄ red) —
the visual half of a capture. Everything else (board, rules, AI, network,
persistence, assets) is deliberately out of scope.

This replaces the earlier `poc/` directory, which was reported as validating the
technology stack but had never been compiled and contained 12 build-blocking
defects. Everything below has been executed; the results are in
[§ Verified build results](#verified-build-results).

---

## Layout

```
kotlin/
├── settings.gradle.kts          3 modules, repositories declared once
├── build.gradle.kts             plugin versions only, all `apply false`
├── gradle/libs.versions.toml    single source of truth for versions
├── gradle/wrapper/              Gradle 8.14.3
├── shared/                      KMP module: model + Compose UI
│   └── src/
│       ├── commonMain/kotlin/com/tripletriad/
│       │   ├── model/Card.kt            Card, CardColor, Element, powerLabel()
│       │   └── ui/
│       │       ├── App.kt               root composable
│       │       ├── CardView.kt          FlippableCard + CardFace + CardDigits
│       │       └── CardColors.kt        colours/sizes lifted from the AS3 source
│       ├── commonTest/kotlin/…/CardTest.kt      5 model tests
│       ├── desktopTest/kotlin/…/FlipUiTest.kt   3 Compose UI tests
│       └── iosMain/kotlin/…/MainViewController.kt
├── androidApp/                  Android host (ComponentActivity + setContent)
├── desktopApp/                  JVM host — lets you run the UI without an emulator
└── iosApp/iosApp/*.swift        SwiftUI host sources (see the iOS caveat below)
```

## Toolchain

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 8.14.3 | via the committed wrapper |
| JDK | 17 | `jvmToolchain(17)` in every module |
| Kotlin | 2.2.20 | `org.jetbrains.kotlin.plugin.compose` is versioned with Kotlin, not with Compose |
| Compose Multiplatform | 1.9.3 | Material 3 |
| Android Gradle Plugin | 8.13.2 | |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |

There is no `composeOptions.kotlinCompilerExtensionVersion` anywhere: since
Kotlin 2.0 the Compose compiler ships inside Kotlin and is applied as a plugin.

## Prerequisites

- JDK 17 on `PATH` or `JAVA_HOME`.
- For the Android module only: an Android SDK with platform 36 and a
  `local.properties` pointing at it. Copy `local.properties.sample` and fill in
  `sdk.dir`. On Windows **escape both the drive colon and the backslashes**, and
  end the file with a single `LF` — an unescaped colon or a `CRLF` makes
  `lintDebug` fail with `PropertyEscape`:

  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```

- Nothing extra for the desktop module.

## Commands

Run the UI without an emulator or Xcode:

```bash
./gradlew :desktopApp:run
```

Build the Android debug APK (output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`):

```bash
./gradlew :androidApp:assembleDebug
```

Everything — compile all JVM/Android targets, unit tests, UI tests, Android lint:

```bash
./gradlew build
```

Model + Compose UI tests only:

```bash
./gradlew :shared:desktopTest
```

## Verified build results

Run on Windows 11, JDK 17.0.5 (Temurin), Android SDK platform 36.1 /
build-tools 36.0.0.

| Command | Result |
|---------|--------|
| `./gradlew clean` then `./gradlew build assembleDebug` | **BUILD SUCCESSFUL**, 248 tasks |
| `./gradlew :androidApp:assembleDebug` | **BUILD SUCCESSFUL** — `androidApp-debug.apk`, 10 151 KB |
| `./gradlew :androidApp:assembleRelease` | **BUILD SUCCESSFUL** — `androidApp-release-unsigned.apk`, 7 455 KB |
| `./gradlew :desktopApp:build` | **BUILD SUCCESSFUL** — `desktopApp.jar` |
| `./gradlew :shared:desktopTest` | **8 tests, 0 failures** |
| `./gradlew :shared:lint` | **0 errors**, 9 warnings (all "a newer version is available") |
| `./gradlew :desktopApp:run` | window opens, titled "Triple Triad — KMP PoC", nothing on stderr |

The whole-project run above was executed with `--no-build-cache` after `clean`, so
238 of the 248 tasks really ran rather than being replayed from cache.

Test breakdown:

```
com.tripletriad.model.CardTest    5 tests, 0 failures
  oppositeIsAnInvolution
  captureChangesOwnerAndNothingElse
  captureTwiceReturnsTheOriginal
  aceIsRenderedAsA
  powersOutsideOneToTenAreRejected

com.tripletriad.ui.FlipUiTest     3 tests, 0 failures   (real Compose tree, desktop target)
  cardIsDisplayedOwnedByBlue
  tappingTheCardFlipsItAndHandsItToTheOtherSide
  flippingTwiceReturnsTheCardToBlue
```

`CardTest` also runs on the Android debug and release variants via
`testDebugUnitTest` / `testReleaseUnitTest` (5 tests each, part of `./gradlew build`).

`FlipUiTest` drives the real Compose tree: it clicks the card by test tag and
waits for the on-screen owner label to change, so a broken flip fails the build
rather than passing silently. That was checked by mutation rather than assumed —
changing the half-way threshold in `CardView.kt` from `value >= 90f` to
`value >= 9000f` (so the owner never switches) makes exactly the two flip tests
fail; the change was then reverted.

### iOS caveat

The iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) are declared and each
produces a static `shared.framework` — `linkDebugFrameworkIosSimulatorArm64` and
friends exist in the task graph. **They have not been built**, because
Kotlin/Native cannot compile Apple targets on a Windows host; those compilations
are skipped there, which is why `./gradlew build` still succeeds.

`iosApp/iosApp/*.swift` contains the SwiftUI host, but **there is no `.xcodeproj`
or `.xcworkspace`** — an Xcode project cannot be authored meaningfully off a Mac.
To finish the iOS side, on macOS: create an iOS App target, add
`iOSApp.swift`/`ContentView.swift` to it, and add a "Run Script" build phase
calling `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Until someone has
done that and run it, **iOS remains unvalidated** — do not report it otherwise.

## What this PoC does and does not prove

Proven:

- Kotlin 2.2.20 + Compose Multiplatform 1.9.3 + AGP 8.13.2 + Gradle 8.14.3 are a
  working combination.
- One `commonMain` Compose UI runs on Android and on the JVM from a single source.
- A 3D-ish Y-axis flip with a mid-animation state change works in common code
  (`Animatable` + `graphicsLayer { rotationY }` + `cameraDistance`, un-mirroring
  the face past 90°).
- Common-code tests and Compose UI tests both run in CI-able Gradle tasks.

Not proven — these are the actual risks of the migration and none is touched here:

- **Card artwork from Starling texture atlases.** The PoC draws cards
  programmatically; the real game slices 263 card images out of atlases.
- **iOS.** Never compiled (see above).
- **The 3×3 board and drag-and-drop.** Compose has no built-in in-process drop
  target; this needs the bounds-registration design in
  [../docs/migration/08-PHASE-4-UI-LAYER.md](../docs/migration/08-PHASE-4-UI-LAYER.md).
- **The rules engine**, including the Same/Plus/Combo cascade.
- **Networking.** Note that `net/Socket.as` in the AS3 source is largely dead
  code; see TR-007 in
  [../docs/migration/16-RISK-ASSESSMENT.md](../docs/migration/16-RISK-ASSESSMENT.md).
- **Performance.** No frame timings were measured. The APK sizes above are for a
  single procedurally-drawn card with no assets and say nothing about the real app.

## Fidelity to the AS3 source

Values were read out of the original rather than invented:

| Here | AS3 source |
|------|-----------|
| card 88 × 118 dp | `new Quad(88, 118, 0x5a595a)` — `tto/display/Card.as:73` |
| `BlueCard = 0xFF2D4660`, `RedCard = 0xFF602D2D` | `Card.BLUE_COLOR`, `Card.RED_COLOR` — `Card.as:30-31` |
| digit badge 36 × 24, digits at (14,0) (26,6) (14,12) (2,6) | `CardDigits.positions` — `tto/display/CardDigits.as:14` |
| power 10 renders as `A` | `CardDigits` swaps in the `cd10` texture |
| landscape, no action bar | `application.xml`: `aspectRatio landscape`, `fullScreen true` |
| 400 ms flip | the 0.4 s tweens in `tto/utils/TTOCore.as` |

## Licensing note

Nothing here uses Square Enix assets — the card is drawn from primitives and the
only name in the code is "Geezard". That is not a workaround for BR-003 in
[../docs/migration/16-RISK-ASSESSMENT.md](../docs/migration/16-RISK-ASSESSMENT.md):
the unlicensed-IP problem applies to the real game's art, audio and naming, and it
is unresolved.
