
# Phase 0: Preparation - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 0 - Preparation
- **Duration**: 2 weeks
- **Status**: ⚠️ **NEARLY COMPLETE** — 5 of 6 tasks delivered or mostly delivered, and
  **all five blocking decisions resolved on 2026-07-25** (see
  [§ Decisions taken](#-decisions-taken-2026-07-25)). Task 1.4 (training) is void: there is
  no team, and there will not be one — this is a single-developer project.
- **Version**: 1.4
- **Last Updated**: 2026-07-25

> **What is actually done, in one paragraph.** The PoC in [the repository root](../../README.md)
> builds and runs on a physical Android device, loads all 263 cards from a JSON resource,
> and passes 47 test executions with 0 failures — but has never been compiled for iOS from
> this Windows host, and no iOS app has ever been run.
> Seven analysis documents exist in [`docs/analysis/`](../analysis/README.md) — including a
> [game-rules specification](../analysis/game-rules.md) with a 35-case test matrix and
> [data-flow diagrams](../analysis/data-flow.md) — plus five
> standards documents in [`docs/development/`](../development/README.md), and ktlint +
> detekt are enforced in the build at `maxIssues = 0`. CI is green on all five jobs,
> including the project's first successful Apple compilation — the shared framework links
> and its tests run on `macos-latest`, though no iOS *app* exists. Team training has not
> started because there is no
> team and none is planned. **The five blocking decisions are now resolved** — IP risk
> accepted, the original socket protocol abandoned, budget void, absolute performance
> targets, assets embedded in the APK. One question replaced them: how updates reach
> installed devices.

---

## ✅ Decisions taken (2026-07-25)

The five outstanding approvals were all resolved by the project owner. Recorded here because
they do more than unblock Phase 0 — they change what this project *is*, and several
documents written under the old framing are now wrong rather than merely incomplete.

**The project is a single-developer, AI-assisted personal project.** Not a funded team
migration. Its stated purpose is to build a first Kotlin mobile application. Every figure in
this documentation set that assumes a team — cost, FTE allocation, week-by-week schedule,
role assignments — is an artefact of the original framing.

| Decision | Resolution | Consequence |
|---|---|---|
| **Square Enix IP (BR-003)** | Risk **accepted** | No wide distribution, no marketing, no commercialisation. The 263 card names and stats stay in the repository |
| **Multiplayer (TR-007)** | Original socket server **abandoned** | Phase 5 is a new design, not a port. Bluetooth under consideration; transport undecided |
| **Budget** | **Void** | No salaries, no paid licences. Cost and timeline estimates carry no meaning |
| **Performance policy** | **Absolute targets** | AIR is abandoned outright; no baseline will be produced and no parity claim made |
| **Asset delivery** | **Embedded in the APK** | No runtime asset download. App *update* delivery decided separately — see below |

### What these decisions remove from the plan

- **Phase 5 shrinks and changes kind.** The 29 socket handlers, the XMLSocket framing and
  the server protocol are no longer migration targets. What remains is a design question.
- **Phase 8 (release) is re-scoped, not deleted.** No store listing, no staged rollout, no
  AAB, no review response, no marketing. But sideloaded updates still need a signing key,
  monotonic versioning and a release workflow — less work than a store launch, not zero.
- **The AIR project is dead, not deprecated.** `sources/air/` is a reference for reading
  behaviour out of, not something to keep runnable. This is what makes the
  no-performance-baseline problem moot rather than unresolved.
- **The `< 20 MB` app-size criterion is void.** Assets ship in the APK, so the package will
  be roughly 45 MB plus the binary, by design. The criterion needs deleting, not restating.

### What they do not change

The technical facts behind the IP decision are unaffected by accepting the risk, and the
documentation keeps stating them: `cards.json` contains 263 Square Enix card names and
statistics. Accepting a risk is not the same as the risk being absent — the practical
consequence is that publishing to an app store would make the project both findable and
takedown-able, which is precisely what the decision rules out. The AS3 repository having
gone unchallenged is an absence of enforcement, not a permission.

### ⚠️ A private key is publicly downloadable, right now

This is not a hypothetical and it is not softened by any decision above.
`sources/air/TripleTriadOnlineReborn.p12` is committed, the repository is public, and the file
is served:

```
GET https://raw.githubusercontent.com/korobetski/AS3-Triple-Triad/master/sources/air/TripleTriadOnlineReborn.p12
→ HTTP 200, 2434 bytes
```

A `.p12` holds a **private key**. Anyone can fetch it. Deleting the file now does not undo
that — it has been publicly served and may be cached, cloned or indexed. Treat it as
compromised rather than as a mistake to tidy up:

1. **Establish whether the key is still valid.** It is the AIR signing key and AIR is
   abandoned, so it likely signs nothing that matters — confirm that rather than assume it.
2. **If it was issued by a CA and is still valid, revoke it.**
3. **Do not reuse it, or its passphrase, for the Android signing key** that Phase 8 now needs.
4. Add `*.p12`, `*.pfx`, `*.jks`, `*.keystore` to `.gitignore` and keep the new key in GitHub
   Secrets, never in the tree.

The IP decision explicitly accepted a known risk. This is a different thing: an exposure
nobody chose. See
[git-workflow.md](../development/git-workflow.md#-a-private-key-is-publicly-downloadable).

### Two further decisions, same day

| Decision | Resolution | Consequence |
|---|---|---|
| **iOS scope** | **Android only for now** | The Apple targets stay declared — the `ios-framework` CI job compiles and tests the shared framework on `macos-latest` at no cost, which keeps `commonMain` honest about platform assumptions. But no iOS app will be built, and "no `.xcodeproj`" stops being a gap. It also frees the multiplayer design to use Android-only APIs |
| **Update delivery** | **GitHub Releases + in-app check** | The app queries the Releases API at startup, compares the published tag to its own `versionName`, and offers to download and install the newer APK. Chosen over a Play closed track because nothing is submitted to or indexed by a store |

The update decision creates real work that did not exist before, all of it in Phase 8: a
signing key with a stable signature (Android refuses cross-key updates), monotonic
`versionCode` management, a release workflow putting a signed APK on a GitHub Release, the
`REQUEST_INSTALL_PACKAGES` permission, and the checker itself. Enumerated in
[12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md).

**Repository visibility: public.** Decided 2026-07-25, and in fact already the case —
`https://api.github.com/repos/korobetski/AS3-Triple-Triad` reports `"visibility": "public"`.
That settles the update mechanism the easy way: the Releases API needs **no token**, so the
checker is a single unauthenticated `GET /repos/korobetski/AS3-Triple-Triad/releases/latest`.
Budget for the unauthenticated rate limit — 60 requests per hour per IP — which one call at
startup will never approach, but a retry loop would.

It also means GitHub Actions runs on **free standard runners**, including `macos-latest`. The
minute-conservation argument for gating jobs behind `needs:` does not apply here (see § Task
1.5).

### Still open

- **Multiplayer transport.** Deferred by agreement: to be designed together rather than
  decided now. Bluetooth is a candidate;
  [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) records why it is the *most*
  platform-specific option rather than the simplest.

That is the only open decision left. Repository visibility was the last of the others.

---

## 🎯 Phase Overview

### Purpose
Phase 0 establishes the foundation for the entire migration project. This phase ensures that all prerequisites are met, the team is prepared, and the technical approach is validated before full-scale development begins.

### Key Objectives

1. **Validate migration feasibility** - Confirm technical decisions through PoC
2. **Prepare development environment** - Set up all tools and infrastructure
3. **Complete detailed analysis** - Finalize understanding of source code
4. **Train the team** - Ensure all developers have necessary skills
5. **Establish processes** - Define workflows, standards, and CI/CD

---

## 📝 Detailed Tasks

#### Task 1.1: Development Environment Setup
**Status**: ⚠️ PARTIAL — the project builds from a clean clone on one machine. Nothing
shared exists.

**Description**: Set up all development infrastructure and tools required for the migration.

- [x] Prepare existing repo — the Kotlin project lives in [the repository root](../../README.md)
      on the `migration/kotlin-multiplatform` branch. **Note the Gradle root is the repository root,
      not the repository root**, which has consequences for both the IDE and CI
      are documented in [git-workflow.md](../development/git-workflow.md); **no protection
      rules exist** on this repository
- [x] Set up GitHub Actions for CI/CD (basic pipeline) — green on all five jobs; see Task 1.5
      formatting is consistent out of the box. But there is **no IDE configuration guide**,
      and there is a trap that needs one: AGP 9.3.1 requires a recent
      Android Studio
      not meaningful until the multiplayer/server question (TR-007) is decided
      which is not the same thing

      enforcement absent
- [x] Working CI/CD pipeline (build, test) — five jobs, all green
- [x] Development environment documentation —
      [README.md § Prerequisites](../../README.md#prerequisites), including
      the Windows `local.properties` escaping trap that cost half a day

- [x] The project can be cloned and built — verified from `clean`: `./gradlew build
      assembleRelease` succeeds, 264 tasks
      (Windows 11). Nobody has tried this on macOS or Linux
- [x] CI pipeline runs successfully on push — all five jobs green
- [x] Development environment guide is available — for the build; not for the IDE

---

#### Task 1.2: Proof of Concept (PoC) Development
**Status**: ⚠️ PARTIALLY DELIVERED — the PoC in [the repository root](../../README.md)
builds and is verified, and now covers requirements **1, 2, 3, 4 and 6**.
Requirement 5 (runs on iOS) is unmet.

> **History.** A first PoC in `poc/` was reported COMPLETE and "technology stack
> validated" while never having been compiled; it had 12 build-blocking defects
> (missing Ktor and serialization dependencies, `import kotlinx.coroutines.IO`,
> non-existent artifact versions, Material 2/3 mismatch, no Gradle wrapper).
> It was deleted and rewritten from scratch as
> [the repository root](../../README.md), which builds. Results are recorded in
> [README.md § Verified build results](../../README.md#verified-build-results).
>
> **Now delivered.** Android debug + release APKs, a JVM desktop host, and
> **116 tests / 202 executions, 0 failures** across desktop and androidHostTest.
> Requirement 2 is closed: all **263 cards** (153 `ff14` + 110
> `ff8`) are extracted from `tto/datas/cards.as` by
> [`tools/extract_cards.py`](../../tools/extract_cards.py) and loaded
> at runtime from a JSON file through the Compose Multiplatform resource bundle,
> which is also the mechanism the 263 card *images* will need. Five UI tests read
> the shipped resource end-to-end.
>
> **Still not done.** Requirement 5 is unmet: iOS has never been compiled, because
> Kotlin/Native cannot target Apple platforms from a Windows host and no Xcode
> project exists. Frame timing has **not** been measured (the test device locked
> mid-session), so the "60+ FPS" validation point below is unverified. Do not close
> this task until both are addressed.
>
> **Three fidelity defects were found and fixed** while closing requirement 2, all
> of which had been reported as "correct geometry" before being checked against the
> source: the card was modelled as a bare 88×118 sprite rather than a 104×128
> sprite containing an 88×118 quad at (8, 5); the digit badge was 36×24 at the
> card's **top-left** instead of 44×30 at (28, 88) near the bottom; and each digit
> glyph was shifted by −4 dp, putting the left digit off the plate. The corrected
> values, each cited to a source line, are in
> [README.md § Fidelity to the AS3 source](../../README.md#fidelity-to-the-as3-source).

**Description**: Create a working proof of concept to validate the technology stack and migration approach.

1. **Display a Triple Triad card** using Compose Multiplatform
2. **Load card data** from JSON file
3. **Handle touch input** on card
4. **Animate card flip** using Compose Animation API
5. **Run on both Android and iOS** emulators
6. **Use Kotlin Multiplatform** shared module

- [x] Compose MP renders correctly — **Android and JVM desktop only**; iOS not compiled
      recorded zero frames because the test device locked mid-session. The flip looks
      smooth on the device, which is not a measurement. Commands to fill this in:
      [performance-baseline.md](../analysis/performance-baseline.md) §2
- [x] JSON data loading works — 263 cards read from a JSON resource through the
      Compose resource bundle, covered by 5 end-to-end UI tests
- [x] Touch handling works correctly — verified under real touch and `adb shell input tap`
      things and must be restated.** MEASURED: Kotlin PoC is 17 679 KB debug /
      14 807 KB release-unsigned (and `isMinifyEnabled = false`, so the release figure
      is an un-shrunk upper bound). 7.00 MB of that is the 263 card faces, now embedded. For comparison, the existing AS3 build
      `sources/air/tto.apk` is 9.67 MB — but **it contains no card artwork at all**;
      it is a downloader shell that fetches assets at runtime. The real runtime asset
      payload is ~40 MB (18 MB `cards/`, 8.3 MB `card_thumbs/`, 6.4 MB `npcs/`, plus
      UI and rules pages). So a sub-20 MB installed app requires an explicit decision
      between shipping everything, download-on-demand, or asset re-encoding — see
      [performance-baseline.md](../analysis/performance-baseline.md) §1 and
      [performance-guidelines.md](../development/performance-guidelines.md) §4
- [x] Memory usage is acceptable — 72.4 MB TOTAL PSS idle (debug build, Pixel 6a),
      of which 29.2 MB is the Compose runtime and 3.0 MB graphics. Note this is one
      card with no textures; graphics is the figure to watch once atlases arrive

  API 37), not an emulator
- ❌ PoC runs on iOS simulator (iOS 17+) — never compiled
- ✅ Card renders with the correct layout and power values — **now MET**, and
  verifiable: every coordinate is cited to its `Card.as` / `CardDigits.as` line, the
  power order is the AS3 `[top, right, bottom, left]`, the hex `'A'` encoding is
  handled, and the on-device screenshot confirms `#92 ff8_Odin 8/10/3/5` rendering
  its 10 as `A`. Three earlier geometry errors were found and fixed (see the history
  note above). **Caveat unchanged**: it is drawn from primitives — there is no card
  artwork, and slicing images out of the Starling atlases remains an unvalidated
  Phase 1 risk. See [api-mapping.md](../analysis/api-mapping.md) §7.
- ✅ Flip animation works — but it is a **deliberate substitution**, not a port: the
  original is a four-leg `scaleX` yoyo, this is a `rotationY` rotation. Recorded on
  `BoardCard`. If pixel-parity is required, this must be rewritten.
- ✅ Touch input is responsive
- ❌ Performance metrics meet minimum requirements — cold start (658/752 ms) and
  memory (72.4 MB PSS) measured; **frame timing not measured**

- ✅ Working PoC code at the [repository root](../../README.md)
  (the earlier `poc/` directory was deleted — see the history note above)
- ✅ PoC validation report — [README.md](../../README.md)
- ⚠️ Performance benchmarks — partial; see
  [performance-baseline.md](../analysis/performance-baseline.md), which states
  explicitly what could not be measured and why
- ✅ Technology validation document — Set C in
  [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- ✅ Reproducible card-data extractor —
  [`tools/extract_cards.py`](../../tools/extract_cards.py)

---

#### Task 1.3: Complete Source Code Analysis
**Status**: ✅ DELIVERED — **7 of 7 documents, 8 of 8 sub-tasks**. The last two were the
[game-rules specification](../analysis/game-rules.md) and the
[data-flow diagrams](../analysis/data-flow.md). One acceptance criterion remains unmet and
cannot be met by writing more: nobody has reviewed any of it. See
[docs/analysis/](../analysis/README.md).

**Description**: Finalize the analysis of the ActionScript 3 codebase, creating detailed documentation for migration.

- [x] Create complete class dependency graph — **generated**, not hand-written:
      [dependency-matrix.md](../analysis/dependency-matrix.md), refreshable with
      `python docs/analysis/tools/analyse_as3.py`. Confirms 103 files / 17,066 lines /
      103 classes / 167 distinct external imports in `tto/`
- [x] Document all event types and their usage —
      [event-catalog.md](../analysis/event-catalog.md). The game defines only **3**
      custom event types; everything else is a Starling/Feathers/Flash built-in, and
      31 navigation transitions are unchecked strings
- [x] Map all AS3 APIs to Kotlin equivalents —
      [api-mapping.md](../analysis/api-mapping.md), ranked by real use count and
      marked ✅/🔶/⚠️/❌ by how much confidence each row carries
- [x] Identify all external dependencies and their replacements — Starling (310
      imports), Feathers (244), Flash/AIR (148), Adobe corelib (14), as3crypto (2)
- [x] Document all game rules and their interactions —
      **[game-rules.md](../analysis/game-rules.md)**, a specification of the rules *as
      implemented*, with a 35-case test matrix and 9 recorded defects and hazards. The 20 rules in
      `datas/tripleTriadRules.as:9-30` and their combinatorial interaction through
      `TTOCore.applyRules`/`basicRule`/`specialRule`/`comboRule` are the correctness
      core of the game and are not yet specified. This is the highest-value remaining
      analysis task; see [testing-strategy.md](../development/testing-strategy.md) §2
- [x] Create data flow diagrams for critical components —
      **[data-flow.md](../analysis/data-flow.md)**, four Mermaid diagrams covering boot and
      asset loading, the pre-match rule chain, the turn loop with capture resolution inside it,
      and profile persistence — plus a port-or-rewrite verdict per path. The coupling tables in
      [dependency-matrix.md](../analysis/dependency-matrix.md) §2-§4 cover static structure;
      this covers runtime flow
- [x] Identify potential performance bottlenecks —
      [performance-baseline.md](../analysis/performance-baseline.md) and
      [performance-guidelines.md](../development/performance-guidelines.md) §5. The
      dominant one is texture-atlas replacement
- [x] Document all network message types and formats —
      [network-protocol.md](../analysis/network-protocol.md). ⚠️ Confirmed by count:
      **exactly 2 of the 29** `Socket_On_*` handlers are reachable
      (`Socket_On_pong`, `Socket_On_clients`), and `Socket_On_` appears in no file
      other than `net/Socket.as`. The live protocol is 3 outbound JSON actions and 2
      inbound messages. This deliverable is a protocol **specification** exercise,
      not reverse engineering — see TR-007 in
      [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md)

1. **Dependency Matrix** - All class dependencies in spreadsheet format
2. **Event Catalog** - All custom events with their payloads
3. **API Mapping** - AS3 to Kotlin API translations
4. **Network Protocol Specification** - Complete message format documentation
5. **Performance Baseline** - Current AS3 performance metrics

- [x] [`docs/analysis/game-rules.md`](../analysis/game-rules.md)
- [x] [`docs/analysis/data-flow.md`](../analysis/data-flow.md)
- [x] [`docs/analysis/dependency-matrix.md`](../analysis/dependency-matrix.md) (generated)
- [x] [`docs/analysis/event-catalog.md`](../analysis/event-catalog.md)
- [x] [`docs/analysis/api-mapping.md`](../analysis/api-mapping.md)
- [x] [`docs/analysis/network-protocol.md`](../analysis/network-protocol.md)
- [x] [`docs/analysis/performance-baseline.md`](../analysis/performance-baseline.md)
- [x] [`docs/analysis/tools/analyse_as3.py`](../analysis/tools/analyse_as3.py) — the generator

- [x] Every class in `sources/src/tto/` is documented — all 103 are listed with line
      count, base class, interfaces and dependency counts in
      [dependency-matrix.md](../analysis/dependency-matrix.md) §8
- [x] All dependencies are mapped to Kotlin equivalents — with a confidence mark on
      each row, so an unverified equivalent is not mistaken for a verified one
- [x] Network protocol is fully documented — including the finding that there is
      almost none
- [x] Game rules specified — [game-rules.md](../analysis/game-rules.md)

**New findings that change scope** (all in
[docs/analysis/README.md](../analysis/README.md#headline-findings)):

| Finding | Consequence |
|---------|-------------|
| The shipped `tto.apk` contains **no card artwork** — it is a 9.67 MB downloader | The app-size criterion in Task 1.2 was comparing against the wrong artifact |
| **No AS3 performance baseline is obtainable** — AIR is end-of-life | A before/after performance comparison is impossible by construction. Someone must accept this and set absolute targets instead |
| `flash.net.XMLSocket` is **not wire-compatible** with WebSocket | Server work is unavoidable; contradicts the "server remains as-is" scope |
| `net/TTONet.as` is dead code in the *default* package | Idle-detection and network-change handling were never wired up; do not budget for preserving them |
| `theme/BaseTTOTheme.as` is 2,290 lines (13% of the codebase) that largely **disappears** | The one place the migration is smaller than the original |
| ⚠️ A **private key (`.p12`) is publicly downloadable** — verified `HTTP 200` on a public repository | Not a migration concern but the most urgent item in this document. Treat as compromised; act before generating the Android signing key — see [git-workflow.md](../development/git-workflow.md#-a-private-key-is-publicly-downloadable) |

---

#### Task 1.4: Team Training
**Status**: ⏳ NOT STARTED

**Description**: Ensure all team members have the necessary skills for the migration.

---

#### Task 1.5: CI/CD Pipeline Setup
**Status**: ✅ COMPLETE AND GREEN —
[`.github/workflows/build.yml`](../../.github/workflows/build.yml) exists, all **8 Gradle
task paths it invokes were verified to exist** with `--dry-run`, and **all five jobs pass**.

It took two runs. The first failed at the first step of every job with
`./gradlew: Permission denied` (exit 126): `gradlew` was committed from Windows,
where `core.filemode` is `false`, so it was recorded `100644` rather than `100755`. Fixed in
the index with `git update-index --chmod=+x`; see
[git-workflow.md § File modes on Windows](../development/git-workflow.md#file-modes-on-windows).

Three risks flagged before the green run are now settled: the **8 Compose UI tests do pass
headless** on `ubuntu-latest` without `xvfb-run` (this was the failure expected first),
`compileSdk 36` resolves on the runner, and **`ios-framework` passed — the project's first
successful Apple compilation.** Two caveats: the green is reported from the Actions UI, so
the per-target test counts on CI have not been read back from the `shared-test-results`
artifact; and `ios-framework` proves the framework links, not that an iOS app exists.

**The critical path is `shared` → `ios-framework`, and it is self-inflicted.** Those two jobs
account for 4m33s + 7m46s ≈ the full 12m23s, because `android`, `desktop` and `ios-framework`
all declare `needs: shared`. None of them needs its *output* — each runs its own Gradle
invocation. The gate existed to avoid burning macOS minutes on a build that would fail anyway,
but this repository is **public**, so standard runners including `macos-latest` are free and
there are no minutes to conserve. Removing the three `needs:` lets every job start at once and
should bring wall clock down to the longest single job, **≈7m46s**. That change is applied;
the figure is a prediction until the next push confirms it.

The green run also warned that four actions declared `using: node20` and were being forced
onto Node 24. All are now pinned to Node 24 majors, with `gradle/actions/setup-gradle` held
at **v5 rather than v6 deliberately** — v6 moves caching into a proprietary component whose
use implies accepting Gradle's Terms of Use. That is a licensing decision for the project
owner; the rationale is recorded in the workflow header.

> **Corrections against the draft that used to live in this document.** The YAML
> below was aspirational and would have failed on every run.
> - it triggered on `main`; this repository's default branch is **`master`**
> - it ran Gradle from the repository root, which at the time was not the Gradle root (the
>   repository root has no `settings.gradle.kts`)
> - it called `xcodebuild -project iosApp/iosApp.xcodeproj`; **no `.xcodeproj`
>   exists**, so that step could only ever fail
> - it invoked `detekt`, `ktlintCheck` and `koverXmlReport`; **none of those tasks
>   existed** until Task 1.6 added the first two. Kover still does not exist.
>
> The committed workflow is 5 jobs: `quality` (ktlint + detekt), `shared`, `android`,
> `desktop`, and `ios-framework` on `macos-latest` — which was the **first real iOS
> compile**, since Kotlin/Native cannot target Apple from the Windows host used so far,
> and it passed. It deliberately builds the shared framework only, not an iOS app.

**1. Build Stage**
- [x] Build shared module — `shared` job, `:shared:build`
- [x] Build Android app — `android` job, debug + release APKs uploaded as artifacts
      builds and tests `:shared` for `iosSimulatorArm64` instead
- [x] Run on all supported platforms — ubuntu for JVM/Android, macos for Apple

**2. Test Stage**
- [x] Run unit tests (shared) — part of `:shared:build`; 47 executions
      Compose UI tests run on the JVM desktop target in seconds
- [x] Run iOS tests — the `ios-framework` job runs `:shared:iosSimulatorArm64Test` on
      `macos-latest` and it passes. This is the shared module's *common* tests executed on
      an Apple target; there are no iOS-specific tests, and no UI test runs there
      Kotlin/Native); not in the build
- [x] Static analysis (detekt, ktlint) — `quality` job; also wired into `check`, so
      `./gradlew build` fails on a formatting violation

**3. Quality Stage**
- [x] Code formatting check — ktlint, configured from `.editorconfig`
- [x] Linting (Android) — `lintDebug`, part of `:shared:build`
      certificate is committed to this repository; a secret scanner would have caught
      it. See [git-workflow.md](../development/git-workflow.md#-a-private-key-is-publicly-downloadable)

The committed workflow is [`.github/workflows/build.yml`](../../.github/workflows/build.yml).
It is the authority; the aspirational YAML that used to be reproduced here has been
removed rather than left to drift, because it contained four errors that would each
have broken the build (listed in the status note above).

Shape of it:

| Job | Runner | Runs |
|-----|--------|------|
| `quality` | ubuntu | `ktlintCheck detekt` — fastest signal, deliberately first and independent |
| `shared` | ubuntu | `:shared:build` — all common/desktop/Android compilations plus 47 test executions |
| `android` | ubuntu | `assembleDebug assembleRelease`, APKs uploaded |
| `desktop` | ubuntu | `:desktopApp:build` |
| `ios-framework` | **macos** | `:shared:linkDebugFrameworkIosSimulatorArm64` then `:shared:iosSimulatorArm64Test` |

Notes for whoever runs it first:

- `defaults.run.working-directory: kotlin` on every job — the Gradle root is not the
  repository root.
- Triggers use `paths-ignore` on `docs/`, `sources/` and `*.md`, so documentation-only commits do not burn
  runner minutes.
- `android-actions/setup-android@v3` is present because `compileSdk` is 36 and the
  runner image does not always carry it; there is no `local.properties` on CI, so AGP
  falls back to `ANDROID_HOME`.
- `ios-framework` costs ~10x the minutes of a Linux job. Run it on PRs, but consider not
  making it a required check until an iOS app actually exists.

**4. Artifact Stage**
- [x] Build Android APK — debug + release, uploaded by the `android` job
- [x] Publish artifacts — `actions/upload-artifact` for APKs, the iOS framework, test
      results and (on failure) the ktlint/detekt reports

- [x] [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
      inside `build.yml`; a second workflow would duplicate checkout, JDK setup and
      Gradle caching for no benefit
      and store credentials are Phase 8 concerns; a release pipeline that cannot sign
      anything is theatre. And see the committed-`.p12` finding first
- [x] detekt configuration — [`detekt/detekt.yml`](../../detekt/detekt.yml),
      every override carrying its reason, `maxIssues = 0`
- [x] ktlint configuration — [`.editorconfig`](../../.editorconfig), which
      the IDE reads too, so there is one source of truth
- [x] CI/CD documentation — this section plus
      [git-workflow.md](../development/git-workflow.md)

- [x] All builds pass on CI — **all five jobs green**, including `ios-framework` on
      `macos-latest`
- [x] Tests run successfully — locally 47 executions, 0 failures; on CI the `shared` and
      `ios-framework` jobs pass, which includes the common tests and the 8 headless Compose
      UI tests. The per-target CI counts have not been read back from the
      `shared-test-results` artifact
- [x] Code quality checks pass — `./gradlew ktlintCheck detekt` is green at
      `maxIssues = 0`, locally and in the `quality` job
- [x] Artifacts are generated correctly — the `android-apks` and `ios-framework` uploads are
      unconditional steps, so a green job means they succeeded. **Inferred, not inspected**:
      nobody has downloaded and opened them
- [x] Pipeline runs in < 15 minutes — **measured: 12m23s wall clock** on run #3, read from
      `/actions/runs/30151167874/jobs`. Per job: `ktlint + detekt` 1m49s, `Desktop JAR` 2m18s,
      `Android APK` 4m08s, `Shared module` 4m33s, `iOS framework` **7m46s**. Passes, but not
      comfortably — and the margin is structural, see below

---

#### Task 1.6: Project Standards and Guidelines
**Status**: ✅ DELIVERED — see [docs/development/](../development/README.md). The
standards are **enforced, not advisory**: `./gradlew build` runs ktlint and detekt and
fails on any finding (`maxIssues = 0`). Verified green on this codebase.

**Description**: Establish coding standards, best practices, and development guidelines for the migration.

- [x] [`docs/development/coding-standards.md`](../development/coding-standards.md)
- [x] [`docs/development/architecture-guidelines.md`](../development/architecture-guidelines.md)
- [x] [`docs/development/git-workflow.md`](../development/git-workflow.md)
- [x] [`docs/development/testing-strategy.md`](../development/testing-strategy.md)
- [x] [`docs/development/performance-guidelines.md`](../development/performance-guidelines.md)
- [x] [`.editorconfig`](../../.editorconfig) — also read by the IDE, so
      formatting cannot diverge between a developer's editor and CI
- [x] ktlint configuration — no separate file by design; `.editorconfig` is the only
      source of truth
- [x] detekt configuration — [`detekt/detekt.yml`](../../detekt/detekt.yml)

Two exceptions to Kotlin's official style are documented with reasons rather than left
implicit: `@Composable` functions stay `PascalCase`, and non-`const` design tokens
(`val CardWidth = 88.dp`) stay `PascalCase` while `const val` uses
`SCREAMING_SNAKE_CASE`. Enforcing this turned up two real fixes in the PoC — six
misnamed `const val`s and a `MainViewController` that needs its `PascalCase` name
because Swift call sites read it as a constructor.

**Logging conventions**: **not defined.** No logging framework is in the build. Worth
settling in Phase 1, because the AS3 original uses `trace()` throughout — including as
the *entire* body of some socket handlers — and none of that should be carried over.

---

## ⚠️ Risks and Mitigation

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Compose MP not ready for production | Low | High | ✅ base UI stack + resource loading validated on Android and JVM. Everything else still open | Tech Lead |
| Unlicensed Square Enix IP blocks any public release | Very High | ~~Critical~~ **Accepted** | ✅ Decision taken 2026-07-25: **risk accepted**, no wide distribution, no marketing, no commercialisation. The exposure is unchanged (263 card names and stats ship in `cards.json`); what changed is that it no longer blocks | Project owner |
| Multiplayer is greenfield, not a migration | Certain | Medium | ✅ Decision taken 2026-07-25: the original socket server is **abandoned**; Phase 5 becomes a new design. Transport still undecided | Project owner |
| **Texture atlases have no Compose equivalent** | **High** | **High** | **Phase 1 spike with a performance acceptance criterion.** `utils/Assets.as` has the highest fan-in in the codebase (57 files) and the PoC loads no texture at all | Tech Lead |
| No AS3 performance baseline is obtainable | Certain | Low | ✅ Decision taken 2026-07-25: **absolute targets**, AIR abandoned outright, no parity claim. Moot rather than unresolved | Project owner |
| Team skill gaps | n/a | n/a | ⛔ Void — single developer, AI-assisted. Replaced by a real risk: **no second reader.** Nobody has reviewed any Phase 0 output, and the PoC history shows what unreviewed self-reported completion produces | Project owner |
| PoC reveals technology issues | Medium | High | ✅ it did, and they were fixed: 3 geometry errors, a build-cache poisoning trap, and 6 naming violations found by enabling the linters | Tech Lead |
| CI/CD setup complexity | ~~Medium~~ **Closed** | Medium | ✅ all five jobs green. One defect (the `gradlew` executable bit) found and fixed; headless Compose UI tests and `compileSdk 36` on the runner both worked | DevOps |
| iOS development environment issues | Medium | Medium | **Partly mitigated.** The `ios-framework` CI job compiles and tests `:shared` for `iosSimulatorArm64` on `macos-latest` and passes, so Apple compilation is proven. Still no `.xcodeproj` and no simulator run — that needs a Mac | DevOps |

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 1 Plan**: [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

### Phase 0 outputs

- **Proof of Concept**: [README.md](../../README.md)
- **Source analysis** (Task 1.3): [docs/analysis/](../analysis/README.md)
- **Development standards** (Task 1.6): [docs/development/](../development/README.md)
- **CI** (Task 1.5): [.github/workflows/build.yml](../../.github/workflows/build.yml)

---

## 📝 Notes

This document is optimized for AI agent consumption. Key information is structured for easy parsing and understanding.

**Generated**: 2026-07-21
**Last executed against**: 2026-07-25
**Status**: ⚠️ IN PROGRESS — see the status note at the top. Five tasks delivered or
mostly delivered, one blocked on staffing, and **five approvals outstanding of which
three are blocking**.
**Review Required**: nobody has reviewed any of the Phase 0 output. Tech Lead approval
is still required before Phase 1, and the completion criteria above should be read as a
checklist rather than a summary — every unticked box is a real gap, stated on purpose.
