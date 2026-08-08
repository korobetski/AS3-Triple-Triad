# Triple Triad Online - Migration Plan: Executive Summary

## 📋 Document Information

- **Project**: Triple Triad Online (TTO)
- **Migration**: ActionScript 3 → Kotlin Multiplatform
- **Date**: 2026-07-21
- **Status**: superseded by the code; kept for the decisions in it
- **Version**: 1.0

---

## 🎯 Project Overview

### What is Triple Triad Online?

**Triple Triad Online** is a digital implementation of the classic **Triple Triad** card game from the Final Fantasy series. It features:

- **Card Collection**: 153 FF14 cards + 110 FF8 cards (`datas/cards.as`)
- **Game Modes**: Single-player (PvE) and Multiplayer (PvP)
- **Special Rules**: 17 special Triple Triad rules (Fallen Ace, Reverse, Same,
  Same Wall, Plus, Combo, Ascension, Descension, Elemental, Swap, Roulette,
  Sudden Death, Random, Order, Chaos, All Open, Three Open)
- **Online Features**: Matchmaking, chat, ranked matches — ⚠️ **non-functional in
  the source**; only connect/ping/user-list are wired up (see
  [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) §8)
- **Progression**: XP, ranks, achievements, inventory
- **Customization**: Decks, avatars, themes

### Current Technology Stack

| Component | Technology | Notes |
|-----------|------------|-------|
| **Language** | ActionScript 3 | Adobe AIR 16.0 SDK |
| **Runtime** | Adobe AIR | `supportedProfiles: desktop extendedDesktop` — desktop only; landscape, fullscreen |
| **UI Framework** | Feathers UI | Component-based |
| **Rendering** | Starling Framework | GPU-accelerated |
| **Build** | Flex/ANT | Legacy system |
| **Network** | XMLSocket (raw TCP) | Custom protocol, mixed JSON/XML, largely dead |
| **Storage** | AIR `flash.filesystem.File` | AES-encrypted JSON `.sav` files + `UserSettings.json` |
| **Audio** | `SoundManager` (custom) | Flash audio, 2 channels (background + noise) with independent volumes |
| **i18n** | `i18n.as` + asset bundles | **4 languages**: `de_DE`, `en_US`, `fr_FR`, `ja_JA` |

### Migration Target

| Component | Technology | Notes |
|-----------|------------|-------|
| **Language** | Kotlin 2.0+ | Multiplatform |
| **Platform** | Android + iOS | Native mobile |
| **UI Framework** | Compose Multiplatform | Declarative UI |
| **Rendering** | Compose Canvas / Skia | GPU-accelerated |
| **Build** | Gradle Kotlin DSL | Modern build |
| **Network** | Ktor WebSocket | Standard protocol |
| **Storage** | SQLDelight / Room | Cross-platform DB |
| **Audio** | Media3 ExoPlayer | Android + iOS |

---

## 🎯 Migration Objectives

### Primary Goals

1. **Platform Expansion**: Move from desktop-only to mobile (Android + iOS)
2. **Modernization**: Replace legacy Flash/AIR with modern Kotlin
3. **Performance**: Maintain or improve game performance
4. **Feature Parity**: 100% of original features preserved
5. **Future-Proofing**: Enable continuous updates and improvements

### Success Criteria

| Metric | Target | Measurement |
|--------|--------|-------------|
| Feature Completion | 100% | Checklist verification |
| Performance (FPS) | > 60 FPS | Profiling on mid-range devices |
| App Size (Android) | < 50 MB | APK size |
| App Size (iOS) | < 100 MB | IPA size |
| Memory Usage | < 100 MB | Runtime profiling |
| Launch Time | < 2 seconds | Cold start measurement |
| Test Coverage | > 80% | Unit + integration tests |

---

## 📊 Project Scope

### What's Included

✅ **All Game Logic**
- Complete Triple Triad rules engine (TTOCore)
- All 17 special rules (Fallen Ace, Reverse, Same, Plus, Combo, etc.)
- Card flipping logic
- Turn management
- Scoring system

✅ **All Data**
- 153 FF14 cards
- 110 FF8 cards
- All card types (12) and rarities (1-5)
- Player profiles and saves
- Achievements
- Inventory items

✅ **All UI Screens** (22 navigable destinations + 9 embedded panels/components)
- Menu and navigation
- Game boards (PvE and PvP)
- Deck management
- Card collection
- Inventory
- Shop
- Settings
- Profile
- Help and tutorials

✅ **All Animations** (24 animation classes)
- Card flips
- Card movement (fly)
- Rule-specific animations
- Turn indicators
- Win/lose animations

⚠️ **Network Features** — scope conflict, must be resolved before approval
- WebSocket communication
- Multiplayer matchmaking
- Game state synchronization
- Chat system
- Server integration

> **The plan contradicts itself here.** This section claims full multiplayer
> parity, while "What's NOT Included" below states the backend server "remains
> as-is". Both cannot hold:
> 1. The AS3 client speaks **XMLSocket** (raw TCP). Ktor WebSocket cannot talk to
>    it. Reaching the existing server requires either a server-side WebSocket
>    endpoint or a TCP↔WebSocket proxy — both are backend work.
> 2. Worse, there is nothing to reach parity *with*: 27 of the 29 message handlers
>    in `net/Socket.as` are unreachable dead code. Multiplayer does not function in
>    the source build.
>
> Pick one: **(a)** treat multiplayer as new development and budget backend work
> (Phase 5 becomes design + client + server), or **(b)** move multiplayer out of
> scope for v1 and ship PvE-only. Option (b) removes 3 weeks from the timeline
> and is consistent with the current state of the codebase.

✅ **Platform Features**
- Save/load system
- Audio (sound effects and music)
- Internationalization (DE / EN / FR / JA — 4 locales)
- Offline mode

### What's NOT Included (Out of Scope)

❌ **Backend Server Migration**
- Existing server remains as-is
- Only client-side migration
- Server protocol adaptation if needed

❌ **New Features**
- Focus on parity, not enhancement
- New features can be added post-migration

❌ **Third-Party Integrations**
- Analytics (to be added post-migration)
- Ads (to be added post-migration)
- Social features (to be added post-migration)

---

## 🗺️ Migration Strategy

### Approach: Incremental Parallel Migration

**Rationale**: 
- Reduces risk by validating each phase
- Allows for early testing and feedback
- Enables parallel work on different components
- Provides clear milestones and deliverables

### Phase Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      MIGRATION TIMELINE                          │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  Week 1-2:    Week 3-6:    Week 7-8:    Week 9-12:           │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────┐    │
│  │ PREP    │  │ INFRA   │  │ DATA    │  │ CORE LOGIC   │    │
│  │         │  │         │  │         │  │              │    │
│  └─────────┘  └─────────┘  └─────────┘  └─────────────┘    │
│                                                                  │
│  Week 13-20:   Week 21-23:  Week 24-26:  Week 27-30:         │
│  ┌─────────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │
│  │   UI        │  │NETWORK  │  │ANIMATION│  │TESTING  │   │
│  │             │  │         │  │         │  │         │   │
│  └─────────────┘  └─────────┘  └─────────┘  └─────────┘   │
│                                                                  │
│  Week 31-32:                                                    │
│  ┌─────────┐                                                   │
│  │ RELEASE │                                                   │
│  └─────────┘                                                   │
│                                                                  │
└─────────────────────────────────────────────────────────────┘
```

*(Units are **weeks**, matching the phase table below: 2+4+2+4+8+3+3+4+2 = 32 weeks
≈ 7.5 months.)*

### Phase Breakdown

| Phase | Duration | Key Activities | Deliverables |
|-------|----------|----------------|--------------|
| **0: Preparation** | 2 weeks | Analysis, PoC, Setup | Migration plan, Environment, PoC |
| **1: Infrastructure** | 4 weeks | Project structure, CI/CD | Gradle config, KMP setup, CI/CD |
| **2: Data Layer** | 2 weeks | Models, Repositories | All data models, JSON data |
| **3: Core Logic** | 4 weeks | Game engine | TTOCore, Rules, GameState |
| **4: UI Layer** | 8 weeks | All screens | 22 screens, 9 components, Theme |
| **5: Network** | 3 weeks | WebSocket, Sync | SocketManager, Network layer |
| **6: Animations** | 3 weeks | All animations | 24 animations |
| **7: Testing** | 4 weeks | Comprehensive testing | >80% test coverage |
| **8: Release** | 2 weeks | Beta, Launch | Published apps |

**Total Duration**: **32 weeks** (~7.5 months) — the phase durations above sum
to exactly 32 (2+4+2+4+8+3+3+4+2). This is the **unbuffered** figure; see PR-001 in
[16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md).

---

## 💰 Budget Estimate

> ⚠️ **Corrected.** The previously published figure (€232,500–€297,500) was
> arithmetically inconsistent with its own inputs: 8–9 people over 7–9 months at
> €8k–10k/month is €448k–€810k in salary alone, and the monthly burn-rate table
> below independently implied €475k–€590k. The €200k–€250k salary line understated
> the stated team by a factor of ~2.4. Corrected figures follow.

### Cost Breakdown

Anchored on **FTE-months**: 32 weeks ≈ 7.4 months × 8 FTE average = **59 FTE-months**.

| Category | Low Estimate | High Estimate | Notes |
|----------|--------------|---------------|-------|
| **Salaries** | €475,000 | €590,000 | 59 FTE-months @ €8k–10k/month |
| Tools & Software | €5,000 | €10,000 | IDE licences, services, cloud |
| Infrastructure | €3,000 | €5,000 | CI/CD, macOS runners, test servers |
| Training | €2,000 | €5,000 | Kotlin/Compose training |
| Apple/Google developer accounts | €150 | €150 | €99/yr Apple + $25 Google one-off |
| Subtotal | €485,150 | €610,150 | |
| Contingency (10%) | €48,515 | €61,015 | Unforeseen issues |
| **TOTAL** | **€533,665** | **€671,165** | |

### Monthly Burn Rate

| Period | Team Size | Monthly Cost |
|--------|-----------|--------------|
| Month 1 (Phase 0) | 5-6 people | €40,000 - €60,000 |
| Months 2-3 (Phases 1-3) | 7-8 people | €56,000 - €80,000 |
| Months 4-7.5 (Phases 4-8) | 8-9 people | €64,000 - €90,000 |
| **Average** | **8 people** | **€64,000 - €80,000** |

### If €232,500 is a hard ceiling

That budget buys roughly **23 FTE-months** after non-salary costs — for example
**3 FTE for 7.5 months**, not 8–9. At that staffing level the 32-week plan is not
achievable and scope must be cut explicitly. Realistic options:

| Option | Team | Duration | Scope |
|--------|------|----------|-------|
| A — Reduced team, longer schedule | 3 FTE | ~20 months | Full parity |
| B — Reduced team, reduced scope | 3 FTE | ~8 months | Android only, PvE only, no PvP/network, simplified animations |
| C — Full plan | 8-9 FTE | 7.5 months | Full parity — requires ~€534k-€671k |

**This decision is a prerequisite for approving the plan** and should be settled
before Phase 0 starts. Option B is the only one that fits both the original
budget and a sub-year timeline; note it drops the network layer entirely, which
[02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) shows is
non-functional in the source anyway.

---

## 👥 Team Structure

### Recommended Team Composition

| Role | Count | Responsibilities | Required Skills |
|------|-------|------------------|----------------|
| **Tech Lead** | 1 | Technical oversight, architecture | Kotlin, AS3, Architecture |
| **Senior Kotlin Dev** | 2 | Core migration, optimization | Kotlin, Compose, Architecture |
| **Junior Kotlin Dev** | 2 | Screen migration, testing | Kotlin, Compose |
| **Android Specialist** | 1 | Android-specific implementation | Android, Kotlin, Java |
| **iOS Specialist** | 1 | iOS-specific implementation | iOS, Swift, Kotlin |
| **UI/UX Designer** | 1 | UI design, animations | Design, Compose, Animation |
| **QA Engineer** | 1 | Testing, bug reporting | Testing, QA |
| **DevOps** | 0.5 | CI/CD, infrastructure | DevOps, GitHub |

**Total Team Size**: 8-9 FTE (Full-Time Equivalents)

### Skill Matrix

| Skill | Priority | Team Members |
|-------|----------|--------------|
| Kotlin | Required | All developers |
| Compose Multiplatform | Required | All developers |
| Android Development | Required | 1 specialist + all Kotlin devs |
| iOS Development | Required | 1 specialist + basic for all |
| ActionScript 3 | Nice-to-have | Tech Lead (for reference) |
| Starling Framework | Nice-to-have | Tech Lead (for understanding) |
| Feathers UI | Nice-to-have | UI/UX Designer (for understanding) |
| Git | Required | All team |
| Gradle | Required | All developers |
| WebSocket | Required | Network team |
| Testing | Required | QA + all developers |

---

## ⚠️ Key Risks

### 🔴 Blocking risk: intellectual property

**This is an unlicensed fan implementation of Square Enix intellectual property.**
Triple Triad, Final Fantasy VIII and Final Fantasy XIV are Square Enix
trademarks; the card artwork under `sources/assets/cards/` and `sources/bin/assets/atlas/`
consists of extracted FFXIV/FF8 game assets, and `application.xml` carries a
"© Moogle Works" notice with no licence from the rights holder.

[12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md) currently plans public submission
to Google Play and the Apple App Store, using "final fantasy" as a store keyword.
That would almost certainly result in a DMCA takedown, developer-account
penalties, and possible legal exposure — after the full project cost has been
spent.

**No amount of engineering mitigates this.** It must be resolved before any budget
is committed. Viable paths:

| Path | Description | Consequence |
|------|-------------|-------------|
| **A — Licence** | Obtain written permission from Square Enix | Unlikely for a third-party commercial release; worth a formal enquiry |
| **B — Reskin** | Keep the rules engine, replace *all* Final Fantasy names, artwork, fonts, audio and the "Triple Triad" title with original assets | Legally viable (game *mechanics* are not copyrightable). Adds an art/audio workstream absent from this plan |
| **C — Private distribution** | No public store listing; personal/archival use only | Removes the store-release rationale for the whole migration |
| **D — Cancel** | Do not proceed | |

Path B is the only one compatible with the stated goal of a public mobile
release, and it materially changes scope, budget and team composition (an artist
is required, and none is staffed). See **BR-003** in
[16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md).

### Other High Priority Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **IP / copyright (above)** | **Very High** | **Critical** | **Reskin with original assets, or do not release publicly** |
| Multiplayer is greenfield, not a migration | Very High | High | Re-scope Phase 5 or drop PvP from v1 |
| Compose MP immaturity | Medium | High | Use stable, mutually compatible versions; extensive testing |
| Performance on mobile | Medium | High | Early performance testing, optimization |
| Animation complexity | High | High | Prioritize critical animations |
| iOS compatibility | Medium | High | Dedicated iOS developer |
| Schedule delays | Medium | Medium | Realistic planning, buffer time |

### Risk Mitigation Strategy

1. **Proof of Concept (PoC)**: Validate technology choices early
2. **Incremental Migration**: Test each component as it's migrated
3. **Regular Testing**: Continuous testing throughout migration
4. **Performance Monitoring**: Regular profiling and optimization
5. **Team Training**: Ensure all developers are properly trained
6. **Buffer Time**: Include contingency in timeline (10% buffer)

---

## ✅ Success Metrics

### Technical Success

| Metric | Target | Measurement |
|--------|--------|-------------|
| Code Coverage | > 80% | Unit tests |
| FPS (Minimum) | > 60 | Profiling on target devices |
| Launch Time | < 2s | Cold start on mid-range device |
| Memory Usage | < 100MB | Runtime profiling |
| App Size (Android) | < 50MB | APK size |
| App Size (iOS) | < 100MB | IPA size |

### Functional Success

| Metric | Target | Measurement |
|--------|--------|-------------|
| Screens Migrated | 100% | Manual verification |
| Features Implemented | 100% | Checklist verification |
| Rules Compatibility | 100% | Automated tests |
| Bug Rate (Critical) | < 2% | QA testing |

### Project Success

| Metric | Target | Measurement |
|--------|--------|-------------|
| Schedule Adherence | ± 10% | Project tracking |
| Budget Adherence | ± 5% | Financial tracking |
| Code Quality | > 90/100 | SonarQube / Code review |
| User Satisfaction | > 4.5/5 | Beta testing feedback |

---

## 🎯 Next Steps

### Immediate Actions (Week 1)

1. ✅ **Create migration branch** - DONE
   ```bash
   git checkout -b migration/kotlin-multiplatform
   ```

2. **Review and approve this plan** - All stakeholders
   - Technical feasibility review
   - Budget approval
   - Timeline approval

3. **Assemble the team** - Project Manager
   - Recruit or assign team members
   - Validate skill sets
   - Plan training if needed

4. **Set up project infrastructure** - DevOps
   - Create new GitHub repository (or use existing)
   - Configure CI/CD pipeline
   - Set up project structure

5. **Begin Phase 0: Preparation** - Tech Lead
   - Complete source code analysis
   - Create detailed dependency map
   - Develop Proof of Concept (PoC)

### Phase 0 Deliverables (Weeks 1-2)

- [ ] Complete source code analysis document
- [ ] Technology decision validation
- [ ] Development environment setup guide
- [ ] Working PoC (display a card in Compose)
- [ ] CI/CD pipeline configured
- [ ] Team training completed

---

## 📚 Related Documents

- **Full Migration Plan**: See [00-INDEX.md](./00-INDEX.md) for all documents
- **Current System Analysis**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack Details**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase Documents**: See 04-12 for detailed phase plans

---

## 📞 Contact Information

- **Project Repository**: [AS3-Triple-Triad](https://github.com/korobetski/AS3-Triple-Triad)
- **Migration Branch**: `migration/kotlin-multiplatform`
- **Documentation**: `docs/migration/`

---

*This document provides a high-level overview of the migration project. For detailed technical information, see the specific phase documents.*

*Status: PLANNING COMPLETE - Ready for stakeholder review*
