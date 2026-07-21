# Phase 1: Infrastructure Setup - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 1 - Infrastructure Setup
- **Duration**: 4 weeks (Weeks 3-6)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
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

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 3 | Project structure, Gradle config, Android/iOS setup | DevOps + Tech Lead |
| Week 4 | Platform-specific code, Core utilities | Tech Lead + Senior Kotlin Devs |
| Week 5 | Data models, JSON files, Localization | Tech Lead + Team |
| Week 6 | Testing infrastructure, CI/CD, Documentation | QA + DevOps + Tech Lead |

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

### Week 3: Project Foundation

#### Task 1.1: Root Project Setup
**Owner**: DevOps | **Duration**: 1 day | **Priority**: CRITICAL

Create root project files:
- `settings.gradle.kts` - Plugin management and includes
- `build.gradle.kts` - Root configuration
- `gradle.properties` - Centralized properties
- `.gitignore`, `.editorconfig`

**Key configurations**:
- Kotlin Multiplatform plugin
- Compose Multiplatform plugin
- Version catalog for dependencies
- Repository configuration

**Acceptance Criteria**:
- [ ] `./gradlew projects` shows all modules
- [ ] Basic build succeeds
- [ ] Version management is centralized

---

#### Task 1.2: Shared Module Configuration
**Owner**: Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

Set up `shared/build.gradle.kts` with:
- All source sets (commonMain, androidMain, iosMain, jvmMain, commonTest)
- All dependencies (Ktor, Compose, Koin, SQLDelight, etc.)
- Compose compiler configuration
- Multiplatform settings

**Acceptance Criteria**:
- [ ] Shared module builds successfully
- [ ] All dependencies resolve
- [ ] All source sets configured properly

---

#### Task 1.3: Android Application Setup
**Owner**: Android Specialist | **Duration**: 1 day | **Priority**: HIGH

Configure androidApp module:
- `build.gradle.kts` with Android configuration
- `AndroidManifest.xml`
- `MainActivity.kt` with Compose setup
- Resource directories

**Acceptance Criteria**:
- [ ] Android app builds
- [ ] Runs on emulator
- [ ] Compose configured

---

#### Task 1.4: iOS Application Setup
**Owner**: iOS Specialist | **Duration**: 1 day | **Priority**: HIGH

Configure iosApp module:
- `Info.plist`
- `ContentView.swift` with Compose integration
- `AppDelegate.swift`, `SceneDelegate.swift`
- Asset catalog

**Acceptance Criteria**:
- [ ] iOS app builds
- [ ] Runs on simulator
- [ ] Compose integration works

---

### Week 4: Platform-Specific Code

#### Task 1.5: Platform Audio (expect/actual)
**Owner**: Android + iOS Specialists | **Duration**: 2 days | **Priority**: HIGH

Create unified audio API:
```kotlin
// commonMain
expect class AudioPlayer {
    fun playSound(soundId: String, loop: Boolean)
    fun stopAll()
    fun setVolume(volume: Float)
}
```

Implement for Android (Media3 ExoPlayer) and iOS (AVFoundation).

**Acceptance Criteria**:
- [ ] Audio works on both platforms
- [ ] Volume/mute control works

---

#### Task 1.6: Platform File Access (expect/actual)
**Owner**: Android + iOS Specialists | **Duration**: 1 day | **Priority**: MEDIUM

Create unified file API:
```kotlin
// commonMain
expect class FileManager {
    fun readAsset(path: String): ByteArray
    fun readAssetAsString(path: String): String
    fun getDocumentsDirectory(): String
}
```

Implement for Android (Assets/Context) and iOS (NSBundle/NSFileManager).

**Acceptance Criteria**:
- [ ] File operations work on both platforms
- [ ] Asset loading works

---

#### Task 1.7: Core Utilities
**Owner**: Senior Kotlin Devs | **Duration**: 2 days | **Priority**: MEDIUM

Create utility classes (ported from AS3):
- `Tools.kt` - Common functions (rand, madmax, etc.)
- `Constants.kt` - App constants
- `Logger.kt` - Napier wrapper
- `CryptoHelper.kt` - Save file encryption

**Acceptance Criteria**:
- [ ] All utilities compile and work
- [ ] Logging configured

---

### Week 5: Data and Resources

#### Task 1.8: Data Models
**Owner**: Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

Create core data models (see [13-DATA-MODELS.md]):
- `Card.kt` - Card data
- `Tile.kt` - Board tile
- `Board.kt` - 3x3 board
- `Player.kt` - Player data
- `GameRules.kt` - Rule definitions
- `GameState.kt` - Game state

**Acceptance Criteria**:
- [ ] All models compile
- [ ] Serialization works
- [ ] Unit tests pass

---

#### Task 1.9: JSON Data Files
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: HIGH

Convert AS3 data to JSON:
- `shared/src/commonMain/resources/data/cards/ff14.json`
- `shared/src/commonMain/resources/data/cards/ff8.json`
- `CardRepository.kt` - Data loading

**Acceptance Criteria**:
- [ ] All card data converted
- [ ] JSON loads correctly
- [ ] Repository works

---

#### Task 1.10: Localization
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: MEDIUM

Extract all strings from AS3:
- `shared/src/commonMain/resources/locales/en.json`
- `shared/src/commonMain/resources/locales/fr.json`
- `I18n.kt` - Localization class

**Acceptance Criteria**:
- [ ] All strings extracted
- [ ] Localization works for both languages
- [ ] Fallback to English works

---

### Week 6: Testing and Finalization

#### Task 1.11: Testing Infrastructure
**Owner**: QA Engineer | **Duration**: 2 days | **Priority**: HIGH

Set up:
- Kotest + Turbine for coroutine testing
- Compose testing
- Test utilities
- Coverage reporting

**Acceptance Criteria**:
- [ ] Unit tests run successfully
- [ ] Coverage is measured

---

#### Task 1.12: CI/CD Enhancement
**Owner**: DevOps | **Duration**: 1 day | **Priority**: MEDIUM

Enhance pipelines:
- Automatic testing
- Code coverage upload
- Static analysis (detekt, ktlint)
- Artifact management
- Release automation

**Acceptance Criteria**:
- [ ] All CI pipelines pass
- [ ] Coverage reported
- [ ] Artifacts built and stored

---

#### Task 1.13: Documentation
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: MEDIUM

Create development guides:
- `docs/development/project-setup.md`
- `docs/development/build-guide.md`
- `docs/development/testing-guide.md`
- `CONTRIBUTING.md`

---

## 📊 Phase 1 Deliverables

### Code Deliverables
- [ ] Complete project structure
- [ ] All build files
- [ ] Platform-specific implementations
- [ ] Core utility classes
- [ ] All data models
- [ ] JSON data files
- [ ] Localization files
- [ ] Repository implementations
- [ ] Test infrastructure
- [ ] CI/CD workflows

### Documentation Deliverables
- [ ] Phase documentation
- [ ] Setup guides
- [ ] Testing guide

---

## ✅ Phase 1 Completion Criteria

### Technical
- [ ] Project builds on all platforms
- [ ] All data models implemented
- [ ] Core utilities functional
- [ ] CI/CD pipeline operational

### Documentation
- [ ] All Phase 1 documents complete
- [ ] Setup guides available

### Team
- [ ] Team can build locally
- [ ] Team can run tests
- [ ] Team understands project structure

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval
- [ ] DevOps approval

---

## ⚠️ Risks and Mitigation

| Risk | Probability | Impact | Mitigation | Owner |
|------|-------------|--------|------------|-------|
| Gradle complexity | Medium | High | Use templates, experienced DevOps | DevOps |
| iOS issues | Medium | High | Early validation, dedicated specialist | iOS Specialist |
| Dependency conflicts | Medium | Medium | Version catalog, test thoroughly | Tech Lead |

---

## 🎯 Next Phase: Phase 2 - Data Layer

**Phase 2 Focus** (Weeks 7-8):
- Complete remaining data models
- Implement repository pattern
- Set up SQLDelight database
- Create data migration scripts
- Test all data operations

**Prerequisites**: All Phase 1 deliverables complete

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

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE - Ready for execution after Phase 0*  
*Review Required: Tech Lead approval before starting*
