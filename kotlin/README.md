# Triple Triad — Kotlin Multiplatform PoC

Proof of concept for the migration described in
[../docs/migration/00-INDEX.md](../docs/migration/00-INDEX.md).

It does three things:

1. **Loads all 263 cards** from a JSON resource extracted out of the AS3 source
   (`tto/datas/cards.as`), through the Compose Multiplatform resource bundle.
2. **Draws a card** at the real geometry, with the four edge powers positioned exactly
   as `tto.display.CardDigits` positions them.
3. **Flips it on tap**, handing it to the other side (blue ⇄ red) — the visual half of a
   capture — and steps through the catalog.

Everything else (board, drag-and-drop, rules, AI, network, persistence, artwork) is
deliberately out of scope. See
[§ What this PoC does and does not prove](#what-this-poc-does-and-does-not-prove).

This replaces the earlier `poc/` directory, which was reported as validating the
technology stack but had never been compiled and contained 12 build-blocking defects.
Everything below has been executed; the results are in
[§ Verified build results](#verified-build-results).

---

## Layout

```
kotlin/
├── settings.gradle.kts          3 modules, repositories declared once
├── build.gradle.kts             plugin versions; applies ktlint + detekt to all modules
├── .editorconfig                formatting rules — read by both ktlint and the IDE
├── config/detekt/detekt.yml     static-analysis overrides, each with its reason
├── gradle/libs.versions.toml    single source of truth for versions
├── gradle/wrapper/              Gradle 8.14.3
├── tools/extract_cards.py       regenerates cards.json from the AS3 source
├── shared/                      KMP module: model + data + Compose UI
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/tripletriad/
│       │   │   ├── model/Card.kt        Card, CardColor, CardType, powerLabel()
│       │   │   ├── data/CardRepository.kt  CardCatalog + parser + resource loader
│       │   │   └── ui/
│       │   │       ├── App.kt           root composable + card browser
│       │   │       ├── CardView.kt      FlippableCard + CardFace + CardDigits
│       │   │       └── CardColors.kt    colours and geometry lifted from the AS3 source
│       │   └── composeResources/files/cards.json    263 cards, generated
│       ├── commonTest/…         CardTest (5) + CardCatalogTest (8)
│       ├── desktopTest/…        FlipUiTest (3) + CatalogUiTest (5) + shared helpers
│       └── iosMain/…/MainViewController.kt
├── androidApp/                  Android host (ComponentActivity + setContent)
├── desktopApp/                  JVM host — lets you run the UI without an emulator
└── iosApp/iosApp/*.swift        SwiftUI host sources (see the iOS caveat below)
```

CI lives outside this directory, at
[`../.github/workflows/build.yml`](../.github/workflows/build.yml), because GitHub only
reads workflows from the repository root.

## Toolchain

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 8.14.3 | via the committed wrapper |
| JDK | 17 | `jvmToolchain(17)` in every module |
| Kotlin | 2.2.20 | `org.jetbrains.kotlin.plugin.compose` is versioned with Kotlin, not with Compose |
| Compose Multiplatform | 1.9.3 | Material 3, plus `compose.components.resources` |
| kotlinx.serialization | 1.9.0 | plugin version tracks Kotlin |
| Android Gradle Plugin | 8.13.2 | |
| ktlint (via `org.jlleitschuh.gradle.ktlint`) | plugin 12.1.2 | configured entirely from `.editorconfig` |
| detekt | 1.23.8 | `buildUponDefaultConfig`, `maxIssues = 0` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | |

There is no `composeOptions.kotlinCompilerExtensionVersion` anywhere: since Kotlin 2.0 the
Compose compiler ships inside Kotlin and is applied as a plugin.

## Prerequisites

- JDK 17 on `PATH` or `JAVA_HOME`.
- For the Android module only: an Android SDK with platform 36 and a `local.properties`
  pointing at it. Copy `local.properties.sample` and fill in `sdk.dir`. On Windows
  **escape both the drive colon and the backslashes**, and end the file with a single
  `LF` — an unescaped colon or a `CRLF` makes `lintDebug` fail with `PropertyEscape`:

  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```

- Nothing extra for the desktop module.
- Python 3 only if you need to regenerate `cards.json`.

## Commands

Run the UI without an emulator or Xcode:

```bash
./gradlew :desktopApp:run
```

Build the Android debug APK (output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`):

```bash
./gradlew :androidApp:assembleDebug
```

Everything — compile all JVM/Android targets, unit tests, UI tests, Android lint, ktlint
and detekt:

```bash
./gradlew build
```

Fast test loop (all 21 tests, about 4 s warm):

```bash
./gradlew :shared:desktopTest
```

Static analysis on its own, and the formatter:

```bash
./gradlew ktlintCheck detekt
```

```bash
./gradlew ktlintFormat
```

Regenerate the card catalog after any change to `tto/datas/cards.as`, from the
**repository root**:

```bash
python kotlin/tools/extract_cards.py kotlin/shared/src/commonMain/composeResources/files/cards.json
```

## Card data

`shared/src/commonMain/composeResources/files/cards.json` is **generated**, not
hand-written. [`tools/extract_cards.py`](tools/extract_cards.py) parses the two array
literals in `sources/src/tto/datas/cards.as` and resolves the i18n keys against
`sources/bin/datas/locales/en_US.json`.

| | Count |
|---|--:|
| `ff14` collection | 153 cards |
| `ff8` collection | 110 cards |
| **total** | **263** |

Each table in the AS3 starts with a `{name:"Back", power:[], rarity:0}` sentinel at index
0, which is not a playable card and is dropped; `id` is otherwise the array index, which
is what `Card.draw()`, `CardItem` and the save file use as a card's identity.

Two details the extractor has to get right:

- **Powers are hex.** `Card.as:316-330` reads each one as `uint("0x" + power[i])`, which
  is how the literal `'A'` in `power:[1,8,'A',8]` means 10.
- **`type` means two different things.** In the `ff14_` collection it is one of four FFXIV
  tribes (`beast`, `garlean`, `primals`, `scions`) driving `RULE_TYPE`; in `ff8_` it is one
  of eight FF8 elements driving `RULE_ELEMENTAL`. One field, compared against
  `tile.element` by the same two lines of `TTOCore.as:48-49`, so it is one enum here.

The script asserts both counts and spot-checks specific cards against the source, so it
fails loudly rather than emitting a plausible-looking wrong catalog.

## Running on a real Android device

### From Android Studio

Open **`kotlin/` as the project root**, not the repository root. The repository root has no
`settings.gradle.kts`, so Studio finds no modules and no run configuration appears. After
the Gradle sync, pick the `androidApp` configuration, select the device, Run.

If the sync is refused with something like *"AGP version not supported"*, the IDE is older
than AGP 8.13.2 requires (Android Studio Otter / 2025.2 or newer). Either update Studio,
or lower `agp` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — in which case
also lower `androidCompileSdk` to 35 and let AGP download that platform. The command line
below does not depend on the IDE version at all.

### From the command line

Build, install and launch:

```bash
./gradlew :androidApp:installDebug
```

```bash
adb shell am start -n com.tripletriad.android/.MainActivity
```

Useful while testing:

```bash
adb logcat -s AndroidRuntime:E System.err:W
```

```bash
adb shell input tap 1200 450
```

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

```bash
adb uninstall com.tripletriad.android
```

Two Windows traps, both hit during this work:

- Do **not** pipe `adb exec-out screencap -p` into a file from PowerShell — the
  redirection re-encodes the stream and corrupts the PNG. Write it on the device and
  `adb pull` it, as above.
- Under **Git Bash / MSYS**, `adb shell screencap -p /sdcard/s.png` fails with a usage
  message: MSYS rewrites `/sdcard/...` into a Windows path before `adb` sees it. Run adb
  from PowerShell, or prefix the command with `MSYS_NO_PATHCONV=1`.

If the device is paired over Wi-Fi (`adb devices` shows an
`adb-…._adb-tls-connect._tcp` entry rather than a serial), expect to re-pair when the
connection drops; USB is steadier for tight iteration.

## Verified build results

Run on Windows 11, JDK 17 (Temurin), Android SDK platform 36.1 / build-tools 36.0.0.

| Command | Result |
|---------|--------|
| `./gradlew clean` then `./gradlew build assembleRelease` | **BUILD SUCCESSFUL**, 264 tasks |
| `./gradlew :androidApp:assembleDebug` | **BUILD SUCCESSFUL** — `androidApp-debug.apk`, 10 351 KB |
| `./gradlew :androidApp:assembleRelease` | **BUILD SUCCESSFUL** — `androidApp-release-unsigned.apk`, 7 605 KB |
| `./gradlew :desktopApp:build` | **BUILD SUCCESSFUL** — `desktopApp.jar` |
| `./gradlew :shared:desktopTest` | **21 tests, 0 failures** |
| `./gradlew build` (all targets) | **47 test executions, 0 failures** |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** — 0 findings, `maxIssues = 0` |
| `./gradlew :shared:lint` | **0 errors**, warnings only ("a newer version is available") |
| `./gradlew :desktopApp:run` | window opens, titled "Triple Triad — KMP PoC", nothing on stderr |
| `./gradlew :androidApp:installDebug` + launch | **runs on a physical device** — see below |

Release APK note: `isMinifyEnabled = false`, so 7 605 KB is an **un-shrunk upper bound**,
not what a shipped build would weigh.

### On a physical device

Installed and launched on a **Pixel 6a, Android 17 (API 37), arm64-v8a**, 1080×2400 at
420 dpi, paired over adb-over-Wi-Fi:

- `installDebug` → "Installed on 1 device", then `am start` →
  `topResumedActivity=com.tripletriad.android/.MainActivity`.
- Nothing from `AndroidRuntime` or `FATAL` in logcat; the only app line is
  `ProfileInstaller: Installing profile for com.tripletriad.android`.
- Rendered in landscape as declared in the manifest.
- Screenshot confirms the catalog loaded — *"catalog: 263 cards (ff14 153 / ff8 110)"* —
  and that card #1 is `ff8_Geezard 1/4/1/5` with the digits placed left 5, top 1,
  bottom 1, right 4.
- The flip works under real touch as well as under `adb shell input tap`; "next card"
  advances to `#2 ff8_Funguar 5/1/1/3` and resets the owner to blue.
- A power of 10 renders as `A` (checked on `#92 ff8_Odin 8/10/3/5`).

Measured on that device, **debug build** (no R8, no baseline profile — pessimistic):

| Metric | Value |
|---|--:|
| Cold start (`am start-activity -W`, 2 runs) | 658 ms / 752 ms |
| TOTAL PSS, idle | 72.4 MB |
| Java heap / Native heap / Code / Graphics | 12.7 / 6.5 / 29.2 / 3.0 MB |
| Frame timing, jank % | **not measured** — see [Known issues](#known-issues) |

API 37 is above the declared `targetSdk 36`, which is fine — Android is backward
compatible in that direction — but it does mean this run did not exercise any behaviour
gated on targeting 37.

### Test breakdown

```
commonTest — runs on desktop, androidDebug and androidRelease
  com.tripletriad.model.CardTest          5 tests
    oppositeIsAnInvolution
    captureChangesOwnerAndNothingElse
    captureTwiceReturnsTheOriginal
    aceIsRenderedAsA
    invalidFieldsAreRejected
  com.tripletriad.data.CardCatalogTest    8 tests
    bothCollectionsAreParsed
    powersKeepTheAs3TopRightBottomLeftOrder
    hexPowerAIsTen
    typeCoversBothTheFf14TribesAndTheFf8Elements
    ownerDefaultsToBlueBecauseTheDataDoesNotStoreIt
    collectionsAreLookedUpByTheAs3TexturePrefix
    unknownFieldsDoNotBreakParsing
    invalidDataIsRejectedAtConstruction

desktopTest — real Compose tree on the JVM
  com.tripletriad.ui.FlipUiTest           3 tests
  com.tripletriad.ui.CatalogUiTest        5 tests
```

21 distinct tests; 47 executions across the three targets, 0 failures.

`CatalogUiTest` reads the shipped `cards.json` out of the actual resource bundle, so it
fails if the resource is dropped from packaging, if the generated `Res` accessor moves, or
if the JSON schema drifts from the model. The parser is tested separately and purely in
`commonTest`.

`FlipUiTest` clicks the card by test tag and waits for the on-screen owner label to
change, so a broken flip fails the build rather than passing silently. **That was checked
by mutation rather than assumed** — changing the half-way threshold in `CardView.kt` from
`value >= 90f` to `value >= 9000f` (so the owner never switches) makes exactly the two
flip tests fail; the change was then reverted.

## Known issues

**No frame-timing measurement.** `dumpsys gfxinfo` recorded zero frames because the
device's screen locked partway through the session. The plan's "60+ FPS" criterion is
therefore unverified — the flip looks smooth, which is not a measurement. The commands to
fill this in are in
[../docs/analysis/performance-baseline.md](../docs/analysis/performance-baseline.md) §2.

**No layout assertions.** The UI tests assert text content and state changes, not
geometry. Since the whole point of `CardColors.kt` is reproducing exact AS3 coordinates, a
regression that moved the digit badge would pass CI.
`assertLeftPositionInRootIsEqualTo` and friends would close the gap.

**Digit glyph metrics are approximate.** The digit *positions* are the AS3 values exactly,
but the original draws 18×18 bitmap textures whose glyphs sit inside their own padding,
whereas this renders centred `Text` at 13 sp. The badge therefore reads slightly heavier
than the original, and the left and right digits overhang the plate a little more. The
overhang itself is faithful — `CardDigits.positions` really does place them at x = 2 and
x = 26 over a 28-wide plate at x = 8.

**Three things on the card face are inventions, not ports.** They are marked as such in
the code, and must go when real artwork arrives: the border colours (`BlueEdge`,
`RedEdge` — the original's frame is part of the per-card artwork), the star row standing in
for the `{rarity}stars` texture, and the card-name label (the original draws no name text
at all; the name is baked into the artwork).

**The flip animation is a substitution, not a port.** `Card.flip()` in the original does
not rotate anything: it runs a four-leg `scaleX` yoyo (1 → 0 → 1.2 → 0 → 1, 0.1 s per leg,
`EASE_IN` in and `EASE_OUT` out), swapping to the card back and changing colour at each
pinch. A `rotationY` flip reads better on a high-DPI screen, but if pixel-parity with the
original is a requirement this has to be rewritten. Documented on `FlippableCard`.

**CI is green, on the second attempt.**
[`../.github/workflows/build.yml`](../.github/workflows/build.yml) failed on its first run at
the first step of every job:

```
./gradlew: Permission denied      (exit code 126)
```

`kotlin/gradlew` was committed from Windows, where `core.filemode` is `false`, so it
landed in the git index as `100644` instead of `100755`. Fixed with
`git update-index --chmod=+x kotlin/gradlew`; see
[git-workflow.md § File modes on Windows](../docs/development/git-workflow.md#file-modes-on-windows).

All five jobs then passed. Three risks flagged before that run are now settled, and they
were the interesting ones:

- **The Compose UI tests run headless on Linux.** `runComposeUiTest` gets a rendering
  surface on `ubuntu-latest` without `xvfb-run`. That was the failure I expected first and
  it did not happen.
- **`compileSdk 36` resolves on the runner** via `android-actions/setup-android`.
- **`ios-framework` passed**, which makes it the project's first successful Apple
  compilation — `linkDebugFrameworkIosSimulatorArm64` plus `iosSimulatorArm64Test` on
  `macos-latest`. It has never been built from this Windows host and cannot be.

Two caveats on that green. The result is reported from the Actions UI, not something
measured here, so the per-target test counts on CI have not been read back — the
`shared-test-results` artifact uploads on `always()` and is where that would be checked.
And `ios-framework` proves the framework links and its common tests pass; there is still no
`.xcodeproj`, so no iOS *app* has been built (see [iOS caveat](#ios-caveat)).

The missing Android SDK is *not* a risk for the `quality`, `desktop` and `ios-framework`
jobs, which have no `setup-android` step: AGP 8.x resolves the SDK location at task
execution, not at configuration, so those jobs configure `:shared` fine without one. That
was verified locally by moving `local.properties` aside and running `ktlintCheck --dry-run`
and `:desktopApp:build --dry-run` with `ANDROID_HOME` unset, and then confirmed by those
three jobs passing on CI.

**Action versions are pinned to Node 24 majors.** The first green run warned that
`actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4` and
`android-actions/setup-android@v3` declare `using: node20` and were being forced onto Node
24. They are now `v6`, `v5`, `v6` and `v4` respectively. `gradle/actions/setup-gradle` is
pinned to **v5, deliberately not v6**: v5 is the oldest major on Node 24, and v6 moves
caching into a proprietary `gradle-actions-caching` component whose use implies accepting
Gradle's Terms of Use. That is a licensing call for the project owner, not a maintenance
bump, and the rationale is recorded in the workflow itself.

### iOS caveat

The iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) are declared and each produces
a static `shared.framework` — `linkDebugFrameworkIosSimulatorArm64` and friends exist in
the task graph. **They have never been built on this host**, because Kotlin/Native cannot
compile Apple targets on Windows; those compilations are skipped there, which is why
`./gradlew build` still succeeds locally. They *have* now been built on the
`macos-latest` CI runner — see the `ios-framework` note below.

`iosApp/iosApp/*.swift` contains the SwiftUI host, but **there is no `.xcodeproj` or
`.xcworkspace`** — an Xcode project cannot be authored meaningfully off a Mac. To finish
the iOS side, on macOS: create an iOS App target, add `iOSApp.swift`/`ContentView.swift`
to it, and add a "Run Script" build phase calling
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Until someone has done that and
run it, **the iOS app remains unvalidated** — do not report it otherwise.

The `ios-framework` CI job was the project's first real Apple compile, and it passed: the
shared framework links for `iosSimulatorArm64` and its common tests run there. That closes
the "does the shared code compile for Apple at all" question and leaves only the app shell
— no simulator run, no UI, no `.xcodeproj`.

## What this PoC does and does not prove

Proven, by execution:

- Kotlin 2.2.20 + Compose Multiplatform 1.9.3 + kotlinx.serialization 1.9.0 + AGP 8.13.2
  + Gradle 8.14.3 are a working combination.
- One `commonMain` Compose UI runs on Android and on the JVM from a single source.
- **Structured data loads from a JSON resource through the Compose resource bundle** on
  both, and the same parser tests run on three targets.
- A 3D-ish Y-axis flip with a mid-animation state change works in common code
  (`Animatable` + `graphicsLayer { rotationY }` + `cameraDistance`, un-mirroring the face
  past 90°).
- Common-code tests and Compose UI tests both run in Gradle tasks, and the UI tests were
  shown capable of failing.
- ktlint and detekt run clean at `maxIssues = 0` on this codebase.

Not proven — these are the actual risks of the migration:

- **Card artwork from Starling texture atlases.** The PoC draws cards programmatically and
  loads no texture. The real game slices 263 card images out of atlases, and Compose has
  no atlas support. Highest unvalidated risk; see
  [../docs/analysis/api-mapping.md](../docs/analysis/api-mapping.md) §7.
- **iOS.** Never compiled (see above).
- **The 3×3 board and drag-and-drop.** Feathers' `DragDropManager` broker has no Compose
  equivalent; see [../docs/analysis/event-catalog.md](../docs/analysis/event-catalog.md) §3.1.
- **The rules engine**, including the Same/Plus/Combo cascade — 20 rules that interact.
- **Networking.** `net/Socket.as` is largely dead code: 27 of its 29 handlers are
  unreachable. See
  [../docs/analysis/network-protocol.md](../docs/analysis/network-protocol.md).
- **Performance.** No frame timings. The APK sizes above are for cards drawn from
  primitives with no artwork; the runtime asset payload is roughly 40 MB.
- **Any library outside the table above** — Ktor, SQLDelight, Koin, Media3 are all
  unverified with this Kotlin/Compose combination.

## Fidelity to the AS3 source

Every value below was read out of the original rather than invented. The geometry is
placed by absolute offset in the AS3 sprite's coordinate space so it can be checked
line-for-line.

| Here | AS3 source |
|------|-----------|
| card sprite 104 × 128 dp, face centred | `this.width = 104; this.height = 128` — `Card.as:60-61` |
| coloured face 88 × 118 dp at (8, 5) | `new Quad(88, 118, 0x5a595a)` at `x=8, y=5` — `Card.as:73-75` |
| `BlueCard = 0xFF2D4660`, `RedCard = 0xFF602D2D`, `GreyCard = 0xFF5A595A` | `Card.BLUE_COLOR` / `RED_COLOR` / `GREY_COLOR` — `Card.as:29-31` |
| digit cluster at (28, 88), bounds 44 × 30 | `_digits.x = 28; _digits.y = 88` — `Card.as:89-90` |
| `cdbg` plate 28 × 28 at (8, 1), alpha 0.5 | `CardDigits.as:26-29`, size from `assets/digits/digits.xml` |
| digits 18 × 18 at (14,0) (26,6) (14,12) (2,6) | `CardDigits.positions` — `CardDigits.as:13`; sizes from `digits.xml` |
| power order top / right / bottom / left | `// power [top, right, bottom, left];` — `CardDigits.as:22` |
| power 10 renders as `A` | `CardDigits` picks `cdA`; there is no `cd10` in `digits.xml` |
| rarity row at (1, 1) relative to the face | `{rarity}stars` texture at (9, 6) in sprite space — `Card.as:176-178` |
| type marker at x = 72 relative to the face | `type-{type}` texture at (80, 3) in sprite space — `Card.as:181-183` |
| landscape, no action bar | `application.xml`: `aspectRatio landscape`, `fullScreen true` |
| 400 ms flip | four 0.1 s legs — `Card.as:232-290` (but see Known issues) |

An earlier revision of this PoC had three geometry errors, all now fixed: the card was
modelled as a bare 88 × 118 sprite, the digit badge was 36 × 24 at the **top-left** of the
card rather than 44 × 30 near the bottom, and `Digit()` shifted each glyph by −4 dp,
putting the left digit at x = −2. The current values are the ones in the table.

## Licensing note

⚠️ **This changed when card data was added.**
[`cards.json`](shared/src/commonMain/composeResources/files/cards.json) now contains the
**names and stats of all 263 cards** — "Dodo", "Geezard", "Odin", the FFXIV tribes, the
`STR_FF14_CARD_*` i18n keys — extracted from the AS3 source and the shipped locale files.
Those names are Square Enix's.

No art or audio is included, and the card is still drawn from primitives. But the earlier
claim that "nothing here uses Square Enix assets" no longer holds, and this PoC is **not**
a demonstration that the IP problem can be side-stepped.

BR-003 in
[../docs/migration/16-RISK-ASSESSMENT.md](../docs/migration/16-RISK-ASSESSMENT.md) —
unlicensed Square Enix IP across art, audio and naming — is unresolved and blocking. If it
is resolved by reskinning, `cards.json` needs new names too; the *stats* (powers, rarity,
type) would carry over, since `tools/extract_cards.py` separates them from the naming.
