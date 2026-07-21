# Phase 3: Core Logic - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 3 - Core Logic
- **Duration**: 4 weeks (Weeks 9-12)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
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

**TTOCore.as Analysis** (from 02-CURRENT-SYSTEM-ANALYSIS.md):
- Primary rules engine for Triple Triad
- Handles card flipping logic based on game rules
- Manages special rule combinations
- Coordinates animations with the screen
- ~396 lines of complex logic
- Multiple rule combinations
- Recursive combo logic
- State management across multiple cards

**Key Methods to Migrate**:
- `applyRules(tile:Tile, color:String, checking:Boolean):uint`
- `basicRule(tile:Tile, COLOR:String):Array`
- `specialRule(tile:Tile, COLOR:String):Array`
- `comboRule(tile:Tile, enqueue:Array, bounce:uint, COLOR:String, tileComboted:Array):Array`
- `animate(tile:Tile, color:String):void`

**Kotlin Implementation Strategy**:
```kotlin
class TTOCore {
    private val rules = TripleTriadRules()
    
    // Main entry point
    fun applyRules(
        tile: Tile,
        color: CardColor,
        checking: Boolean = false
    ): List<Tile> {
        val result = mutableListOf<Tile>()
        
        // Basic rule: flip adjacent cards with lower power
        result.addAll(basicRule(tile, color))
        
        // Special rules
        if (rules.same) {
            result.addAll(sameRule(tile, color))
        }
        if (rules.plus) {
            result.addAll(plusRule(tile, color))
        }
        if (rules.combo && !checking) {
            result.addAll(comboRule(tile, result, 0u, color, mutableListOf()))
        }
        
        return result.distinct()
    }
    
    private fun basicRule(tile: Tile, color: CardColor): List<Tile> {
        val result = mutableListOf<Tile>()
        val card = tile.card ?: return result
        
        // Check all adjacent tiles
        for (adjacent in tile.getAdjacentTiles()) {
            if (canFlip(tile, adjacent, color)) {
                result.add(adjacent)
            }
        }
        
        return result
    }
    
    private fun canFlip(source: Tile, target: Tile, color: CardColor): Boolean {
        val sourceCard = source.card ?: return false
        val targetCard = target.card ?: return false
        
        // Must be opponent's card
        if (target.color != color.opponent()) return false
        
        // Check all adjacent sides
        val directions = listOf(
            Direction.TOP to { s: Tile, t: Tile -> s.topPow > t.bottomPow },
            Direction.RIGHT to { s: Tile, t: Tile -> s.rightPow > t.leftPow },
            Direction.BOTTOM to { s: Tile, t: Tile -> s.bottomPow > t.topPow },
            Direction.LEFT to { s: Tile, t: Tile -> s.leftPow > t.rightPow }
        )
        
        return directions.any { (_, comparator) -> comparator(source, target) }
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

**Acceptance Criteria**:
- [ ] All TTOCore methods migrated
- [ ] All rules work identically to AS3
- [ ] Unit tests cover all code paths
- [ ] Performance meets requirements

---

#### Task 3.2: Migrate tripleTriadRules.as
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**tripleTriadRules.as Analysis**:
- Constants for all game rules
- Rule combinations (roulette function)
- Rule type definitions
- Two modes: FF14 and FF8 with different rule sets
- ~116 lines

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

**Acceptance Criteria**:
- [ ] All rule types defined
- [ ] Roulette function works correctly
- [ ] Rules match AS3 behavior

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
@Serializable
data class TurnState(
    val currentTurn: Int = 0,
    val currentPlayer: CardColor = CardColor.BLUE,
    val timeline: List<CardColor> = listOf(CardColor.BLUE, CardColor.RED),
    val cardsPlaced: Int = 0,
    val isOpponentTurn: Boolean = false
) {
    fun nextTurn(): TurnState {
        val nextIndex = (currentTurn + 1) % timeline.size
        return copy(
            currentTurn = nextIndex,
            currentPlayer = timeline[nextIndex],
            cardsPlaced = cardsPlaced + 1
        )
    }
}

// Complete game state
@Serializable
data class GameState(
    val id: String = UUID.randomUUID().toString(),
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
    val timestamp: Long = System.currentTimeMillis()
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

**Acceptance Criteria**:
- [ ] Game state fully modeled
- [ ] State transitions work correctly
- [ ] State is serializable
- [ ] ViewModel integrates with state

---

#### Task 3.4: Create Game Flow System
**Owner**: Tech Lead + Senior Kotlin Devs | **Duration**: 4 days | **Priority**: CRITICAL

**BaseMatchScreen.as Analysis**:
- Base class for all match screens
- Manages game flow through phases
- Handles card placement and rules
- Manages turn system
- ~448 lines
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
            // Deck selection phase
            deckSelectionPhase()
            
            // Rule-specific phases
            when {
                viewModel.state.value.rules.openRule != OpenRule.DEFAULT_OPEN -> {
                    openPhase()
                }
                viewModel.state.value.rules.order != OrderRule.DEFAULT_ORDER -> {
                    orderPhase()
                }
            }
            
            when {
                viewModel.state.value.rules.reverse -> reversePhase()
                viewModel.state.value.rules.fallenAce -> fallenAcePhase()
                viewModel.state.value.rules.swap -> swapPhase()
            }
            
            // Start main game
            letsGetStarted()
            
            // Main game loop
            while (!viewModel.state.value.isGameOver) {
                awaitTurn()
            }
            
            // End game
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

**Acceptance Criteria**:
- [ ] All game phases implemented
- [ ] Phase transitions work correctly
- [ ] Flow matches AS3 behavior
- [ ] Can be interrupted/cancelled

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

**Acceptance Criteria**:
- [ ] All core logic tests pass
- [ ] Test coverage meets targets
- [ ] All rules validated against AS3 behavior

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

**Acceptance Criteria**:
- [ ] All performance metrics met
- [ ] No performance regressions
- [ ] Memory usage controlled

---

## 📊 Phase 3 Deliverables

### Code Deliverables
- [ ] TTOCore.kt implementation
- [ ] TripleTriadRules.kt implementation
- [ ] Game state classes
- [ ] Game flow manager
- [ ] All core logic tests
- [ ] Performance optimizations

### Documentation Deliverables
- [ ] Core logic migration notes
- [ ] Rule implementation documentation
- [ ] Performance optimization guide

---

## ✅ Phase 3 Completion Criteria

### Technical
- [ ] All core logic migrated
- [ ] All rules work identically to AS3
- [ ] Game flow system operational
- [ ] Performance meets requirements

### Testing
- [ ] All core logic tests pass
- [ ] Test coverage >90% for core logic
- [ ] All rules validated

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 4 - UI Layer

**Phase 4 Focus** (Weeks 13-20):
- Migrate all 28 screens
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
*Status: PLANNING COMPLETE*
