# Build Guide

Phase 1, Task 1.13. Which task builds what, where the output goes, and how to reproduce a CI
failure locally.

Assumes the toolchain is already in place — [project-setup.md](./project-setup.md). Running tests
has its own document, [testing-guide.md](./testing-guide.md); this one stops at "the tests ran".

## 1. One command per intent

```bash
./gradlew :desktopApp:run                # the UI, no device, no SDK — the fastest loop
```

```bash
./gradlew :shared:desktopTest            # the fast test loop, seconds warm
```

```bash
./gradlew ktlintFormat                   # fix formatting before it fails the build
```

```bash
./gradlew build                          # what CI will run against your branch
```

```bash
./gradlew :androidApp:installDebug       # build, install and replace on the attached device
```

Nothing here needs `clean`. Gradle's up-to-date checks are reliable on this build; reaching for
`clean` by reflex costs minutes and hides nothing. §9 covers the cases where it is the right answer.

## 2. What `./gradlew build` actually runs

`build` = `assemble` + `check`, aggregated over all three modules. In order of what fails first in
practice:

| Step | Task | Notes |
|---|---|---|
| Compile common + desktop + Android + (Apple) | `:shared:assemble` | Apple targets are **skipped** off a Mac, silently |
| Common tests, twice | `:shared:desktopTest`, `:shared:testAndroidHostTest` | The same 110 tests per target — this is by design, see [testing-guide.md](./testing-guide.md) |
| Compose UI tests | `:shared:desktopTest` | The 55 `desktopTest`-only tests run in the same task |
| Coverage + its gate | `:shared:coverageVerify` | Wired into `check`, so a coverage collapse fails `build` |
| Android lint | `:shared:lint`, `:androidApp:lint*` | Warnings only today |
| APK | `:androidApp:assembleDebug`, `assembleRelease` | Release is **unsigned** — §6 |
| Desktop jar | `:desktopApp:build` | |
| Formatting | `ktlintCheck` | Every module; config is `.editorconfig` |
| Static analysis | `detekt` | Every module and every source set; `maxIssues = 0` |

ktlint and detekt are applied to **every** module from the root `build.gradle.kts`, in an
`allprojects` block, so a module added later cannot escape them by omission. detekt is pointed at
`src` rather than its default `main`/`test`, which in a Kotlin Multiplatform module are empty — the
default would have analysed nothing and reported success.

## 3. Outputs

| Command | Output | Last measured |
|---|---|--:|
| `:androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | 19 070 KB |
| `:androidApp:assembleRelease` | `androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk` | 16 141 KB |
| `:desktopApp:build` | `desktopApp/build/libs/desktopApp.jar` | |
| `:shared:coverageReport` | `shared/build/reports/jacoco/coverageReport/html/index.html` | |
| `:shared:desktopTest` | `shared/build/reports/tests/desktopTest/index.html` | |
| `ktlintCheck` / `detekt` | `*/build/reports/ktlint/`, `*/build/reports/detekt/` | |

About 7 MB of that APK is card artwork and 1.1 MB is audio, packaged as Compose resources and
`res/raw` respectively — both already-compressed formats, so the APK carries them at roughly their
source size. [README § Card artwork](../../README.md#card-artwork) records why the
individual PNGs are shipped rather than the two sprite atlases — it is a memory measurement, not a
preference.

### Installable desktop packages exist but are unverified

`compose.desktop.nativeDistributions` declares Dmg, Msi and Deb, so
`:desktopApp:packageDistributionForCurrentOS` is in the task graph. **It has never been run here**,
and the Msi target needs WiX installed. `:desktopApp:run` is what the desktop host exists for: a way
to see the real UI without an emulator.

## 4. Static analysis

```bash
./gradlew ktlintCheck detekt             # both, without building anything else
```

```bash
./gradlew ktlintFormat                   # rewrites files in place
```

`ktlintFormat` fixes formatting only. detekt findings — a long method, a magic number, a name that
repeats its class — are design feedback and have to be answered rather than reformatted.
[coding-standards.md](./coding-standards.md) is the reasoning; `detekt/detekt.yml` is the authority,
and every override in it carries the reason it was taken.

Both are excluded from `build/`, because the Compose resource accessors are generated there and are
not ours to format.

## 5. Coverage

```bash
./gradlew :shared:coverageReport         # HTML + XML
```

The gate runs inside `build` — you do not have to remember it:

| Counter | Gate | Last measured |
|---|--:|--:|
| line | 90% | **97.8%** |
| branch | 75% | **85.9%** |

Those are **floors, not targets**, set well below the measured figures on purpose: they exist to
catch a test file being deleted or a new area arriving untested, not to turn every refactor into a
coverage negotiation. That the gate can actually fail was verified by raising the line minimum to
99% and watching `build` stop.

Measured on the desktop target only, and **JaCoCo rather than Kover** — Kover cannot be applied to
this module at all, for a reason worth knowing before you try again:
[README § JaCoCo, not Kover](../../README.md#jacoco-not-kover).

## 6. Release builds are unsigned, and unshrunk

`isMinifyEnabled = false`, and no signing config exists. So:

- **16 141 KB is an upper bound**, not a shipping size — R8 has never run on this code.
- `androidApp-release-unsigned.apk` cannot be installed on a device as-is.

Signing keys and release automation are Phase 8
([12-PHASE-8-RELEASE.md](../migration/12-PHASE-8-RELEASE.md)), deliberately not Phase 1: a key
committed early is a key committed forever. Note the warning in
[git-workflow.md § A private key is publicly downloadable](./git-workflow.md#-a-private-key-is-publicly-downloadable).

## 7. Reproducing a CI failure locally

[`.github/workflows/build.yml`](../../.github/workflows/build.yml) has five jobs, none gated behind
another (measured: `needs:` made the wall clock 12m23s where the longest single job is 7m46s). Run
the failing job's command rather than a full `build`:

| Job | Run locally |
|---|---|
| `quality` | `./gradlew ktlintCheck detekt` |
| `shared` | `./gradlew :shared:build :shared:coverageReport` |
| `android` | `./gradlew :androidApp:assembleDebug :androidApp:assembleRelease` |
| `desktop` | `./gradlew :desktopApp:build` |
| `ios-framework` | **not reproducible off a Mac** — see [project-setup.md § 5](./project-setup.md#5-what-your-host-can-build) |

Two differences between your machine and the runner that have actually mattered:

- **CI has no `local.properties`.** It relies on `ANDROID_HOME` from
  `android-actions/setup-android`. A build that works only because of your `sdk.dir` will fail
  there.
- **CI is Linux, and the Compose UI tests run headless.** They get a rendering surface on
  `ubuntu-latest` with no `xvfb-run`. That was the failure expected first when CI was introduced,
  and it did not happen — worth knowing so nobody adds a workaround for it.

CI also uploads `shared-test-results` on `always()` and `shared-coverage`, which is where per-target
test counts and the coverage HTML can be read back from a failed run.

`paths-ignore` skips `docs/**`, `sources/**` and `**/*.md`. So a docs-only commit runs no jobs —
and if you change `sources/` expecting a rebuild, note that nothing in the build reads that tree
directly: the artwork, strings and sounds are copied out of it by the scripts in `tools/`, and until
one of those is re-run the build cannot see the change.

## 8. Build settings worth knowing

From [`gradle.properties`](../../gradle.properties):

| Setting | Value | Consequence |
|---|---|---|
| `org.gradle.caching` | `true` | second build of unchanged code is mostly cache hits |
| `org.gradle.parallel` | `true` | the three modules build concurrently |
| `org.gradle.configuration-cache` | **`false`** | not enabled; turning it on is untested here |
| `org.gradle.jvmargs` | `-Xmx3072m` | lower it and the Kotlin compiler will OOM on `:shared` |
| `org.gradle.tooling.parallel` | `true` | parallel project sync, Gradle 9.4+ |

The same file carries a long comment about the nine `android.*` compatibility shims that were
**removed** during the AGP 9 upgrade, five of them inert and two hiding real migrations. Read it
before adding one back: a shim that pins AGP 8 behaviour buys a green build and defers the actual
break.

## 9. When the build behaves oddly

```bash
./gradlew --stop                         # kill the daemons; do this before deleting anything
```

```bash
./gradlew build --rerun-tasks            # ignore up-to-date checks, keep the outputs
```

```bash
./gradlew clean build                    # last resort
```

Reach for `--stop` first whenever a directory refuses to be deleted or a jar seems stale: on
Windows the daemon holds file locks, and it also holds Kotlin compiler state under `.kotlin/`.

```bash
./gradlew build --warning-mode all       # see which plugin owns a deprecation
```

Expect exactly three deprecation warnings, all from detekt and the Kotlin Multiplatform plugin,
all scheduled for Gradle 10 — [README § Known issues](../../README.md#known-issues). If a fourth
appears, it is probably yours.

**A Windows trap, hit once:** `git worktree remove` on a worktree of this repository can fail with
`Filename too long` — Kotlin's build directories nest deeply. `./gradlew --stop`, delete the
directory, then `git worktree prune`.

## 10. Related

- [project-setup.md](./project-setup.md) — the toolchain, and first-run failures
- [testing-guide.md](./testing-guide.md) — running, filtering and writing tests
- [coding-standards.md](./coding-standards.md) — what ktlint and detekt enforce
- [performance-guidelines.md](./performance-guidelines.md) — app size and frame-timing targets
- [README § Verified build results](../../README.md#verified-build-results) — the measured figures
  quoted above, with the machine they were measured on
