# Triple Triad Online - Migration Plan Index

## 📚 Migration Documentation

This directory contains the complete migration plan for moving **Triple Triad Online** from **ActionScript 3** (Adobe AIR) to **Kotlin Multiplatform** with **Compose Multiplatform** for native Android and iOS.

---

## 📖 Documents

### Core Documents
1. **[01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) - Project overview, goals, and high-level plan**
2. **[02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) - Complete analysis of existing AS3 codebase**
3. **[03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md) - Technology decisions and architecture**

### Migration Phases
4. **[04-PHASE-0-PREPARATION.md](./04-PHASE-0-PREPARATION.md) - Preparation and analysis**
5. **[05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md) - Project setup**
6. **[06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md) - Data models and repositories**
7. **[07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md) - Game core migration**
8. **[08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) - User interface**
9. **[09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) - Network communication**
10. **[10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md) - Animations**
11. **[11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md) - Testing and QA**
12. **[12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md) - Beta and release**

### Supporting Documents
13. **[13-DATA-MODELS.md](./13-DATA-MODELS.md) - AS3 to Kotlin model mappings**
14. **[14-COMPONENT-MAPPING.md](./14-COMPONENT-MAPPING.md) - Feathers to Compose component mapping**
15. **[15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md) - AS3 to Kotlin code patterns**
16. **[16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) - Risk analysis and mitigation**
17. **[17-TESTING-GUIDE.md](./17-TESTING-GUIDE.md) - Testing framework and examples**

### Phase 0 outputs (produced, not planned)

Unlike the documents above, these describe work that has been done. They are the place
to look when the plan and reality disagree — reality is in here.

- **[docs/analysis/README.md](../analysis/README.md)** — analysis of the existing AS3 codebase
  (Task 1.3): dependency matrix, event catalog, API mapping, network protocol,
  performance baseline. Start with the
  [headline findings](../analysis/README.md#headline-findings).
- **[docs/development/README.md](../development/README.md)** — coding standards, architecture,
  git workflow, testing strategy, performance guidelines (Task 1.6). Enforced in the
  build, not advisory.
- **[README.md](../../README.md)** — the Proof of Concept and its
  verified results.
- **[.github/workflows/build.yml](../../.github/workflows/build.yml)** — CI
  (Task 1.5). All five jobs green, on the second run.

---

## 🎯 Quick Start

### For AI Agents
- **Start here**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Technical details**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Migration mapping**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

### For Human Developers
- **Project overview**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Current system**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Migration phases**: Start with Phase 0 documents

---

## 🔴 Blocking issues — resolve before approving this plan

Three items must be settled before Phase 0 is signed off. Each one changes scope,
budget or feasibility, and none is an engineering problem.

| # | Issue | Where | Impact |
|---|-------|-------|--------|
| 1 | ✅ **Resolved 2026-07-25 — risk accepted.** All card art, character art, UI sprites, audio and the "Triple Triad" / "Final Fantasy" names remain Square Enix property, and `cards.json` ships 263 card names and statistics. The decision is to accept this on the condition of **no wide distribution, no marketing, no commercialisation** — which is why Phase 8's store release is now void. | BR-003 in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) | No longer blocking. The exposure is unchanged; what changed is that a store listing — the thing that makes it findable and takedown-able — is ruled out. |
| 2 | **Multiplayer is greenfield, not a migration.** 27 of the 29 `Socket_On_*` handlers in `net/Socket.as` are unreachable dead code; only connect / ping / user-list work. XMLSocket is also not wire-compatible with WebSocket, so server work is unavoidable — contradicting the "server remains as-is" scope. | TR-007 in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md), §8 of [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) | Phase 5's 3-week estimate is unfounded (realistically 8-12 weeks incl. server). **Decided 2026-07-25/08-06**: the socket architecture is abandoned outright and multiplayer is retained as a new design — local play verified by cryptography, progression held server-side. See [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) § The shape of the network layer. The technical risk is retired; the staffing gap is not. |
| 3 | **Budget was arithmetically inconsistent.** The published €232,500-€297,500 understated its own staffing assumption (8-9 FTE × 7-9 months at €8-10k/month) by ~130%. Corrected to **€533,665-€671,165**. | [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) | Either fund ~€534k-€671k, or cut scope/team explicitly. Three costed options are set out in the executive summary. |

Two further decisions have been added by the Phase 0 analysis. Neither is blocking in
the same sense, but both change what the plan can promise:

| # | Issue | Where | Impact |
|---|-------|-------|--------|
| 4 | **No AS3 performance baseline is obtainable.** Adobe AIR is end-of-life and the existing client cannot be run, let alone profiled. | [performance-baseline.md](../analysis/performance-baseline.md) §4 | "No worse than today" cannot be evidenced. Either fund an AIR environment now, or set absolute targets and stop claiming parity. |
| 5 | **Asset delivery is undecided.** The shipped `tto.apk` is a 9.67 MB downloader containing **no card artwork**; the real runtime payload is ~40 MB. | [performance-baseline.md](../analysis/performance-baseline.md) §1 | The plan's "< 20 MB" figure silently assumes download-on-demand. Ship everything (~45 MB), keep downloading, or re-encode — pick one. |

On the PoC: the first attempt (`poc/`) was reported COMPLETE and "technology stack
validated" but had never been compiled and had 12 build-blocking defects. It has
been **deleted and rewritten** as [README.md](../../README.md), which does
build — Android debug + release APKs, a JVM desktop host, and **21 tests / 47
executions with 0 failures**, verified on a physical Pixel 6a. It loads all **263
cards** from a JSON resource through the Compose resource bundle. See
[README.md § Verified build results](../../README.md#verified-build-results).

That validates the *toolchain* (Kotlin 2.2.20 / Compose Multiplatform 1.9.3 /
kotlinx.serialization 1.9.0 / AGP 9.3.1 / Gradle 9.6.1), single-source Compose UI on
Android and JVM, and structured-data loading through Compose resources. It does **not**
validate the highest-risk areas, which remain untouched: card artwork sliced from
Starling texture atlases, the 3×3 board with drag-and-drop, the rules engine,
networking, the iOS *app* (the shared framework compiles and tests on the macOS CI runner,
but nothing has ever rendered on a simulator and iOS is out of scope for now), frame timing,
or any of Ktor / SQLDelight / Koin / Media3.

⚠️ **The IP exposure is real and was accepted with open eyes.** `cards.json` ships the names
and stats of all 263 cards, so the PoC cannot be described as free of Square Enix material —
see its [licensing note](../../README.md#licensing-note). The risk was accepted on
2026-07-25 on the condition of no wide distribution; accepting it does not reduce it.

---

## 📊 Project Status

| Phase | Status | Start Date | End Date | Owner |
|-------|--------|------------|----------|-------|
| Phase 0: Preparation | ⚠️ NEARLY COMPLETE — 5 of 6 tasks delivered, CI green, all five blocking decisions resolved 2026-07-25. Training void (no team); iOS app needs a Mac. See [04-PHASE-0-PREPARATION.md](./04-PHASE-0-PREPARATION.md) | - | - | - |
| Phase 1: Infrastructure | ✅ DONE — the structure, build, models, data and CI were delivered during Phase 0's PoC; **1.5** (audio, on device), **1.6** (file access / user settings), **1.7** (logger), **1.10** (4 locales, on device), **1.11** (coverage, 97.8% line, gated) and **1.13** (setup / build / testing guides + [CONTRIBUTING.md](../../CONTRIBUTING.md)) followed. **1.4** (iOS app) is void — Android only. Nothing is reviewed or approved. See [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md) | - | - | - |
| Phase 2: Data Layer | ✅ DONE — 2026-07-30. **2.1** (Item hierarchy, GameSave, Npc, Achievement, MatchRecord, XpTable), **2.2** (five repositories), **2.4**, **2.6**, **2.7** and **2.8** (coverage 97.6% line / 88.3% branch, gated) delivered. **2.3 is deliberately not SQLDelight** — profiles and match history are JSON documents behind a host-supplied `DocumentStore`, extending Phase 1's `SettingsStore` pattern. **2.5**: `extract_npcs.py` is new (85 opponents); five of the six scripts already existed; legacy AES `.sav` reading is out of scope by decision, replaced by a new obfuscated save format. Five AS3 bugs found and fixed, six documentation errors corrected. Nothing is reviewed or approved. See [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md) § What was built | - | - | - |
| Phase 3: Core Logic | ✅ DONE — 2026-08-02, and **two thirds of it was already built**: `RulesEngine`, `GameRules`/`RuleKeys`, `Board`, `Power` and `MatchState` came with Phase 0's PoC and Phase 1, so **3.1** and **3.3** were delivered before the phase opened. New here: **3.2**'s roulette (`Roulette`, the two per-collection pools), **3.4**'s pre-match chain (`MatchSetup` — Random hand, Swap, Open visibility, coin flip, Sudden Death rematch), the **opponent AI** (`MatchAi`, absent from the plan's task list), **3.5** and **3.6** (4.8 µs per placement; every target met by 2-3 orders of magnitude, no optimisation applied). Five deviations from the plan, three more AS3 defects decided on. **The roulette, Open and the AI are not reachable from the UI** — that needs an opponent-selection screen and is carried into Phase 4. Nothing is reviewed or approved. See [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md) § What was built | - | - | - |
| Phase 4: UI Layer | 🔄 IN PROGRESS — 2026-08-06. **The game is playable end to end** and **28 of the 32 screens exist**: character creation with the collection choice, the dashboard and everything behind it (collection browser, deck editor, bag, shop, record with achievements, rules), opponent selection filtered by the hour, deck selection, and the match itself. Also done: the **theme system** (the AS3 palette and Raleway — the plan named the wrong font), **drag-and-drop** alongside tap, and the **turn timer** that `playerPanel` held and that had been missed as a game mechanic. **The `ff8_` collection is reachable for the first time**: the AS3 hard-codes `MODE = 'ff14_'` and never changes it. **The tutorial and both tournament ladders are done**: all three are scripted matches built as data on the ordinary match screen — the tutorial with the three of its nine lines the original could never reach now restored, the ladders with their thirteen inline opponents extracted to `campaigns.json` and their 500 MGP fee, the only money the game ever takes. Of the **4 screens left, only two are blocked on Phase 5** (`PVPScreen` and `PVPMatchScreen`, the only two that touch a socket); the other two (`EmptyScreen`, `BackstageScreen`) will not be ported and say why. Not done: the pre-match animations. Nothing is reviewed or approved. See [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) § What was built | - | - | - |
| Phase 5: Network | 🔄 IN PROGRESS — **the solo-vs-server half is built and running, 2026-08-08.** The legacy socket layer is abandoned (TR-007 re-scoped). The mechanism is a **replayable, signed transcript** the server verifies by re-running the real rules, which is only possible because the Phase 3 engine is pure and deterministic. Delivered: `:core` extracted and published, the transcript and `TranscriptVerifier`, the offline queue, the **version gate** (426 before the body is read), **accounts replacing the local profile** with progression held server-side (aggregates plus per-match history), **several servers** with per-server sessions and queues, five-state connectivity, and **update notices** — a `ClientRelease` announced by the deployment and opened by the app, with no auto-updater and reasons given. Verified end to end against the local Docker/Postgres container. **Left: local PvP** — `MatchView` and the peer protocol; transport still undecided. See [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) § Sequencing | - | - | - |
| Phase 6: Animations | ✅ DONE — 2026-08-08. **The match is animated end to end**, and the phase was re-scoped on the way: of the 24 classes in `tto/anims/`, **nineteen are the same fifty lines with a different texture**, so the bulk of this phase is four motion shapes and a table (`MatchBanner`), not 24 pieces of work. Delivered: the twenty captions transcribed from the AS3 tweens, their artwork in **four locales** (80 files — the captions are pictures of words, which was not anticipated), the pre-match chain read from Phase 3's own `MatchSetup.intro`, **`PileOuFace`** driven by the model's coin flip, the per-placement captions, the turn and outcome banners, and **pacing computed from the animations** instead of the original's `1000 + rand(4) * 1000` guess. One defect found by the work: **the turn clock had been running under the intro**, so the player's thirty seconds began behind the Start banner; it now waits, as `nextTurn` does. Also **the card motion on placement**: the capture flip shipped with Phase 4 and the landing (`Card.afterFly`) is new here. Also delivered outside the match: **`UnlockCardAnim`**, wired to the inventory's Use on a card item only (a pack yields another bag item, not a card, and showing one there would announce a card the player does not own yet), and **`TalkAnim` as `TalkBubble`**, which now speaks the tutorial's nine lines. The twenty-fourth class, **`Mogu`, is dead code**: nothing constructs it, its asset carries this tree's disabled-file hyphen, and it extends `flash.display.MovieClip` rather than a Starling object, so it could never have been on the stage. It is struck from the phase — the third such find after `RULE_COMBO` and `ElementalAnim`. **Nothing animation-side is left; the tutorial screen it waits on is Phase 4's work.** See [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md) | - | - | - |
| Phase 7: Testing | ⏳ NOT STARTED | - | - | - |
| Phase 8: Release | ⛔ VOID AS WRITTEN — re-scoped 2026-07-25, no store release. See [12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md) | - | - | - |

**Overall Status**: 🔄 **PHASES 1-3 DONE, PHASE 6 DONE, PHASES 4 AND 5 PART DONE — the game is
playable end to end against a real server, it announces its rules while you play, and it now teaches
you how.**

The technical groundwork is real: the app builds and runs on a physical device, the standards are
enforced in the build, and CI is green — including the project's first successful Apple
compilation. What is left is local PvP (Phase 5), four screens (Phase 4) — of which two are
the PvP pair Phase 5 unblocks and two will not be ported — and the test pass (Phase 7). Phase 6 is
finished.

> **A note on this documentation set, 2026-08-08.** The phase documents have been reduced to what
> they still tell you: the decisions taken, the AS3 defects found, the departures from the plan and
> why. The sketched implementations they used to carry were deleted once the real code existed —
> a sketch that disagrees with the shipped code is worse than no sketch, and the code is linked
> from each task. Week-by-week schedules, effort estimates and sign-off checklists went with them,
> for the reason given below: they are artefacts of a team-based framing that does not apply.

**All five blocking decisions were resolved on 2026-07-25** — see
[04-PHASE-0-PREPARATION.md § Decisions taken](./04-PHASE-0-PREPARATION.md#-decisions-taken-2026-07-25).
The project is now explicitly a **single-developer, AI-assisted personal project**: Square
Enix IP risk accepted (no wide distribution, no marketing, no commercialisation), the
original socket protocol abandoned, budget void, absolute performance targets, assets
embedded in the APK. **Every cost, FTE, timeline and role figure in this documentation set
is an artefact of the original team-based framing and should be ignored.**

Three further decisions the same day: **Android only for now** — the Apple targets stay
declared and CI keeps compiling the shared framework, but no iOS app will be built — **updates
via GitHub Releases with an in-app check** rather than a store track, and **the repository is
public**, which makes the update check a single unauthenticated GET and Actions runners free.
Phase 8 is re-scoped accordingly, see [12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md).

What remains:

| Remaining gap | Kind |
|---------------|------|
| ⚠️ **A private key is publicly downloadable** — `sources/air/*.p12`, verified HTTP 200 | exposure; act before generating an Android key |
| Multiplayer transport | design, deferred by agreement |
| Nobody has reviewed any Phase 0 output | no second reader |

The game rules are now specified — [game-rules.md](../analysis/game-rules.md), with a 35-case
test matrix and 9 recorded defects and hazards. The technology choice can be called validated
**for the base UI stack and data loading**; it cannot be called validated for texture atlases,
the board, the rules engine, networking, or any library outside that base set.

Team training (Task 1.4) is **void**, not pending: there is no team and none is planned. The
risk it was mitigating has been replaced by a sharper one — **no second reader.** The PoC
history in this repository shows exactly what unreviewed, self-reported completion produces:
a first attempt declared COMPLETE and "technology stack validated" that had never been
compiled and carried 12 build-blocking defects.

---

## 🔗 Related Files

- **Source Code**: `sources/src/tto/` - Original AS3 game code
  (**103 files, 16,965 lines**). The wider `sources/src/` tree holds 579 files /
  ~186,000 lines; the remainder is vendored Starling, Feathers UI, as3crypto
  (`com.hurlant`) and Adobe corelib (`com.adobe`).
- **Assets**: `sources/assets/` - Game assets (cards, sounds, images)
- **Build Files**: `sources/` - Original Flex/ANT build configuration

---

## 📅 Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| 0 - Preparation | 2 weeks | Analysis, PoC, Environment |
| 1 - Infrastructure | 4 weeks | Project structure, CI/CD |
| 2 - Data Layer | 2 weeks | Models, Repositories |
| 3 - Core Logic | 4 weeks | TTOCore, Rules Engine |
| 4 - UI Layer | 8 weeks | 28 screens + 9 embedded components |
| 5 - Network | 3 weeks | WebSocket, SocketManager |
| 6 - Animations | 3 weeks | All 24 animation classes |
| 7 - Testing | 4 weeks | Unit, Integration, UI tests |
| 8 - Release | 2 weeks | Beta, Release, Deployment |

**Total Estimated Duration**: **32 weeks** (~7.5 months) — the sum of the phase
durations above is exactly 32 (2+4+2+4+8+3+3+4+2). Note this is the **unbuffered**
figure: PR-001 in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) allocates a 10%
buffer that totals 35.2 weeks but was never reflected in any published schedule.
Per-week task allocations are also over-committed in several phases (up to 10 days
of work for one owner in a 5-day week) and need re-levelling.

---

## 👥 Team Structure

### Required Roles
- **Tech Lead**: 1 FTE - Technical oversight
- **Senior Kotlin Dev**: 2 FTE - Core migration
- **Junior Kotlin Dev**: 2 FTE - Screen migration
- **Android Specialist**: 1 FTE - Platform-specific
- **iOS Specialist**: 1 FTE - Platform-specific
- **UI/UX Designer**: 1 FTE - Design and animations
- **QA Engineer**: 1 FTE - Testing
- **DevOps**: 0.5 FTE - CI/CD and infrastructure

**Total**: 8-9 FTE

⚠️ **Roles missing from this plan but required by its own scope**:
- **Artist**: needed to produce original card art, UI and icons if blocking issue
  #1 is resolved by reskinning (263 cards plus UI). Currently 0 FTE.
- **Sound designer**: same reasoning for audio. Currently 0 FTE.
- **Backend developer**: needed for the game server per blocking issue #2. Currently 0 FTE,
  while Phase 5 assumes a "Network Team" that does not appear in this roster at all. The
  2026-08-06 decision to hold player progression server-side **widens** this gap: the server now
  needs accounts, a persistent datastore, backups with a tested restore, and personal-data
  handling — not just a message endpoint.

---

## 💰 Budget Summary

> ⚠️ **Corrected.** The previous figures did not add up: 8-9 people over 7-9 months
> at €8-10k/month is €448k-€810k in salary alone, not €200k-€250k. Anchored on
> **59 FTE-months** (32 weeks ≈ 7.4 months × 8 FTE average):

| Category | Estimated Cost |
|----------|----------------|
| Salaries (59 FTE-months @ €8-10k) | €475,000 - €590,000 |
| Tools & Software | €5,000 - €10,000 |
| Infrastructure | €3,000 - €5,000 |
| Training | €2,000 - €5,000 |
| Apple + Google developer accounts | €150 |
| Contingency (10%) | €48,515 - €61,015 |
| **Total** | **€533,665 - €671,165** |

If €232,500 is a hard ceiling it buys roughly **23 FTE-months** (e.g. 3 FTE for
7.5 months), not 8-9 FTE. See the three costed scope options in
[01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md#if-232500-is-a-hard-ceiling).

**Not included in any figure above**: the asset-replacement work required by
blocking issue #1 (original card art for 263 cards, UI, fonts and audio), and the
server-side work required by blocking issue #2.

---

## 🎯 Next Steps

1. **Review this plan** - All stakeholders
2. **Approve budget and timeline** - Management
3. **Assemble the team** - Project manager
4. **Set up development environment** - DevOps
5. **Start Phase 0: Preparation** - Tech Lead

---

*Generated for AI agent consumption and human reference*

*Last updated: 2026-07-25 — Phase 0 execution. Added
[docs/analysis/](../analysis/README.md) (Task 1.3, 7 documents + a generator),
[docs/development/](../development/README.md) (Task 1.6, 5 documents + enforced ktlint
and detekt configs), and [CI](../../.github/workflows/build.yml) (Task 1.5). The PoC
closed requirement 2 — 263 cards loaded from JSON through Compose resources — and three
card-geometry errors in it were found and fixed against the AS3 source. Two new
non-engineering decisions were surfaced (#4 performance-comparison policy, #5 asset
delivery).*

*Previously, 2026-07-24 — documents reviewed against the AS3 source; factual errors,
arithmetic inconsistencies and non-compiling code samples corrected. See the correction
notices in individual documents.*
