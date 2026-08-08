# Risk Assessment - Triple Triad Online Migration

## 📋 Document Information

- **Purpose**: Identify, analyze, and mitigate risks for the AS3 to Kotlin migration
- **Status**: superseded by the code; kept for the decisions in it
- **Last Updated**: 2026-07-21
- **Related**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)

---

## 🎯 Risk Assessment Methodology

### Risk Classification

| Severity | Description | Color |
|----------|-------------|-------|
| **Critical** | Project-threatening, could cause failure | 🔴 |
| **High** | Major impact, could delay project | 🟠 |
| **Medium** | Moderate impact, manageable | 🟡 |
| **Low** | Minor impact, easy to handle | 🟢 |

### Probability Scale
| Rating | Description | Value |
|--------|-------------|-------|
| Very High | >80% chance | 5 |
| High | 60-80% chance | 4 |
| Medium | 40-60% chance | 3 |
| Low | 20-40% chance | 2 |
| Very Low | <20% chance | 1 |

### Impact Scale
| Rating | Description | Value |
|--------|-------------|-------|
| Critical | Project failure, major data loss | 5 |
| High | Significant delay, major feature loss | 4 |
| Medium | Moderate delay, minor feature loss | 3 |
| Low | Minor delay, cosmetic issues | 2 |
| Very Low | Negligible impact | 1 |

### Risk Score Calculation
**Risk Score = Probability × Impact**

| Score Range | Risk Level | Action |
|-------------|------------|--------|
| 16-25 | Critical | Immediate mitigation required |
| 9-15 | High | High priority mitigation |
| 4-8 | Medium | Monitor and mitigate |
| 1-3 | Low | Accept or low-priority mitigation |

---

## 📊 Risk Register

---

## 1. Technical Risks

### TR-001: Kotlin Multiplatform Immaturity

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | Kotlin Multiplatform and Compose Multiplatform are relatively new technologies that may have bugs, limitations, or missing features |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | All (especially Phase 1, 4) |

**Potential Consequences**:
- Unexpected bugs in KMP/Compose MP
- Missing features required for the game
- Performance issues on iOS
- Compatibility issues with future versions
- Limited community support for complex issues

**Mitigation Strategies**:
1. ⚠️ Use stable **and mutually compatible** versions. Note that "Kotlin 2.0+
   with Compose MP 1.6.0" as previously specified is *not* a valid pairing —
   Compose MP 1.6.0 targets Kotlin 1.9.2x. Use Kotlin 1.9.24 + Compose MP 1.6.11,
   or Kotlin 2.0.x + Compose MP 1.6.11+ with the `org.jetbrains.kotlin.plugin.compose`
   plugin. See [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md).
2. ✅ Create Proof of Concept (PoC) in Phase 0 to validate
3. ✅ Test on both Android and iOS early and often
4. ✅ Have fallback plans (separate Android/iOS UIs if needed)
5. ✅ Monitor JetBrains issue tracker for known issues
6. ✅ Maintain good relationship with JetBrains community

**Contingency Plan**:
- If critical issues found: Evaluate Flutter as alternative
- If iOS performance poor: Consider native iOS development
- If KMP too immature: Build separate Android/iOS codebases

**Owner**: Tech Lead
**Status**: ⚠️ ACTIVE - Monitor during migration

---

### TR-002: Compose Multiplatform Performance on Mobile

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | Compose MP may not achieve required performance (60+ FPS) for smooth game animations on mobile devices |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | Phase 4, 6 |

**Potential Consequences**:
- Frame rate drops below 60 FPS
- Animations feel choppy
- Poor user experience
- Negative reviews due to performance

**Mitigation Strategies**:
1. ✅ Performance testing in PoC (Phase 0)
2. ✅ Optimize animation code
3. ✅ Use hardware acceleration (Skia)
4. ✅ Limit concurrent animations
5. ✅ Profile and optimize hot paths
6. ✅ Test on mid-range devices, not just high-end

**Performance Targets**:
- Minimum FPS: 60
- Target FPS: 90
- Frame time: <16.67ms
- Memory: <100MB

**Owner**: Tech Lead + UI/UX Designer
**Status**: ⚠️ ACTIVE - Validate in PoC

---

### TR-003: Animation Complexity

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | The 24 custom animation classes from AS3 may be difficult to recreate in Compose with the same visual fidelity |
| **Probability** | High (4) |
| **Impact** | High (4) |
| **Risk Score** | **16** (CRITICAL) |
| **Phase Affected** | Phase 4, 6 |

**Potential Consequences**:
- Animations don't match original look and feel
- Visual quality degraded
- User dissatisfaction
- Loss of game charm

**Mitigation Strategies**:
1. ✅ Prioritize most important animations first
2. ✅ Create animation style guide
3. ✅ Use animation libraries (Lottie for complex animations)
4. ✅ Work closely with UI/UX designer
5. ✅ Get user feedback on animation quality
6. ✅ Consider simplifying less critical animations

**Animation Priority**:
- **Critical**: Card flip, card fly, combo chain, turn indicators
- **High**: Rule activations, win/loss animations
- **Medium**: Special effects, transitions
- **Low**: Cosmetic animations

**Owner**: UI/UX Designer
**Status**: ⚠️ ACTIVE - Address in Phase 4

---

### TR-004: iOS Compatibility Issues

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | Kotlin Multiplatform iOS support may have issues or limitations |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | Phase 1, 4, 5 |

**Potential Consequences**:
- iOS app doesn't build
- iOS app crashes
- iOS features missing
- iOS performance poor
- Delay in iOS release

**Mitigation Strategies**:
1. ✅ Dedicated iOS specialist on team
2. ✅ Test iOS early and often
3. ✅ Use KMP iOS template as starting point
4. ✅ Validate all platform-specific code
5. ✅ Have iOS-specific workarounds ready
6. ✅ Monitor Kotlin iOS GitHub issues

**iOS-Specific Risks**:
- Audio implementation (AVFoundation)
- File system access
- Asset loading
- App lifecycle
- Background mode

**Owner**: iOS Specialist
**Status**: ⚠️ ACTIVE - Validate in Phase 1

---

### TR-005: WebSocket Network Protocol Issues

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | The server uses XMLSocket protocol which may have compatibility issues with modern WebSocket |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | Phase 5 |

**Potential Consequences**:
- Cannot connect to existing server
- Message format incompatible
- Real-time features don't work
- Multiplayer broken

**Mitigation Strategies**:
1. ✅ Reverse-engineer server protocol (Phase 0)
2. ✅ Create protocol specification document
3. ✅ Test with real server early
4. ✅ Work with server team if possible
5. ✅ Have fallback communication method ready
6. ✅ Consider creating proxy server if needed

**Protocol Concerns**:
- XML vs JSON message format
- Connection handshake
- Message framing
- Keepalive mechanism
- Error handling

**Owner**: Network Team
**Status**: ⚠️ ACTIVE - Address in Phase 0-1

---

### 🔴 TR-007: Multiplayer Is Greenfield Development, Not a Migration

| Field | Value |
|-------|-------|
| **Category** | Technical / Scope |
| **Description** | The plan treats Phase 5 as porting an existing, working network layer. It is not. `net/Socket.as` declares 29 `Socket_On_*` handlers; `dataHandler()` dispatches to exactly **two** (`pong`, `clients`). The other 27 are unreachable dead code left over from an abandoned XML→JSON protocol refactor. No match synchronisation runs in the source build. |
| **Probability** | Very High (5) — verified by inspection, not projected |
| **Impact** | High (4) |
| **Risk Score** | **20** (CRITICAL) |
| **Phase Affected** | Phase 5, and the Phase 0 protocol-analysis deliverable |

**Evidence**: `net/Socket.as:dataHandler()` is 17 lines and handles only the literal
string `'pong'` plus a JSON payload's `users` field. Every other `Socket_On_*`
method has exactly one occurrence in the file — its own declaration.

**Compounding factor**: the transport is `flash.net.XMLSocket` — a raw TCP socket.
Ktor WebSocket **cannot** connect to it (no HTTP upgrade, no frame protocol).
Server-side work is unavoidable, contradicting the "backend server remains as-is"
scope statement in [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md).

**Potential Consequences**:
- Phase 5's 3-week estimate is unfounded; designing a protocol, implementing both
  client and server, and testing multiplayer is realistically 8–12 weeks
- "100% feature parity" is unachievable and also meaningless for a feature that
  does not work in the baseline
- Phase 0's "reverse-engineer the server protocol" deliverable cannot succeed —
  there is no live protocol to observe beyond connect/ping/user-list

**Mitigation Strategies**:
1. ❌ **Re-scope: drop PvP from v1** — *not taken*. Multiplayer is wanted, in both a local and an
    online form (decision, 2026-08-06).
2. ✅ **Re-plan Phase 5 as greenfield design + client + server** — taken. The legacy socket
    architecture was abandoned outright on 2026-07-25, and the replacement design is sketched in
    [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md) § The shape of the network layer.
    **Backend capacity is still 0 FTE**, and the 2026-08-06 decision to hold player progression
    server-side makes that gap larger, not smaller: it adds accounts, a datastore, backups and
    personal-data obligations to what was already unstaffed.
3. ✅ Phase 0's deliverable was corrected: [network-protocol.md](../analysis/network-protocol.md)
    opens by stating it is a specification exercise, not reverse engineering.
4. ✅ Moot. The endpoint is not being used by anything: the whole socket architecture is dropped, so
    whether `triple-triad-online.com:2468` still answers no longer matters.

**Owner**: Tech Lead + Project Manager

**Status**: 🟠 **RE-SCOPED, 2026-07-25 / 2026-08-06 — the technical risk is retired, the staffing
risk is not.** There is no longer a false premise ("port the working network layer"): the phase is
acknowledged greenfield and has a design direction. What remains open is that its cost still bears
no relation to the three weeks budgeted, and that nobody is allocated to the server it now requires.
An earlier revision of this file left the status at UNRESOLVED after Phase 5 had already recorded
the decision, so the two documents contradicted each other.

---

### TR-006: Dependency Conflicts

| Field | Value |
|-------|-------|
| **Category** | Technical |
| **Description** | Incompatible library versions may cause build failures or runtime issues |
| **Probability** | Medium (3) |
| **Impact** | Medium (3) |
| **Risk Score** | **9** (HIGH) |
| **Phase Affected** | Phase 1 |

**Potential Consequences**:
- Build failures
- Runtime crashes
- Unexpected behavior
- Difficult debugging

**Mitigation Strategies**:
1. ✅ Use version catalog for dependency management
2. ⚠️ Test all library combinations — only the base UI stack has been tested so
   far. Ktor, kotlinx.serialization, SQLDelight, Koin and Media3 are still
   unverified against it.
3. ✅ Use compatible versions — Kotlin 2.2.20 / Compose Multiplatform 1.9.3 /
   AGP 9.3.1 / Gradle 9.6.1 / JDK 17 is verified working in
   [`README.md`](../../README.md#verified-build-results)
4. ✅ Resolve conflicts early
5. ✅ Document all dependencies and versions
6. ✅ Use dependency lock files

**Key Dependencies**:
- Kotlin Multiplatform
- Compose Multiplatform
- Ktor
- SQLDelight
- Koin
- Kotlinx Serialization
- Coil
- Media3 ExoPlayer

**Owner**: DevOps + Tech Lead
**Status**: ⏳ NOT STARTED - Address in Phase 1

---

## 2. Project Risks

### PR-001: Schedule Delays

| Field | Value |
|-------|-------|
| **Category** | Project |
| **Description** | The 30-32 week timeline may be optimistic, leading to schedule overruns |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | All |

**Potential Consequences**:
- Project takes longer than planned
- Budget overrun
- Team burnout
- Stakeholder dissatisfaction

**Mitigation Strategies**:
1. ✅ Realistic planning with buffer time (10% buffer included)
2. ✅ Incremental delivery with clear milestones
3. ✅ Regular progress tracking
4. ✅ Early identification of blockers
5. ✅ Parallel work where possible
6. ✅ Clear dependencies between tasks

**Buffer Allocation**:
- Phase 0: 2 weeks → 2.2 weeks
- Phase 1: 4 weeks → 4.4 weeks
- Phase 2: 2 weeks → 2.2 weeks
- Phase 3: 4 weeks → 4.4 weeks
- Phase 4: 8 weeks → 8.8 weeks
- Phase 5: 3 weeks → 3.3 weeks
- Phase 6: 3 weeks → 3.3 weeks
- Phase 7: 4 weeks → 4.4 weeks
- Phase 8: 2 weeks → 2.2 weeks
- **Sum with buffer: 35.2 weeks**

> ⚠️ **The buffer is not actually in the plan.** The buffered total is 35.2 weeks,
> but every other document states the project duration as 30–32 weeks. The
> published schedule is the *un*buffered 32-week figure, so a 10% overrun consumes
> the whole margin and slips the release date. Either publish 35 weeks as the
> committed timeline, or state explicitly that there is no schedule buffer.
>
> Independently, the per-week task allocations are over-committed. Examples, all
> for the same owner in the same week: Phase 2 Week 7 assigns the Tech Lead
> 3+2+2+1 = 8 days of work; Phase 3 Week 9 assigns 5+3 = 8 days; Phase 3 Week 10
> assigns 3+4 = 7 days; Phase 4 Week 14 assigns 2+5+3 = 10 days; Phase 7 Week 30
> assigns 5+3+2 = 10 days. A 5-day week cannot absorb these. Task durations must
> be re-levelled against owner capacity before the schedule is credible.

**Owner**: Project Manager
**Status**: ⚠️ ACTIVE — buffer claimed but not reflected in the published 32-week
schedule; task-level allocations exceed owner capacity

---

### PR-002: Budget Overrun

| Field | Value |
|-------|-------|
| **Category** | Project |
| **Description** | Actual costs may exceed the corrected budget of €533,665-€671,165. Note the original €232,500-€297,500 estimate was arithmetically inconsistent with its own staffing assumptions — see the corrected breakdown in [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | All |

**Potential Consequences**:
- Insufficient funds to complete project
- Need for additional funding
- Scope reduction
- Quality compromise

**Mitigation Strategies**:
1. ✅ Detailed budget tracking
2. ✅ Regular financial reviews
3. ✅ Contingency budget (10% included)
4. ✅ Early warning system for cost overruns
5. ✅ Prioritize must-have features
6. ✅ Consider phased delivery if needed

**Cost Control Measures**:
- Track time spent per task
- Monitor contractor rates
- Review tool and service costs
- Optimize cloud/infrastructure spending
- Defer nice-to-have features if needed

**Owner**: Project Manager + Finance
**Status**: ⚠️ ACTIVE — a 10% contingency does not cover a baseline that was
understated by ~130%. Re-baseline before approval.

---

### PR-003: Team Skill Gaps

| Field | Value |
|-------|-------|
| **Category** | Project |
| **Description** | Team members may lack necessary skills for Kotlin Multiplatform development |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | Phase 0 (Training) |

**Potential Consequences**:
- Poor code quality
- Slow development
- Bugs and issues
- Need for external help

**Mitigation Strategies**:
1. ✅ Comprehensive training program (Phase 0)
2. ✅ Skill assessment before project start
3. ✅ Pair programming for knowledge sharing
4. ✅ Mentorship from senior developers
5. ✅ Access to learning resources
6. ✅ Hire specialists if needed

**Training Plan**:
- Kotlin fundamentals: 1 day
- Kotlin Multiplatform: 1 day
- Compose Multiplatform: 1 day
- Domain knowledge: 0.5 day
- Hands-on workshops: Ongoing

**Owner**: Tech Lead + Training Coordinator
**Status**: ⏳ NOT STARTED - Address in Phase 0

---

### PR-004: Team Turnover

| Field | Value |
|-------|-------|
| **Category** | Project |
| **Description** | Key team members may leave during the project |
| **Probability** | Low (2) |
| **Impact** | High (4) |
| **Risk Score** | **8** (MEDIUM) |
| **Phase Affected** | All |

**Potential Consequences**:
- Knowledge loss
- Development slowdown
- Quality issues
- Need for replacements

**Mitigation Strategies**:
1. ✅ Documentation of all processes
2. ✅ Code reviews for knowledge sharing
3. ✅ Pair programming
4. ✅ Cross-training on critical components
5. ✅ Retention incentives
6. ✅ Clear knowledge transfer process

**Knowledge Retention**:
- Document all major decisions
- Keep code well-commented
- Maintain up-to-date documentation
- Record training sessions
- Create runbooks for critical tasks

**Owner**: Project Manager
**Status**: ⏳ NOT STARTED - Ongoing effort

---

### PR-005: Scope Creep

| Field | Value |
|-------|-------|
| **Category** | Project |
| **Description** | Additional features or changes may be requested during migration |
| **Probability** | High (4) |
| **Impact** | Medium (3) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | All |

**Potential Consequences**:
- Schedule delays
- Budget overrun
- Feature incompleteness
- Quality issues

**Mitigation Strategies**:
1. ✅ Clear scope definition upfront
2. ✅ Change control process
3. ✅ Impact assessment for all changes
4. ✅ Defer non-critical enhancements
5. ✅ Focus on feature parity first
6. ✅ Separate enhancement backlog

**Change Control Process**:
1. Submit change request
2. Assess impact (schedule, budget, risk)
3. Get stakeholder approval
4. Update plan and documentation
5. Implement change

**Owner**: Project Manager
**Status**: ⏳ NOT STARTED - Process to be established

---

## 3. Business Risks

### 🔴 BR-003: Unlicensed Use of Square Enix Intellectual Property

| Field | Value |
|-------|-------|
| **Category** | Business / Legal |
| **Description** | The project is an unlicensed fan implementation of Square Enix IP. "Triple Triad", "Final Fantasy", FFVIII and FFXIV are Square Enix trademarks. All card artwork, fonts, character art and sound effects are extracted from shipped Square Enix titles. Phase 8 plans public distribution on Google Play and the Apple App Store. |
| **Probability** | Very High (5) |
| **Impact** | Critical (5) |
| **Risk Score** | **25** (CRITICAL — highest in the register) |
| **Phase Affected** | Phase 8 primarily, but invalidates the entire investment |

**Evidence in the repository**:
- `application.xml`: `<copyright>© Moogle Works 2015</copyright>` — no Square Enix licence
- `sources/assets/cards/ff8_cards.xml`, `ff14_cards.xml` — texture atlases of extracted card art
- `sources/bin/assets/atlas/` — FFXIV UI, avatar and NPC sprites
- `datas/cards.as` — card names as `STR_FF14_CARD_n` / `STR_FF8_CARD_n` localisation keys
  resolving to Square Enix character names
- `sources/assets/-mogu_anime_en.xml`, `anims/Mogu.as` — Moogle character
- [12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md) Task 8.3 lists `final fantasy`
  as a Google Play store tag

**Potential Consequences**:
- DMCA takedown of both store listings, typically within weeks of launch
- Suspension or termination of the Apple and Google developer accounts
- Cease-and-desist; statutory damages exposure for wilful infringement
- Total loss of the project investment (€534k–€671k under the corrected budget)
- Reputational damage to everyone credited on the release

**Mitigation Strategies** — engineering cannot mitigate this; only scope can:
1. ⬜ **Legal review before Phase 0 sign-off.** Non-negotiable gate.
2. ⬜ **Reskin (recommended):** retain the rules engine — game mechanics are not
   copyrightable — and replace *every* Square Enix asset: card art, card names,
   the "Triple Triad" title, fonts, audio, NPC art, UI atlases. Budget an
   artist and a sound designer; **neither is currently staffed** in
   [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md).
3. ⬜ **Or** restrict to private/personal distribution with no store listing —
   which removes the commercial rationale for the migration.
4. ⬜ **Or** formally approach Square Enix for a licence (low probability of
   success, but cheap to ask and definitive either way).
5. ⬜ Remove `final fantasy` and all franchise terms from store metadata under
   every scenario except a granted licence.
6. ⬜ Strip the bundled `AdobeAIRInstaller-32.0.exe` (11 MB) from the repository —
   redistributing the Adobe AIR installer is a separate licensing question.

**Contingency Plan**:
- If a licence is refused and reskinning is rejected → **cancel the project**.
  Proceeding to a public release under those conditions is not a risk to manage,
  it is a decision to infringe.

**Note on BR-001**: that risk lists "Final Fantasy IP value" as a market
*positive*. Absent a licence it is a liability, not an asset.

**Owner**: Project Sponsor + Legal Counsel
**Status**: 🔴 **UNRESOLVED — BLOCKS PHASE 0 SIGN-OFF**

---

### BR-001: Market Timing

| Field | Value |
|-------|-------|
| **Category** | Business |
| **Description** | Market conditions may change, affecting the value of the mobile version |
| **Probability** | Low (2) |
| **Impact** | Medium (3) |
| **Risk Score** | **6** (MEDIUM) |
| **Phase Affected** | Phase 8 |

**Potential Consequences**:
- Reduced user interest
- Competition from other games
- Platform changes (Android/iOS)
- Economic downturn

**Mitigation Strategies**:
1. ✅ Market research before start
2. ✅ Competitive analysis
3. ✅ Flexible release strategy
4. ✅ Feature roadmap for post-release
5. ✅ Monitor market trends
6. ✅ Adapt to changing conditions

**Market Considerations**:
- Mobile gaming market growth
- Card game popularity
- Final Fantasy IP value
- Niche market appeal

**Owner**: Product Manager
**Status**: ⚠️ ACTIVE - Monitor throughout

---

### BR-002: User Adoption

| Field | Value |
|-------|-------|
| **Category** | Business |
| **Description** | Users may not adopt the mobile version as expected |
| **Probability** | Medium (3) |
| **Impact** | Medium (3) |
| **Risk Score** | **9** (HIGH) |
| **Phase Affected** | Phase 8+ (Post-release) |

**Potential Consequences**:
- Low download numbers
- Poor user retention
- Negative reviews
- Low revenue

**Mitigation Strategies**:
1. ✅ Market research and validation
2. ✅ Beta testing with target users
3. ✅ Marketing and promotion plan
4. ✅ Quality assurance
5. ✅ Feature set aligned with user expectations
6. ✅ Pricing strategy

**Adoption Factors**:
- Feature parity with desktop
- Performance and quality
- User interface usability
- Localization
- Marketing effectiveness

**Owner**: Marketing + Product Manager
**Status**: ⏳ NOT STARTED - Address in Phase 8

---

## 4. Quality Risks

### QR-001: Feature Parity Not Achieved

| Field | Value |
|-------|-------|
| **Category** | Quality |
| **Description** | The Kotlin version may not achieve 100% feature parity with the AS3 version |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | All |

**Potential Consequences**:
- User dissatisfaction
- Negative reviews
- Loss of credibility
- Need for rework

**Mitigation Strategies**:
1. ✅ Complete feature inventory
2. ✅ Prioritize all features
3. ✅ Regular parity validation
4. ✅ Comprehensive testing
5. ✅ User acceptance testing
6. ✅ Clear documentation of any gaps

**Parity Validation**:
- Checklist of all AS3 features
- Regular comparison testing
- User feedback on missing features
- Stakeholder review

**Owner**: QA Engineer + Tech Lead
**Status**: ⏳ NOT STARTED - Ongoing validation

---

### QR-002: Bug Rate Exceeds Target

| Field | Value |
|-------|-------|
| **Category** | Quality |
| **Description** | The final app may have more bugs than target. Note the target is stated inconsistently: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md) says "<2% critical bugs" while the Quality Targets below say "Critical bugs: 0". Adopt zero critical/major as the release gate and drop the 2% figure. |
| **Probability** | Medium (3) |
| **Impact** | High (4) |
| **Risk Score** | **12** (HIGH) |
| **Phase Affected** | Phase 7 |

**Potential Consequences**:
- Poor user experience
- Negative reviews
- Support burden
- Reputation damage

**Mitigation Strategies**:
1. ✅ Comprehensive testing strategy
2. ✅ Multiple testing levels (unit, integration, UI, UAT)
3. ✅ Automated testing where possible
4. ✅ Beta testing with external users
5. ✅ Bug triage and prioritization
6. ✅ Quality gates before release

**Quality Targets**:
- Critical bugs: 0
- Major bugs: 0
- Minor bugs: <5
- Cosmetic bugs: <10

**Owner**: QA Engineer
**Status**: ⏳ NOT STARTED - Address in Phase 7

---

## 📊 Risk Summary

### By Category

Scores below are the arithmetic sum of each category's individual risk scores.
(The previous version of this table did not add up: Technical was listed as 79
against an actual 73, Project as 60 against 56, and the grand total as 178 against
168. Corrected, and extended with BR-003 and TR-007.)

| Category | Count | Critical | High | Medium | Low | Total Score |
|----------|-------|----------|------|--------|-----|--------------|
| Technical | 7 | 2 | 5 | 0 | 0 | 93 |
| Project | 5 | 0 | 4 | 1 | 0 | 56 |
| Business | 3 | 1 | 1 | 1 | 0 | 40 |
| Quality | 2 | 0 | 2 | 0 | 0 | 24 |
| **Total** | **17** | **3** | **12** | **2** | **0** | **213** |

*Technical: 16+20+12+12+12+12+9 = 93 · Project: 12+12+12+12+8 = 56 ·
Business: 25+9+6 = 40 · Quality: 12+12 = 24*

> **Note on banding**: per the scale in this document, 9–15 is *High*. TR-006
> (score 9) is therefore High, not Medium — the previous summary miscategorised it.

### Top 10 Risks by Score

| Rank | ID | Risk | Score | Status |
|------|-----|------|-------|--------|
| 1 | **BR-003** | **Unlicensed Square Enix IP** | **25** | 🔴 UNRESOLVED — blocks sign-off |
| 2 | **TR-007** | **Multiplayer is greenfield, not a migration** | **20** | 🔴 UNRESOLVED — re-scope required |
| 3 | TR-003 | Animation Complexity | 16 | ⚠️ ACTIVE |
| 4 | TR-001 | Kotlin Multiplatform Immaturity | 12 | ⚠️ ACTIVE |
| 5 | TR-002 | Compose Performance | 12 | ⚠️ ACTIVE |
| 6 | TR-004 | iOS Compatibility | 12 | ⚠️ ACTIVE |
| 7 | TR-005 | XMLSocket → WebSocket Protocol | 12 | ⚠️ ACTIVE |
| 8 | PR-001 | Schedule Delays | 12 | ⚠️ ACTIVE (see note) |
| 9 | PR-002 | Budget Overrun | 12 | ⚠️ ACTIVE (see note) |
| 10 | PR-003 | Team Skill Gaps | 12 | ⏳ NOT STARTED |

*Also at score 12 and not shown: PR-005 (Scope Creep), QR-001 (Feature Parity),
QR-002 (Bug Rate).*

### Risk Distribution

- **Critical (16-25)**: 3 risks (18%)
- **High (9-15)**: 12 risks (71%)
- **Medium (4-8)**: 2 risks (12%)
- **Low (1-3)**: 0 risks (0%)

---

## 🛡️ Risk Mitigation Strategy

### 1. Proactive Risk Management
- **Risk Identification**: Regular risk assessment throughout project
- **Risk Tracking**: Maintain up-to-date risk register
- **Risk Review**: Weekly risk review meetings
- **Risk Reporting**: Monthly risk reports to stakeholders

### 2. Risk Mitigation Actions
1. **High-Priority Risks**: Immediate action, dedicated resources
2. **Medium-Priority Risks**: Monitor closely, mitigate as needed
3. **Low-Priority Risks**: Accept or low-effort mitigation

### 3. Risk Contingency Planning
- Develop contingency plans for all high-priority risks
- Test contingency plans where possible
- Ensure team is aware of contingency plans
- Regular review of contingency plans

### 4. Risk Communication
- Clear communication of risks to stakeholders
- Transparent reporting of risk status
- Early warning of emerging risks
- Regular updates on risk mitigation progress

---

## 📋 Risk Management Process

### 1. Identify Risks
- Brainstorming sessions
- Expert consultation
- Historical data analysis
- Stakeholder interviews

### 2. Analyze Risks
- Assess probability
- Assess impact
- Calculate risk score
- Determine risk level

### 3. Plan Risk Response
- Develop mitigation strategies
- Assign risk owners
- Define contingency plans
- Set risk triggers

### 4. Implement Risk Response
- Execute mitigation actions
- Monitor risk indicators
- Track risk status
- Report progress

### 5. Monitor and Review
- Regular risk reviews
- Update risk register
- Reassess risk scores
- Close resolved risks

---

## 📞 Related Documents

- **Executive Summary**: [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md)
- **Phase 0**: [04-PHASE-0-PREPARATION.md](./04-PHASE-0-PREPARATION.md)

---
