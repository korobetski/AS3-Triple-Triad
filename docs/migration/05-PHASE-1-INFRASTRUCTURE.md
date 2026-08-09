
# Phase 1: Infrastructure Setup - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 1 - Infrastructure Setup
- **Duration**: 4 weeks (Weeks 3-6)
- **Status**: IN PROGRESS — Task 1.10 done; see § Phase 1 Deliverables
- **Version**: 1.1
- **Last Updated**: 2026-08-09
- **Prerequisites**: Phase 0 - Preparation

---

## 🎯 Phase Overview

### Purpose
Phase 1 establishes the complete Kotlin Multiplatform project infrastructure with Compose Multiplatform, creating the foundation for all subsequent migration work.

### Key Objectives
1. Create full multi-module Gradle project structure
2. Configure all dependencies and plugins
3. Implement platform-specific code (Android, iOS, Shared)
4. Set up core utilities and configuration system
5. Create first production-ready components
6. Establish complete build and CI/CD pipeline

---

## 🏗️ Project Structure

```
triple-triad-kotlin/
├── .github/
│   └── workflows/                    # CI/CD workflows
│
├── build.gradle.kts                  # Root build
├── settings.gradle.kts              # Project settings
├── gradle.properties                # Gradle config
│
├── shared/                          # KMP Shared Module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/tripletriad/
│       │   ├── core/        # Game logic
│       │   ├── data/        # Models, repositories
│       │   ├── network/     # WebSocket, API
│       │   ├── ui/          # Compose components
│       │   └── di/          # Dependency injection
│       ├── androidMain/kotlin/      # Android-specific
│       ├── iosMain/kotlin/          # iOS-specific
│       └── commonTest/kotlin/        # Shared tests
│
├── androidApp/                      # Android Application
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/tripletriad/android/
│
└── iosApp/                         # iOS Application
    ├── iosApp/
    │   ├── Info.plist
    │   └── ContentView.swift
    └── iosAppTests/
```

---

## 📝 Tasks by Week

> ⚠️ **Task numbering collides with Phase 0.** Both documents number their tasks
> 1.1, 1.2, 1.3… so "Task 1.2" is ambiguous across the plan. Renumber Phase 1 tasks
> as 1.1-1.13 → **P1.1-P1.13** (and likewise for other phases) before using these
> IDs in a tracker.

#### Task 1.1: Root Project Setup

Create root project files:

- `settings.gradle.kts` - Plugin management and includes
- `build.gradle.kts` - Root configuration
- `gradle.properties` - Centralized properties
- `.gitignore`, `.editorconfig`

  (required from Kotlin 2.0 — the Compose compiler now ships with Kotlin)
- Kotlin serialization plugin (`@Serializable` is used throughout the data layer).
  The first PoC used `@Serializable` without ever applying this plugin, which is
  one of the reasons it could not compile — don't repeat it.
- Version catalog (`gradle/libs.versions.toml`) — see the verified compatible
  version set in [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md). A working
  catalog for this exact stack already exists in
  [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml);
  start from it rather than from scratch.
- Repository configuration (`google()`, `mavenCentral()`, plus
  `gradlePluginPortal()` under `pluginManagement`)
- **Gradle wrapper** — commit `gradlew`, `gradlew.bat` and `gradle/wrapper/`. The
  first PoC had none, so its own `./gradlew` build instructions could not run. The
  the PoC pins Gradle 9.6.1.

---

#### Task 1.2: Shared Module Configuration

Set up `shared/build.gradle.kts` with:

- All source sets (commonMain, androidMain, iosMain, jvmMain, commonTest)
- All dependencies (Ktor, Compose, Koin, SQLDelight, etc.)
- Compose compiler configuration
- Multiplatform settings

---

#### Task 1.3: Android Application Setup

Configure androidApp module:

- `build.gradle.kts` with Android configuration
- `AndroidManifest.xml`
- `MainActivity.kt` with Compose setup
- Resource directories

---

#### Task 1.4: iOS Application Setup

Configure iosApp module:

- `Info.plist`
- `ContentView.swift` with Compose integration
- `AppDelegate.swift`, `SceneDelegate.swift`
- Asset catalog

---

#### Task 1.5: Platform Audio — ✅ **DONE, on Android, with no new dependency**

[`audio/AudioPlayer.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/audio/AudioPlayer.kt)
and
[`AndroidAudioPlayer`](../../androidApp/src/main/kotlin/com/tripletriad/android/AndroidAudioPlayer.kt);
sounds imported by [`tools/import_sounds.py`](../../tools/import_sounds.py). Five tests in
`SoundTest`, nine in `MatchAudioTest`. Full write-up in the [README](../../README.md#audio).

- [x] Audio works — on **Android**, confirmed by `dumpsys audio` on a physical device: the
      `MediaPlayer` is `state:started` at 44 100 Hz stereo and no `SoundPool` load failed. Desktop
      is silent by design and iOS is void
- [x] Effects do not interrupt background music — two engines, two channels
- [x] Independent per-channel volume works and persists — the options sliders write
      `background_volume` / `noise_volume` and reach the running music as they move
- [x] Overlapping short effects play concurrently — `SoundPool` with six streams

> ⚠️ **Four corrections to the API and the plan below.**
>
> 1. **No `expect fun createAudioPlayer()`.** Same reason as Task 1.6: `expect` obliges an `actual`
>    for three iOS targets that cannot be compiled from a Windows host. The host constructs the
>    player it can build.
> 2. **No Media3.** `SoundPool` and `MediaPlayer` are platform classes older than `minSdk 24` and do
>    everything `SoundManager` did. Media3 buys accurate seeking and gapless concatenation; there is
>    one seek, into a file that carries a Xing header, and nothing to concatenate. The plan's own
>    note about ExoPlayer cutting off effects is right, and is why `SoundPool` is there — but it does
>    not follow that ExoPlayer is needed for the music.
> 3. **`play(soundId: String, ...)` is the wrong signature.** A string id is how the AS3 lost track:
>    fourteen call sites each naming a file, with a typo playing nothing at all. `Sound` is an enum,
>    so a typo does not compile and a test can walk every member.
> 4. **No `AudioChannel` parameter.** Which channel a sound belongs to is a property *of the sound* —
>    the music is the music — not a decision each of the fourteen call sites re-makes. `Sound.music`
>    carries it.
>
> **On formats**: nothing was converted, and the reason is measured rather than assumed. Every MPEG
> frame header was parsed: MPEG-1/2 Layer III, 22 050–48 000 Hz, 21 of 22 mono, 17 VBR, all with
> Xing headers — every combination in Android's mandatory decoder set. Ogg would lose a future iOS,
> WAV would cost 6–10× the size for a host that plays nothing, AAC would re-encode lossy audio for
> no gain. The one open question is whether MP3's frame padding makes the music's loop point click,
> which needs ears rather than a build.

> ⚠️ **Corrected.** Two problems with the API previously sketched here:
> 1. It was inconsistent with [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md),
>    which declared `stopSound()` where this document declared `stopAll()`.
> 2. More importantly it modelled a **single** audio stream. The AS3
>    `SoundManager` has **two independent channels** — `BACKGROUND_CHANNEL` and
>    `NOISE_CHANNEL` — with separate persisted volumes
>    (`BACKGROUND_VOLUME`, `NOISE_VOLUME`, saved in `UserSettings.json` and exposed
>    as two sliders in `SettingsScreen`). With one stream, every card-flip sound
>    would cut the background music, and the settings screen could not be built.
>
> Note also that `SoundManager.playSound(soundId, isNoise, loops)`'s second
> parameter selects the **channel**, not looping — a detail that an earlier
> revision of [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md) got wrong.

---

#### Task 1.6: Platform File Access — ✅ **DONE, with a different shape**

Delivered as
[`settings/SettingsStore.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/settings/SettingsStore.kt)
+ [`UserSettings.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/settings/UserSettings.kt),
implemented by
[`AndroidSettingsStore`](../../androidApp/src/main/kotlin/com/tripletriad/android/AndroidSettingsStore.kt)
and
[`DesktopSettingsStore`](../../desktopApp/src/main/kotlin/com/tripletriad/desktop/DesktopSettingsStore.kt).
Ten tests in
[`UserSettingsTest`](../../shared/src/commonTest/kotlin/com/tripletriad/settings/UserSettingsTest.kt).
Full write-up in the [README](../../README.md#user-settings).

- [x] File operations work — verified on a physical Pixel 6a: first launch creates the file, editing
      it changes the language, a second launch does **not** rewrite it
- [x] Asset loading works — **already did, and needs none of this API**. Compose resources read
      `cards.json`, the 282 images and the four locale bundles; `readAsset` would be a second
      mechanism doing the same job worse

> ⚠️ **Three corrections to the API sketched above.**
>
> 1. **`expect class` is the wrong shape.** `expect` obliges *every* declared target to supply an
>    `actual`, and `:shared` declares three iOS targets. Kotlin/Native cannot build Apple targets
>    from a Windows host, so an iOS `actual` could not be compiled or run before being pushed — the
>    macOS CI job would be the first thing to see it. An interface in `commonMain`, implemented by
>    each host module, gives the same seam with nothing unverifiable in it. It also solves the
>    `Context` problem for free: `:androidApp` has one, `:shared` does not need one.
> 2. **`readAsset`/`readAssetAsString` are redundant.** Compose resources already do this, are
>    already load-bearing for the catalog, the artwork and the locales, and work identically on
>    every target. Adding a parallel asset API would mean two ways to read a bundled file.
> 3. **`getDocumentsDirectory()` cannot be honoured on Android.** It maps to AIR's
>    `documentsDirectory`, i.e. shared external storage, which scoped storage closed off at API 29.
>    App-private storage is the correct target and needs no path accessor at all.

---

#### Task 1.7: Core Utilities — ⚠️ **mostly void; read `tools.as` before budgeting two days**

`utils/tools.as` is 158 lines, of which about ten are portable and most of those are **already
done**:

| AS3 | Status |
|---|---|
| `madmax(value)` | **Done** as [`clampPower`](../../shared/src/commonMain/kotlin/com/tripletriad/model/Power.kt) — it is `min(10, max(0, v))`, i.e. the effective-power clamp, and it belongs in `Power.kt` rather than in a `Tools` bag |
| `rand(to)` | Superseded by `kotlin.random`. Worth knowing it was **`Math.round(random()*to)`** — inclusive of `to`, and biased: the two end values get half the weight of the others |
| `array_rand(arr, n)` | Not ported; needed when the RANDOM hand rule is. One line with `shuffled().take(n)` |
| `fileOpen`, `imageLoad`, `purge` | Flash `URLLoader` and display-list plumbing. Nothing to port |

`Constants.kt` is not wanted either: constants live next to what reads them — `Board.SIZE`,
`HAND_SIZE`, `Card.POWER_RANGE`, `CardSpriteWidth`.

The logger is now **done**:
[`log/Log.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/log/Log.kt) — four levels, a
`fun interface` sink, `println` by default, and a logcat sink installed by `MainActivity`. Seven
tests in [`LogTest`](../../shared/src/commonTest/kotlin/com/tripletriad/log/LogTest.kt).

**Napier was not taken.** Its job is to forward to `android.util.Log` on Android and `println`
elsewhere — eighty lines here plus four in the host. A dependency has to do something hard.

Two things the port has that the AS3 did not: the message is a **lambda**, so a suppressed line is
never formatted (a logger that formats then discards is one nobody calls from a per-frame path),
and it has **callers** — `UserSettingsRepository` had three `runCatching { }.getOrNull()` that
threw the failure away, and all three now report. A logger with no callers would have been dead
code shipped to satisfy a checklist.

Still outstanding: **`CryptoHelper`**, which is only needed when save games are.

> **`CryptoHelper` is obfuscation, not encryption, and should not be reproduced as-is.**
> `utils/CryptoHelper.as` builds its AES key from the *pixels of a bundled image*:
> `[Embed] assets/tto_key.gif` (1 219 bytes, present in `sources/assets/`), read as
> `getPixels(new Rectangle(0, 0, 31, 31))` — 961 pixels × 4 bytes = **3 844 bytes**, which is not a
> valid AES key length (16/24/32). Whatever as3crypto's `AESKey` does with that, two things are
> certain: the key ships inside the app, so anyone holding the APK holds the key; and it protects a
> **local single-player save**, which has no attacker worth the trouble. When saves are ported,
> either drop the encryption and say so, or use a real KMP crypto library — but do not spend effort
> re-implementing a 31×31 GIF as a key schedule.
>
> Settings are **not** encrypted in the original either: `conf.as` reads and writes plain JSON. So
> this task is not a prerequisite for Task 1.6.

---

#### Task 1.8: Data Models

Create core data models (see [13-DATA-MODELS.md]):

- `Card.kt` - Card data
- `Tile.kt` - Board tile
- `Board.kt` - 3x3 board
- `Player.kt` - Player data
- `GameRules.kt` - Rule definitions
- `GameState.kt` - Game state

---

#### Task 1.9: JSON Data Files

Convert AS3 data to JSON:

- `shared/src/commonMain/resources/data/cards/ff14.json`
- `shared/src/commonMain/resources/data/cards/ff8.json`
- `CardRepository.kt` - Data loading

---

#### Task 1.10: Localization — ✅ **DONE**

Delivered as
[`i18n/Strings.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/i18n/Strings.kt) +
[`StringKeys.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/i18n/StringKeys.kt),
imported by [`tools/import_locales.py`](../../tools/import_locales.py) into
`shared/src/commonMain/composeResources/files/locales/`. Covered by
[`StringsTest`](../../shared/src/commonTest/kotlin/com/tripletriad/i18n/StringsTest.kt) (9, both
targets) and
[`StringsBundleTest`](../../shared/src/desktopTest/kotlin/com/tripletriad/i18n/StringsBundleTest.kt)
(8). Full write-up in the [README](../../README.md#localisation).

- [x] All strings extracted — 691 keys across the four bundles
- [x] Localization works for all 4 locales — verified on a physical Pixel 6a in French and
      Japanese, plus two UI tests driving the real tree
- [x] Japanese renders correctly — see the font note below; **no bundled font was needed**
- [x] Fallback to `en_US` works for missing keys — and is exercised by the shipped data, not a
      fixture: `de_DE` has no `STR_NEXT_MATCH` at all

> ⚠️ **Two things this document got wrong, corrected against the source.**
>
> 1. **The bundles are not where this said they were.** `sources/bin/assets/{de_DE,en_US,fr_FR,ja_JA}/`
>    holds `rules.png` + `rulesAtlas.xml`, which is a *texture atlas of rule-name images*. The
>    strings are already JSON, in **`sources/bin/datas/locales/`**. The acceptance criterion
>    "extracted from the 4 existing `rulesAtlas.xml` / string bundles" conflated the two; nothing
>    needed extracting from an atlas.
> 2. **The paths were `resources/`, which does not work here.** Compose Multiplatform reads
>    through `composeResources/`, the same mechanism `cards.json` and the card art already use.
>
> **On the CJK font.** The warning is real but does not apply yet, and it was worth checking rather
> than budgeting for. `Eurostile` has no CJK coverage — but `display/Card.as:81` uses it for *card
> text*, which this port does not render in a bundled font at all. Compose's default family maps to
> the platform's, and Android's CJK fallback renders the Japanese correctly on device (verified:
> **あなたは勝つ！**). The concern returns the moment Eurostile is adopted for card digits or names,
> and the licence question with it.
>
> **Not corrected, deliberately.** `STR_DRAW` is "Zeichnen" (de) and "描く" (ja) — both meaning *to
> draw a picture*, not *a tie*. They are the original's strings, so overriding them is a product
> decision rather than a port decision; `app-<tag>.json` is the mechanism when it is taken.

---

#### Task 1.11: Testing Infrastructure — ✅ **DONE, with JaCoCo instead of Kover**

- [x] Unit tests run successfully — 135 distinct / 240 executions, 0 failures
- [x] Coverage is measured — **96.7% line, 86.0% branch**, `./gradlew :shared:coverageReport`,
      gated at 90/75 by `coverageVerify` which `check` depends on. Full write-up in the
      [README](../../README.md#coverage)

> ⚠️ **Kover cannot be applied to this module at all**, so the plan's tool was not usable.
> Version 0.9.3 (the newest; 0.10.0 does not exist) aborts during plugin application with
> *"Kover requires extension with name 'android' for project ':shared' since it is recognized as
> Kotlin/Android project"*. Under `com.android.kotlin.multiplatform.library` there is no
> project-level `android` extension, because that configuration moved inside
> `kotlin { android { } }`. 0.9.1, 0.9.2 and 0.9.3 each fail identically and Kover has no opt-out
> for the detection. JaCoCo is a Gradle built-in with no AGP coupling, so that is what is wired up.

**Kotest and Turbine were not added either**, and this is a decision rather than an omission.
`kotlin.test` plus `kotlinx-coroutines-test` cover everything the 135 tests need; Kotest would be
a second assertion vocabulary alongside the one already in use, and Turbine tests `Flow`, of which
this codebase has none — `MatchState` is a plain immutable value passed through `remember`. Add
them when there is a Flow to test.

The coverage gates are **floors, not targets**: set well below the measured figures so they catch
a deleted test file rather than making every refactor a negotiation. The gate was proved able to
fail by raising the line minimum to 99% and watching the build stop.

---

#### Task 1.12: CI/CD Enhancement

Enhance pipelines:

- Automatic testing
- Code coverage upload
- Static analysis (detekt, ktlint)
- Artifact management
- Release automation

- [x] All CI pipelines pass — five jobs, green
- [x] Coverage reported — the `shared` job runs `:shared:coverageReport` and uploads the HTML as
      a `shared-coverage` artifact; the gate itself runs inside `:shared:build`
- [x] Artifacts built and stored — test results, coverage, and the debug APK
      key, which is a secret this repository does not have yet

---

#### Task 1.13: Documentation

Create development guides:

- `docs/development/project-setup.md`
- `docs/development/build-guide.md`
- `docs/development/testing-guide.md`
- `CONTRIBUTING.md`

> **Done**, all four, as listed. What they are *not* is a restatement of the README: each names
> what it does not cover and links to the document that does, because four overlapping
> descriptions of the same build is how documentation starts contradicting itself.
>
> The division taken:
>
> | Document | Answers |
> |---|---|
> | [project-setup.md](../development/project-setup.md) | what to install, `local.properties`, the IDE, **which host can build what**, and the eight first-run failures that were actually hit |
> | [build-guide.md](../development/build-guide.md) | what `build` runs, where each output lands, and **the command that reproduces each CI job** |
> | [testing-guide.md](../development/testing-guide.md) | task-per-source-set, `--tests` filtering, the `ComposeTestSupport` vocabulary, the mutation-check procedure, and what no test here can do |
> | [CONTRIBUTING.md](../../CONTRIBUTING.md) | the front door: the blocking IP issue, the loop, and the six conventions this project holds contributors to |
>
> Three things were found and fixed while writing them, which is the argument for writing
> documentation *after* the code rather than before:
>
> - `testing-strategy.md` § 5 still said "use Kover, not JaCoCo — not yet in the build" and quoted
>   95 tests. Both were months out of date and directly contradicted the README.
> - The README's prerequisites named Android SDK platform 36; `compileSdk` has been 37 since the
>   toolchain bump.
> - The README's verified-results table still called the desktop window "Triple Triad — KMP PoC";
>   it has been "Triple Triad" since the app-startup work.
>
> `docs/migration/17-TESTING-GUIDE.md` is deliberately left alone. It is a Phase 0 planning
> document — a target pyramid and framework examples written before any of this code existed — and
> the new guide says so and describes the repository instead.

---

#### Task 1.14: Android asset packaging — ⚠️ **wired by hand; the plugin does not do it here**

The app crashed on launch on device with
`MissingResourceException: composeResources/tripletriad.shared.generated.resources/files/locales/tto-en_US.json`.
The APK held **one** asset in total and no Compose resource at all — no locale, no `cards.json`,
no artwork — so `rememberStrings` failed before the first frame.

The Compose plugin registers `copyAndroidMainComposeResourcesToAndroidAssets` for exactly this
job, and under `com.android.kotlin.multiplatform.library` it never configures that task's
`outputDirectory` (running it directly fails with *"property 'outputDirectory' doesn't have a
configured value"*). Nothing depended on it either, so the build stayed green. The desktop and iOS
targets have their own assemble tasks, which is why 520 desktop tests passed against resources the
phone never had — **every automated check in the repository was blind to it**.

What is in place now:

| Where | What |
|-------|------|
| `:shared` | `androidComposeAssets`, a `Sync` that re-lays the prepared tree under `composeResources/<packageOfResClass>/`, published through a consumable `androidComposeAssetsElements` configuration |
| `:androidApp` | resolves that configuration and hands it to AGP with `variant.sources.assets.addGeneratedSourceDirectory(...)` |
| `:androidApp` | `verifyComposeAssets`, which `check` depends on |

Two dead ends worth not repeating: `assets.srcDir(configuration)` compiles and resolves but leaves
the producing task out of the graph, so a clean build packaged an empty APK again; and
`assets.directories` is a `MutableSet<String>`, which cannot carry a task dependency at all. The
Variant API is the only supported route.

`verifyComposeAssets` reads the **packaged APK**, not the build directory, because that is the
only place the question is settled — and it stays correct if the plugin is fixed later, at which
point the hand-wiring can be deleted and the check should still pass.

---

## ⚠️ Risks and Mitigation

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Gradle complexity | Medium | High | Use templates, experienced DevOps | DevOps |
| iOS issues | Medium | High | Early validation, dedicated specialist | iOS Specialist |
| Dependency conflicts | Medium | Medium | Version catalog, test thoroughly | Tech Lead |

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 0**: [04-PHASE-0-PREPARATION.md](./04-PHASE-0-PREPARATION.md)
- **Phase 2**: [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)
- **Data Models**: [13-DATA-MODELS.md](./13-DATA-MODELS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Status: PLANNING COMPLETE - Ready for execution after Phase 0*
*Review Required: Tech Lead approval before starting*
