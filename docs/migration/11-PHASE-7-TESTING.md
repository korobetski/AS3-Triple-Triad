# Phase 7: Testing - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 7 - Testing
- **Duration**: 4 weeks (Weeks 27-30)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phases 1-6

---

## 🎯 Phase Overview

### Purpose
Comprehensive testing of all migrated components to ensure correctness, performance, and quality before release.

### Key Objectives
1. Unit testing for all components
2. Integration testing for system interactions
3. UI testing for all screens
4. Performance testing and optimization
5. Bug fixing and regression testing
6. User acceptance testing

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 27 | Unit tests, Core logic tests | QA + Team |
| Week 28 | Integration tests, UI tests | QA + Team |
| Week 29 | Performance tests, Stress tests | QA + Tech Lead |
| Week 30 | UAT, Bug fixing, Final validation | QA + All |

---

## 📝 Testing Strategy

### Test Pyramid
```
          /\
         /  \  UI Tests (10%)
        /    \
       /------\ Integration Tests (20%)
      /        \
     /          \ Unit Tests (70%)
    /____________\
```

### Test Coverage Targets
- **Overall**: >80%
- **Core Logic**: >95%
- **Data Layer**: >90%
- **Network**: >90%
- **UI Layer**: >80%
- **Animations**: 100%

---

## 🎯 Tasks by Week

### Week 27: Unit Testing

#### Task 7.1: Complete Unit Test Coverage
**Owner**: QA + Team | **Duration**: 5 days | **Priority**: CRITICAL

**Components to Test**:
- All data models (Card, Tile, Board, etc.)
- All utilities (Tools, CryptoHelper, etc.)
- All repositories
- TTOCore and all rules
- Game state management
- Network message handling

**Test Examples**:
```kotlin
// CardTest.kt
class CardTest : BaseTest() {
    init {
        test("Card power comparison") {
            val card1 = Card(id = 1u, power = listOf("6", "6", "6", "6"), ...)
            val card2 = Card(id = 2u, power = listOf("4", "4", "4", "4"), ...)
            
            card1.topPow shouldBe 6u
            card1.canFlipAgainst(card2, Direction.TOP) shouldBe true
        }
    }
}

// TTOCoreTest.kt
class TTOCoreTest : BaseTest() {
    private lateinit var core: TTOCore
    
    @BeforeTest
    fun setup() {
        core = TTOCore()
    }
    
    @Test
    fun `basicRule flips adjacent card with lower power`() {
        val board = createTestBoard()
        val result = core.basicRule(board[0], CardColor.BLUE)
        result shouldHaveSize 1
    }
    
    @Test
    fun `comboRule triggers chain reaction`() {
        val board = createComboBoard()
        val result = core.comboRule(board[0], listOf(), 0u, CardColor.BLUE, mutableListOf())
        result shouldHaveSizeGreaterThan 1
    }
}

// SocketManagerTest.kt
class SocketManagerTest : BaseTest() {
    @Test
    fun `connect updates state correctly`() = runTest {
        val manager = createTestSocketManager()
        val states = mutableListOf<ConnectionState>()
        manager.connectionState.onEach { states.add(it) }.launchIn(this)
        
        manager.connect()
        
        states shouldContain ConnectionState.Connected
    }
}
```

**Test Utilities**:
```kotlin
// TestDataFactory.kt
object TestDataFactory {
    fun createTestCard(id: UInt = 1u, power: List<String> = listOf("5", "5", "5", "5")): Card {
        return Card(id = id, collection = CardCollection.FF14, nameKey = "test", power = power, rarity = 1, type = null)
    }
    
    fun createTestBoard(): Board {
        val board = Board()
        board.placeCard(createTestCard(1), board[0], CardColor.BLUE)
        board.placeCard(createTestCard(2), board[1], CardColor.RED)
        return board
    }
    
    fun createTestGameState(): GameState {
        return GameState(
            mode = GameMode.FF14,
            rules = GameRules(),
            blueDeck = listOf(createTestCard(1), createTestCard(2))
        )
    }
}
```

**Acceptance Criteria**:
- [ ] Unit test coverage >80% overall
- [ ] All critical components tested
- [ ] Tests run successfully

---

#### Task 7.2: Core Logic Validation
**Owner**: QA + Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

**Validation Approach**:
1. **Property-Based Testing**: Verify rule invariants
2. **Comparison Testing**: Compare with AS3 behavior
3. **Edge Case Testing**: Test unusual scenarios
4. **Stress Testing**: Test complex scenarios

**Property-Based Tests**:
```kotlin
// TTOCorePropertyTest.kt
class TTOCorePropertyTest : BaseTest() {
    init {
        test("flipping card always results in valid board state") {
            // Generate random valid board
            // Apply random valid flip
            // Assert board state is valid
        }
        
        test("combo chain never exceeds board size") {
            // Test that combo chains don't infinite loop
            // Max chain length = board size
        }
        
        test("same rule only flips cards with same value") {
            // Verify same rule logic
        }
    }
}
```

**Comparison Tests**:
- Use known AS3 game states as test oracles
- Verify Kotlin implementation produces same results
- Test all 17 rules against AS3 behavior

**Acceptance Criteria**:
- [ ] All core logic validated
- [ ] Behavior matches AS3
- [ ] No regressions

---

### Week 28: Integration Testing

#### Task 7.3: Integration Tests
**Owner**: QA + Team | **Duration**: 4 days | **Priority**: CRITICAL

**Integration Areas**:
1. **Game Flow**: Test complete game from start to finish
2. **Network + Game**: Test multiplayer game flow
3. **Data + UI**: Test data loading and display
4. **Rules + Board**: Test rule application on board

**Integration Test Examples**:
```kotlin
// GameFlowIntegrationTest.kt
class GameFlowIntegrationTest : BaseTest() {
    @Test
    fun `complete game flow works`() = runTest {
        val viewModel = GameViewModel()
        
        // Initialize game
        viewModel.initializeGame(GameMode.FF14)
        
        // Select deck
        viewModel.selectDeck(listOf(1u, 2u, 3u, 4u, 5u))
        
        // Place cards
        repeat(5) {
            viewModel.selectCard(testDeck[it])
            viewModel.placeCardOnTile(testBoard[it])
        }
        
        // Verify game state
        viewModel.state.value.cardsPlaced shouldBe 5
    }
}

// NetworkIntegrationTest.kt
class NetworkIntegrationTest : BaseTest() {
    @Test
    fun `network messages update game state`() = runTest {
        val socketManager = createTestSocketManager()
        val viewModel = GameViewModel(socketManager)
        
        // Simulate network message
        socketManager.handleMessage(SocketMessage.CardMove(
            gameId = "test",
            player = "opponent",
            cardIndex = 0,
            position = 0
        ))
        
        // Verify game state updated
    }
}
```

**Acceptance Criteria**:
- [ ] All integration tests pass
- [ ] Component interactions work correctly
- [ ] Data flows correctly through layers

---

#### Task 7.4: UI Testing
**Owner**: QA + Team | **Duration**: 3 days | **Priority**: HIGH

**UI Test Areas**:
1. All 32 screen/panel classes (22 navigable + 9 embedded + 1 base)
2. All common components
3. Navigation between screens
4. User interactions
5. Responsive behavior

**UI Test Examples**:
```kotlin
// MenuScreenTest.kt
class MenuScreenTest : BaseTest() {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun menuScreen_displaysAllButtons() {
        composeTestRule.setContent {
            MenuScreen(navController = mockNavController)
        }
        
        composeTestRule.onNodeWithText("New Game").assertExists()
        composeTestRule.onNodeWithText("PvP").assertExists()
        composeTestRule.onNodeWithText("Decks").assertExists()
        composeTestRule.onNodeWithText("Settings").assertExists()
    }
    
    @Test
    fun menuScreen_newGameButtonNavigates() {
        val navController = mockNavController()
        
        composeTestRule.setContent {
            MenuScreen(navController = navController)
        }
        
        composeTestRule.onNodeWithText("New Game").performClick()
        
        verify { navController.navigate("new_game") }
    }
}

// BoardComponentTest.kt
class BoardComponentTest : BaseTest() {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun board_displaysAllTiles() {
        val board = Board()
        
        composeTestRule.setContent {
            BoardComponent(board = board, onTileClick = {})
        }
        
        // Verify 9 tiles are displayed
        composeTestRule.onAllNodesWithContentDescription("Tile").assertCountEquals(9)
    }
    
    @Test
    fun tile_clickCallsHandler() {
        val board = Board()
        var clickedTile: Tile? = null
        
        composeTestRule.setContent {
            BoardComponent(
                board = board,
                onTileClick = { tile -> clickedTile = tile }
            )
        }
        
        composeTestRule.onNodeWithContentDescription("Tile 0").performClick()
        
        clickedTile shouldBe board[0]
    }
}

// DragDropTest.kt
class DragDropTest : BaseTest() {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun card_canBeDraggedToTile() {
        val card = TestDataFactory.createTestCard()
        val tile = Tile(id = 0, row = 0, col = 0)
        var droppedCard: Card? = null
        
        composeTestRule.setContent {
            Box {
                DraggableCard(
                    card = card,
                    onDragStart = {},
                    onDragEnd = {}
                )
                DropTargetTile(
                    tile = tile,
                    onCardDrop = { c -> droppedCard = c }
                )
            }
        }
        
        // Perform drag and drop
        // Verify card dropped on tile
        droppedCard shouldBe card
    }
}
```

**Acceptance Criteria**:
- [ ] All UI tests pass
- [ ] All screens tested
- [ ] All components tested
- [ ] Navigation tested

---

### Week 29: Performance Testing

#### Task 7.5: Performance Testing
**Owner**: QA + Tech Lead | **Duration**: 4 days | **Priority**: CRITICAL

**Performance Metrics**:
- FPS: >60 (target: >90 - only meaningful on 90/120 Hz panels; on a 60 Hz device
  60 FPS IS the ceiling, so state the target per refresh rate)
- Frame time: <16.67ms (60fps), <11.11ms (90fps)
- Memory usage: <100MB
- Launch time: <2s
- App size: Android <50MB, iOS <100MB

**Performance Tests**:
```kotlin
// PerformanceTest.kt
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @Test
    fun cardFlipAnimation_performance() {
        val card = TestDataFactory.createTestCard()
        
        benchmarkRule.measureRepeated {
            // Run card flip animation
            // Measure time
        }
        
        // Assert average time < 16ms
    }
    
    @Test
    fun boardRender_performance() {
        val board = TestDataFactory.createFullBoard()
        
        benchmarkRule.measureRepeated {
            // Render board with all cards
        }
        
        // Assert average time < 16ms
    }
    
    @Test
    fun comboAnimation_performance() {
        val board = TestDataFactory.createComboBoard()
        val core = TTOCore()
        
        benchmarkRule.measureRepeated {
            // Run combo rule on board
            core.comboRule(board[0], listOf(), 0u, CardColor.BLUE, mutableListOf())
        }
        
        // Assert average time < 10ms
    }
}
```

**Performance Monitoring**:
```kotlin
// PerformanceMonitor.kt
class PerformanceMonitor {
    private val frameTimes = mutableListOf<Long>()
    private val memoryUsage = mutableListOf<Long>()
    
    fun recordFrame(time: Long) {
        frameTimes.add(time)
        if (frameTimes.size > 100) frameTimes.removeAt(0)
    }
    
    fun recordMemory(usage: Long) {
        memoryUsage.add(usage)
        if (memoryUsage.size > 100) memoryUsage.removeAt(0)
    }
    
    // WARNING: the previous formula was wrong by a factor of 1e6. With frame
    // times in nanoseconds, `average() / 1_000_000` is milliseconds, so dividing
    // 1e9 by milliseconds yields nonsense. Either 1e9/ns or 1000/ms.
    fun getAverageFPS(): Float {
        if (frameTimes.isEmpty()) return 0f
        val avgNanos = frameTimes.average()
        return (1_000_000_000.0 / avgNanos).toFloat()
    }
    
    fun getAverageMemory(): Long = memoryUsage.average().toLong()
    
    fun getFrameTimePercentiles(): Map<String, Float> {
        if (frameTimes.isEmpty()) return emptyMap()
        val sorted = frameTimes.sorted()
        return mapOf(
            "p50" to sorted[sorted.size / 2] / 1_000_000f,
            "p90" to sorted[(sorted.size * 0.9).toInt()] / 1_000_000f,
            "p99" to sorted[(sorted.size * 0.99).toInt()] / 1_000_000f
        )
    }
}
```

**Acceptance Criteria**:
- [ ] All performance metrics met
- [ ] No performance regressions
- [ ] Performance tests pass

---

#### Task 7.6: Stress Testing
**Owner**: QA | **Duration**: 2 days | **Priority**: HIGH

**Stress Test Scenarios**:
- 100 concurrent animations
- 50 rapid card placements
- Complex combo chain (all 9 cards)
- Multiple network messages simultaneously
- Low memory conditions
- Slow network conditions

**Stress Test Implementation**:
```kotlin
// StressTest.kt
class StressTest : BaseTest() {
    @Test
    fun concurrentAnimations_stressTest() = runTest {
        val animations = (1..100).map { i ->
            async {
                // Run animation i
            }
        }
        
        animations.awaitAll()
        
        // Verify no crashes
        // Verify all animations completed
    }
    
    @Test
    fun rapidCardPlacement_stressTest() = runTest {
        val viewModel = GameViewModel()
        val cards = (1..50).map { TestDataFactory.createTestCard(it.toUInt()) }
        
        cards.forEach { card ->
            viewModel.selectCard(card)
            viewModel.placeCardOnTile(Tile(id = 0, row = 0, col = 0))
        }
        
        // Verify no errors
        // Verify game state is valid
    }
}
```

**Acceptance Criteria**:
- [ ] Stress tests pass
- [ ] No crashes under load
- [ ] Graceful degradation

---

### Week 30: Final Testing

#### Task 7.7: Bug Fixing
**Owner**: QA + All | **Duration**: 5 days | **Priority**: CRITICAL

**Bug Management**:
1. **Triage**: Prioritize bugs by severity
2. **Fix**: Developers fix bugs
3. **Verify**: QA verifies fixes
4. **Regression**: Test for regressions

**Bug Severity Levels**:
| Severity | Description | Target Resolution |
|----------|-------------|-------------------|
| S1 | Critical (crash, data loss) | Immediate |
| S2 | Major (broken feature) | Within 1 day |
| S3 | Minor (visual issue) | Within 1 week |
| S4 | Cosmetic | Before release |

**Bug Tracking**:
- Use GitHub Issues with labels
- Track in project board
- Daily bug triage meetings

**Acceptance Criteria**:
- [ ] No S1/S2 bugs open
- [ ] All S3/S4 bugs addressed or deferred
- [ ] No regressions introduced

---

#### Task 7.8: User Acceptance Testing (UAT)
**Owner**: QA | **Duration**: 3 days | **Priority**: HIGH

**UAT Process**:
1. **Recruit**: 10-20 test users
2. **Prepare**: Create test builds and scenarios
3. **Execute**: Users test the app
4. **Collect**: Gather feedback and bug reports
5. **Iterate**: Fix critical issues
6. **Validate**: Final testing

**Test Scenarios for UAT**:
- Complete PvE game
- Complete PvP game (with test partner)
- Deck management
- Collection viewing
- Settings configuration
- All rule types
- Special animations

**Feedback Questions**:
- How does the app feel?
- Are there any crashes or freezes?
- Do animations feel smooth?
- Does the game play correctly?
- Any visual issues?
- Any usability issues?

**Acceptance Criteria**:
- [ ] Positive feedback from >80% of testers
- [ ] All critical issues addressed
- [ ] User satisfaction score >4.5/5

---

#### Task 7.9: Final Validation
**Owner**: QA + Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

**Final Checklist**:
- [ ] All features implemented
- [ ] All tests pass
- [ ] Performance meets targets
- [ ] No critical bugs
- [ ] App works on all target devices
- [ ] Localization works
- [ ] Offline mode works
- [ ] Network mode works
- [ ] All animations work
- [ ] Sound works

**Release Checklist**:
```markdown
# Release Checklist

## Code Quality
- [ ] All tests pass
- [ ] Code coverage >80%
- [ ] No lint errors
- [ ] No warnings
- [ ] Code review complete

## Functionality
- [ ] All features working
- [ ] No crashes
- [ ] No data loss
- [ ] Game rules correct
- [ ] Network sync works

## Performance
- [ ] FPS >60 on mid-range devices
- [ ] Memory <100MB
- [ ] Launch <2s
- [ ] App size within limits

## Compatibility
- [ ] Android minSdk 24 supported (matching the PoC; an earlier revision of this
      checklist said API 26 while the PoC targets 24 - pick one and align both)
- [ ] iOS 15+ supported (Phase 0 states "iOS 17+" for the PoC simulator; that is a
      test-environment choice, not the deployment target - state both explicitly)
- [ ] All target devices tested
- [ ] Landscape orientation tested. NOTE: the AS3 original is
      `<aspectRatio>landscape</aspectRatio>` + `<fullScreen>true</fullScreen>`
      (application.xml). An earlier revision claimed "portrait only", which
      contradicts the source. A 3x3 board plus two player panels is a landscape
      layout; decide deliberately whether to add a portrait layout.

## Localization
- [ ] German (de_DE) works
- [ ] English (en_US) works
- [ ] French (fr_FR) works
- [ ] Japanese (ja_JA) works - requires a CJK-capable font; Eurostile has none
- [ ] All strings translated across all 4 locales
- [ ] RTL not applicable (no RTL locale is supported)

## Security
- [ ] No hardcoded secrets
- [ ] Data encrypted
- [ ] Network secure
- [ ] Permissions correct
```

**Acceptance Criteria**:
- [ ] All checklist items complete
- [ ] Final approval from Tech Lead
- [ ] Final approval from QA

---

## 📊 Phase 7 Deliverables

### Code Deliverables
- [ ] Complete test suite
- [ ] Performance monitoring tools
- [ ] Stress test suite
- [ ] Bug fixes

### Documentation Deliverables
- [ ] Test plan document
- [ ] Test cases
- [ ] Performance report
- [ ] Bug reports and fixes
- [ ] UAT report

---

## ✅ Phase 7 Completion Criteria

### Testing
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] All UI tests pass
- [ ] All performance tests pass
- [ ] All stress tests pass
- [ ] Test coverage >80%

### Quality
- [ ] No critical bugs
- [ ] No regressions
- [ ] Performance meets targets
- [ ] UAT complete and positive

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval
- [ ] All team members confirm readiness

---

## 🎯 Next Phase: Phase 8 - Release

**Phase 8 Focus** (Weeks 31-32):
- Beta release preparation
- App store submissions
- Deployment
- Post-release monitoring

**Prerequisites**: All Phase 7 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Phase 6**: [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md)
- **Phase 8**: [12-PHASE-8-RELEASE.md](./12-PHASE-8-RELEASE.md)
- **Testing Guide**: [17-TESTING-GUIDE.md](./17-TESTING-GUIDE.md)

---
