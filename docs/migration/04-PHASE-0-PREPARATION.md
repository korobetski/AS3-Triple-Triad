# Phase 0: Preparation - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 0 - Preparation
- **Duration**: 2 weeks
- **Status**: ⚠️ **IN PROGRESS** — 4 of 6 tasks delivered or mostly delivered; 2 blocked
  on having a team. **Not signed off**: the approvals below are unresolved and three of
  them are blocking.
- **Version**: 1.2
- **Last Updated**: 2026-07-25

> **What is actually done, in one paragraph.** The PoC in [`kotlin/`](../../kotlin/README.md)
> builds and runs on a physical Android device, loads all 263 cards from a JSON resource,
> and passes 47 test executions with 0 failures — but has never been compiled for iOS.
> All five analysis documents exist in [`docs/analysis/`](../analysis/README.md), all five
> standards documents in [`docs/development/`](../development/README.md), and ktlint +
> detekt are enforced in the build at `maxIssues = 0`. CI is written and its Gradle tasks
> verified, but no job has ever run. Team training has not started because there is no
> team. **Nothing here is a substitute for the blocking approvals** — the Square Enix IP
> question, the multiplayer scope decision and the budget re-baseline are all still open,
> and two new decisions have been added (performance-comparison policy, asset delivery).

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

## 📅 Timeline

| Week | Tasks | Owner | Status |
|------|-------|-------|--------|
| Week 1 | Environment setup, PoC development, Analysis completion | Tech Lead + DevOps | ⚠️ IN PROGRESS |
| Week 2 | Team training, CI/CD setup, Documentation review | Tech Lead + Team | ⚠️ PARTIAL — CI written and standards documented; training blocked on staffing |

### Task status at a glance

| Task | Status |
|------|--------|
| 1.1 Development environment | ⚠️ PARTIAL — local builds work; no shared environments or artifact repository |
| 1.2 Proof of Concept | ⚠️ 5 of 6 requirements — **iOS never compiled** |
| 1.3 Source code analysis | ⚠️ MOSTLY — 5 of 5 documents; game-rules spec and data-flow diagrams outstanding |
| 1.4 Team training | ⏳ NOT STARTED — no team |
| 1.5 CI/CD pipeline | ⚠️ WRITTEN, NEVER RUN |
| 1.6 Standards and guidelines | ✅ DELIVERED and enforced |

---

## 📝 Detailed Tasks

### Week 1: Foundation Setup

#### Task 1.1: Development Environment Setup
**Owner**: DevOps
**Duration**: 2 days
**Priority**: HIGH
**Status**: ⚠️ PARTIAL — the project builds from a clean clone on one machine. Nothing
shared exists.

**Description**: Set up all development infrastructure and tools required for the migration.

**Sub-tasks**:
- [x] Prepare existing repo — the Kotlin project lives in [`kotlin/`](../../kotlin/README.md)
      on the `migration/kotlin-multiplatform` branch. **Note the Gradle root is `kotlin/`,
      not the repository root**, which has consequences for both the IDE and CI
- [ ] Configure repository structure (branches, protection rules) — branch *conventions*
      are documented in [git-workflow.md](../development/git-workflow.md); **no protection
      rules exist** on this repository
- [x] Set up GitHub Actions for CI/CD (basic pipeline) — written; see Task 1.5. Never run
- [ ] Configure development IDEs — `.editorconfig` is committed and the IDE reads it, so
      formatting is consistent out of the box. But there is **no IDE configuration guide**,
      and there is a trap that needs one: **Android Studio must open `kotlin/`**, and AGP
      8.13.2 requires Studio Otter / 2025.2 or newer
- [ ] Set up shared development resources — not done
- [ ] Create development, staging, and production environments — not done, and arguably
      not meaningful until the multiplayer/server question (TR-007) is decided
- [ ] Configure artifact repositories — not done. CI uploads APKs as workflow artifacts,
      which is not the same thing

**Dependencies**: None

**Deliverables**:
- [ ] Configured GitHub repository with proper branch structure — conventions documented,
      enforcement absent
- [x] Working CI/CD pipeline (build, test) — written and its tasks verified; **unproven**
- [x] Development environment documentation —
      [kotlin/README.md § Prerequisites](../../kotlin/README.md#prerequisites), including
      the Windows `local.properties` escaping trap that cost half a day
- [ ] IDE configuration guide for team — not written

**Acceptance Criteria**:
- [x] The project can be cloned and built — verified from `clean`: `./gradlew build
      assembleRelease` succeeds, 264 tasks
- [ ] All *developers* can clone and build the project — untested; one machine, one OS
      (Windows 11). Nobody has tried this on macOS or Linux
- [ ] CI pipeline runs successfully on push — never pushed
- [ ] Branch protection rules are configured — no
- [x] Development environment guide is available — for the build; not for the IDE

---

#### Task 1.2: Proof of Concept (PoC) Development
**Owner**: Tech Lead
**Duration**: 3 days
**Priority**: CRITICAL
**Status**: ⚠️ PARTIALLY DELIVERED — the PoC in [`kotlin/`](../../kotlin/README.md)
builds and is verified, and now covers requirements **1, 2, 3, 4 and 6**.
Requirement 5 (runs on iOS) is unmet.

> **History.** A first PoC in `poc/` was reported COMPLETE and "technology stack
> validated" while never having been compiled; it had 12 build-blocking defects
> (missing Ktor and serialization dependencies, `import kotlinx.coroutines.IO`,
> non-existent artifact versions, Material 2/3 mismatch, no Gradle wrapper).
> It was deleted and rewritten from scratch as
> [`kotlin/`](../../kotlin/README.md), which builds. Results are recorded in
> [kotlin/README.md § Verified build results](../../kotlin/README.md#verified-build-results).
>
> **Now delivered.** Android debug + release APKs, a JVM desktop host, and
> **21 tests / 47 executions, 0 failures** across desktop, androidDebug and
> androidRelease. Requirement 2 is closed: all **263 cards** (153 `ff14` + 110
> `ff8`) are extracted from `tto/datas/cards.as` by
> [`kotlin/tools/extract_cards.py`](../../kotlin/tools/extract_cards.py) and loaded
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
> [kotlin/README.md § Fidelity to the AS3 source](../../kotlin/README.md#fidelity-to-the-as3-source).

**Description**: Create a working proof of concept to validate the technology stack and migration approach.

**PoC Requirements**:
1. **Display a Triple Triad card** using Compose Multiplatform
2. **Load card data** from JSON file
3. **Handle touch input** on card
4. **Animate card flip** using Compose Animation API
5. **Run on both Android and iOS** emulators
6. **Use Kotlin Multiplatform** shared module

**Technical Specifications**:
```
PoC Scope:
├── shared module (KMP)
│   ├── data class Card
│   ├── CardComponent @Composable
│   ├── Card data JSON
│   └── Flip animation
├── androidApp
│   └── MainActivity displaying card
└── iosApp
    └── SwiftUI view displaying card
```

**Validation Points**:
- [x] Compose MP renders correctly — **Android and JVM desktop only**; iOS not compiled
- [ ] Card animations perform smoothly (60+ FPS) — **UNVERIFIED.** `dumpsys gfxinfo`
      recorded zero frames because the test device locked mid-session. The flip looks
      smooth on the device, which is not a measurement. Commands to fill this in:
      [performance-baseline.md](../analysis/performance-baseline.md) §2
- [x] JSON data loading works — 263 cards read from a JSON resource through the
      Compose resource bundle, covered by 5 end-to-end UI tests
- [x] Touch handling works correctly — verified under real touch and `adb shell input tap`
- [ ] App size is reasonable (< 20MB for PoC). **This criterion compares the wrong
      things and must be restated.** MEASURED: `kotlin/` PoC is 10 351 KB debug /
      7 605 KB release-unsigned (and `isMinifyEnabled = false`, so the release figure
      is an un-shrunk upper bound). For comparison, the existing AS3 build
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

**Success Criteria**:
- ✅ PoC runs on Android — **exceeded**: run on a physical Pixel 6a (Android 17 /
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
  `FlippableCard`. If pixel-parity is required, this must be rewritten.
- ✅ Touch input is responsive
- ❌ Performance metrics meet minimum requirements — cold start (658/752 ms) and
  memory (72.4 MB PSS) measured; **frame timing not measured**

**Risk Mitigation**:
- If Compose MP has issues, evaluate fallback to separate Android/iOS UIs
- If animation performance is poor, investigate optimization strategies
- If iOS setup is problematic, consider using KMP iOS template

**Deliverables**:
- ✅ Working PoC code in the [`kotlin/`](../../kotlin/README.md) directory
  (the earlier `poc/` directory was deleted — see the history note above)
- ✅ PoC validation report — [kotlin/README.md](../../kotlin/README.md)
- ⚠️ Performance benchmarks — partial; see
  [performance-baseline.md](../analysis/performance-baseline.md), which states
  explicitly what could not be measured and why
- ✅ Technology validation document — Set C in
  [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- ✅ Reproducible card-data extractor —
  [`kotlin/tools/extract_cards.py`](../../kotlin/tools/extract_cards.py)

---

#### Task 1.3: Complete Source Code Analysis
**Owner**: Tech Lead
**Duration**: 2 days
**Priority**: HIGH
**Status**: ⚠️ MOSTLY DELIVERED — 5 of 5 documents written, 2 of 8 sub-tasks
outstanding. See [docs/analysis/](../analysis/README.md).

**Description**: Finalize the analysis of the ActionScript 3 codebase, creating detailed documentation for migration.

**Sub-tasks**:
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
- [ ] Document all game rules and their interactions — **NOT DONE.** The 20 rules in
      `datas/tripleTriadRules.as:9-30` and their combinatorial interaction through
      `TTOCore.applyRules`/`basicRule`/`specialRule`/`comboRule` are the correctness
      core of the game and are not yet specified. This is the highest-value remaining
      analysis task; see [testing-strategy.md](../development/testing-strategy.md) §2
- [ ] Create data flow diagrams for critical components — **NOT DONE.** The coupling
      tables in [dependency-matrix.md](../analysis/dependency-matrix.md) §2-§4 cover
      static structure but not runtime flow
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

**Analysis Documents to Create**:
1. **Dependency Matrix** - All class dependencies in spreadsheet format
2. **Event Catalog** - All custom events with their payloads
3. **API Mapping** - AS3 to Kotlin API translations
4. **Network Protocol Specification** - Complete message format documentation
5. **Performance Baseline** - Current AS3 performance metrics

**Deliverables**:
- [x] [`docs/analysis/dependency-matrix.md`](../analysis/dependency-matrix.md) (generated)
- [x] [`docs/analysis/event-catalog.md`](../analysis/event-catalog.md)
- [x] [`docs/analysis/api-mapping.md`](../analysis/api-mapping.md)
- [x] [`docs/analysis/network-protocol.md`](../analysis/network-protocol.md)
- [x] [`docs/analysis/performance-baseline.md`](../analysis/performance-baseline.md)
- [x] [`docs/analysis/tools/analyse_as3.py`](../analysis/tools/analyse_as3.py) — the generator

**Acceptance Criteria**:
- [x] Every class in `sources/src/tto/` is documented — all 103 are listed with line
      count, base class, interfaces and dependency counts in
      [dependency-matrix.md](../analysis/dependency-matrix.md) §8
- [x] All dependencies are mapped to Kotlin equivalents — with a confidence mark on
      each row, so an unverified equivalent is not mistaken for a verified one
- [x] Network protocol is fully documented — including the finding that there is
      almost none
- [ ] Analysis is reviewed and approved by team — **not done**; nobody has reviewed this
- [ ] Game rules specified — **not done**, see sub-tasks above

**New findings that change scope** (all in
[docs/analysis/README.md](../analysis/README.md#headline-findings)):

| Finding | Consequence |
|---------|-------------|
| The shipped `tto.apk` contains **no card artwork** — it is a 9.67 MB downloader | The app-size criterion in Task 1.2 was comparing against the wrong artifact |
| **No AS3 performance baseline is obtainable** — AIR is end-of-life | A before/after performance comparison is impossible by construction. Someone must accept this and set absolute targets instead |
| `flash.net.XMLSocket` is **not wire-compatible** with WebSocket | Server work is unavoidable; contradicts the "server remains as-is" scope |
| `net/TTONet.as` is dead code in the *default* package | Idle-detection and network-change handling were never wired up; do not budget for preserving them |
| `theme/BaseTTOTheme.as` is 2,290 lines (13% of the codebase) that largely **disappears** | The one place the migration is smaller than the original |
| A **signing certificate (`.p12`) is committed** to this repository | Out of scope for the migration but should not surface during Phase 8 — see [git-workflow.md](../development/git-workflow.md#-a-signing-certificate-is-committed-to-this-repository) |

---

### Week 2: Team Preparation

#### Task 1.4: Team Training
**Owner**: Tech Lead + Senior Kotlin Devs
**Duration**: 3 days
**Priority**: HIGH
**Status**: ⏳ NOT STARTED

**Description**: Ensure all team members have the necessary skills for the migration.

**Training Modules**:

**Module 1: Kotlin Fundamentals (1 day)**
- Kotlin syntax and idioms
- Null safety
- Collections and sequences
- Extension functions
- Functional programming in Kotlin

**Module 2: Kotlin Multiplatform (1 day)**
- KMP architecture and concepts
- Source sets and platform declarations
- expect/actual mechanism
- Multiplatform dependencies
- Cross-platform testing

**Module 3: Compose Multiplatform (1 day)**
- Compose fundamentals
- State management with StateFlow
- Custom components
- Animation API
- Multiplatform Compose setup

**Module 4: Triple Triad Domain (0.5 day)**
- Game rules overview
- Current codebase walkthrough
- Migration strategy review
- Q&A session

**Training Format**:
- Hands-on workshops with exercises
- Code review sessions
- Pair programming on PoC extension
- Documentation review

**Training Materials**:
- [ ] Kotlin cheat sheet (customized for team)
- [ ] Compose MP tutorial project
- [ ] KMP setup guide
- [ ] Triple Triad domain primer
- [ ] Coding standards document

**Deliverables**:
- Training materials (slides, exercises, solutions)
- Team skill assessment results
- Training completion checklist

**Acceptance Criteria**:
- [ ] All developers can write basic Kotlin code
- [ ] All developers can create Compose components
- [ ] All developers understand KMP concepts
- [ ] Team can extend the PoC
- [ ] Coding standards are understood and agreed upon

---

#### Task 1.5: CI/CD Pipeline Setup
**Owner**: DevOps
**Duration**: 2 days
**Priority**: HIGH
**Status**: ⚠️ RUN ONCE, FAILED, FIRST DEFECT FIXED —
[`.github/workflows/build.yml`](../../.github/workflows/build.yml) exists and all **8
Gradle task paths it invokes were verified to exist** with `--dry-run`. The first push
failed at the first step of every job with `./gradlew: Permission denied` (exit 126):
`kotlin/gradlew` was committed from Windows, where `core.filemode` is `false`, so it was
recorded `100644` rather than `100755`. Fixed in the index with
`git update-index --chmod=+x`; see
[git-workflow.md § File modes on Windows](../development/git-workflow.md#file-modes-on-windows).

Because the failure preceded Gradle starting, **nothing downstream has been exercised on
CI**. Do not treat this as a working pipeline until a push turns it green. Remaining
risks, in likelihood order, are listed in
[kotlin/README.md § Known issues](../../kotlin/README.md#known-issues) — the notable one
is whether the 8 Compose UI tests survive a headless Linux runner.

> **Corrections against the draft that used to live in this document.** The YAML
> below was aspirational and would have failed on every run:
> - it triggered on `main`; this repository's default branch is **`master`**
> - it ran Gradle from the repository root; the Gradle root is **`kotlin/`** (the
>   repository root has no `settings.gradle.kts`)
> - it called `xcodebuild -project iosApp/iosApp.xcodeproj`; **no `.xcodeproj`
>   exists**, so that step could only ever fail
> - it invoked `detekt`, `ktlintCheck` and `koverXmlReport`; **none of those tasks
>   existed** until Task 1.6 added the first two. Kover still does not exist.
>
> The committed workflow is 5 jobs: `quality` (ktlint + detekt), `shared`, `android`,
> `desktop`, and `ios-framework` on `macos-latest` — which would be the **first real
> iOS compile**, since Kotlin/Native cannot target Apple from the Windows host used
> so far. It deliberately builds the shared framework only, not an iOS app.

**Pipeline Stages**:

**1. Build Stage**
- [x] Build shared module — `shared` job, `:shared:build`
- [x] Build Android app — `android` job, debug + release APKs uploaded as artifacts
- [ ] Build iOS app — **not possible**: no Xcode project. The `ios-framework` job
      builds and tests `:shared` for `iosSimulatorArm64` instead
- [x] Run on all supported platforms — ubuntu for JVM/Android, macos for Apple

**2. Test Stage**
- [x] Run unit tests (shared) — part of `:shared:build`; 47 executions
- [ ] Run Android instrumented tests — none exist yet, and none are needed while the
      Compose UI tests run on the JVM desktop target in seconds
- [ ] Run iOS tests — the job calls `:shared:iosSimulatorArm64Test`, which has
      **never been executed**
- [ ] Code coverage reporting — **not done.** Needs Kover (JaCoCo does not cover
      Kotlin/Native); not in the build
- [x] Static analysis (detekt, ktlint) — `quality` job; also wired into `check`, so
      `./gradlew build` fails on a formatting violation

**3. Quality Stage**
- [x] Code formatting check — ktlint, configured from `.editorconfig`
- [x] Linting (Android) — `lintDebug`, part of `:shared:build`
- [ ] Security scanning — **not done.** Note the finding that a `.p12` signing
      certificate is committed to this repository; a secret scanner would have caught
      it. See [git-workflow.md](../development/git-workflow.md#-a-signing-certificate-is-committed-to-this-repository)
- [ ] Dependency vulnerability check — **not done**

**Pipeline Configuration**:

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
- Triggers are paths-filtered to `kotlin/**`, so documentation-only commits do not burn
  runner minutes.
- `android-actions/setup-android@v3` is present because `compileSdk` is 36 and the
  runner image does not always carry it; there is no `local.properties` on CI, so AGP
  falls back to `ANDROID_HOME`.
- `ios-framework` costs ~10x the minutes of a Linux job. Run it on PRs, but consider not
  making it a required check until an iOS app actually exists.

**4. Artifact Stage**
- [x] Build Android APK — debug + release, uploaded by the `android` job
- [ ] Build Android AAB — not done; needed for Play, and for `bundletool get-size`
- [ ] Build iOS IPA — not possible without an Xcode project
- [x] Publish artifacts — `actions/upload-artifact` for APKs, the iOS framework, test
      results and (on failure) the ktlint/detekt reports
- [ ] Version management — not done

**Deliverables**:
- [x] [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
- [ ] ~~`.github/workflows/test.yml`~~ — **deliberately not created.** Testing is a job
      inside `build.yml`; a second workflow would duplicate checkout, JDK setup and
      Gradle caching for no benefit
- [ ] ~~`.github/workflows/release.yml`~~ — **deliberately not created.** Signing keys
      and store credentials are Phase 8 concerns; a release pipeline that cannot sign
      anything is theatre. And see the committed-`.p12` finding first
- [x] detekt configuration — [`kotlin/config/detekt/detekt.yml`](../../kotlin/config/detekt/detekt.yml),
      every override carrying its reason, `maxIssues = 0`
- [x] ktlint configuration — [`kotlin/.editorconfig`](../../kotlin/.editorconfig), which
      the IDE reads too, so there is one source of truth
- [ ] Code coverage configuration — **not done**; needs Kover
- [x] CI/CD documentation — this section plus
      [git-workflow.md](../development/git-workflow.md)

**Acceptance Criteria**:
- [ ] All builds pass on CI — **unknown, never run.** They pass locally
- [ ] Tests run successfully — locally yes (47 executions, 0 failures); on CI unknown
- [x] Code quality checks pass — `./gradlew ktlintCheck detekt` is green at
      `maxIssues = 0`
- [ ] Artifacts are generated correctly — unverified
- [ ] Pipeline runs in < 15 minutes — unmeasured. Locally a clean
      `build assembleRelease` is ~2 min, so the ubuntu jobs should be comfortable; the
      macOS job is the unknown

---

#### Task 1.6: Project Standards and Guidelines
**Owner**: Tech Lead
**Duration**: 1 day
**Priority**: MEDIUM
**Status**: ✅ DELIVERED — see [docs/development/](../development/README.md). The
standards are **enforced, not advisory**: `./gradlew build` runs ktlint and detekt and
fails on any finding (`maxIssues = 0`). Verified green on this codebase.

**Description**: Establish coding standards, best practices, and development guidelines for the migration.

**Standards to Define**:

**1. Coding Standards**
- Code formatting (use ktlint with Kotlin style guide)
- Naming conventions (see 03-TECHNICAL-STACK.md)
- File organization
- Import ordering
- Documentation requirements
- Test naming conventions

**2. Architecture Guidelines**
- Layer separation (presentation, domain, data)
- Dependency injection patterns
- State management patterns
- Error handling patterns
- Logging conventions

**3. Git Workflow**
- Branch naming convention
- Commit message format
- Pull request process
- Code review requirements
- Merge strategy

**4. Testing Strategy**
- Unit test requirements
- Integration test requirements
- UI test requirements
- Test coverage targets
- Testing tools and frameworks

**5. Performance Guidelines**
- Memory usage limits
- Frame rate targets
- Load time targets
- App size limits
- Profiling requirements

**Deliverables**:
- [x] [`docs/development/coding-standards.md`](../development/coding-standards.md)
- [x] [`docs/development/architecture-guidelines.md`](../development/architecture-guidelines.md)
- [x] [`docs/development/git-workflow.md`](../development/git-workflow.md)
- [x] [`docs/development/testing-strategy.md`](../development/testing-strategy.md)
- [x] [`docs/development/performance-guidelines.md`](../development/performance-guidelines.md)
- [x] [`kotlin/.editorconfig`](../../kotlin/.editorconfig) — also read by the IDE, so
      formatting cannot diverge between a developer's editor and CI
- [x] ktlint configuration — no separate file by design; `.editorconfig` is the only
      source of truth
- [x] detekt configuration — [`kotlin/config/detekt/detekt.yml`](../../kotlin/config/detekt/detekt.yml)

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

## 📊 Phase 0 Deliverables

### Required Deliverables

| Deliverable | Owner | Status | Notes |
|-------------|-------|--------|-------|
| Development environment setup | DevOps | ⚠️ PARTIAL | builds and runs locally; no shared/staging environments, no artifact repository, no IDE config guide |
| PoC (Proof of Concept) | Tech Lead | ⚠️ 5 of 6 requirements | JSON loading closed; **iOS unmet** |
| Source code analysis documents | Tech Lead | ⚠️ MOSTLY | all 5 written; game-rules spec and data-flow diagrams outstanding |
| Team training completion | Tech Lead | ⏳ NOT STARTED | requires a team |
| CI/CD pipeline | DevOps | ⚠️ WRITTEN, NEVER RUN | tasks verified to exist; no push yet |
| Standards and guidelines | Tech Lead | ✅ DELIVERED | and mechanically enforced |

### Documentation

- [x] `docs/migration/04-PHASE-0-PREPARATION.md` (this document)
- [x] [`docs/analysis/README.md`](../analysis/README.md) — index and headline findings
- [x] [`docs/analysis/dependency-matrix.md`](../analysis/dependency-matrix.md) (generated)
- [x] [`docs/analysis/event-catalog.md`](../analysis/event-catalog.md)
- [x] [`docs/analysis/api-mapping.md`](../analysis/api-mapping.md)
- [x] [`docs/analysis/network-protocol.md`](../analysis/network-protocol.md)
- [x] [`docs/analysis/performance-baseline.md`](../analysis/performance-baseline.md)
- [x] [`docs/development/README.md`](../development/README.md)
- [x] [`docs/development/coding-standards.md`](../development/coding-standards.md)
- [x] [`docs/development/architecture-guidelines.md`](../development/architecture-guidelines.md)
- [x] [`docs/development/git-workflow.md`](../development/git-workflow.md)
- [x] [`docs/development/testing-strategy.md`](../development/testing-strategy.md)
- [x] [`docs/development/performance-guidelines.md`](../development/performance-guidelines.md)
- [x] [`kotlin/README.md`](../../kotlin/README.md) — PoC validation report
- [x] `kotlin/` — Proof of Concept code (builds; 263 cards loaded from JSON; one
      flipping card; no board, rules, network or artwork)
- [x] [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
- [ ] Training materials — not started
- [ ] Game-rules specification — not started, and the highest-value remaining analysis

---

## ✅ Phase 0 Completion Criteria

### Technical Completion
- [ ] Development environment is fully configured — local builds work; no shared
      environments, artifact repository or IDE config guide
- [ ] CI/CD pipeline is operational — **written, never executed**
- [ ] PoC validates all technology choices — validates the **base UI stack + data
      loading** on Android and JVM. Does not validate iOS, texture atlases,
      drag-and-drop, the rules engine, networking, or any of Ktor / SQLDelight / Koin /
      Media3
- [ ] Source code analysis is complete and documented — 5 of 5 documents; game-rules
      specification and data-flow diagrams outstanding
- [x] All standards and guidelines are defined — and enforced in the build

### Team Readiness
- [ ] All team members have completed training — no team assembled
- [ ] Team can build and run the PoC
- [ ] Team understands the migration strategy
- [ ] Team is familiar with the codebase
- [ ] Team is ready to start Phase 1

### Documentation
- [x] All Phase 0 documents are complete — except training materials
- [x] All analysis documents are available
- [ ] All training materials are available
- [x] All standards are documented

### Approvals
- [ ] **BR-003 (Square Enix IP) resolved in writing** — blocking, see
      [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md). ⚠️ Note this got *worse*, not
      better: the PoC now ships `cards.json` with the names and stats of all 263 cards,
      so it can no longer be described as free of Square Enix material. See the
      [licensing note](../../kotlin/README.md#licensing-note)
- [ ] **TR-007 (multiplayer scope) decided** — PvP in v1 or deferred. The analysis now
      confirms the premise by count: 2 of 29 handlers reachable, and XMLSocket is not
      wire-compatible with WebSocket. See
      [network-protocol.md](../analysis/network-protocol.md)
- [ ] **Budget re-baselined** — the original €232.5k-€297.5k figure was
      arithmetically inconsistent; see [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- [ ] **Performance-comparison policy decided** — new. No AS3 baseline is obtainable
      (AIR is end-of-life), so "no worse than today" cannot be evidenced. Either fund an
      AIR environment now or accept absolute targets instead. See
      [performance-baseline.md](../analysis/performance-baseline.md) §4
- [ ] **Asset-delivery strategy decided** — new. Ship ~45 MB, download on demand as the
      AIR client does today, or re-encode. The current 20 MB criterion assumes one of
      these without saying which. See
      [performance-guidelines.md](../development/performance-guidelines.md) §4
- [ ] PoC actually builds and runs on Android and iOS — **Android: done** (`kotlin/`,
      APKs produced, 47 test executions green, verified on a physical Pixel 6a).
      **iOS: not done**, never compiled.
- [ ] Tech Lead approves phase completion
- [ ] Team confirms readiness for Phase 1
- [ ] Stakeholders approve to proceed

---

## ⚠️ Risks and Mitigation

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Compose MP not ready for production | Low | High | ✅ base UI stack + resource loading validated on Android and JVM. Everything else still open | Tech Lead |
| **Unlicensed Square Enix IP blocks any public release** | **Very High** | **Critical** | **Resolve BR-003 before Phase 0 sign-off: reskin, licence, or do not release.** ⚠️ Exposure increased — the PoC now ships 263 card names and stats | **Project Sponsor + Legal** |
| **Multiplayer is greenfield, not a migration** | **Very High** | **High** | **Re-scope Phase 5 or drop PvP from v1 (TR-007).** Confirmed by count: 2 of 29 handlers reachable | **Tech Lead** |
| **Texture atlases have no Compose equivalent** | **High** | **High** | **Phase 1 spike with a performance acceptance criterion.** `utils/Assets.as` has the highest fan-in in the codebase (57 files) and the PoC loads no texture at all | Tech Lead |
| **No AS3 performance baseline is obtainable** | **Certain** | **Medium** | AIR is end-of-life. Decide now: fund an AIR environment, or accept absolute targets and stop claiming parity | Tech Lead + Sponsor |
| Team skill gaps | Medium | High | Comprehensive training, pair programming — not started, no team | Tech Lead |
| PoC reveals technology issues | Medium | High | ✅ it did, and they were fixed: 3 geometry errors, a build-cache poisoning trap, and 6 naming violations found by enabling the linters | Tech Lead |
| CI/CD setup complexity | Medium | Medium | Workflow written and its Gradle tasks verified; **the risk is now simply that it has never run** | DevOps |
| iOS development environment issues | **High** | Medium | **Realised, not mitigated.** Kotlin/Native cannot target Apple from Windows, and no Xcode project exists. Needs a Mac, or the `ios-framework` CI job as a first step | DevOps |

---

## 🎯 Next Phase: Phase 1 - Infrastructure

After completing Phase 0, the team will proceed to **Phase 1: Infrastructure Setup** (Weeks 3-6).

**Phase 1 Focus**:
- Create full project structure
- Configure all dependencies
- Set up platform-specific code
- Implement first production components
- Establish build and distribution pipeline

**Prerequisites for Phase 1**:
- All Phase 0 deliverables complete — ⚠️ not yet
- Technology stack validated (PoC successful) — ⚠️ base stack only
- Team trained and ready — ❌ no team
- CI/CD pipeline operational — ⚠️ written, never run

**What Phase 1 should tackle first, based on what Phase 0 found.** In this order,
because each one is a risk that the PoC did *not* retire:

1. **Texture-atlas spike.** Decide and measure how 263 card images get out of the
   Starling atlases and into Compose. Highest unvalidated risk; blocks all of Phase 4.
   See [api-mapping.md](../analysis/api-mapping.md) §7.
2. **Get iOS to compile once**, even if only the shared framework, via the
   `ios-framework` CI job or a Mac. Until then "multiplatform" is one platform.
3. **Verify the remaining library set** — Ktor, SQLDelight, Koin, Media3 — with this
   Kotlin/Compose combination. Set C in
   [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md) covers none of them.
4. **Specify the 20 game rules and their interactions** before porting `TTOCore`. This
   is the outstanding Task 1.3 sub-task and the correctness core of the game.
5. **Add Kover and Macrobenchmark**, so coverage and frame timing stop being unmeasured.

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 1 Plan**: [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

### Phase 0 outputs

- **Proof of Concept**: [kotlin/README.md](../../kotlin/README.md)
- **Source analysis** (Task 1.3): [docs/analysis/](../analysis/README.md)
- **Development standards** (Task 1.6): [docs/development/](../development/README.md)
- **CI** (Task 1.5): [.github/workflows/build.yml](../../.github/workflows/build.yml)

---

## 📝 Notes

This document is optimized for AI agent consumption. Key information is structured for easy parsing and understanding.

**Generated**: 2026-07-21
**Last executed against**: 2026-07-25
**Status**: ⚠️ IN PROGRESS — see the status note at the top. Four tasks delivered or
mostly delivered, two blocked on staffing, and **five approvals outstanding of which
three are blocking**.
**Review Required**: nobody has reviewed any of the Phase 0 output. Tech Lead approval
is still required before Phase 1, and the completion criteria above should be read as a
checklist rather than a summary — every unticked box is a real gap, stated on purpose.
