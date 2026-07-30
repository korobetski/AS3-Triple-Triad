# Triple Triad — Kotlin Multiplatform PoC

Proof of concept for the migration described in
[docs/migration/00-INDEX.md](docs/migration/00-INDEX.md).

It does seven things:

1. **Loads all 263 cards** from a JSON resource extracted out of the AS3 source
   (`tto/datas/cards.as`), through the Compose Multiplatform resource bundle.
2. **Draws a card** at the real geometry from the real artwork, layer for layer as
   `tto.display.Card` stacks them, with the four edge powers positioned exactly as
   `tto.display.CardDigits` positions them.
3. **Plays a match** — a 3×3 board and two hands, turn by turn, laid out either side of the
   board in landscape and above and below it in portrait; a captured card flips with
   `Card.flip()`'s own four-leg squash.
4. **Implements the rules engine** — capture, Reverse, Fallen Ace, Same, Same Wall, Plus,
   combo, the three type rules, turn order and scoring — as pure functions with no UI, tested
   against the [specification's 35-case matrix](docs/analysis/game-rules.md#16-test-matrix-for-the-port).
   See [§ Rules engine](#rules-engine).
5. **Sequences the match as a state machine**, `MatchState -> MatchState`, replacing the
   original's cascade of `setTimeout` callbacks.
6. **Starts like an app**: a splash that names each startup phase, a main menu (play / options /
   quit) and an options screen that changes the language on the spot and persists it. See
   [§ Screens and navigation](#screens-and-navigation).
7. **Plays the original's audio** — the looping match theme and nine effects, on two channels with
   the volumes from the settings file. See [§ Audio](#audio).

Everything else (drag-and-drop, AI, network, save games, the collection and deck-builder
screens) is deliberately out of scope. See
[§ What this PoC does and does not prove](#what-this-poc-does-and-does-not-prove).

This replaces the earlier `poc/` directory, which was reported as validating the
technology stack but had never been compiled and contained 12 build-blocking defects.
Everything below has been executed; the results are in
[§ Verified build results](#verified-build-results).

---

## Layout

```
.
├── CONTRIBUTING.md              the front door: the loop, and what this project holds you to
├── settings.gradle.kts          3 modules, repositories declared once
├── build.gradle.kts             plugin versions; applies ktlint + detekt to all modules
├── .editorconfig                formatting rules — read by both ktlint and the IDE
├── detekt/detekt.yml            static-analysis overrides, each with its reason
├── gradle/libs.versions.toml    single source of truth for versions
├── gradle/wrapper/              Gradle 9.6.1
├── tools/extract_cards.py       regenerates cards.json from the AS3 source
├── tools/import_card_art.py     copies the card artwork into composeResources
├── tools/import_locales.py      normalises the four AS3 string bundles
├── tools/make_launcher_icons.py regenerates the Android launcher icon from the AIR art
├── tools/import_sounds.py       copies the ten sounds this port plays into res/raw
├── shared/                      KMP module: model + data + Compose UI
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/tripletriad/
│       │   │   ├── model/Card.kt        Card, CardColor, CardType, powerLabel()
│       │   │   ├── model/GameRules.kt   12 rule slots: 3 enums + 9 booleans
│       │   │   ├── model/Board.kt       immutable 3×3 board, Side, PlacedCard
│       │   │   ├── model/Power.kt       effective power, clamping, ascension tally
│       │   │   ├── model/RulesEngine.kt capture resolution + combo, pure
│       │   │   ├── model/Match.kt       turn order, scoring
│       │   │   ├── data/CardRepository.kt  CardCatalog + parser + resource loader
│       │   │   ├── i18n/Strings.kt      AppLocale, lookup + fallback, LocalStrings
│       │   │   ├── i18n/StringKeys.kt   every key the UI names, in one place
│       │   │   ├── log/Log.kt           levels, lazy messages, pluggable sink
│       │   │   ├── audio/AudioPlayer.kt Sound, the loop point, silent + recording players
│       │   │   ├── settings/SettingsStore.kt   interface + in-memory implementation
│       │   │   ├── settings/UserSettings.kt    UserSettings.json, load/save, first run
│       │   │   └── ui/
│       │   │       ├── App.kt           root composable, four screens, back handling
│       │   │       ├── Startup.kt       StartupPhase, the splash's own model
│       │   │       ├── SplashScreen.kt  logo + phase line + progress
│       │   │       ├── MainMenuScreen.kt   play / options / quit
│       │   │       ├── OptionsScreen.kt    language + the two volumes
│       │   │       ├── CardArt.kt       texture loading, face cache, digit atlas
│       │   │       ├── MatchScreen.kt   playable board, both hands, orientation layout
│       │   │       ├── CardView.kt      CardFace + CardDigits, scalable
│       │   │       └── CardColors.kt    colours and geometry lifted from the AS3 source
│       │   └── composeResources/files/
│       │       ├── cards.json    263 cards, generated
│       │       ├── art/          283 PNGs, 7.01 MB, imported (incl. the logo)
│       │       └── locales/      tto-<tag>.json imported ×4, app-<tag>.json authored ×4
│       ├── commonTest/…         CardTest (5) + CardCatalogTest (8) + RulesEngineTest (37)
│       │                        + MatchStateTest (27) + StringsTest (9)
│       │                        + UserSettingsTest (12) + LogTest (7)
│       │                        + SoundTest (5) = 110,
│       │                        run on desktop + androidHostTest
│       ├── desktopTest/…        MatchUiTest (10) + NavigationTest (9) + MatchAudioTest (9)
│       │                        + StringsBundleTest (8) + OptionsUiTest (7)
│       │                        + MatchLayoutTest (6) + CardBundleTest (4)
│       │                        + CardFaceTest (2) = 55
│       └── iosMain/…/MainViewController.kt
├── androidApp/                  Android host: settings store, logcat sink, audio player
│   └── src/main/res/            generated launcher icon + the ten sounds, in raw/
├── desktopApp/                  JVM host + DesktopSettingsStore — run the UI without an emulator
└── iosApp/*.swift               SwiftUI host sources (see the iOS caveat below)
```

CI is [`.github/workflows/build.yml`](.github/workflows/build.yml). It used to need
`working-directory: kotlin` in every job, because the Gradle build sat in a subdirectory while
GitHub only reads workflows from the repository root; now that the build *is* the root, that
is gone and the path filters are `paths-ignore` rather than a `kotlin/**` allow-list.

## Toolchain

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 9.6.1 | via the committed wrapper; daemon JVM pinned by `gradle/gradle-daemon-jvm.properties` |
| JDK | 17 | `jvmToolchain(17)` in every module |
| Kotlin | 2.2.20 | `org.jetbrains.kotlin.plugin.compose` is versioned with Kotlin, not with Compose |
| Compose Multiplatform | 1.9.3 | Material 3, plus `compose.components.resources` |
| kotlinx.serialization | 1.9.0 | plugin version tracks Kotlin |
| kotlinx-coroutines-test | 1.8.1 | `commonTest` only; pinned to the version Compose already brings in |
| Android Gradle Plugin | 9.3.1 | `:shared` uses `com.android.kotlin.multiplatform.library`; `:androidApp` needs no Kotlin plugin of its own |
| ktlint (via `org.jlleitschuh.gradle.ktlint`) | plugin 12.1.2 | configured entirely from `.editorconfig` |
| JaCoCo | Gradle built-in | coverage; **not Kover**, which cannot be applied here at all — see [§ Coverage](#coverage) |
| detekt | 1.23.8 | `buildUponDefaultConfig`, `maxIssues = 0` |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 | |

There is no `composeOptions.kotlinCompilerExtensionVersion` anywhere: since Kotlin 2.0 the
Compose compiler ships inside Kotlin and is applied as a plugin.

## Prerequisites

Summarised here; [docs/development/project-setup.md](docs/development/project-setup.md) is the
full version, including which host can build what and the first-run failures worth recognising.

- JDK 17 on `PATH` or `JAVA_HOME`.
- For the Android module only: an Android SDK with platform 37 (what `compileSdk` names) and a `local.properties`
  pointing at it. Copy `local.properties.sample` and fill in `sdk.dir`. On Windows
  **escape both the drive colon and the backslashes**, and end the file with a single
  `LF` — an unescaped colon or a `CRLF` makes `lintDebug` fail with `PropertyEscape`:

  ```properties
  sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
  ```

- Nothing extra for the desktop module.
- Python 3 only if you need to regenerate `cards.json`.

## Commands

The ones used daily. What each task actually runs, where its output lands and how to reproduce a
CI job locally: [docs/development/build-guide.md](docs/development/build-guide.md). Running,
filtering and writing tests: [docs/development/testing-guide.md](docs/development/testing-guide.md).

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

Fast test loop (all 165 tests, 22 s forced from scratch, instant when nothing changed):

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
python tools/extract_cards.py shared/src/commonMain/composeResources/files/cards.json
```

Re-import the artwork after any change to the catalog — it fails if a card has no picture:

```bash
python tools/import_card_art.py
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

## Card artwork

`shared/src/commonMain/composeResources/files/art/` is **imported**, not authored:
[`tools/import_card_art.py`](tools/import_card_art.py) copies it byte-for-byte out of
`sources/assets/` and renames each file to the AS3 texture id, so a card's picture is
addressable as `files/art/{collection}{id}.png` — literally `Card.as:166`.

| | Count | Size |
|---|--:|--:|
| card faces (`ff14_1` … `ff8_110`) | 263 | 7.00 MB |
| card back, digit atlas, 5 rarity rows, 12 type icons | 19 | 85 KB |
| `logo.png` — the wordmark, for the splash and the menu | 1 | 15 KB |

### Individual files, not the sprite sheets

`sources/assets/cards/` ships two ShoeBox atlases, and the question of whether to use them is
a measurement rather than a preference. Both encodings are 8-bit RGBA, so this compares like
with like:

| | Individual files | Sprite sheets |
|---|--:|--:|
| Download size, all 263 cards | 7.00 MB | ~4.9 MB (three 1024×2048 sheets) |
| Resident bitmap, a match (≤ 19 cards) | ~1.0 MB | 24 MB |
| Resident bitmap, all 263 decoded | 14 MB | 24 MB |
| Cards actually covered by the shipped sheets | 263 / 263 | **190 / 263** |

A sheet decodes whole or not at all: 1024 × 2048 × 4 bytes = 8 MB each, resident whether one
card is on screen or all of them. Trading 2 MB of download for 24 MB of permanently resident
memory is the wrong way round on a phone, and `ff14_cards.xml` stops at id 80 — a complete set
would need repacking with a tool this repository does not contain. So: individual files, loaded
on demand and cached (`CardArt`).

`digits.png` is the one atlas kept, and not for size — it is 10 KB either way. It is the only
source of the 28×28 `cdbg` plate, and its entries are the untrimmed 18×18 rectangles the
geometry in `CardColors.kt` is built on; the loose `digits/1.png` files are trimmed to 15×12
and would need their offsets re-derived. It is sliced into `BitmapPainter`s with source
rectangles, so the sheet is decoded once and no glyph is ever copied out of it.

### Layer order

The card is the full **104×128 sprite**, not the 88×118 colour quad, because the artwork
includes the frame. `CardFace` stacks `Card.as`'s display list in `addChild` order — colour
quad, artwork, rarity row, type icon, digit cluster, and the back on top while flipping. The
artwork has a **translucent centre**, and what shows through it is the owner's colour, which is
how the original serves both sides from 263 images instead of 526. Verified on the device: the
same picture reads blue in one hand and red in the other.

Two exceptions, both marked in the code. The `cardSelected` glow (layer 0) is drawn at
(−16, −4) — outside even the sprite bounds — so this port rings the card instead rather than
grow every slot by 16 dp. The `_modifier` badge (layer 5) is the Ascension/Descension `±N`
text, which no rule in this UI switches on yet.

Not every card has a translucent centre: the FF8 five-star character cards ship a silver frame
with an opaque illustration, so they read grey in both hands. That is the source artwork, not a
layering fault — `ff8_102.png` (Laguna) shows it directly.

## Launcher icon

The manifest used to say `android:icon="@mipmap/"` — an empty reference, so the launcher fell
back to the stock Android robot. `androidApp/src/main/res/` now holds a full icon set, and every
file in it is generated by
[`tools/make_launcher_icons.py`](tools/make_launcher_icons.py) from
`sources/assets/appIcons/icon_128.png`, the icon the AIR build shipped and the only size of it
that exists. Do not hand-edit `res/`; re-run the script.

The two halves of the source are **separated rather than resized together**, because 128 px is
not enough for an xxxhdpi icon (432 px of adaptive canvas):

| Layer | How | Sharpness |
|---|---|---|
| teal plate | vector gradient, `drawable/ic_launcher_background.xml` | exact at every density |
| white wing | PNG per density, `mipmap-*/ic_launcher_foreground.png` | resampled from 128 px |
| legacy (API 24–25) | plate + wing composited, `mipmap-*/ic_launcher.png` | plate exact |
| themed (API 33+) | the same wing as `<monochrome>` | resampled |

The whole set is 85 KB across 12 files, of which 58 KB is the legacy bitmaps.

Both halves were **measured, not eyeballed**. Fitting a plane per channel across the plate's
9 338 opaque background pixels gives equal x and y slopes to three decimals with a median
residual of 4/255 — so the plate is a linear gradient at 45°, `#0CD4FE` to `#034D60`, and it is
kept as a vector. The wing is pure white, so the plate's red channel (0–12) and the wing's (255)
never overlap and the red channel *is* the coverage mask; a blend pixel at the wing edge,
(190, 233, 242), is white at α = 0.74 over the fitted plate to within 4/255. Only the wing is
ever resampled, and its mask is re-sharpened by the scale factor afterwards, so the one lossy
step is confined to a smooth silhouette.

The source's drop shadow under the wing is **dropped on purpose**: an adaptive icon must not
carry a baked shadow, since the launcher casts its own from the layer geometry.

### The foreground is smaller than the usual 72dp

The wing reaches a radius of 66.7 px in the 128 px source — past the plate's own rounded
corners. Mapping the plate to the standard 72dp would put the wing at radius 37.5dp, which a
**circular mask (r = 36) cuts**. So the plate maps to 63.4dp instead, which keeps every wing
pixel inside the 66dp safe circle under any mask shape. The icon therefore reads a little
smaller than a stock Android Studio import, and is never clipped — verified against circle,
squircle and square masks before building, then on a device.

**`android:roundIcon` is deliberately not declared.** It exists so an API 25 launcher can ask
for a circular treatment of a square icon, and the AS3 plate is already a circle — its corner
radius is 59/128 of the side, recovered from the 2 994 transparent corner pixels, leaving a
10 px straight segment per side. The round variant was generated first, compared, found
indistinguishable, and dropped: it was 59 KB of near-duplicate bitmaps.

**Not verified:** the `<monochrome>` themed-icon layer. Seeing it requires turning on themed
icons in the launcher's own settings, which is a change to the device rather than to this build.

## Coverage

`./gradlew :shared:coverageReport` writes HTML and XML to
`shared/build/reports/jacoco/coverageReport/`. `./gradlew build` runs `coverageVerify`, which is
wired into `check`, so a coverage collapse fails the build rather than waiting to be noticed.

| Counter | Covered | Gate |
|---|--:|--:|
| line | **97.8%** (1267/1296) | 90% |
| branch | **85.9%** (486/566) | 75% |
| instruction | 95.8% (11 731/12 242) | — |
| method | 93.6% (424/453) | — |

The gates are **floors, not targets**, set well under the measured figures on purpose: they
exist to catch a test file being deleted or a whole area going untested, not to turn every
refactor into a coverage negotiation. That the gate can actually fail was checked by raising the
line minimum to 99% and watching the build stop
(`lines covered ratio is 0.96, but expected minimum is 0.99`).

### JaCoCo, not Kover

Task 1.11 asked for Kover. **Kover cannot be applied to this module at all.** Version 0.9.3 —
the newest that exists; there is no 0.10.0 — aborts during plugin application with:

```
Kover error: Kover requires extension with name 'android' for project ':shared'
since it is recognized as Kotlin/Android project
```

Under `com.android.kotlin.multiplatform.library` there is no project-level `android` extension to
find, because the Android configuration moved inside `kotlin { android { } }`. Kover offers no way
to opt out of that detection, so the choice was JaCoCo or no coverage. 0.9.1, 0.9.2 and 0.9.3 were
each tried and each fail identically.

### Measured on desktop only

Not a shortcut. `commonMain` is the code under test; `desktopTest` runs all 110 common tests plus
the 55 that need a UI; the Android host-test run executes *the same common sources* a second time.
Instrumenting both would double-count identical lines rather than reach new ones. What is
genuinely not measured is the two host modules — `AndroidSettingsStore`, `DesktopSettingsStore`,
`MainActivity`, the logcat sink — which have no tests and are excluded rather than counted as 0%.
The generated Compose resource accessors are excluded too.

## Logging

Nothing logged at all until the settings layer needed to explain itself.
[`log/Log.kt`](shared/src/commonMain/kotlin/com/tripletriad/log/Log.kt) is about eighty lines:
four levels, a `fun interface` sink, `println` by default.

```kotlin
Log.w(TAG, failure) { "settings file is not readable JSON; replacing it" }
```

**The message is a lambda**, so a suppressed line is never formatted. A logger that builds its
string and then discards it is one nobody dares call from the per-frame and per-card paths, which
is exactly where it would earn its keep. A test pins this by counting how many times the lambda
runs while the level is above it.

**No Napier**, which the plan named. Napier's job is to forward to `android.util.Log` on Android
and `println` elsewhere; that is the file above plus four lines in `MainActivity`, which installs
the logcat sink and holds release builds at `INFO`. `:shared` keeps no Android import. A
dependency earns its place by doing something hard.

### It has callers, which is the point

`UserSettingsRepository` had three `runCatching { }.getOrNull()` that discarded the failure with
it: an unreadable file, unparseable JSON, a failed write. All three now report. Repairing a
settings file silently is how a file that is rewritten on *every* launch goes unnoticed for
months.

Verified end to end rather than only in tests: a corrupt `UserSettings.json` was written onto a
Pixel with `adb shell run-as`, and the relaunch produced

```
W Settings: settings file is not readable JSON; replacing it
W Settings: kotlinx.serialization.json.internal.JsonDecodingException: Unexpected JSON token
            at offset 2: Expected quotation mark '"', but had 'n' instead at path: $
```

followed by a repaired file holding the device's own language.

## Screens and navigation

Four destinations, in a `remember`ed enum:

```
SPLASH ──(startup finishes)──▶ MENU ──▶ MATCH   (‹ chevron, or system back)
                                └────▶ OPTIONS (‹ Back, or system back)
                                └────▶ onQuit  (the host's business)
```

**No Compose Navigation.** `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.3 specifies a `NavHost`
with named routes. Four destinations, no deep links, no arguments and no back stack worth the name
do not pay for a dependency and a route-string layer. When this grows toward the original's 32
screens, that is the point to reconsider — and the enum will have told us so by then.

**The Android system back gesture is handled**, which it was not before: `BackHandler` from
`androidx.compose.ui.backhandler` — multiplatform in Compose 1.9, so no Android-only source set —
returns to the menu from a match or the options. Without it, back mid-match finished the activity
and the app appeared to quit from the middle of a game. It is deliberately *disabled* on the menu,
where leaving the app is the right answer. It needs the `ui-backhandler` artifact, which
`compose.ui` does not bring in.

### Splash

The AS3 build had no splash — Flash's own preloader covered the wait and `MenuScreen` was the first
thing drawn. This is not a port of anything: an installed APK has no preloader, and the update
check that is coming needs somewhere visible to live.

So startup is an **ordered enum, not a spinner**:

| `StartupPhase` | Waits for | Line |
|---|---|---|
| `SETTINGS` | `UserSettings.json` — it decides the language everything after is shown in | `reading settings…` |
| `CARDS` | `cards.json`, 263 records | `loading cards…` |
| `ART` | the nineteen shared textures | `loading artwork…` |
| `READY` | nothing; terminal | `ready` |

Adding an update check is one entry, one branch in `rememberStartup`, and one string. A bare
spinner would have had nowhere to put something that can be slow *and can fail*.

The sequence is now **sequential**, where the two loads used to run concurrently with the match
gated on neither. That was right without a splash — the board arrived a fraction sooner — and wrong
with one: the artwork pop-in happened in front of the user instead of behind a progress line. What
did *not* change is that `CardArt` is still nullable everywhere downstream, so a failed art load
still costs appearance and not playability.

### Main menu

`MenuScreen.as` in shape — the `logo_white_512` wordmark centred over a vertical stack, 8 px gap —
but three actions rather than eight: **Play, Options, Quit**. The original's Continue / New Game /
Load Game all need save games, which do not exist yet; `MenuScreen.as:52-58` is the order to grow
the list back in.

`onQuit` is a parameter, not something `:shared` does: `finish()` on Android, `exitApplication` on
desktop, and on iOS nothing at all, since Apple's guidelines have no "quit". A test asserts the
callback fires and that the menu stays put — `:shared` must not try to leave an app.

### Options

The three fields `UserSettings.json` actually holds, under the AS3's own headings
(`STR_GENERAL_SETTINGS`, `STR_AUDIO_SETTINGS` — `SettingsScreen.as` splits them the same way).

**Changes apply and persist immediately.** There is no Save button and `STR_SETTINGS_SAVED` — which
exists in all four bundles — is not used: on a phone, a pane you can leave with a back gesture must
not be able to lose what you just did, and picking a language redraws the screen in it, which *is*
the confirmation.

**The volume sliders work**, one per channel, and reach the running music as they are dragged —
see [§ Audio](#audio). They were shipped before the audio was, with a caveat under them saying
`saved, but nothing plays yet`; that string is now unused and stays in the bundles for the next
thing that is half-built.

Nearly every label came for free: `STR_PLAY`, `STR_SETTINGS`, `STR_QUIT`, `STR_LANGUAGE`, both
volume labels and both headings are all in the imported bundles, translated four ways. Only five
new `APP_*` keys were needed — `APP_BACK`, `APP_AUDIO_PENDING` and three `APP_STARTUP_*` — which is
why the app-owned count went from 5 to 10 and not to 17.

## Audio

`SoundManager.as` is ported as
[`audio/AudioPlayer.kt`](shared/src/commonMain/kotlin/com/tripletriad/audio/AudioPlayer.kt) — an
interface plus a `Sound` enum — with
[`AndroidAudioPlayer`](androidApp/src/main/kotlin/com/tripletriad/android/AndroidAudioPlayer.kt)
behind it. Same seam as `SettingsStore`, and for a stronger reason: there is no multiplatform audio
API at all.

### The files are not converted, and this is the measurement that says so

Every MPEG frame header in `sources/bin/sounds/` was parsed rather than assumed:

| | |
|---|---|
| codec | MPEG-1 and MPEG-2 Layer III |
| sample rates | 22 050 / 44 100 / 48 000 Hz |
| channels | **21 of 22 mono**; only the music is stereo |
| bitrate | 17 of 22 are VBR — all 22 carry a Xing header |
| total | 22 files, 1.40 MB, 84.5 s |

**No conversion is needed for Android**, which is the only target that plays anything today. Every
one of those combinations is in Android's *mandatory* decoder set at every API level from 24 up, so
`SoundPool` and `MediaPlayer` take the bytes as they are. The Xing headers matter for exactly one
file — the music, which seeks back to its loop point — because a VBR seek without one is a bitrate
guess.

Converting would cost something and buy nothing here:

| Target format | Verdict |
|---|---|
| **Ogg Vorbis / Opus** | Android supports both, so no gain. Would *lose* a future iOS: neither is decoded natively by AVFoundation, where MP3 is |
| **WAV / PCM** | The only format the desktop JVM decodes out of the box — at 6–10× the size, for a host that exists so the UI can be run without an emulator |
| **AAC / M4A** | Supported on Android and iOS, so no compatibility gain over MP3, and a re-encode of already-lossy audio |

**One caveat, and it needs ears rather than a build.** MP3 cannot loop sample-exactly: every encoder
adds decoder delay at the start and pads the end to a whole frame, so the seek back to 16.374 s
lands a few milliseconds off and may click. Ogg Vorbis would fix that on Android. Whether it is
audible over the loop point of this particular track is a judgement no test here can make — and the
AS3 original was *worse*, since it polled the position every frame and could overshoot by a whole
frame before restarting. If it turns out to click, the fix is to convert that **one** file.

### What is played, and what is left out

`Sound` names **ten** of the original's twenty-two files. The rest are accounted for rather than
forgotten, by [`tools/import_sounds.py`](tools/import_sounds.py), which fails if a file is neither
played nor explained:

* **ten are referenced by no call site at all** in the AS3 source — including `flip`, whose only two
  calls are commented out in favour of `se_ttriad.scd_157`, and `win`, which the win sounds are not;
* **two belong to the coin flip** (`anims/PileOuFace.as`), which this port has not implemented.

That is 0.31 MB of sounds nothing could play, left out of the APK.

| Moment | AS3 id | From |
|---|---|---|
| match music, looping from 16.374 s | `shuffle_or_boogie` | `BaseMatchScreen.as:114` |
| hands dealt | `se_ttriad.scd_2` | `:157`, `openPhase()` |
| placed, captured nothing | `se_ttriad.scd_1` | `TTOCore.as:87` |
| a card changes hands | `se_ttriad.scd_157` | `Card.as:229`, in `flipTo` |
| a combo propagates | `se_ttriad.scd_15` | `TTOCore.as:125` |
| turn changes | `se_ttriad.scd_4` | `BaseMatchScreen.as:374` |
| blue / red wins | `se_ttriad.scd_7` / `_8` | `PVEMatchScreen.as:95` / `:139` |
| any control tapped | `se_ui.scd_72` | `TouchLabel.as:31` |
| next match | `se_gs.scd_162` | `RematchPanel.as:36` |

A draw is silent, which is the original's behaviour: its draw branch plays nothing.

### No Media3, and two engines rather than one

Task 1.5 names Media3. It is not needed: `SoundPool` and `MediaPlayer` are platform classes older
than `minSdk 24`, and between them they do everything `SoundManager` did. **No dependency was
added.**

The two are for different jobs, and the split is `SoundManager`'s own (`NOISE_CHANNEL` against
`BACKGROUND_CHANNEL`): `SoundPool` keeps short sounds decoded and overlaps them, so three cards
flipping in a combo do not cut each other off, but it would hold a 64-second track as ~11 MB of PCM
and cannot loop to a point. `MediaPlayer` streams and seeks, and would add latency if constructed
per tap.

### Three deviations from the original, each deliberate

1. **The cross-fade is not reproduced.** `fadeSoundChannel` was called with a 150 ms delay and
   stepped the volume by 0.01, so fading out from 1.0 took a hundred steps — **fifteen seconds**,
   not 150 ms — and it compared a float to `0` exactly. There is one track, so nothing cross-fades.
2. **The capture sound plays once per placement, not once per card.** `Card.as` fires it inside
   `flipTo`, so four simultaneous flips fired it four times; on `SoundPool` that is the same sample
   four times in the same millisecond — a volume spike, not a richer sound.
3. **The music pauses when the app is backgrounded** and resumes where it left off. AIR on a desktop
   had no notion of being backgrounded. Pause rather than stop, because backgrounding does not change
   the composition: the effect that starts the music would not fire again, and the match would come
   back silent.

### Verified on a device, because nothing else can verify it

No test can hear anything. What the tests pin is the **mapping** — which moment asks for which
sound — through the real UI with a recording player.

On the phone, `dumpsys audio` during a match:

```
type:android.media.MediaPlayer  state:started  usage=USAGE_GAME content=CONTENT_TYPE_MUSIC
    sampleRate=44100  channelMask=0x3        <- shuffle_or_boogie, stereo, decoding
type:android.media.SoundPool    state:idle    usage=USAGE_GAME content=CONTENT_TYPE_SONIFICATION
```

The music is genuinely decoding from the unconverted MP3, and no `SoundPool` load failed — a failure
logs a warning, and there were none.

**What remains unverified**: whether the loop point clicks, and whether the mix sounds right. Both
need someone to listen.

## Rules engine

The rules are implemented as **pure functions over immutable state** — no UI, no coroutines,
no display objects. `RulesEngine.resolve(board, position, card, player, tally)` returns a
[`Resolution`](shared/src/commonMain/kotlin/com/tripletriad/model/RulesEngine.kt): the
resulting board plus every capture, each tagged with its kind and its combo wave.

That shape is the point. The AS3 original keeps domain state *inside* Starling display
objects — `Card.modifier` has no backing field, it is stored in a `TextField` and parsed back
out — which is why its rules engine cannot be unit-tested and why its AI dry run corrupts the
board it is evaluating. See
[data-flow.md § 1.1](docs/analysis/data-flow.md) and
[§ 4.3](docs/analysis/data-flow.md).

| Type | What it holds |
|---|---|
| `GameRules` | the 12 rule slots — **3 enums and 9 booleans**, not 20 flags, so Ascension and Elemental are mutually exclusive by construction |
| `Board` | immutable 3×3, positions 0..8 row-major; `place()` and `capture()` return new boards |
| `effectivePower(…)` | printed power → Fallen Ace → type modifier → clamp, in that order |
| `AscensionTally` | the board-wide per-type counter behind Ascension and Descension |
| `RulesEngine` | capture resolution, precedence, and combo propagation |
| `TurnOrder`, `score()` | 9 placements, 5 for the first player and 4 for the second; score counts unplayed cards |

### Two power ranges, not one

**Card powers are 1..10. Effective powers are 0..10.** The floor is zero because Fallen Ace
produces 0 directly and Descension can drive a 1 down to 0. `Card` enforces `1..10` in its
`init` block, which is right for card data and would be wrong for a tile — hence
`MIN_EFFECTIVE_POWER` and `clampPower`, the AS3 `tools.madmax`.

### Two deliberate departures from the source

Both are recorded in [game-rules.md § 15](docs/analysis/game-rules.md#15-defects-and-ambiguities)
and both change the outcome of real games, so neither is silent. They live in
`RulesEngineOptions`, and `RulesEngineOptions.FAITHFUL` reproduces the original including its
defects — used by tests that pin both behaviours.

1. **Same and Plus use effective powers** (§ 15.4). The AS3 computes them from *printed*
   values while basic capture uses modified ones, so under Elemental or Ascension the two
   disagree. FF14 uses the modified values, and the author's own comment at `TTOCore.as:215`
   reads as uncertainty rather than a decision.
2. **Same Wall fires with one neighbour** (§ 15.2). The AS3 gates it behind
   `same.length > 1`, which makes the rule inoperative in exactly the board states it exists
   for — a wall is meant to *be* the second match.

The defaults are the corrected behaviour rather than the original because the AIR client is
abandoned and unrunnable, so bit-for-bit fidelity is unverifiable anyway, while FF14 remains a
reference anyone can check by playing it. Flip either option to reverse the choice.

### What is not implemented

Roulette rule generation, the pre-match phase chain (Random hand, Swap, the coin flip), Order
and Chaos enforcement, Sudden Death, the AI, and the match state machine that sequences turns.
The engine resolves *one placement*; nothing yet drives a whole match.

## Running on a real Android device

### From Android Studio

Open the **repository root** — the Gradle build is the root, so Studio finds the three
modules on sync. (Before the project was promoted out of a `kotlin/` subdirectory, the root
had no `settings.gradle.kts` and Studio found nothing.) After the sync, pick the
`androidApp` configuration, select the device, Run.

If the sync is refused with something like *"AGP version not supported"*, the IDE is older
than AGP 9.3.1 requires. Either update Studio,
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
| `./gradlew :androidApp:assembleDebug` | **BUILD SUCCESSFUL** — `androidApp-debug.apk`, 19 070 KB |
| `./gradlew :androidApp:assembleRelease` | **BUILD SUCCESSFUL** — `androidApp-release-unsigned.apk`, 16 141 KB |
| `./gradlew :desktopApp:build` | **BUILD SUCCESSFUL** — `desktopApp.jar` |
| `./gradlew :shared:desktopTest` | **95 tests, 0 failures** |
| `./gradlew :shared:build` (all targets) | **222 test executions, 0 failures** |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** — 0 findings, `maxIssues = 0` |
| `./gradlew :shared:lint` | **0 errors**, warnings only ("a newer version is available") |
| `./gradlew :desktopApp:run` | window opens, titled "Triple Triad", nothing on stderr |
| `./gradlew :androidApp:installDebug` + launch | **runs on a physical device** — see below |

Release APK note: `isMinifyEnabled = false`, so 16 141 KB is an **un-shrunk upper bound**,
not what a shipped build would weigh.

### On a physical device

Installed and launched on a **Pixel 6a, Android 17 (API 37), arm64-v8a**, 1080×2400 at
420 dpi, paired over adb-over-Wi-Fi:

- `installDebug` → "Installed on 1 device", then `am start` →
  `topResumedActivity=com.tripletriad.android/.MainActivity`.
- Nothing from `AndroidRuntime` or `FATAL` in logcat; the only app line is
  `ProfileInstaller: Installing profile for com.tripletriad.android`.
- **Both orientations verified by screenshot.** Landscape (2400×1080): red hand left in a
  2×3 block, board centred, blue hand right, every card at the authored 104×128 with no
  overlap and nothing clipped. Portrait (1080×2400): red hand a strip across the top, board
  centred, blue hand across the bottom. Rotating a running match keeps it (`configChanges`).
- **The status bar, the navigation buttons and the clock/battery/signal row are hidden** —
  `MainActivity.goFullScreen`. Recoverable with an edge swipe.
- **The artwork renders and the layers stack correctly.** The colour quad shows through the
  translucent centre — the same picture reads blue in one hand and red in the other; the rarity
  row sits top-left, the type icon top-right, the digit badge over the artwork and not under it.
- Placement, capture and the flip all work under real touch and under `adb shell input tap`.
  A match played out to nine placements ended `blue 5 — 5 red` / `draw`, with four cards
  showing their captured colour and red's unplayed card still counting for red. A scripted
  capture — a card with `left = A` played beside one with `right = 3` — moved the score from
  `5 — 5` to `6 — 4` and left the flipped card's artwork, stars and digits upright.
- A power of 10 renders as `A` (visible on `Laguna 9/5/3/A`).

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
commonTest — runs on desktop and androidHostTest
  com.tripletriad.settings.UserSettingsTest 12 tests
  com.tripletriad.log.LogTest              7 tests
  com.tripletriad.i18n.StringsTest         9 tests
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

desktopTest — real Compose tree on the JVM, plus the JVM-only bundle reads
  com.tripletriad.ui.MatchUiTest         10 tests
  com.tripletriad.i18n.StringsBundleTest  8 tests
  com.tripletriad.ui.MatchLayoutTest      6 tests
  com.tripletriad.data.CardBundleTest     4 tests
  com.tripletriad.ui.CardFaceTest         2 tests
```

`RulesEngineTest` is the
[§ 16 test matrix](docs/analysis/game-rules.md#16-test-matrix-for-the-port) from the rules
specification, case for case: basic capture and Reverse, Fallen Ace and its interactions,
Same / Plus / Same Wall, combo propagation, the three type rules, turn order and scoring.

165 distinct tests; **275 executions** — the 110 in `commonTest` run once per target
(`desktopTest` and `testAndroidHostTest`) and the 55 in `desktopTest` once — 0 failures.

Line coverage is **97.8%**, branch **85.9%**, measured on the desktop target and gated in
`check` — see [§ Coverage](#coverage).

It used to be 249, over three targets, and the drop is not a loss of coverage. AGP 9 stopped
creating a release unit-test variant for library modules, and the module then moved to
`com.android.kotlin.multiplatform.library`, where the Android unit tests run once under
`:shared:testAndroidHostTest`. The runs that disappeared were the same 77 tests against
variants differing only in flags no unit test reads — unit tests do not go through R8.

**The suite is not vacuous.** Mutating `RulesEngine.beats` from `defence < attack` to
`defence <= attack` — the single most plausible way to get capture wrong — makes
`equalPowersNeverCapture` fail, and only that test. Reverting restores green. Ties are the
case a plausible-looking port gets wrong, because `reverse` looks like a negation and is not:
both comparisons are strict, so equal powers hold under both.

`CardBundleTest` reads the shipped `cards.json` out of the actual resource bundle, so it
fails if the resource is dropped from packaging, if the generated `Res` accessor moves, or
if the JSON schema drifts from the model. The parser is tested separately and purely in
`commonTest`.

`MatchUiTest` drives the real `App()` — pick a card, pick a cell, nine times over — and
asserts invariants rather than a particular board: the turn passes, an illegal placement is
swallowed rather than thrown, the score always totals 10, a finished match announces a result.
Every one of its tests also covers resource packaging, because `App()` shows nothing but
"loading cards…" until the bundle is parsed, so they all hang at `awaitCatalog()` if it is
missing.

`CardFaceTest` asserts that a card is drawn with **its own** artwork, which is there because
it was not. `rememberCardFace` used `produceState`, whose value lives in an unkeyed `remember`:
changing the keys restarts the producer but keeps the previous value, and the producer only
loaded when the value was null. A composable slot handed a second card therefore kept drawing
the first one's picture. It needs a *reused* slot to show up, which the hand does constantly —
slots close up as cards are played — and no assertion had ever looked at which bitmap a slot
held, so it was found by playing the game and not by the suite. The test compares bitmap
identity rather than pixels, since `CardArt` caches one instance per texture id. Mutation-checked:
restoring the `produceState` version fails `theFaceFollowsTheCardWhenASlotIsReused` and nothing
else.

`MatchLayoutTest` covers `matchLayout`, which is a pure function of a measured width and
height precisely so it *can* be covered. Its load-bearing test is
`theArrangementAlwaysFitsInTheSpaceItWasGiven`: across nine viewports, the footprint of two
hand areas plus the board must not exceed the bounds. Three earlier revisions of this screen
estimated the space instead of measuring it and each one over-subscribed its column on some
device — which is not a visible error, because `Modifier.size` silently coerces into the
constraints it is given, so children collapse to zero height while continuing to draw at full
size. The symptom is cards drawn on top of each other; the test is the thing that would have
caught it.

## Localisation

Four languages, from the original's own bundles: **English, French, German, Japanese**
(`utils/conf.as:11`, `application.xml`). The device language is narrowed to the nearest of the
four and everything falls back to `en_US`.

```
shared/src/commonMain/composeResources/files/locales/
  tto-<tag>.json    687-688 keys, imported by tools/import_locales.py — do not edit
  app-<tag>.json    5 keys this port wrote — edit these
```

The split is provenance. `tto-*` is Square Enix wording that must stay exactly what the original
displayed; `app-*` exists because the AS3 showed whose turn it was *graphically* and never wrote
the sentence. `Strings` merges them with `app-*` on top, then falls back to English.

`app-de_DE.json` and `app-ja_JA.json` are `{}` on purpose. Those five sentences are not
translated into German or Japanese — they resolve to English while the other 647 / 680 keys stay
in the device's language. An empty file states that; a missing one would read as an oversight.

### What the source data turned out to be

The plan (Task 1.10) said the bundles were under `sources/bin/assets/{de_DE,…}/`. They are not:
those four directories hold `rules.png` + `rulesAtlas.xml`, a *texture* atlas of rule-name
images. The strings are already JSON, in **`sources/bin/datas/locales/`**.

Four defects in that data, all reported by the importer on every run:

| Defect | Detail |
|---|---|
| Duplicate key, different values | `STR_REGISTER_MATCH` twice in `en_US` ("Create Match" then "Defy") and `fr_FR`. Resolved **last-wins**, which is what AS3's `JSON.parse` did, so the port shows what the original showed. Pinned by a test, because the alternative is an implementation detail silently choosing a product string. |
| Keys absent from the fallback | `RULE_OPEN` (only `de_DE`, a pre-rename leftover), `STR_GSGROUP` (only `fr_FR`), and two malformed `ja_JA` keys — `STR_SAVES_LISTは` and a `STR_NPC_MA_DINCHT` with two trailing zero-width spaces. Unreachable typos; kept rather than quietly deleted. |
| Uneven coverage | `de_DE` is 44 keys short of the union, `ja_JA` 11. `STR_NEXT_MATCH` is one of them, which is why the reset control reads "Next Match" on a German device — a real fallback, exercised by the shipped data rather than by a contrived fixture. |
| Markup in one locale only | `fr_FR` prefixes 18 `RULE_*_HELP` values with `<i>FF14 uniquement</i>`; Feathers rendered HTML in a text field and Compose's `Text` does not. Nothing displays rule help yet, so the markup is left in the data — stripping it now would hide that the rules screen needs an `AnnotatedString` converter. |

Mistranslations are also present and **not** fixed. `STR_DRAW` is "Zeichnen" (de) and "描く" (ja),
both meaning *to draw a picture* rather than *a tie*. The main menu adds two more, now that
`STR_PLAY` is on screen: it is **untranslated in German** ("Play") and in Japanese reads **"再生"**,
which is *playback* — the button on a media player, not an invitation to play a game. All of them
are the original's strings, they are visible on the app's most prominent button, and overriding them
is a product decision rather than a porting one. `app-<tag>.json` is the mechanism when it is taken:
an entry there wins over the imported bundle. Inventing replacements inside a bundle that is
evidently machine-translated would not make it trustworthy, so they are reported instead.

### Verified on the device

French renders end to end. Japanese renders too — **あなたは勝つ！** for a win — with no bundled
font: the port uses the platform font family, so Android's CJK fallback covers it. The plan's
"Japanese needs a CJK font" warning is about `Eurostile`, which `Card.as:81` bundles for card
text and this port does not use; the concern returns if that font is ever adopted.

One real bug came out of switching language, and only the screenshot could see it. The status bar
was three items in a centred row, which fitted because every string was English — French pushed
"Match suivant" onto a second line. The turn line now takes the leftover width and elides; the
controls keep theirs. **No test in the suite can catch that**, because wrapping does not change
the semantics text a Compose test reads.

## User settings

`UserSettings.json` — three fields, exactly the ones `utils/conf.as` wrote:

```json
{ "language": "fr_FR", "background_volume": 1.0, "noise_volume": 1.0 }
```

The snake_case keys are deliberate and pinned by a test: a file written by the AS3 build has to keep
parsing, and one written here has to stay readable by it. Two volumes rather than one because
`SoundManager` has two channels — with a single stream every card flip would duck the music.

First run writes the file, seeded from the device language, which is `conf.as:22-40`. After that
**the file decides**: changing the device language does not follow, as in the original, and the
settings screen is where it is meant to be changed. Verified on a French device — a file saying
`en_US` produces an English UI.

### Where it lives, and why not where the AS3 put it

| Host | Path |
|---|---|
| Android | `Context.filesDir/UserSettings.json` |
| Desktop | `~/My Games/Triple Triad Online/UserSettings.json` |

`conf.as:14` wrote `My Games/Triple Triad Online/UserSettings.json` under AIR's
`documentsDirectory` — shared external storage on Android. Scoped storage (API 29+) closed that
off, `minSdk` is 24 so a legacy path would need a permission prompt plus a runtime branch, and a
settings file has no business being visible to a file manager. App-private storage is the right
target; the file name is kept so the contents stay recognisable.

### No `expect`/`actual`, on purpose

Task 1.6 asks for `expect class FileManager`. That is the wrong shape here:

- **`expect` obliges every declared target to supply an `actual`,** and `:shared` declares the three
  iOS targets. Kotlin/Native cannot build Apple targets from a Windows host, so an iOS `actual`
  could not be compiled — let alone run — before being pushed; the macOS CI job would be the first
  thing to see it. Writing unverifiable code to satisfy a target that was explicitly descoped
  ("Android only for now") buys nothing.
- **The Android implementation needs a `Context`,** which means threading one into a composable or
  parking it in a global.

So `:shared` declares a `SettingsStore` interface and each host supplies the implementation it can
actually build. `:shared` keeps no platform file access at all, and when iOS returns it implements
the same interface with `NSFileManager` — still no `expect`. The Android store writes to a temp file
and renames, so a kill mid-write leaves the old settings rather than a truncated file.

A corrupt file is **repaired, not fatal**: `conf.as` would have thrown out of `JSON.parse` and taken
the launch with it. Nothing in this file is worth failing to start over, and an unrepaired file
would throw on every launch thereafter.

## Known issues

**Three deprecation warnings remain, none of them ours.** `./gradlew build --warning-mode all`
prints exactly these, all from plugin internals and all scheduled for removal in Gradle 10:

| Warning | Raised by |
|---|---|
| `ReportingExtension.file(String) has been deprecated` | detekt 1.23.8, `DetektPlugin.kt:28` |
| `The archives configuration has been deprecated for artifact declaration` | Kotlin Multiplatform, when `jvm("desktop")` registers its jar |
| `Declaring dependencies using multi-string notation has been deprecated` | Kotlin Multiplatform, resolving `kotlin-native-prebuilt` |

Everything else was fixed rather than documented — see
[Toolchain](#toolchain). Gone with it: the four `API 'applicationVariants' /
'libraryVariants' / 'testVariants' / 'unitTestVariants' is obsolete` warnings, and seven
deprecated `android.*` option settings.

**No frame-timing measurement.** `dumpsys gfxinfo` recorded zero frames because the
device's screen locked partway through the session. The plan's "60+ FPS" criterion is
therefore unverified — the flip looks smooth, which is not a measurement. The commands to
fill this in are in
[docs/analysis/performance-baseline.md](docs/analysis/performance-baseline.md) §2.

**No card-internal layout assertions.** `MatchLayoutTest` covers the *arrangement* — which
hand goes where, at what scale, and that it fits. Nothing asserts where a layer sits *inside*
a card, so a regression that moved the digit badge would pass CI. Since the whole point of
`CardColors.kt` is reproducing exact AS3 coordinates,
`assertLeftPositionInRootIsEqualTo` and friends would close the gap.

**Starling's easing curves are not Compose's.** `Transitions.EASE_IN` / `EASE_OUT` are
mapped to `FastOutLinearInEasing` / `LinearOutSlowInEasing`, which are the closest
equivalents and not the same functions. A visual diff pass against the original is still
owed — see [docs/analysis/api-mapping.md](docs/analysis/api-mapping.md).

**The mid-flip frame was never photographed.** `adb shell screencap` PNG-encodes a
1080×2400 frame in roughly 300 ms, so a 24-shot burst fired at the tap lands at most one
frame inside a 400 ms animation, and both attempts landed after it had settled. What is
verified is the settled result — the card changed hands, and its artwork, stars and digits
are upright. That the *intermediate* frames cannot mirror is an argument rather than an
observation: `scaleX` and `scaleY` only ever take values in [0, 1.2], so no axis is ever
inverted. Catching the frame needs `screenrecord` plus a frame extractor, which is not
installed here.

**A card is scaled by multiplying its geometry, not by scaling its render layer.** The first
implementation measured `CardFace` at its authored 88×118 (`requiredSize`) and shrank it with
`graphicsLayer { scaleX = scale }`. That reports a small size while drawing a large one, so
anything that promotes the composable to an offscreen layer clips it — and the dimmed hand does
exactly that, because `alpha < 1` forces one. The symptom was the waiting side's cards rendering
as slivers while the active side's looked correct. Multiplying every dp and sp by `scale` keeps
drawn bounds and reported bounds equal, which is the only version of this that composes safely.
Noted on `CardFace`.

**Portrait support is a deliberate departure.** `application.xml` declares
`<aspectRatio>landscape</aspectRatio>` and the AS3 build is desktop-only, so the original has
exactly one arrangement. A phone does not, so `matchLayout` picks between two: hands either side
of the board in landscape (the FFXIV arrangement — opponent left, player right), above and below
it in portrait. The `screenOrientation` lock is therefore gone from the manifest. Board tiles get
their own scale, always ≥ the hand scale, because a portrait hand is five cards across where the
board is three and would otherwise leave a third of the screen empty; FFXIV draws the board
larger than the hands too.

**CI is green, on the second attempt.**
[`.github/workflows/build.yml`](.github/workflows/build.yml) failed on its first run at
the first step of every job:

```
./gradlew: Permission denied      (exit code 126)
```

`gradlew` was committed from Windows, where `core.filemode` is `false`, so it
landed in the git index as `100644` instead of `100755`. Fixed with
`git update-index --chmod=+x gradlew`; see
[git-workflow.md § File modes on Windows](docs/development/git-workflow.md#file-modes-on-windows).

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

`iosApp/*.swift` contains the SwiftUI host, but **there is no `.xcodeproj` or
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

- Kotlin 2.2.20 + Compose Multiplatform 1.9.3 + kotlinx.serialization 1.9.0 + AGP 9.3.1
  + Gradle 9.6.1 are a working combination.
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
  [docs/analysis/api-mapping.md](docs/analysis/api-mapping.md) §7.
- **iOS.** Never compiled (see above).
- **The 3×3 board and drag-and-drop.** Feathers' `DragDropManager` broker has no Compose
  equivalent; see [docs/analysis/event-catalog.md](docs/analysis/event-catalog.md) §3.1.
- **The rules engine**, including the Same/Plus/Combo cascade — 20 rules that interact.
- **Networking.** `net/Socket.as` is largely dead code: 27 of its 29 handlers are
  unreachable. See
  [docs/analysis/network-protocol.md](docs/analysis/network-protocol.md).
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
| no action bar, no system bars | `application.xml`: `fullScreen true` |
| 400 ms flip, four 0.1 s legs | `Card.as:249-291` — `flip`/`yoyo`/`unflip`/`yoyo2` |
| `scaleY` 1→0→1.2→0→1, `scaleX` 1→1.2→1 | same, `horizon = false` |
| rarity row at (9, 6), 29×28 | `Card.as:177-178`; size from `card_rarities` |
| type icon at (80, 3), 20×20 | `Card.as:182-183`; size from `card_types` |
| artwork 104×128 at (0, 0), over the colour quad | `Card.as:169-170` |
| card back on top, shown while flipping | `Card.as:93-94` |

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
[docs/migration/16-RISK-ASSESSMENT.md](docs/migration/16-RISK-ASSESSMENT.md) —
unlicensed Square Enix IP across art, audio and naming — is unresolved and blocking. If it
is resolved by reskinning, `cards.json` needs new names too; the *stats* (powers, rarity,
type) would carry over, since `tools/extract_cards.py` separates them from the naming.
