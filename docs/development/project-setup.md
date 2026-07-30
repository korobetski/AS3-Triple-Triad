# Project Setup

Phase 1, Task 1.13. From a clone to a running app.

This document covers **getting the toolchain in place**. Three neighbours cover the rest, and
nothing here is repeated there:

| For | Read |
|---|---|
| what the project *is*, and what it does and does not prove | [README.md](../../README.md) |
| what each Gradle task builds, and where its output lands | [build-guide.md](./build-guide.md) |
| running, filtering and writing tests | [testing-guide.md](./testing-guide.md) |
| making a change and getting it merged | [CONTRIBUTING.md](../../CONTRIBUTING.md) |

## 1. What you need

| | Version | Needed for | Notes |
|---|---|---|---|
| JDK | **17** | everything | Only to *launch* the wrapper. The daemon runs on Temurin 17 regardless — see below |
| Android SDK | **platform 37** | `:androidApp`, `:shared`'s Android target | Named by `androidCompileSdk` in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) |
| Python | 3 (run with 3.10) | the five scripts in `tools/` only | Not part of the build; nothing in CI runs them |
| Pillow | any | `tools/make_launcher_icons.py` only | The other four scripts are stdlib-only |
| Android Studio | 2025.2+ | optional | The command line does not need it — see §4 |
| adb | with the SDK | running on a phone | [README § Running on a real Android device](../../README.md#running-on-a-real-android-device) |

**Nothing else is installed.** No Gradle, no Kotlin compiler, no Node, no Xcode, no emulator
image: `gradlew` fetches Gradle 9.6.1, Gradle fetches the Kotlin and Compose compilers, and
`:desktopApp:run` puts the real UI on screen without a device.

### The JDK you launch with is not the JDK the build uses

[`gradle/gradle-daemon-jvm.properties`](../../gradle/gradle-daemon-jvm.properties) pins the daemon
to **Temurin 17**, and lists a foojay download URL per platform, so Gradle provisions that JVM
itself if your local one does not match. `settings.gradle.kts` applies
`org.gradle.toolchains.foojay-resolver-convention` for the same reason at the toolchain level, and
every module declares `jvmToolchain(17)`.

`./gradlew --version` reports both, and this is the line to check when something compiles
differently for you than for CI:

```
Launcher JVM:  17.0.5 (Eclipse Adoptium 17.0.5+8)
Daemon JVM:    Compatible with Java 17, Eclipse Temurin (from gradle/gradle-daemon-jvm.properties)
```

## 2. First build

Three commands, in this order, because each one proves more than the last and the first failure is
the informative one.

```bash
./gradlew projects
```

Configures the build without compiling anything. It must list `:shared`, `:androidApp` and
`:desktopApp`. If it lists nothing, you are not in the repository root — see §4.

```bash
./gradlew :desktopApp:run
```

The whole UI, with no Android SDK and no device: a splash, then the menu, then a playable match. A
window titled **Triple Triad** appears. This is the fastest proof that the shared module,
the Compose resource bundle, `cards.json` and the artwork all work.

```bash
./gradlew build
```

Everything: all JVM and Android compilations, 275 test executions, coverage with its gate, Android
lint, ktlint and detekt. Takes minutes cold. Needs `local.properties` — §3.

## 3. `local.properties`, for the Android module only

Copy the sample and fill in one line:

```bash
cp local.properties.sample local.properties
```

The file is git-ignored, because it is a machine path.

**On Windows, escape both the drive colon and the backslashes, and end the file with a single
`LF`.** An unescaped colon or a `CRLF` makes `lintDebug` fail with `PropertyEscape` — a Java
`Properties` file treats `:` as a key/value separator:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

macOS and Linux need no escaping:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

**You do not need this file for the desktop module, for `ktlintCheck` or for `detekt`.** That is
not an assumption: it was checked by moving `local.properties` aside with `ANDROID_HOME` unset, and
it is why the `quality`, `desktop` and `ios-framework` CI jobs carry no `setup-android` step. AGP
resolves the SDK location when a task *executes*, not when the build is configured.

## 4. The IDE

**Open the repository root.** The Gradle build *is* the root — `settings.gradle.kts`, `gradlew` and
the three modules are all there — so Studio finds all three modules on sync. (This is worth stating
because it used to be false: the build sat in a `kotlin/` subdirectory and opening the root found
nothing.)

Then pick the `androidApp` run configuration, or run `:desktopApp:run` from the Gradle panel.

`.editorconfig` is **load-bearing, not cosmetic**. ktlint is configured entirely from it, and the
IDE reads the same file, so formatting in the editor and formatting in CI agree by construction.
Do not let an IDE "reformat with project defaults" prompt talk you out of it: with that file's
ktlint block absent, ktlint 1.0.1 falls back to the `ktlint_official` style and reports hundreds of
violations in code that was clean a minute earlier. That happened once and cost an hour to
attribute.

### If the sync is refused with "AGP version not supported"

Your Studio is older than AGP 9.3.1 requires. Either update it, or lower `agp` in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) — and if you do, also lower
`androidCompileSdk` to 35 and let AGP fetch that platform. **The command line does not depend on
the IDE version at all**, so this only blocks IDE work.

## 5. What your host can build

Kotlin/Native cannot compile Apple targets off a Mac, and it does not say so — it **skips** them
silently. So `./gradlew build` succeeding on Windows tells you nothing about iOS.

| | Windows | Linux | macOS |
|---|---|---|---|
| `:shared` common + desktop + Android | yes | yes | yes |
| `:androidApp` APK | yes | yes | yes |
| `:desktopApp` | yes | yes | yes |
| `shared.framework` for iOS | **skipped** | **skipped** | yes |
| an iOS *app* | no | no | not yet — there is no `.xcodeproj`; see [README § iOS caveat](../../README.md#ios-caveat) |

The `ios-framework` CI job on `macos-latest` exists precisely because no Windows or Linux build can
cover that row.

## 6. First-run failures

Every row here was actually hit, by someone, on this repository.

| Symptom | Cause | Fix |
|---|---|---|
| `./gradlew: Permission denied` (exit 126), on CI or WSL | `gradlew` committed from Windows landed in the index as `100644` | `git update-index --chmod=+x gradlew` — [git-workflow.md § File modes on Windows](./git-workflow.md#file-modes-on-windows) |
| `lintDebug` fails with `PropertyEscape` | unescaped `:` or a `CRLF` in `local.properties` | §3 |
| `./gradlew projects` lists no modules | opened or `cd`'d somewhere other than the repository root | the root holds `settings.gradle.kts` |
| Studio: "AGP version not supported" | IDE older than AGP 9.3.1 | §4 |
| Hundreds of ktlint findings in files you did not touch | `.editorconfig` lost its ktlint block | restore it; do not reformat the codebase |
| `adb shell screencap -p /sdcard/s.png` fails with a usage message | Git Bash / MSYS rewrote the device path into a Windows one | prefix with `MSYS_NO_PATHCONV=1`, or use PowerShell |
| A screenshot pulled with `adb exec-out … > file.png` is corrupt | PowerShell re-encodes the redirected stream | write it on the device, then `adb pull` |
| iOS tasks "succeed" instantly on Windows | Kotlin/Native skipped them | §5 |

Three deprecation warnings on every build are **expected and not yours** — they come from detekt
and the Kotlin Multiplatform plugin. They are listed in
[README § Known issues](../../README.md#known-issues); do not go hunting for them in this
repository's own scripts.

## 7. Generated files, and the scripts that generate them

Six paths are **imported or generated, never hand-edited**. Editing them by hand is a change
that the next script run silently reverts.

| Path | Regenerate with | When |
|---|---|---|
| `shared/…/composeResources/files/cards.json` | `python tools/extract_cards.py <dest>` | `sources/src/tto/datas/cards.as` changed |
| `shared/…/composeResources/files/npcs.json` | `python tools/extract_npcs.py <dest>` | `sources/src/tto/datas/NPCs.as` changed, or `cards.as` (two pools are computed from it) |
| `shared/…/composeResources/files/art/` | `python tools/import_card_art.py` | the catalog changed |
| `shared/…/composeResources/files/locales/tto-*.json` | `python tools/import_locales.py` | `sources/bin/datas/locales/` changed |
| `androidApp/src/main/res/` (icon) | `python tools/make_launcher_icons.py` | the source icon changed — needs Pillow |
| `androidApp/src/main/res/raw/` (sounds) | `python tools/import_sounds.py` | `Sound` gained or lost a member |

Run them from the **repository root**; each resolves its own paths from `__file__` and will tell you
if a source is missing. They are checked in as generated output on purpose — CI runs no Python, so a
clone builds without it.

The `app-*.json` locale files next to `tto-*.json` are the opposite case: those are **authored**
here and safe to edit. The split is provenance, and
[README § Localisation](../../README.md#localisation) explains it.

## 8. Related

- [build-guide.md](./build-guide.md) — the task graph, outputs, and reproducing a CI failure
- [testing-guide.md](./testing-guide.md) — running and writing tests
- [coding-standards.md](./coding-standards.md) — what ktlint and detekt will hold you to
- [git-workflow.md](./git-workflow.md) — branches, commits, PRs
- [docs/migration/05-PHASE-1-INFRASTRUCTURE.md](../migration/05-PHASE-1-INFRASTRUCTURE.md) — the
  phase this document closes
