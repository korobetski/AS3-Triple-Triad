# AS3 to Kotlin Migration Cheat Sheet

## 📋 Quick Reference for Developers

This document provides quick mappings between ActionScript 3 and Kotlin patterns for the Triple Triad Online migration.

---

## 🔄 Game Loop and Timers

### setTimeout → Coroutines

**AS3:**
```actionscript
// Single timeout
setTimeout(nextPhase, 1500);

// Repeating timer
var timer:int = setInterval(update, 1000);
clearInterval(timer);
```

**Kotlin:**
```kotlin
// Single delay (in Compose)
LaunchedEffect(Unit) {
    delay(1500L)
    nextPhase()
}

// In ViewModel
viewModelScope.launch {
    delay(1500L)
    nextPhase()
}

// Repeating (in ViewModel)
val timerJob = viewModelScope.launch {
    while (isActive) {
        update()
        delay(1000L)
    }
}

// Cancel
timerJob.cancel()
```

### Frame-based Animation

**AS3 (Starling):**
```actionscript
Starling.juggler.add(flippable);
Starling.juggler.tween(card, 0.4, {
    transition: Transitions.EASE_IN,
    y: card.y - 100,
    alpha: 0,
    onComplete: afterFly,
    onCompleteArgs: [x, y]
});
```

**Kotlin (Compose):**

> ⚠️ **The previous snippet was wrong twice.** `rememberInfiniteTransition()` is for
> animations that **loop forever** — exactly the opposite of a one-shot card fly.
> And `InfiniteTransition.animateFloat`/`animateDp` require an
> `InfiniteRepeatableSpec`; passing a plain `tween(...)` does not compile.
>
> A Starling `juggler.tween(..., onComplete:)` is a **one-shot animation with a
> completion callback**. The Compose equivalent is `Animatable` driven from a
> coroutine, which gives you a real suspension point to sequence on:

```kotlin
// One-shot fly + fade, with a completion callback — the direct analogue of
// juggler.tween(card, 0.4, {y: ..., alpha: 0, onComplete: afterFly})
@Composable
fun FlyingCard(
    card: Card,
    targetOffsetY: Dp,
    onComplete: () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val density = LocalDensity.current

    LaunchedEffect(card.id) {
        val targetPx = with(density) { targetOffsetY.toPx() }
        // Run both tracks concurrently, then fire the callback once both finish.
        coroutineScope {
            launch { offsetY.animateTo(targetPx, tween(400, easing = FastOutLinearInEasing)) }
            launch { alpha.animateTo(0f, tween(400, easing = FastOutLinearInEasing)) }
        }
        onComplete()          // reached only after both animations complete
    }

    CardComponent(
        card = card,
        modifier = Modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .alpha(alpha.value)
    )
}
```

For **sequencing** several animations — which the combo cascade needs — just
`await` them in order inside one coroutine; no callback pyramid:

```kotlin
LaunchedEffect(comboChain) {
    comboChain.forEach { capture ->
        flipAnimatable(capture.tileId).animateTo(180f, tween(400))
        delay(120)                       // stagger between shock waves
    }
    onCascadeComplete()
}
```

**Only** use `rememberInfiniteTransition` for genuinely looping effects, such as the
pulsing turn indicator:

```kotlin
val transition = rememberInfiniteTransition(label = "turnPulse")
val scale by transition.animateFloat(
    initialValue = 1f,
    targetValue = 1.1f,
    animationSpec = infiniteRepeatable(   // required — not a bare tween()
        animation = tween(1000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
)
```

---

## 🎯 Common Patterns in Triple Triad Code

### Power Values (Hex)

**AS3:**
```actionscript
// Card data
{name: "Card 1", power: [4, 2, 3, 4], rarity: 1}

// Get power as uint
public function get topPow():uint {
    return uint("0x" + _data.power[0]);
}
```

**Kotlin:**
```kotlin
@Serializable
data class Card(
    val power: List<String> // ["4", "2", "3", "4"] or ["A", "5", "6", "8"]
) {
    val topPow: UInt get() = power[0].hexToUInt()
    val rightPow: UInt get() = power[1].hexToUInt()
    val bottomPow: UInt get() = power[2].hexToUInt()
    val leftPow: UInt get() = power[3].hexToUInt()
}

fun String.hexToUInt(): UInt = when (this) {
    "A", "a" -> 10u
    "B", "b" -> 11u
    "C", "c" -> 12u
    "D", "d" -> 13u
    "E", "e" -> 14u
    "F", "f" -> 15u
    else -> this.toUInt(16)
}
```

### Card Colors

**AS3 (actual — `display/Card.as:29-31`):**
```actionscript
public static const GREY_COLOR:uint = 0x5a595a;
public static const BLUE_COLOR:uint = 0x2d4660;   // NOT 0x43a7c8
public static const RED_COLOR:uint  = 0x602d2d;   // NOT 0xbb594f

// Usage: _color holds the NAME, not the colour value.
_color = 'GREY';                                  // 'BLUE' | 'RED' | 'GREY'
// and the quad is tinted separately:
colorBackground = new Quad(88, 118, 0x5a595a);
```

> ⚠️ **Corrected.** `0x43a7c8` / `0xbb594f` are *text* colours from
> `theme/BaseTTOTheme.as:1537-1544` (`largeBlueElementFormat` /
> `largeRedElementFormat`), not card colours — a previous revision conflated the
> two. Also, `Card._color` is a **String** name (`'BLUE'`/`'RED'`/`'GREY'`), so
> `_color = Card.BLUE_COLOR` (assigning a uint) was never valid.

**Kotlin:**
```kotlin
@Serializable
enum class CardColor { BLUE, RED, GREY }

// Card background tints
val CardBlue = Color(0xFF2D4660)
val CardRed  = Color(0xFF602D2D)
val CardGrey = Color(0xFF5A595A)

// Text colours (distinct from the above)
val TextBlue = Color(0xFF43A7C8)
val TextRed  = Color(0xFFBB594F)
```

> Ownership does **not** live on `Card` — see the note in
> [13-DATA-MODELS.md](./13-DATA-MODELS.md). `Card` is immutable card data; who owns
> a card is a property of the tile or hand entry holding it.

### Element Types

**AS3:**
```actionscript
private var _element:String = "none";

// Possible values: "none", "earth", "fire", "holy", "ice", "lightning", "poison", "water", "wind"
```

**Kotlin:**
```kotlin
enum class Element {
    NONE, EARTH, FIRE, HOLY, ICE, LIGHTNING, POISON, WATER, WIND
}

// In Tile class
@Transient
var element: Element = Element.NONE
```

### Card Types

**AS3:**
```actionscript
// Possible type values
"beast", "garlean", "primals", "scions", "earth", "fire", "holy", "ice", "lightning", "poison", "water", "wind"
```

**Kotlin:**
```kotlin
enum class CardType {
    NONE, BEAST, GARLEAN, PRIMALS, SCIONS,
    EARTH, FIRE, HOLY, ICE, LIGHTNING, POISON, WATER, WIND
}
```

---

## 🎮 Game-Specific Patterns

### Rule Constants

**AS3:**
```actionscript
// From tripleTriadRules.as
public static const RULE_OPEN:String = 'STR_OPEN';
public static const RULE_DEFAULT_OPEN:String = 'RULE_DEFAULT_OPEN';
public static const RULE_ALL_OPEN:String = 'RULE_ALL_OPEN';
// ... etc
```

**Kotlin:**
```kotlin
enum class OpenRule {
    DEFAULT_OPEN, ALL_OPEN, THREE_OPEN
}

enum class OrderRule {
    DEFAULT_ORDER, ORDER, CHAOS
}

enum class TypeRule {
    DEFAULT_TYPE, ASCENSION, DESCENSION, ELEMENTAL
}

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
)
```

### Game Phases

**AS3:**
```actionscript
// In BaseMatchScreen.as
private function deckSelectionPhase():void { ... }
private function openPhase():void { ... }
private function orderPhase():void { ... }
private function reversePhase():void { ... }
private function fallenAcePhase():void { ... }
private function swapPhase():void { ... }
private function pileOuFace():void { ... }
private function letsGetStarted():void { ... }
private function nextTurn():void { ... }
```

**Kotlin:**
```kotlin
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

// In ViewModel
private val _phase = MutableStateFlow(GamePhase.DECK_SELECTION)
val phase: StateFlow<GamePhase> = _phase.asStateFlow()

fun nextPhase() {
    _phase.value = when (_phase.value) {
        GamePhase.DECK_SELECTION -> GamePhase.OPEN_PHASE
        GamePhase.OPEN_PHASE -> GamePhase.ORDER_PHASE
        // ... etc
    }
}
```

---

## 💡 Tips for Migration

### 1. Start with Data Models
- Migrate `Card`, `Tile`, `Board` first
- Use `@Serializable` data classes
- Separate state from display

### 2. Test Core Logic Thoroughly
- `TTOCore` is the most critical component
- Write property-based tests for rules
- Verify all 17 rules work identically

### 3. Handle State Carefully
- AS3 uses a lot of global static state
- Use ViewModel + StateFlow in Kotlin
- Avoid global variables where possible

### 4. Plan for Animations
- Compose has good animation support
- Use `animate*AsState` for simple animations
- Use `InfiniteTransition` for repeating animations
- For complex animations, consider custom `Animatable`

### 6. Performance
- Compose is efficient but has overhead
- Use `remember` and `derivedStateOf` for optimization
- Lazy loading for large lists (cards)
- Profile early and often

---
