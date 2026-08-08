
# Phase 3: Core Logic - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 3 - Core Logic
- **Duration**: 4 weeks (Weeks 9-12)
- **Status**: DONE — 2026-08-02. Most of it was already built: see § What was built.
- **Version**: 1.0
- **Last Updated**: 2026-08-02
- **Prerequisites**: Phase 1, Phase 2

---

## 🎯 Phase Overview

### Purpose
Phase 3 migrates the core game logic from ActionScript 3 to Kotlin, including the critical TTOCore rules engine, game state management, and game flow system.

### Key Objectives
1. Migrate TTOCore.as (rules engine)
2. Migrate tripleTriadRules.as (rule definitions)
3. Implement game state management
4. Create game flow system
5. Test all game rules thoroughly

---

## 🔨 What was built

**Two thirds of this phase was already done before it started.** The rules engine and the match
state machine landed with Phase 0's proof of concept and Phase 1 — `RulesEngine` is the whole of
`TTOCore.applyRules` → `animate`, `GameRules` + `RuleKeys` is `tripleTriadRules`' constant table read
in both directions, and `MatchState` is the match as one immutable value with Order, Chaos, scoring,
the outcome and the Sudden Death regrouping. The index already flagged that this phase needed
re-scoping against what existed. It did.

What was missing was everything *around* one placement: how a rule set gets chosen, how a match gets
assembled, and who plays the other side.

### Files

| File | What it is |
|---|---|
| `model/Roulette.kt` | `tripleTriadRules.roulette` — the two per-collection pools and the 1-3 draws |
| `model/MatchSetup.kt` | The pre-match chain: `HandSource`, `randomHand`, `swap`, `HandVisibility`, `CoinFlip`, `MatchIntroStep`, `elementsFor`, `prepare`, `prepareRematch` |
| `model/MatchAi.kt` | `PVEMatchScreen.AI` — `ScoredMove`, `cover`, `evaluate`, `candidates`, `choose`, `play` |
| `commonTest/model/RouletteTest.kt` | § 16 items 33-35 |
| `commonTest/model/MatchSetupTest.kt` | The pre-match chain, and § 16 item 32's rematch path |
| `commonTest/model/MatchAiTest.kt` | The AI, including two AIs playing out every rule set the roulette can produce |
| `desktopTest/model/EnginePerformanceTest.kt` | Task 3.6 |

`ui/MatchScreen.kt` now deals through `MatchPreparation.prepare` instead of its own stand-in.

### Five deviations from the plan

1. **`GamePhase` is not an eleven-member enum on the state.** The plan modelled the pre-match
   sequence as phases advanced by a `next()` function. Five of the eleven exist only to play an
   animation: nothing downstream branches on them, they cannot be resumed or saved, and there is no
   assertion to write about being "in" one. `MatchIntroStep` is a **list** the UI is handed, with
   everything it announces already applied — so the match is legal from the first frame whether the
   UI plays the sequence or skips it. `GameFlowManager`, `GameState` and `TurnState` are likewise
   absent: `MatchState` is all three, and it needs no coroutine scope because nothing waits on a
   timer.

2. **The roulette *adds* rules, it does not generate a rule set.** The plan's
   `GameRules.roulette(mode)` returns a fresh set. The only live caller passes the opponent's own
   rules in and reassigns the result (`BaseMatchScreen.as:64-66`), so an opponent declaring
   `RULE_ROULETTE` plays with everything it declared **plus** one to three draws. Eleven of the 85
   shipped opponents use it. `Roulette.augment(rules, collection, random)` says so in its signature.

3. **The AI's dead cover sort is fixed by default.** `PVEMatchScreen.AI` computes a defensive score
   for all 45 candidate placements, sorts by it, and then picks at random among every move matching
   the best *capture* count — discarding the cover ordering entirely. `MatchAiOptions.FAITHFUL`
   reproduces that; the default settles the tie on cover, which is what the sort was evidently for.
   This follows the `RulesEngineOptions` precedent from Phase 1.

4. **There are no AI difficulty levels, and none were added.** `NPC.difficulty` is read only to
   order the opponent list — every opponent from the tutorial dummy to the Queen of Cards plays with
   the same one-move-lookahead function. Giving the field its apparent meaning is new game design,
   not migration.

5. **The AI is implemented but not reachable from the UI** — see the section below.

### Three AS3 defects found and decided on

| Where | What | Decision |
|---|---|---|
| `tripleTriadRules.as:62` | `possibleRules[tools.rand(length - 1)]` halves the odds of the **first and last** entry in each pool, so All Open and Three Open are drawn half as often as every other rule in both collections | **Fixed** — uniform draw. Generated rule sets therefore do not match the original's distribution, so no test asserts a frequency |
| `BaseMatchScreen.as:128-131` | `RULE_RANDOM` pushes the last remaining card repeatedly once its copy of the collection is down to one entry, so a player owning four cards is dealt a hand containing the same card twice | **Refused** — `randomHand` requires at least five cards. The starting profile owns five and `GameSave.sane()` deduplicates, so it cannot arise without a corrupted save, and dealing a duplicate is worse than saying so |
| `BaseMatchScreen.as:126` | `if (randomizer.length == 5) randomCards = randomizer` skips the draw entirely for a player owning exactly five cards, leaving the hand in collection order | **Fixed** — that order is not cosmetic: under `RULE_ORDER` it decides which card must be played next |

### Two facts about the original worth recording

- **`PVEMatchScreen.AI` is the only AI, and `applyRules(…, checking = true)` is called from exactly
  one place** — the AI's inner loop. `BaseMatchScreen.opponentPhase()` is an empty stub with its body
  commented out; `autoPlay()`, the timer-expiry fallback, plays a uniformly random card on a random
  cell and is what a *human* gets when their clock runs out, not what the opponent does.
- **The coin flip is fair.** `PileOuFace` shows three cards and takes the majority colour
  (`:34`, `:93-95`), which is a ½ either way — and `tools.rand(1)` is one of the few uses of that
  function without the endpoint skew, since `Math.round(Math.random())` really is an even split.
  `CoinFlip` keeps the three rolls because they are what the animation shows.

### What is not wired to the UI, and why

`Roulette`, `HandVisibility`, `MatchIntroStep` and `MatchAi` are implemented and tested, and **none
of them is reachable by a player**. `MatchScreen` still deals under `GameRules()` and the player
still moves both sides.

This is deliberate and it is a Phase 4 dependency, not an omission of Phase 3's logic. Connecting
them needs an opponent-selection screen to choose *which* opponent and therefore which rules — and,
less obviously, it needs Phase 1's UI test suite reworked: `ComposeTestSupport.playOut`,
`sideToPlay` and every per-placement assertion in `MatchAudioTest` are built on the assumption that
a human drives both hands. Rewriting ten UI tests to interleave with an autonomous opponent is UI
work with UI risk, and doing it inside Phase 3 would have mixed it into the phase that proves the
logic. `MatchAiTest.twoAisPlayAnyRuleSetToACompleteMatch` is what proves the AI end to end in the
meantime.

### Verification

| | |
|---|---|
| Build | `./gradlew clean build` — ktlint, detekt at `maxIssues: 0`, all tests, `coverageVerify` |
| Tests | 4 new test classes, 84 new tests. 458 in `:shared` on desktop, 389 on the Android host source set (the difference is the desktop-only UI, bundle and performance tests) |
| Coverage | 97.3% line / 89.5% branch, against a 90/75 gate. Branch coverage is up from 88.3%; the line figure is down 0.3 points, all of it unexercised **default-argument values** — the tests pass an explicit seeded `Random` everywhere, so `random: Random = Random.Default` is never taken. Covering those would mean tests that depend on a global unseeded RNG |
| Performance | Every target in Task 3.6 met by two to three orders of magnitude. See `EnginePerformanceTest` |

---

## 📝 Tasks

#### Task 3.1: Migrate TTOCore.as

**⚠️ What `applyRules` actually does** (read this before writing the Kotlin version).
The AS3 signature `applyRules(tile, color, checking):uint` is misleading — the
function does two unrelated jobs depending on `checking`.
Three consequences the previous plan missed:

- The `uint` return is an **AI heuristic score**, not a flip count for the real
  move. In the `checking = false` path it is always `0`.
- The power transforms are applied **unconditionally**, before the branch, and they
  **mutate the tile**. Modelling this immutably means returning a new `Tile` with
  effective powers, which is why `Tile` carries `topPow`…`leftPow` separately from
  `card.topPow` (see [13-DATA-MODELS.md](./13-DATA-MODELS.md)).
- `animate()` — not `applyRules` — owns the actual flipping and the combo cascade.
  Rules evaluation and animation are entangled in the source; the Kotlin port must
  separate them, and that separation is the real work of Task 3.1.

- [x] All `TTOCore` methods migrated. `applyRules`' power transforms are `effectivePower` in
  `model/Power.kt`; `basicRule`, `specialRule` and `comboRule` are `RulesEngine`; `animate` is not
  ported, because it *was* the flipping and the flipping is now `Board.capture`
- [x] The split this task called "the real work" is done: rules evaluation returns a `Resolution`
  value and the UI animates from `MatchState.lastPlay`, so nothing downstream waits on a timer
- [x] **Not identically to AS3, and deliberately** — two departures, both switchable through
  `RulesEngineOptions` and both pinned by tests either way: the Same Wall one-neighbour gate and
  whether Same and Plus read printed or effective powers. See
  [game-rules.md](../../analysis/game-rules.md) § 15.2 and § 15.4
- [x] `RulesEngineTest` is the § 16 matrix. The AI evaluation hook that this task's `scoreMove`
  sketch describes is now `MatchAi.evaluate`, and it is genuinely side-effect free — see
  § What was built
- [x] Performance: 4.8 us to resolve a placement with every rule active, against the plan's 1 ms
  and 10 ms targets

---

#### Task 3.2: Migrate tripleTriadRules.as

- [x] All rule types defined, as three enums and nine booleans in `model/GameRules.kt`. The
  20 AS3 constants are **not** 20 rules: two are i18n keys for UI headings (`RULE_OPEN`,
  `RULE_TYPE`), three are the *absence* of a rule (`RULE_DEFAULT_*`) and `RULE_COMBO` is dead —
  declared, shown on the help screen, never read. Combo fires unconditionally
- [x] `RuleKeys` is the constant-to-rule mapping **once**, read in both directions. The original
  writes it out twice — a 16-case `switch` in `NPC.gameRules` and a `RULES_W` increment per site —
  which is how they get to disagree, and a rule in one but not the other is silently dropped rather
  than reported. `NpcTest.everyMappedRuleKeyIsAlsoAWinCounterKey` asserts the round trip for all 16
- [x] `Roulette.augment` — but it **augments rather than generates**; see deviation 2 above
- [x] The two pools are transcribed verbatim, and they turn out to be **the legal rule set per
  collection**: Same Wall and Elemental are FF8-only, and Ascension, Descension, Reverse, Fallen
  Ace, Order, Chaos and Swap are FF14-only. The 85 shipped opponents agree exactly, which is what
  makes it a rule of the game rather than an accident of two array literals
  defect table above

---

#### Task 3.3: Implement Game State Management

- [x] Game state fully modelled, as `MatchState`. The `TurnState` sketch above is right that the
  AS3 counter is monotonic and wrong about the array: the timeline is 10 entries read with a
  1-based counter, so `timeline[0]` is never used. `TurnOrder` derives the colour from the
  placement's parity instead, which is behaviour-preserving and cannot be half-fixed into swapping
  who moves first
- [x] Every transition is `MatchState -> MatchState`, and `MatchStateTest` covers them
- [x] `GameRules` is `@Serializable`, which is what `MatchRecord` needs. **`MatchState` is not**,
  and nothing asks it to be: no save-and-resume feature exists, the original could not do it
  either, and serialising a board mid-match is a Phase 5 question about what the wire format is
  `var state` and calls `state.play(card, position)`; the sounds a placement makes are a pure
  function of the resulting state, asserted through the real UI by `MatchAudioTest`. A
  `MutableSharedFlow<GameEvent>` in front of that would be a second source of truth for something
  already derivable

---

#### Task 3.4: Create Game Flow System

1. Deck Selection Phase
2. Open Phase (for Open rules)
3. Order Phase (for Order/Chaos rules)
4. Reverse Phase (for Reverse rule)
5. Fallen Ace Phase (for Fallen Ace rule)
6. Swap Phase (for Swap rule)
7. Pile Ou Face (coin flip for tiebreakers)
8. Main Game Play
9. End Game

- [x] Every step of the chain is implemented: Random (`randomHand`), Open (`HandVisibility`), Order
  and Chaos (`MatchState.playableCards`, Phase 1), Reverse and Fallen Ace (the engine, Phase 1),
  Swap (`swap`), the coin flip (`CoinFlip`), and the Sudden Death rematch (`prepareRematch`)
- [x] The order is the source's, and **every applicable step runs** — the warning above about a
  subject-less `when` running only the first branch is why `introSteps` is a `buildList` of
  independent `if`s, and `MatchSetupTest.everyApplicableStepIsAnnouncedInSourceOrder` pins it
- [x] Interruptible by construction rather than by `Job.cancel()`: there is no flow to interrupt.
  `prepare` returns a value, so abandoning a match is dropping a reference
- [x] The opponent that this task's `awaitTurn()` left as a comment is `MatchAi`

---

#### Task 3.5: Core Logic Testing

1. **Unit Tests**: Test each function in isolation
2. **Integration Tests**: Test component interactions
3. **Property-Based Tests**: Test rule invariants
4. **Comparison Tests**: Compare with AS3 behavior
5. **Edge Case Tests**: Test unusual scenarios

- [x] All pass, on desktop and on the Android host source set
- [x] Coverage: 97.3% line / 89.5% branch across `:shared`, against targets of >95% for the engine
  and >90% elsewhere
- [x] Validated against the **source**, not against a running client: the AIR original is
  unrunnable, so there is no oracle to diff against and "comparison tests with AS3 behaviour" as
  written cannot be done. What replaces it is a line-referenced reading of the source recorded in
  [game-rules.md](../../analysis/game-rules.md), and tests that cite the line they encode
- [x] No property-based library was added. `RulesEngineTest` is the § 16 matrix — the cases that
  distinguish a correct engine from a plausible one — and the new tests sweep seeds where the
  behaviour is randomised. A generator would explore board states that cannot occur while adding a
  dependency and a shrinking algorithm to debug

---

#### Task 3.6: Performance Optimization

- [x] Every metric met by two to three orders of magnitude, measured by
  `EnginePerformanceTest`: **4.8 us** to resolve a placement with every rule live (targets 1 ms and
  10 ms), **62 us** for a whole nine-placement match, **60 us** for an AI turn evaluating all 45
  candidates, **751 us** for an AI-versus-AI match
- [x] **None of the listed techniques was applied.** No `inline` on a hot path, no primitive
  arrays, no cached computed values, no changed data structures. The engine is arithmetic over a
  nine-element list; optimising it would trade the clarity that makes the rules auditable for
  microseconds nothing is waiting on. The measurement is the deliverable, not a speed-up
- [x] Regression guard: each budget is set ~100x the measured cost, so it catches a change of
  *complexity* — a `resolve` that started rebuilding the board per capture, an AI that started
  searching the tree — and not a loaded CI machine. That trade-off is written into the test
- [x] Memory controlled by construction: every value is immutable and nothing is cached, so a
  match's peak footprint is ten cards and nine placements' worth of boards, all collectable

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 2**: [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Status: IMPLEMENTED — 2026-08-02. See § What was built for the five deviations.*
