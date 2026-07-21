# Phase 6: Animations - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 6 - Animations
- **Duration**: 3 weeks (Weeks 24-26)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phases 1-5

---

## 🎯 Phase Overview

### Purpose
Complete and polish all 25+ animations from the AS3 codebase, ensuring smooth performance and visual fidelity on mobile devices.

### Key Objectives
1. Complete any remaining animations
2. Polish existing animations
3. Performance optimization
4. Testing and validation

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 24 | Remaining animations | UI/UX + Team |
| Week 25 | Polish and optimization | UI/UX + Team |
| Week 26 | Testing and validation | QA + Team |

---

## 📝 Animations List

**From `sources/src/tto/anims/` (25 animations)**:

### Game Flow Animations
- **StartAnim** - Game start animation
- **AllOpenAnim** - All cards revealed (Open rule)
- **DrawAnim** - Game draw/tie

### Turn Animations
- **BlueTurnAnim** - Blue player's turn indicator
- **RedTurnAnim** - Red player's turn indicator

### Result Animations
- **BlueWinAnim** - Blue player wins
- **RedWinAnim** - Red player wins

### Rule Animations
- **AscensionAnim** - Ascension rule activation
- **ChaosAnim** - Chaos rule activation
- **ComboAnim** - Combo chain animation
- **DescensionAnim** - Descension rule activation
- **FallenAceAnim** - Fallen Ace rule activation
- **OrderAnim** - Order rule activation
- **PlusAnim** - Plus rule activation
- **RandomAnim** - Random rule activation
- **ReverseAnim** - Reverse rule activation
- **SameAnim** - Same rule activation
- **SuddenDeathAnim** - Sudden Death rule activation
- **SwapAnim** - Swap rule activation
- **ElementalAnim** - Elemental rule activation

### Special Animations
- **Mogu** - Special effect
- **PileOuFace** - Coin flip for tiebreaker
- **TalkAnim** - Chat/talk animation

---

## 🎨 Animation Implementation Guide

### Animation Patterns

#### 1. Card Flip Animation
**Used by**: Card placement, rule activation, combo chains

```kotlin
@Composable
fun CardFlipAnimation(
    card: Card,
    isFlipping: Boolean,
    duration: Int = 400,
    onComplete: () -> Unit = {}
) {
    val rotationY by animateFloatAsState(
        targetValue = if (isFlipping) 180f else 0f,
        animationSpec = tween(
            durationMillis = duration,
            easing = LinearOutSlowInEasing
        ),
        finishedListener = { onComplete() }
    )
    
    Box(
        modifier = Modifier.graphicsLayer { rotationY = rotationY },
        contentAlignment = Alignment.Center
    ) {
        if (rotationY <= 90f) {
            CardFront(card = card)
        } else {
            CardBack()
        }
    }
}
```

#### 2. Card Fly Animation
**Used by**: Card movement, placement

```kotlin
@Composable
fun CardFlyAnimation(
    card: Card,
    from: Offset,
    to: Offset,
    duration: Int = 400,
    onComplete: () -> Unit = {}
) {
    var position by remember { mutableStateOf(from) }
    
    LaunchedEffect(Unit) {
        animate(
            initialValue = from,
            targetValue = to,
            animationSpec = tween(duration),
            typeConverter = Offset.VectorConverter
        ) { value, _ ->
            position = value
        }
        onComplete()
    }
    
    CardComponent(card = card, modifier = Modifier.offset { position.toIntOffset() })
}
```

#### 3. Color Pulse Animation
**Used by**: Turn indicators, active elements

```kotlin
@Composable
fun TurnIndicatorPulse(
    color: Color,
    isActive: Boolean
) {
    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .scale(pulseScale)
            .background(color, CircleShape)
    )
}
```

#### 4. Sequential Animation
**Used by**: Combo chains, multi-step animations

```kotlin
@Composable
fun SequentialAnimation(
    animations: List<() -> Unit>,
    delayBetween: Long = 100
) {
    var currentIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(currentIndex) {
        if (currentIndex < animations.size) {
            animations[currentIndex]()
            delay(delayBetween)
            currentIndex++
        }
    }
}
```

#### 5. Parallel Animation
**Used by**: Simultaneous effects

```kotlin
@Composable
fun ParallelAnimation(
    animations: List<() -> Unit>
) {
    LaunchedEffect(Unit) {
        animations.forEach { it() }
    }
}
```

---

## 📝 Tasks

### Week 24: Complete Remaining Animations

#### Task 6.1: Review Animation Status
**Owner**: UI/UX Designer | **Duration**: 1 day | **Priority**: HIGH

**Actions**:
- Review all 25 animations from AS3
- Identify which are already implemented in Phase 4
- Prioritize remaining animations
- Assign animations to team members

**Acceptance Criteria**:
- [ ] Complete inventory of animations
- [ ] Clear assignment of work
- [ ] Prioritization complete

---

#### Task 6.2: Implement Remaining Animations
**Owner**: Team | **Duration**: 4 days | **Priority**: HIGH

**Animations to Complete**:
- Mogu
- PileOuFace (coin flip)
- TalkAnim
- Any rule animations not completed

**Implementation Approach**:
1. Analyze AS3 animation code
2. Determine animation requirements
3. Implement using Compose Animation API
4. Test animation behavior
5. Integrate with game flow

**Acceptance Criteria**:
- [ ] All animations implemented
- [ ] Animations match original visuals
- [ ] Animations integrate with game

---

### Week 25: Polish and Optimization

#### Task 6.3: Polish Existing Animations
**Owner**: UI/UX Designer + Team | **Duration**: 3 days | **Priority**: HIGH

**Polish Items**:
- Fine-tune timing and easing
- Ensure visual consistency
- Add sound effects (if applicable)
- Improve transitions between animations
- Add animation callbacks for game logic

**Polish Checklist per Animation**:
- [ ] Correct duration
- [ ] Smooth easing
- [ ] Visual match with original
- [ ] Proper callbacks
- [ ] Sound integration (if any)

**Acceptance Criteria**:
- [ ] All animations polished
- [ ] Visual quality matches original
- [ ] Callbacks work correctly

---

#### Task 6.4: Performance Optimization
**Owner**: Tech Lead + Team | **Duration**: 3 days | **Priority**: CRITICAL

**Optimization Goals**:
- All animations > 60 FPS on mid-range devices
- No frame drops during animations
- Memory usage within budget
- Smooth transitions

**Optimization Techniques**:
- Use `remember` for animation values
- Avoid unnecessary recompositions
- Use appropriate animation specs
- Limit concurrent animations
- Use `LaunchedEffect` for one-time animations

**Performance Testing**:
```kotlin
// AnimationPerformanceTest.kt
class AnimationPerformanceTest : BaseTest() {
    init {
        test("Card flip animation > 60 FPS") {
            // Use Android Benchmark library
            // Measure frame time
            // Assert FPS > 60
        }
        
        test("Multiple animations don't drop frames") {
            // Test 10 concurrent card flips
            // Measure FPS
            // Assert no frame drops
        }
        
        test("Combo animation performance") {
            // Test combo chain of 5 cards
            // Measure FPS
            // Assert > 60 FPS
        }
    }
}
```

**Performance Monitoring**:
```kotlin
// AnimationMonitor.kt
class AnimationMonitor {
    private val frameTimes = mutableListOf<Long>()
    private var lastFrameTime = 0L
    
    fun onFrame() {
        val currentTime = System.nanoTime()
        if (lastFrameTime > 0) {
            val frameTime = currentTime - lastFrameTime
            frameTimes.add(frameTime)
            if (frameTimes.size > 60) {
                frameTimes.removeAt(0)
            }
        }
        lastFrameTime = currentTime
    }
    
    fun getCurrentFPS(): Float {
        if (frameTimes.isEmpty()) return 0f
        val avgFrameTime = frameTimes.average() / 1_000_000
        return 1000f / avgFrameTime
    }
    
    fun getFrameTimeStats(): FrameStats {
        return FrameStats(
            average = frameTimes.average() / 1_000_000,
            min = frameTimes.minOrNull()?.toFloat()?.div(1_000_000) ?: 0f,
            max = frameTimes.maxOrNull()?.toFloat()?.div(1_000_000) ?: 0f
        )
    }
}

data class FrameStats(
    val average: Float,
    val min: Float,
    val max: Float
)
```

**Acceptance Criteria**:
- [ ] All animations > 60 FPS
- [ ] No frame drops in normal use
- [ ] Memory usage controlled

---

### Week 26: Testing and Validation

#### Task 6.5: Animation Testing
**Owner**: QA Engineer + Team | **Duration**: 3 days | **Priority**: CRITICAL

**Testing Strategy**:
1. **Visual Testing**: Manual verification of animation appearance
2. **Functional Testing**: Verify animation triggers and callbacks
3. **Performance Testing**: Verify FPS and memory usage
4. **Stress Testing**: Test many concurrent animations
5. **Cross-Platform Testing**: Verify on both Android and iOS

**Test Types**:
```kotlin
// Visual testing
class CardFlipVisualTest : BaseTest() {
    @Test
    fun cardFlip_showsCorrectFrames() {
        // Use Compose screenshot testing
        // Capture frames during animation
        // Verify frames match expected
    }
}

// Functional testing
class CardFlipFunctionalTest : BaseTest() {
    @Test
    fun cardFlip_callsOnComplete() = runTest {
        var completed = false
        
        composeTestRule.setContent {
            CardFlipAnimation(
                card = testCard,
                isFlipping = true,
                onComplete = { completed = true }
            )
        }
        
        // Wait for animation
        composeTestRule.waitUntilTimeout(1000) {
            completed
        }
        
        assertTrue(completed)
    }
}

// Integration testing
class AnimationIntegrationTest : BaseTest() {
    @Test
    fun comboAnimation_triggersCardFlips() = runTest {
        // Test that combo animation triggers appropriate card flips
    }
}
```

**Test Coverage Targets**:
- Animation components: 100%
- Animation callbacks: 100%
- Animation triggers: 100%

**Acceptance Criteria**:
- [ ] All animation tests pass
- [ ] Visual quality verified
- [ ] Performance validated

---

#### Task 6.6: User Acceptance Testing
**Owner**: QA Engineer | **Duration**: 2 days | **Priority**: HIGH

**UAT Process**:
1. Recruit test users (5-10)
2. Create test scenarios
3. Collect feedback on animations
4. Iterate based on feedback
5. Final validation

**Test Scenarios**:
- Single card flip
- Combo chain
- Turn transition
- Game win/loss
- Rule activation
- Multiple simultaneous animations

**Feedback Questions**:
- Do animations feel smooth?
- Do animations match the original?
- Are any animations distracting?
- Do animations enhance gameplay?
- Any visual issues?

**Acceptance Criteria**:
- [ ] Positive feedback from testers
- [ ] All issues addressed
- [ ] Final approval from UI/UX

---

## 📊 Phase 6 Deliverables

### Code Deliverables
- [ ] All 25+ animations implemented
- [ ] Animation utilities and helpers
- [ ] Performance monitoring
- [ ] Animation tests

### Documentation Deliverables
- [ ] Animation catalog
- [ ] Animation usage guide
- [ ] Performance optimization guide

---

## ✅ Phase 6 Completion Criteria

### Technical
- [ ] All animations implemented
- [ ] All animations polished
- [ ] Performance > 60 FPS
- [ ] Memory usage controlled

### Testing
- [ ] All animation tests pass
- [ ] Visual quality verified
- [ ] Performance validated
- [ ] UAT complete

### Approvals
- [ ] Tech Lead approval
- [ ] UI/UX Designer approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 7 - Testing

**Phase 7 Focus** (Weeks 27-30):
- Comprehensive testing
- Bug fixing
- Performance testing
- User acceptance testing
- Final validation

**Prerequisites**: All Phase 6 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 7**: [11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
