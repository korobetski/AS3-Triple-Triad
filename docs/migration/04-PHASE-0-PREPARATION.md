# Phase 0: Preparation - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 0 - Preparation
- **Duration**: 2 weeks
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21

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
| Week 1 | Environment setup, PoC development, Analysis completion | Tech Lead + DevOps | ⏳ NOT STARTED |
| Week 2 | Team training, CI/CD setup, Documentation review | Tech Lead + Team | ⏳ NOT STARTED |

---

## 📝 Detailed Tasks

### Week 1: Foundation Setup

#### Task 1.1: Development Environment Setup
**Owner**: DevOps
**Duration**: 2 days
**Priority**: HIGH
**Status**: ⏳ NOT STARTED

**Description**: Set up all development infrastructure and tools required for the migration.

**Sub-tasks**:
- [ ] Create new GitHub repository for Kotlin version (or prepare existing repo)
- [ ] Configure repository structure (branches, protection rules)
- [ ] Set up GitHub Actions for CI/CD (basic pipeline)
- [ ] Configure development IDEs (IntelliJ IDEA Ultimate recommended)
- [ ] Set up shared development resources (design assets, documentation)
- [ ] Create development, staging, and production environments
- [ ] Configure artifact repositories (for builds, dependencies)

**Dependencies**: None

**Deliverables**:
- Configured GitHub repository with proper branch structure
- Working CI/CD pipeline (build, test)
- Development environment documentation
- IDE configuration guide for team

**Acceptance Criteria**:
- [ ] All developers can clone and build the project
- [ ] CI pipeline runs successfully on push
- [ ] Branch protection rules are configured
- [ ] Development environment guide is available

---

#### Task 1.2: Proof of Concept (PoC) Development
**Owner**: Tech Lead
**Duration**: 3 days
**Priority**: CRITICAL
**Status**: ⚠️ PARTIALLY DELIVERED — the PoC in [`kotlin/`](../../kotlin/README.md)
builds and is verified, but it covers only requirements 1, 3, 4 and 6 below

> **History.** A first PoC in `poc/` was reported COMPLETE and "technology stack
> validated" while never having been compiled; it had 12 build-blocking defects
> (missing Ktor and serialization dependencies, `import kotlinx.coroutines.IO`,
> non-existent artifact versions, Material 2/3 mismatch, no Gradle wrapper).
> It was deleted and rewritten from scratch as
> [`kotlin/`](../../kotlin/README.md), which builds:
> Android debug + release APKs, a JVM desktop host, 5 model tests and 3 Compose UI
> tests that click the card and assert the owner changes. Results are recorded in
> [kotlin/README.md § Verified build results](../../kotlin/README.md#verified-build-results).
>
> **Still not done.** The new PoC deliberately covers only a single card that
> flips. Requirements 2 (JSON loading) and 5 (runs on iOS) are unmet — iOS has
> never been compiled, because Kotlin/Native cannot target Apple platforms from a
> Windows host and no Xcode project exists. No performance figure has been
> measured. Do not close this task on the strength of the new PoC alone.

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
- [ ] Compose MP renders correctly on both platforms
- [ ] Card animations perform smoothly (60+ FPS)
- [ ] JSON data loading works
- [ ] Touch handling works correctly
- [ ] App size is reasonable (< 20MB for PoC). MEASURED on the `kotlin/` PoC:
      10.2 MB debug / 7.5 MB release-unsigned, for one procedurally-drawn card with
      no assets. That passes "< 20MB" but fails the "< 10MB" criterion an earlier
      revision of this document used, and it says nothing about the real app, which
      adds 263 card images plus UI atlases and audio.
- [ ] Memory usage is acceptable

**Success Criteria**:
- PoC runs on Android emulator (API 34)
- PoC runs on iOS simulator (iOS 17+)
- Card renders with the correct layout and power values. PARTLY MET: the `kotlin/`
  PoC reproduces the real geometry (88x118 card, 36x24 digit badge with the four
  powers at the AS3 `CardDigits.positions` offsets, power 10 shown as "A") but
  draws it from primitives -- there is no card artwork. Slicing images out of the
  Starling atlases is unvalidated and remains a Phase 1 risk.
- Flip animation works smoothly
- Touch input is responsive
- Performance metrics meet minimum requirements

**Risk Mitigation**:
- If Compose MP has issues, evaluate fallback to separate Android/iOS UIs
- If animation performance is poor, investigate optimization strategies
- If iOS setup is problematic, consider using KMP iOS template

**Deliverables**:
- Working PoC code in the [`kotlin/`](../../kotlin/README.md) directory
  (the earlier `poc/` directory was deleted — see the history note above)
- PoC validation report — [kotlin/README.md](../../kotlin/README.md)
- Performance benchmarks
- Technology validation document

---

#### Task 1.3: Complete Source Code Analysis
**Owner**: Tech Lead
**Duration**: 2 days
**Priority**: HIGH
**Status**: ⏳ NOT STARTED

**Description**: Finalize the analysis of the ActionScript 3 codebase, creating detailed documentation for migration.

**Sub-tasks**:
- [ ] Create complete class dependency graph (all 103 files in `tto/`; note the
      wider `sources/src/` tree holds 579 files / ~186k lines, the rest being
      vendored Starling, Feathers, as3crypto and Adobe corelib)
- [ ] Document all event types and their usage
- [ ] Map all AS3 APIs to Kotlin equivalents
- [ ] Identify all external dependencies and their replacements
- [ ] Document all game rules and their interactions
- [ ] Create data flow diagrams for critical components
- [ ] Identify potential performance bottlenecks
- [ ] Document all network message types and formats. ⚠️ Reality check: 27 of the
      29 `Socket_On_*` handlers are unreachable dead code, so there is no live
      protocol to observe beyond connect/ping/user-list. This deliverable is a
      protocol **specification** exercise, not reverse engineering — see TR-007 in
      [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md)

**Analysis Documents to Create**:
1. **Dependency Matrix** - All class dependencies in spreadsheet format
2. **Event Catalog** - All custom events with their payloads
3. **API Mapping** - AS3 to Kotlin API translations
4. **Network Protocol Specification** - Complete message format documentation
5. **Performance Baseline** - Current AS3 performance metrics

**Deliverables**:
- `docs/analysis/dependency-matrix.md`
- `docs/analysis/event-catalog.md`
- `docs/analysis/api-mapping.md`
- `docs/analysis/network-protocol.md`
- `docs/analysis/performance-baseline.md`

**Acceptance Criteria**:
- [ ] Every class in `sources/src/tto/` is documented
- [ ] All dependencies are mapped to Kotlin equivalents
- [ ] Network protocol is fully documented
- [ ] Analysis is reviewed and approved by team

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
**Status**: ⏳ NOT STARTED

**Description**: Set up comprehensive CI/CD pipeline for the project.

**Pipeline Stages**:

**1. Build Stage**
- [ ] Build shared module
- [ ] Build Android app
- [ ] Build iOS app
- [ ] Run on all supported platforms

**2. Test Stage**
- [ ] Run unit tests (shared)
- [ ] Run Android instrumented tests
- [ ] Run iOS tests (if applicable)
- [ ] Code coverage reporting
- [ ] Static analysis (detekt, ktlint)

**3. Quality Stage**
- [ ] Code formatting check
- [ ] Linting (Android)
- [ ] Security scanning (if applicable)
- [ ] Dependency vulnerability check

**4. Artifact Stage**
- [ ] Build Android APK/AAB
- [ ] Build iOS IPA (via GitHub Actions macOS runners)
- [ ] Publish artifacts
- [ ] Version management

**Pipeline Configuration**:

```yaml
# .github/workflows/build.yml
name: Build and Test

on:
  push:
    # NOTE: this repository's default branch is `master`, not `main`.
    # `git remote show origin` confirms it. With `main` here the workflow would
    # never trigger.
    branches: [ master, migration/kotlin-multiplatform ]
  pull_request:
    branches: [ master ]

jobs:
  build-shared:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - run: ./gradlew :shared:build

  build-android:
    runs-on: ubuntu-latest
    needs: build-shared
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - run: ./gradlew :androidApp:build

  build-ios:
    runs-on: macos-latest
    needs: build-shared
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      # `iosApp` is an Xcode project, NOT a Gradle module -- `:iosApp:build` does
      # not exist and the job would fail immediately. Build the shared framework
      # with Gradle, run the iOS unit tests, then build the app with xcodebuild.
      - run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
      - run: ./gradlew :shared:iosSimulatorArm64Test
      - run: |
          xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp                      -sdk iphonesimulator                      -destination 'platform=iOS Simulator,name=iPhone 15'                      build

  test:
    runs-on: ubuntu-latest
    needs: [build-shared, build-android]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: \'temurin\'
          java-version: \'17\'
      - run: ./gradlew allTests
      - run: ./gradlew detekt
      - run: ./gradlew ktlintCheck
      # Coverage on KMP needs Kover; JaCoCo alone does not cover Native targets.
      - run: ./gradlew koverXmlReport
      - uses: codecov/codecov-action@v4
        with:
          files: ./**/build/reports/coverage/**/*.xml
```

**Deliverables**:
- `.github/workflows/build.yml`
- `.github/workflows/test.yml`
- `.github/workflows/release.yml`
- `detekt.yml` configuration
- `ktlint` configuration
- Code coverage configuration
- CI/CD documentation

**Acceptance Criteria**:
- [ ] All builds pass on CI
- [ ] Tests run successfully
- [ ] Code quality checks pass
- [ ] Artifacts are generated correctly
- [ ] Pipeline runs in < 15 minutes

---

#### Task 1.6: Project Standards and Guidelines
**Owner**: Tech Lead
**Duration**: 1 day
**Priority**: MEDIUM
**Status**: ⏳ NOT STARTED

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
- `docs/development/coding-standards.md`
- `docs/development/architecture-guidelines.md`
- `docs/development/git-workflow.md`
- `docs/development/testing-strategy.md`
- `docs/development/performance-guidelines.md`
- `.editorconfig` file
- `ktlint` configuration
- `detekt` configuration

---

## 📊 Phase 0 Deliverables

### Required Deliverables

| Deliverable | Owner | Status | Due Date |
|-------------|-------|--------|----------|
| Development environment setup | DevOps | ⏳ NOT STARTED | End of Week 1 |
| PoC (Proof of Concept) | Tech Lead | ⏳ NOT STARTED | End of Week 1 |
| Source code analysis documents | Tech Lead | ⏳ NOT STARTED | End of Week 1 |
| Team training completion | Tech Lead | ⏳ NOT STARTED | End of Week 2 |
| CI/CD pipeline | DevOps | ⏳ NOT STARTED | End of Week 2 |
| Standards and guidelines | Tech Lead | ⏳ NOT STARTED | End of Week 2 |

### Documentation

- [ ] `docs/migration/04-PHASE-0-PREPARATION.md` (this document)
- [ ] `docs/analysis/dependency-matrix.md`
- [ ] `docs/analysis/event-catalog.md`
- [ ] `docs/analysis/api-mapping.md`
- [ ] `docs/analysis/network-protocol.md`
- [ ] `docs/analysis/performance-baseline.md`
- [ ] `docs/development/coding-standards.md`
- [ ] `docs/development/architecture-guidelines.md`
- [ ] `docs/development/git-workflow.md`
- [ ] `docs/development/testing-strategy.md`
- [ ] `docs/development/performance-guidelines.md`
- [x] `kotlin/` - Proof of Concept code (builds; scope limited to one flipping card)

---

## ✅ Phase 0 Completion Criteria

### Technical Completion
- [ ] Development environment is fully configured
- [ ] CI/CD pipeline is operational
- [ ] PoC validates all technology choices
- [ ] Source code analysis is complete and documented
- [ ] All standards and guidelines are defined

### Team Readiness
- [ ] All team members have completed training
- [ ] Team can build and run the PoC
- [ ] Team understands the migration strategy
- [ ] Team is familiar with the codebase
- [ ] Team is ready to start Phase 1

### Documentation
- [ ] All Phase 0 documents are complete
- [ ] All analysis documents are available
- [ ] All training materials are available
- [ ] All standards are documented

### Approvals
- [ ] **BR-003 (Square Enix IP) resolved in writing** — blocking, see
      [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md)
- [ ] **TR-007 (multiplayer scope) decided** — PvP in v1 or deferred
- [ ] **Budget re-baselined** — the original €232.5k-€297.5k figure was
      arithmetically inconsistent; see [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- [ ] PoC actually builds and runs on Android and iOS — **Android: done**
      (`kotlin/`, APK produced, UI tests green). **iOS: not done**, never compiled.
- [ ] Tech Lead approves phase completion
- [ ] Team confirms readiness for Phase 1
- [ ] Stakeholders approve to proceed

---

## ⚠️ Risks and Mitigation

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Compose MP not ready for production | Low | High | Validate with PoC, have fallback plan | Tech Lead |
| **Unlicensed Square Enix IP blocks any public release** | **Very High** | **Critical** | **Resolve BR-003 before Phase 0 sign-off: reskin, licence, or do not release** | **Project Sponsor + Legal** |
| **Multiplayer is greenfield, not a migration** | **Very High** | **High** | **Re-scope Phase 5 or drop PvP from v1 (TR-007)** | **Tech Lead** |
| Team skill gaps | Medium | High | Comprehensive training, pair programming | Tech Lead |
| PoC reveals technology issues | Medium | High | Start PoC early, allow time for pivots | Tech Lead |
| CI/CD setup complexity | Medium | Medium | Use DevOps expertise, leverage templates | DevOps |
| iOS development environment issues | Medium | Medium | Set up early, validate often | DevOps |

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
- All Phase 0 deliverables complete
- Technology stack validated (PoC successful)
- Team trained and ready
- CI/CD pipeline operational

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 1 Plan**: [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

## 📝 Notes

This document is optimized for AI agent consumption. Key information is structured for easy parsing and understanding.

**Generated**: 2026-07-21  
**Status**: PLANNING COMPLETE - Ready for execution  
**Review Required**: Tech Lead approval before starting
