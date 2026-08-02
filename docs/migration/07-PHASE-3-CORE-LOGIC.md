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

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 9 | TTOCore migration, Rule definitions | Tech Lead + Senior Kotlin Devs |
| Week 10 | Game state, Flow system | Tech Lead + Team |
| Week 11 | Testing, Validation | QA + Team |
| Week 12 | Optimization, Finalization | Tech Lead + Team |

---

## 📝 Tasks

### Week 9: Rules Engine Migration

#### Task 3.1: Migrate TTOCore.as
**Owner**: Tech Lead | **Duration**: 5 days | **Priority**: CRITICAL

> ⚠️ **Week 9 is over-allocated**: Task 3.1 (5 days) + Task 3.2 (3 days) = 8 days,
> both owned by the Tech Lead, in a 5-day week. Week 10 has the same problem
> (3 + 4 = 7 days). Re-level before committing to the schedule.

**TTOCore.as Analysis** (from 02-CURRENT-SYSTEM-ANALYSIS.md):
- Primary rules engine for Triple Triad
- Handles card flipping logic based on game rules
- Manages special rule combinations
- Coordinates animations with the screen
- 395 lines of complex logic
- Multiple rule combinations
- Recursive combo logic
- State management across multiple cards

**Key Methods to Migrate**:
- `applyRules(tile:Tile, color:String, checking:Boolean):uint`
- `basicRule(tile:Tile, COLOR:String):Array`
- `specialRule(tile:Tile, COLOR:String):Array`
- `comboRule(tile:Tile, enqueue:Array, bounce:uint, COLOR:String, tileComboted:Array):Array`
- `animate(tile:Tile, color:String):void`

**⚠️ What `applyRules` actually does** (read this before writing the Kotlin version).
The AS3 signature `applyRules(tile, color, checking):uint` is misleading — the
function does two unrelated jobs depending on `checking`:

```actionscript
public function applyRules(tile:Tile, color:String, checking:Boolean = false):uint {
    tile.color = color;

    // 1. ALWAYS: compute the tile's effective edge powers from the card,
    //    applying rule-based transforms in this exact order.

    // Fallen Ace: an A (10) on the PLACED card becomes 0.
    // NOTE: this is NOT the canonical "1 beats A" rule. This implementation
    // simply makes Aces the weakest edge. Preserve it or fix it — but decide
    // deliberately, and write a test that pins the chosen behaviour.
    if (_RULES.FALLEN_ACE) {
        tile.topPow = (tile.card.topPow == 10) ? 0 : tile.card.topPow;
        // ... same for right/bottom/left
    } else {
        tile.topPow = tile.card.topPow; // ... etc
    }

    // Ascension / Descension: += card.modifier, clamped to 0..10 by tools.madmax
    if (_RULES.TYPE_RULE == RULE_ASCENSION || _RULES.TYPE_RULE == RULE_DESCENSION) {
        tile.topPow = tools.madmax(int(tile.topPow) + tile.card.modifier); // ... etc
    }

    // Elemental: +1 if card type matches tile element, -1 if tile has a
    // non-"none" element and the types differ, else 0. Then clamped.
    if (_RULES.TYPE_RULE == RULE_ELEMENTAL) {
        if (tile.card.data.type == tile.element) tile.card.modifier = 1;
        else if (tile.element !== "none" && tile.card.type !== tile.element) tile.card.modifier = -1;
        else tile.card.modifier = 0;
        tile.topPow = tools.madmax(int(tile.topPow) + tile.card.modifier); // ... etc
    }

    // 2. THEN branch on `checking`:
    if (checking) {
        // AI evaluation only: return HOW MANY cards would flip. No side effects.
        return (SAME || SAME_WALL || PLUS)
            ? countDistinctTilesFlipped(specialRule(tile, color))
            : basicRule(tile, color).length;
    } else {
        // Real move: play sound, run the animation pipeline, return 0.
        SoundManager.playSound('se_ttriad.scd_1', true);
        animate(tile, color);
        return 0;
    }
}
```

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

**Kotlin Implementation Strategy** — split the two responsibilities:

```kotlin
class TTOCore(private val rules: GameRules) {

    /** Pure: the tile's effective edge powers after all power-transforming rules. */
    fun effectivePowers(card: Card, element: Element): EdgePowers {
        var p = EdgePowers(card.topPow, card.rightPow, card.bottomPow, card.leftPow)

        if (rules.fallenAce) p = p.map { if (it == 10u) 0u else it }

        val modifier: Int = when (rules.typeRule) {
            TypeRule.ASCENSION, TypeRule.DESCENSION -> card.ascensionModifier
            TypeRule.ELEMENTAL -> when {
                card.type?.name?.lowercase() == element.name.lowercase() -> 1
                element != Element.NONE -> -1
                else -> 0
            }
            TypeRule.DEFAULT_TYPE -> 0
        }
        if (modifier != 0) p = p.map { madmax(it.toInt() + modifier) }
        return p
    }

    /** Pure: which tiles this placement captures. Board is not mutated. */
    fun capturedTiles(board: Board, tileId: Int, color: CardColor): List<Capture> {
        val source = board[tileId]
        val basic = board.neighbours(tileId)
            .filter { (dir, target) -> canFlip(source, target, dir, color, rules) }
            .map { (dir, target) -> Capture(target.id, axis = dir.flipAxis()) }

        // SAME / PLUS pair detection uses the cards' PRINTED digits
        // (`neighbour.card.bottomPow - placed.card.topPow`), while the plain
        // capture check inside the same function uses the MODIFIED tile powers
        // (`neighbour.bottomPow < tile.topPow`). That asymmetry is deliberate in
        // the source but flagged by the original author:
        //     "need to verify with card digit (without modifiers) instead of tile
        //      power values"   — TTOCore.as:215
        // Decide which semantics to keep and pin it with a test. Do not "tidy" it
        // into consistency by accident — it changes which cards flip.
        //
        // Note also that same/plus candidates are collected for ALL occupied
        // neighbours regardless of owner (a pair may include your own card), but
        // only opponent-owned cards are actually flipped.
        val special = if (rules.same || rules.sameWall || rules.plus)
            specialRule(board, tileId, color) else emptyList()

        // Combo cascades ONLY from SAME / PLUS / SAME_WALL captures — never from a
        // basic capture. In the source, comboRule() is invoked exclusively from
        // inside specialRule(), attached to each capture as a `waveEffect` array of
        // successive shock waves (TTOCore.as:274-297).
        val combo = if (special.isNotEmpty())
            comboRule(board, special, color) else emptyList()

        return (basic + special + combo).distinctBy { it.tileId }
    }

    /** AI heuristic: the AS3 `applyRules(..., checking = true)` return value. */
    fun scoreMove(board: Board, tileId: Int, color: CardColor): Int =
        capturedTiles(board, tileId, color).size
    
    // ⚠️ The version of this function previously published here was WRONG.
    // It tested all four edge comparisons against a single neighbour and returned
    // true if ANY matched — so a card to the RIGHT could be captured because the
    // source's TOP beat its BOTTOM. Every capture must compare exactly ONE pair of
    // facing edges, determined by which direction the neighbour actually lies in.
    private fun canFlip(
        source: Tile,
        target: Tile,
        direction: Direction,   // direction FROM source TO target — not optional
        color: CardColor,
        rules: GameRules
    ): Boolean {
        source.card ?: return false
        target.card ?: return false

        // Only opponent-owned cards can be captured.
        if (target.color != color.opponent()) return false

        // Exactly one pair of facing edges.
        val attack = when (direction) {
            Direction.TOP    -> source.topPow
            Direction.RIGHT  -> source.rightPow
            Direction.BOTTOM -> source.bottomPow
            Direction.LEFT   -> source.leftPow
        } ?: return false

        val defence = when (direction) {
            Direction.TOP    -> target.bottomPow
            Direction.RIGHT  -> target.leftPow
            Direction.BOTTOM -> target.topPow
            Direction.LEFT   -> target.rightPow
        } ?: return false

        // Reverse is the ONLY rule that alters this comparison in the AS3 source.
        // Fallen Ace, Ascension/Descension and Elemental are applied earlier, as
        // power *transforms* at placement time — see applyRules() below.
        return if (rules.reverse) attack < defence else attack > defence
    }
    
    // Special rules implementation
    private fun sameRule(tile: Tile, color: CardColor): List<Tile> { /* ... */ }
    private fun plusRule(tile: Tile, color: CardColor): List<Tile> { /* ... */ }
    private fun comboRule(
        tile: Tile,
        enqueue: List<Tile>,
        bounce: UInt,
        color: CardColor,
        tileComboted: MutableList<Tile>
    ): List<Tile> { /* ... */ }
}
```

**Testing Strategy**:
- Property-based testing for rules
- Unit tests for each rule type
- Integration tests with Board
- Comparison with AS3 behavior

**Acceptance Criteria**: DONE — **delivered in Phase 1**, not here
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
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**tripleTriadRules.as Analysis**:
- Constants for all game rules
- Rule combinations (roulette function)
- Rule type definitions
- Two modes: FF14 and FF8 with different rule sets
- 115 lines

**Rule Constants to Migrate**:
```actionscript
// AS3 constants
RULE_OPEN, RULE_DEFAULT_OPEN, RULE_ALL_OPEN, RULE_THREE_OPEN
RULE_SUDDEN_DEATH, RULE_RANDOM, RULE_DEFAULT_ORDER
RULE_ORDER, RULE_CHAOS, RULE_REVERSE, RULE_FALLEN_ACE
RULE_SAME, RULE_SAME_WALL, RULE_PLUS, RULE_COMBO
RULE_TYPE, RULE_DEFAULT_TYPE, RULE_ASCENSION, RULE_DESCENSION, RULE_ELEMENTAL
RULE_SWAP, RULE_ROULETTE
```

**Kotlin Implementation**:
```kotlin
// Rule types as sealed classes
enum class OpenRule {
    DEFAULT_OPEN, ALL_OPEN, THREE_OPEN
}

enum class OrderRule {
    DEFAULT_ORDER, ORDER, CHAOS
}

enum class TypeRule {
    DEFAULT_TYPE, ASCENSION, DESCENSION, ELEMENTAL
}

// Game rules data class
@Serializable
data class GameRules(
    var openRule: OpenRule = OpenRule.DEFAULT_OPEN,
    var order: OrderRule = OrderRule.DEFAULT_ORDER,
    var typeRule: TypeRule = TypeRule.DEFAULT_TYPE,
    var suddenDeath: Boolean = false,
    var random: Boolean = false,
    var reverse: Boolean = false,
    var fallenAce: Boolean = false,
    var same: Boolean = false,
    var sameWall: Boolean = false,
    var plus: Boolean = false,
    var swap: Boolean = false,
    var roulette: Boolean = false
) {
    // Check if any rules are active
    fun hasSpecialRules(): Boolean = listOf(
        suddenDeath, random, reverse, fallenAce,
        same, sameWall, plus, swap, roulette
    ).any { it }
    
    // Convert to map for serialization
    fun toMap(): Map<String, Any> = mapOf(
        "openRule" to openRule.name,
        "order" to order.name,
        "typeRule" to typeRule.name,
        // ... other properties
    )
    
    companion object {
        fun fromMap(map: Map<String, Any>): GameRules { /* ... */ }
        
        // Roulette function for random rule selection
        fun roulette(mode: GameMode): GameRules {
            return when (mode) {
                GameMode.FF14 -> generateFF14Rules()
                GameMode.FF8 -> generateFF8Rules()
            }
        }
        
        private fun generateFF14Rules(): GameRules { /* ... */ }
        private fun generateFF8Rules(): GameRules { /* ... */ }
    }
}
```

**Testing Strategy**:
- Test all rule combinations
- Test roulette function generates valid rules
- Test rule activation/deactivation

**Acceptance Criteria**: DONE — the constants in Phase 1, the roulette here
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
- [ ] ~~Rules match AS3 behaviour~~ — the **draw distribution deliberately does not**. See the
  defect table above

---

### Week 10: Game State and Flow

#### Task 3.3: Implement Game State Management
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**Game State to Model**:
- Current phase (deck selection, open, order, etc.)
- Current turn
- Player states
- Board state
- Card positions
- Scores
- Active rules

**State Classes**:
```kotlin
// Game phase
@Serializable
enum class GamePhase {
    DECK_SELECTION,
    OPEN_PHASE,
    ORDER_PHASE,
    REVERSE_PHASE,
    FALLEN_ACE_PHASE,
    SWAP_PHASE,
    PILE_OU_FACE,
    STARTING,
    PLAYING,
    ENDED
}

// Turn state
//
// ⚠️ The previous version used `(currentTurn + 1) % timeline.size` over a
// 2-element timeline. With size 2 that oscillates 0,1,0,1 forever, so the match
// could never end and `currentTurn` never counted turns. In the AS3 source the
// timeline is a 10-element array of alternating colours produced by the coin
// flip, and `turn` is a MONOTONIC index into it; `endGame()` fires at turn == 10.
// See the TurnState note in 13-DATA-MODELS.md for the full mechanism.
@Serializable
data class TurnState(
    val turnIndex: Int = 0,
    val timeline: List<CardColor> = emptyList(),   // 9 entries, 0-based
    val cardsPlaced: Int = 0
) {
    val currentPlayer: CardColor? get() = timeline.getOrNull(turnIndex)
    val isComplete: Boolean get() = turnIndex >= timeline.size

    fun nextTurn(): TurnState =
        copy(turnIndex = turnIndex + 1, cardsPlaced = cardsPlaced + 1)
}

// Complete game state
@Serializable
data class GameState(
    // NOT UUID.randomUUID() — that is JVM-only and unavailable in commonMain.
    // Inject an IdGenerator, or use kotlin.uuid.Uuid. See 13-DATA-MODELS.md.
    val id: String,
    val mode: GameMode = GameMode.FF14,
    val rules: GameRules = GameRules.roulette(GameMode.FF14),
    val phase: GamePhase = GamePhase.DECK_SELECTION,
    val turn: TurnState = TurnState(),
    val board: Board = Board(),
    val bluePlayer: Player = Player(CardColor.BLUE),
    val redPlayer: Player = Player(CardColor.RED),
    val blueDeck: List<Card> = emptyList(),
    val redDeck: List<Card> = emptyList(),
    val selectedCard: Card? = null,
    val scores: Map<CardColor, Int> = mapOf(
        CardColor.BLUE to 0,
        CardColor.RED to 0
    ),
    val isGameOver: Boolean = false,
    val winner: CardColor? = null,
    // NOT System.currentTimeMillis() — JVM-only. Inject a Clock, or use
    // kotlinx-datetime: Clock.System.now().toEpochMilliseconds()
    val timestamp: Long
) {
    fun isValidMove(card: Card, tile: Tile): Boolean {
        // Check if move is valid
        return true
    }
    
    fun placeCard(card: Card, tile: Tile): GameState {
        // Create new state with card placed
        return copy(
            board = board.placeCard(card, tile, turn.currentPlayer),
            selectedCard = null
        )
    }
    
    fun nextPhase(): GameState {
        // Advance to next phase
        return copy(phase = phase.next())
    }
}
```

**State Management with ViewModel**:
```kotlin
class GameViewModel(
    private val ttoCore: TTOCore,
    private val cardRepository: CardRepository
) : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<GameEvent>()
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()
    
    fun initializeGame(mode: GameMode) {
        viewModelScope.launch {
            val cards = cardRepository.getAllCards(mode.toCollection())
            _state.value = GameState(
                mode = mode,
                rules = GameRules.roulette(mode),
                blueDeck = cards.shuffled().take(5),
                redDeck = cards.shuffled().take(5)
            )
            _events.emit(GameEvent.GameInitialized)
        }
    }
    
    fun selectCard(card: Card) {
        _state.value = state.value.copy(selectedCard = card)
    }
    
    fun placeCardOnTile(tile: Tile) {
        viewModelScope.launch {
            val currentState = state.value
            val selectedCard = currentState.selectedCard ?: return@launch
            
            if (currentState.isValidMove(selectedCard, tile)) {
                val newState = ttoCore.applyRules(
                    tile = tile,
                    color = currentState.turn.currentPlayer,
                    checking = false
                ).fold(currentState) { acc, flippedTile ->
                    acc.copy(board = acc.board.flipTile(flippedTile))
                }.copy(
                    turn = currentState.turn.nextTurn(),
                    selectedCard = null
                )
                
                _state.value = newState
                _events.emit(GameEvent.CardPlaced(selectedCard, tile))
                
                // Check for game over
                if (newState.isGameOver) {
                    _events.emit(GameEvent.GameOver(newState.winner))
                }
            }
        }
    }
}
```

**Acceptance Criteria**: DONE — **delivered in Phase 1**, and not in this shape
- [x] Game state fully modelled, as `MatchState`. The `TurnState` sketch above is right that the
  AS3 counter is monotonic and wrong about the array: the timeline is 10 entries read with a
  1-based counter, so `timeline[0]` is never used. `TurnOrder` derives the colour from the
  placement's parity instead, which is behaviour-preserving and cannot be half-fixed into swapping
  who moves first
- [x] Every transition is `MatchState -> MatchState`, and `MatchStateTest` covers them
- [ ] ~~`GamePhase`~~ — not built; see deviation 1
- [x] `GameRules` is `@Serializable`, which is what `MatchRecord` needs. **`MatchState` is not**,
  and nothing asks it to be: no save-and-resume feature exists, the original could not do it
  either, and serialising a board mid-match is a Phase 5 question about what the wire format is
- [ ] ~~ViewModel~~ — there is no ViewModel and no `GameEvent` bus. `MatchScreen` holds one
  `var state` and calls `state.play(card, position)`; the sounds a placement makes are a pure
  function of the resulting state, asserted through the real UI by `MatchAudioTest`. A
  `MutableSharedFlow<GameEvent>` in front of that would be a second source of truth for something
  already derivable

---

#### Task 3.4: Create Game Flow System
**Owner**: Tech Lead + Senior Kotlin Devs | **Duration**: 4 days | **Priority**: CRITICAL

**BaseMatchScreen.as Analysis**:
- Base class for all match screens
- Manages game flow through phases
- Handles card placement and rules
- Manages turn system
- 447 lines
- Complex game flow with setTimeout for phases

**Game Flow to Implement**:
1. Deck Selection Phase
2. Open Phase (for Open rules)
3. Order Phase (for Order/Chaos rules)
4. Reverse Phase (for Reverse rule)
5. Fallen Ace Phase (for Fallen Ace rule)
6. Swap Phase (for Swap rule)
7. Pile Ou Face (coin flip for tiebreakers)
8. Main Game Play
9. End Game

**Flow Implementation**:
```kotlin
class GameFlowManager(
    private val viewModel: GameViewModel,
    private val scope: CoroutineScope
) {
    private var flowJob: Job? = null
    
    fun startGame() {
        flowJob?.cancel()
        flowJob = scope.launch {
            // ⚠️ The previous version of this function used `when { ... }` blocks to
            // run the rule-announcement phases. A subject-less `when` executes only
            // the FIRST matching branch, so with e.g. both Reverse and Swap active
            // only the Reverse phase would ever run — silently skipping the Swap
            // phase, which actually exchanges cards between the players' hands and
            // is therefore game-affecting, not merely cosmetic.
            //
            // These phases are INDEPENDENT and sequential. Use separate `if`s.
            deckSelectionPhase()

            val rules = viewModel.state.value.rules

            if (rules.openRule != OpenRule.DEFAULT_OPEN) openPhase()
            if (rules.order != OrderRule.DEFAULT_ORDER) orderPhase()
            if (rules.reverse) reversePhase()
            if (rules.fallenAce) fallenAcePhase()
            if (rules.swap) swapPhase()

            // Coin flip decides the turn order and builds the timeline.
            pileOuFacePhase()

            letsGetStarted()

            // Main game loop. The end condition is the turn counter reaching the
            // end of the timeline (9 placements) — NOT a "board full" check.
            // See the TurnState note in 13-DATA-MODELS.md.
            while (!viewModel.state.value.turn.isComplete) {
                awaitTurn()
            }

            endGame()
        }
    }
    
    private suspend fun deckSelectionPhase() {
        viewModel.updatePhase(GamePhase.DECK_SELECTION)
        // Wait for deck selection
        delay(PHASE_DELAY_MS)
    }
    
    private suspend fun openPhase() {
        viewModel.updatePhase(GamePhase.OPEN_PHASE)
        // Implement open rule logic
        val openTiles = calculateOpenTiles()
        viewModel.openTiles(openTiles)
        delay(PHASE_DELAY_MS)
    }
    
    private suspend fun orderPhase() {
        viewModel.updatePhase(GamePhase.ORDER_PHASE)
        // Implement order/chaos rule logic
        val timeline = determineOrderTimeline()
        viewModel.updateTimeline(timeline)
        delay(PHASE_DELAY_MS)
    }
    
    // ... other phase implementations
    
    private suspend fun letsGetStarted() {
        viewModel.updatePhase(GamePhase.STARTING)
        viewModel.startGame()
        delay(PHASE_DELAY_MS)
        viewModel.updatePhase(GamePhase.PLAYING)
    }
    
    private suspend fun awaitTurn() {
        // Wait for player to make move
        // Or handle AI move
        // Check for timeout
    }
    
    private suspend fun endGame() {
        viewModel.updatePhase(GamePhase.ENDED)
        val winner = determineWinner()
        viewModel.endGame(winner)
    }
    
    fun stop() {
        flowJob?.cancel()
        flowJob = null
    }
}
```

**Phase Transitions**:
```kotlin
fun GamePhase.next(): GamePhase = when (this) {
    GamePhase.DECK_SELECTION -> GamePhase.OPEN_PHASE
    GamePhase.OPEN_PHASE -> GamePhase.ORDER_PHASE
    GamePhase.ORDER_PHASE -> GamePhase.REVERSE_PHASE
    GamePhase.REVERSE_PHASE -> GamePhase.FALLEN_ACE_PHASE
    GamePhase.FALLEN_ACE_PHASE -> GamePhase.SWAP_PHASE
    GamePhase.SWAP_PHASE -> GamePhase.PILE_OU_FACE
    GamePhase.PILE_OU_FACE -> GamePhase.STARTING
    GamePhase.STARTING -> GamePhase.PLAYING
    GamePhase.PLAYING -> this // Stay in playing until game over
    GamePhase.ENDED -> this // Game is over
}
```

**Acceptance Criteria**: DONE, as data rather than as a coroutine
- [x] Every step of the chain is implemented: Random (`randomHand`), Open (`HandVisibility`), Order
  and Chaos (`MatchState.playableCards`, Phase 1), Reverse and Fallen Ace (the engine, Phase 1),
  Swap (`swap`), the coin flip (`CoinFlip`), and the Sudden Death rematch (`prepareRematch`)
- [x] The order is the source's, and **every applicable step runs** — the warning above about a
  subject-less `when` running only the first branch is why `introSteps` is a `buildList` of
  independent `if`s, and `MatchSetupTest.everyApplicableStepIsAnnouncedInSourceOrder` pins it
- [ ] ~~All game phases / phase transitions~~ — no phase machine; see deviation 1
- [x] Interruptible by construction rather than by `Job.cancel()`: there is no flow to interrupt.
  `prepare` returns a value, so abandoning a match is dropping a reference
- [x] The opponent that this task's `awaitTurn()` left as a comment is `MatchAi`
- [ ] **Not wired to the UI** — see the section above

---

### Week 11-12: Testing and Validation

#### Task 3.5: Core Logic Testing
**Owner**: QA Engineer + Team | **Duration**: 5 days | **Priority**: CRITICAL

**Testing Strategy**:
1. **Unit Tests**: Test each function in isolation
2. **Integration Tests**: Test component interactions
3. **Property-Based Tests**: Test rule invariants
4. **Comparison Tests**: Compare with AS3 behavior
5. **Edge Case Tests**: Test unusual scenarios

**Test Coverage Targets**:
- TTOCore: >95%
- TripleTriadRules: 100%
- GameState: >90%
- GameFlow: >90%

**Test Types**:
```kotlin
// Property-based tests for rules
class TTOCorePropertyTest : BaseTest() {
    init {
        test("basic rule: card with higher power flips adjacent") {
            // Test that when a card has higher power on adjacent side,
            // the adjacent card is flipped
        }
        
        test("combo rule: flipping one card can trigger chain reaction") {
            // Test that flipping one card can cause a chain of flips
        }
        
        test("reverse rule: adjacent cards belong to same player") {
            // Test reverse rule logic
        }
    }
}

// Comparison tests with AS3
class TTOCoreComparisonTest : BaseTest() {
    // These tests would use known AS3 behavior as oracle
    init {
        test("basicRule matches AS3 for known board state") {
            // Set up specific board state
            // Call basicRule
            // Compare result with known AS3 output
        }
    }
}

// Edge case tests
class TTOCoreEdgeCaseTest : BaseTest() {
    init {
        test("flipping card with equal power does nothing") { /* ... */ }
        test("flipping card on empty tile does nothing") { /* ... */ }
        test("flipping with no valid moves returns empty") { /* ... */ }
    }
}
```

**Acceptance Criteria**: DONE, without a property-based framework
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
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: HIGH

**Optimization Areas**:
- Card flipping logic (most performance-critical)
- Combo rule recursion
- Board state updates
- Rule application

**Optimization Techniques**:
- Use inline functions for hot paths
- Minimize object allocation
- Use primitive types where possible
- Cache computed values
- Optimize data structures

**Performance Metrics**:
- Card flip calculation: < 1ms
- Combo chain (max 5): < 5ms
- Full rule application: < 10ms
- Board state update: < 2ms

**Acceptance Criteria**: DONE, and no optimisation was needed
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

## 📊 Phase 3 Deliverables

### Code Deliverables
- [x] `TTOCore.kt` → `model/RulesEngine.kt` + `model/Power.kt` + `model/Board.kt` (Phase 1)
- [x] `TripleTriadRules.kt` → `model/GameRules.kt` (Phase 1) + `model/Roulette.kt` (new)
- [x] Game state classes → `model/MatchState.kt` + `model/Match.kt` (Phase 1)
- [ ] ~~Game flow manager~~ → `model/MatchSetup.kt`, as data rather than a coroutine; deviation 1
- [x] The opponent → `model/MatchAi.kt` (new, and not in the plan's task list)
- [x] All core logic tests
- [ ] ~~Performance optimizations~~ — measured, and none needed. See Task 3.6

### Documentation Deliverables
- [x] Core logic migration notes — this section, plus
  [game-rules.md](../../analysis/game-rules.md) § 15b
- [x] Rule implementation documentation — the KDoc carries it, with the AS3 line reference for
  every rule and a written decision at each departure. `RulesEngineOptions` and `MatchAiOptions`
  document their own deviations at the point where a caller can reverse them
- [x] Performance figures — `EnginePerformanceTest`. There is no optimisation guide because there
  is no optimisation

---

## ✅ Phase 3 Completion Criteria

### Technical
- [x] All core logic migrated
- [ ] ~~All rules work identically to AS3~~ — five deliberate departures, each with a written
  reason and, where reversible, a switch. See § What was built
- [x] Game flow operational, as values rather than a manager
- [x] Performance meets requirements, by two to three orders of magnitude

### Testing
- [x] All core logic tests pass
- [x] Coverage 97.3% line / 89.5% branch, gated
- [x] All rules validated against the source; there is no runnable original to diff against

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval

**Nothing here is reviewed or approved**, as in Phases 1 and 2. The work is done and self-verified;
sign-off is a separate step and has not happened.

**Carried into Phase 4:** wiring `Roulette`, `HandVisibility`, `MatchIntroStep` and `MatchAi` to a
player, which needs an opponent-selection screen and a rework of the UI test suite. See the section
above.

---

## 🎯 Next Phase: Phase 4 - UI Layer

**Phase 4 Focus** (Weeks 13-20):
- Migrate 22 screens + 9 embedded components
- Create Compose components for all UI elements
- Implement animations
- Theme system
- Navigation

**Prerequisites**: All Phase 3 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 2**: [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: IMPLEMENTED — 2026-08-02. See § What was built for the five deviations.*
