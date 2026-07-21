# AS3 to Kotlin Migration Cheat Sheet

## 📋 Quick Reference for Developers

This document provides quick mappings between ActionScript 3 and Kotlin patterns for the Triple Triad Online migration.

---

## 🏷️ Type System Mapping

### Basic Types

| AS3 | Kotlin | Notes |
|-----|--------|-------|
| `uint` | `UInt` | Use `toUInt()` for conversion |
| `int` | `Int` | Standard integer |
| `Number` | `Double` or `Float` | Use appropriate type |
| `Boolean` | `Boolean` | Same: `true` / `false` |
| `String` | `String` | Same, but Kotlin uses `""` only |
| `null` | `null` | Same concept |

### Collections

| AS3 | Kotlin | Notes |
|-----|--------|-------|
| `Array` | `List<T>` or `MutableList<T>` | Use `listOf()` or `mutableListOf()` |
| `Vector.<T>` | `List<T>` or `MutableList<T>` | Kotlin lists are typed |
| `Object` | `Map<String, Any>` or `MutableMap<String, Any>` | For dynamic objects |
| `Object` (class) | `data class` | For structured data |
| `for each (var x in arr)` | `for (x in arr)` or `arr.forEach { }` | Iteration |
| `arr.push(x)` | `arr.add(x)` or `arr += x` | Add element |
| `arr.length` | `arr.size` | Get size |
| `arr[i]` | `arr[i]` or `arr.get(i)` | Access element |

### Null Safety

| AS3 Pattern | Kotlin Equivalent | Notes |
|--------------|------------------|-------|
| `if (x != null) { ... }` | `x?.let { ... }` | Safe call with let |
| `if (x != null) { y = x.z }` | `y = x?.z` | Safe property access |
| `var y = (x != null) ? x : z` | `val y = x ?: z` | Elvis operator |
| `x && y` | `x && y` | Same |
| `x \|\| y` | `x \|\| y` | Same |
| `x == null` | `x == null` | Same |

---

## 📦 Class and Object Mapping

### Class Definition

**AS3:**
```actionscript
package com.example {
    public class MyClass {
        private var _name:String;
        private var _age:uint;
        
        public function MyClass(name:String, age:uint) {
            _name = name;
            _age = age;
        }
        
        public function get name():String {
            return _name;
        }
        
        public function set name(value:String):void {
            _name = value;
        }
        
        public function greet():String {
            return "Hello, " + _name;
        }
    }
}
```

**Kotlin:**
```kotlin
package com.example

class MyClass(
    private var name: String,
    private var age: UInt
) {
    // Properties with getters/setters are automatic in Kotlin
    
    fun greet(): String = "Hello, $name"
}
```

### Data Class

**AS3:**
```actionscript
public class CardData {
    public var id:uint;
    public var name:String;
    public var power:Array;
    
    public function CardData(id:uint, name:String, power:Array) {
        this.id = id;
        this.name = name;
        this.power = power;
    }
}
```

**Kotlin:**
```kotlin
@Serializable
data class CardData(
    val id: UInt,
    val name: String,
    val power: List<String>
)
```

### Singleton

**AS3:**
```actionscript
public class Game {
    public static var instance:Game;
    
    public static function getInstance():Game {
        if (instance == null) {
            instance = new Game();
        }
        return instance;
    }
    
    private function Game() {
        // private constructor
    }
}
```

**Kotlin:**
```kotlin
object Game {
    // All properties and methods are automatically static
}
```

Or using a class:

```kotlin
class Game private constructor() {
    companion object {
        val instance: Game by lazy { Game() }
    }
}
```

---

## 📢 Event System → Coroutines/Flow

### Basic Events

**AS3:**
```actionscript
// Dispatching
dispatchEvent(new Event(Event.COMPLETE));

// Listening
addEventListener(Event.COMPLETE, onComplete);

private function onComplete(e:Event):void {
    // Handle event
}
```

**Kotlin (SharedFlow for one-time events):**
```kotlin
// In ViewModel or Manager
class EventManager {
    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()
    
    suspend fun emitEvent(event: Event) {
        _events.emit(event)
    }
}

// Receiving in Compose
@Composable
fun MyScreen(viewModel: MyViewModel) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is Event.Complete -> { /* handle */ }
            }
        }
    }
}
```

**Kotlin (StateFlow for state):**
```kotlin
// In ViewModel
class MyViewModel : ViewModel() {
    private val _state = MutableStateFlow<GameState>(GameState.Idle)
    val state: StateFlow<GameState> = _state.asStateFlow()
    
    fun doSomething() {
        _state.value = GameState.Loading
        viewModelScope.launch {
            // Do work
            _state.value = GameState.Complete
        }
    }
}

// In Compose
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.state.collectAsState()
    
    when (state) {
        GameState.Idle -> Text("Idle")
        GameState.Loading -> CircularProgressIndicator()
        GameState.Complete -> Text("Complete!")
    }
}
```

### Custom Events

**AS3:**
```actionscript
public class CardEvent extends Event {
    public static const CARD_PLACED:String = "cardPlaced";
    public var card:Card;
    public var tile:Tile;
    
    public function CardEvent(type:String, card:Card, tile:Tile) {
        super(type);
        this.card = card;
        this.tile = tile;
    }
}

// Dispatch
dispatchEvent(new CardEvent(CardEvent.CARD_PLACED, card, tile));

// Listen
addEventListener(CardEvent.CARD_PLACED, onCardPlaced);
```

**Kotlin:**
```kotlin
// Sealed class for events
sealed class CardEvent {
    data class CardPlaced(val card: Card, val tile: Tile) : CardEvent()
    // Other event types...
}

// Flow in ViewModel
class GameViewModel {
    private val _cardEvents = MutableSharedFlow<CardEvent>()
    val cardEvents: SharedFlow<CardEvent> = _cardEvents.asSharedFlow()
    
    fun placeCard(card: Card, tile: Tile) {
        viewModelScope.launch {
            _cardEvents.emit(CardEvent.CardPlaced(card, tile))
        }
    }
}

// In Compose
@Composable
fun GameScreen(viewModel: GameViewModel) {
    LaunchedEffect(Unit) {
        viewModel.cardEvents.collect { event ->
            when (event) {
                is CardEvent.CardPlaced -> {
                    // Handle card placed
                }
            }
        }
    }
}
```

---

## 🎨 UI Components: Feathers → Compose

### Common Component Mapping

| Feathers Component | Compose Equivalent | Notes |
|-------------------|-------------------|-------|
| `Screen` | `@Composable` function | Use `Box`, `Column`, `Row` |
| `ScreenNavigator` | `NavHost` + `NavController` | Jetpack Navigation Compose |
| `Button` | `Button` | Material Design 3 |
| `Label` | `Text` | Material Typography |
| `Image` | `Image` | Use Coil/Accompanist for loading |
| `LayoutGroup` | `Column` / `Row` / `Box` | Flexible layouts |
| `Sprite` | `Box` + `Canvas` | Use `graphicsLayer` for transforms |
| `Quad` | `Box` with `Modifier.background()` | Solid color rectangle |

### Layout Examples

**AS3 (Feathers):**
```actionscript
var layout:LayoutGroup = new LayoutGroup();
layout.layout = new HorizontalLayout();
layout.addChild(button1);
layout.addChild(button2);
```

**Kotlin (Compose):**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly
) {
    Button(onClick = { /* ... */ }) { Text("Button 1") }
    Button(onClick = { /* ... */ }) { Text("Button 2") }
}
```

**AS3 (Starling):**
```actionscript
var sprite:Sprite = new Sprite();
sprite.width = 100;
sprite.height = 100;
sprite.x = 50;
sprite.y = 50;
sprite.addChild(image);
```

**Kotlin (Compose):**
```kotlin
Box(
    modifier = Modifier
        .size(100.dp, 100.dp)
        .offset(50.dp, 50.dp)
) {
    Image(painter = rememberAsyncImagePainter(url), contentDescription = null)
}
```

### Touch/Click Events

**AS3:**
```actionscript
button.addEventListener(TouchEvent.TOUCH, onTouch);

private function onTouch(e:TouchEvent):void {
    if (e.touchPhase == TouchPhase.TAP) {
        // Handle click
    }
}
```

**Kotlin (Compose):**
```kotlin
Button(
    onClick = { /* Handle click */ }
) {
    Text("Click me")
}

// For custom touch handling
Box(
    modifier = Modifier
        .clickable { /* Handle click */ }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { /* Handle tap */ },
                onDoubleTap = { /* Handle double tap */ }
            )
        }
) {
    // Content
}
```

### Drag and Drop

**AS3 (Feathers):**
```actionscript
// Drag source (Card)
public class Card extends Sprite implements IDragSource {
    private function onTouch(event:TouchEvent):void {
        if (event.touchPhase == TouchPhase.MOVED) {
            var dragData:DragData = new DragData();
            dragData.setDataForFormat("card-drag-format", {data: this});
            DragDropManager.startDrag(this, touch, dragData, ghost);
        }
    }
}

// Drop target (Tile)
public class Tile extends Sprite implements IDropTarget {
    private function onDragDrop(event:DragDropEvent):void {
        if (event.dragData.hasDataForFormat("card-drag-format")) {
            var cardToDrop:Card = event.dragData.getDataForFormat("card-drag-format").data;
            this.card = cardToDrop;
        }
    }
}
```

**Kotlin (Compose):**
```kotlin
// Draggable Card
@Composable
fun DraggableCard(
    card: Card,
    onDragStart: (Card) -> Unit,
    onDragEnd: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onDragStart(card)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDrag = { change, offset ->
                        change.consume()
                    }
                )
            }
            .alpha(if (isDragging) 0.5f else 1f)
    ) {
        CardComponent(card = card)
    }
}

// Drop Target Tile
@Composable
fun DropTargetTile(
    tile: Tile,
    onCardDrop: (Card) -> Unit
) {
    var isDraggingOver by remember { mutableStateOf(false) }
    var draggedCard by remember { mutableStateOf<Card?>(null) }
    
    Box(
        modifier = Modifier
            .size(136.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnter = {
                        isDraggingOver = true
                        true // accept drag
                    },
                    onDragExit = {
                        isDraggingOver = false
                    },
                    onDragEnd = {
                        if (isDraggingOver && draggedCard != null) {
                            onCardDrop(draggedCard!!)
                        }
                        isDraggingOver = false
                        draggedCard = null
                    }
                )
            }
            .border(
                width = if (isDraggingOver) 2.dp else 0.dp,
                color = Color.Green
            )
    ) {
        // Tile content
        tile.card?.let { card ->
            CardComponent(card = card)
        }
    }
}
```

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
```kotlin
val transition = rememberInfiniteTransition()
val alpha by transition.animateFloat(
    initialValue = 1f,
    targetValue = 0f,
    animationSpec = tween(
        durationMillis = 400,
        easing = LinearEasing
    )
)
val offsetY by transition.animateDp(
    initialValue = 0.dp,
    targetValue = (-100).dp,
    animationSpec = tween(400)
)

Box(
    modifier = Modifier
        .offset(y = offsetY)
        .alpha(alpha)
) {
    // Content
}

// For one-time animation with completion
val scope = rememberCoroutineScope()
var playAnimation by remember { mutableStateOf(false) }

if (playAnimation) {
    LaunchedEffect(Unit) {
        // Animate
        delay(400L)
        afterFly(x, y)
    }
}
```

---

## 🗃️ Data and Serialization

### JSON Serialization

**AS3:**
```actionscript
var obj:Object = {name: "Card", id: 1, power: [4, 2, 3, 4]};
var json:String = JSON.stringify(obj);
var parsed:Object = JSON.parse(json);
```

**Kotlin:**
```kotlin
// Requires kotlinx-serialization

@Serializable
data class CardData(
    val name: String,
    val id: UInt,
    val power: List<String>
)

// Serialize
val json = Json.encodeToString(cardData)

// Deserialize
val cardData = Json.decodeFromString<CardData>(json)
```

### Properties File → Kotlin Object

**AS3:**
```actionscript
// conf.as
public class conf {
    public static const DATAS:Object = {
        language: "en_US",
        debug: false,
        server: "localhost"
    };
}

// Usage
var lang:String = conf.DATAS.language;
```

**Kotlin:**
```kotlin
// AppConfig.kt
object AppConfig {
    val language: String = "en_US"
    val debug: Boolean = false
    val server: String = "localhost"
}

// Or from JSON
@Serializable
data class AppConfig(
    val language: String,
    val debug: Boolean,
    val server: String
)

// Load from resources
val config = Json.decodeFromString<AppConfig>(readConfigFile())
```

---

## 🔊 Audio

### Sound Playback

**AS3:**
```actionscript
// Play sound
SoundManager.playSound('se_ttriad.scd_1', true);

// Stop all sounds
SoundManager.stopAll();
```

**Kotlin (Android):**
```kotlin
// Using Media3 ExoPlayer
class AudioManager(private val context: Context) {
    private val exoPlayer: ExoPlayer by lazy { ExoPlayer.Builder(context).build() }
    
    fun playSound(soundId: String, loop: Boolean = false) {
        val mediaItem = MediaItem.fromUri("asset:///sounds/$soundId.mp3")
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.prepare()
        exoPlayer.play()
    }
    
    fun stopAll() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }
}
```

---

## 📁 File System

### Loading Assets

**AS3:**
```actionscript
// Embed at compile time
[Embed(source = "../../assets/bg.jpg")]
private const mainBackground:Class;

// Load from file
var loader:Loader = new Loader();
loader.load(new URLRequest("assets/bg.jpg"));
```

**Kotlin (Android):**
```kotlin
// From assets
val inputStream = context.assets.open("bg.jpg")
val bitmap = BitmapFactory.decodeStream(inputStream)

// In Compose
Image(
    painter = rememberAsyncImagePainter("file:///android_asset/bg.jpg"),
    contentDescription = null
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

**AS3:**
```actionscript
public static const BLUE_COLOR:uint = 0x43a7c8;
public static const RED_COLOR:uint = 0xbb594f;
public static const GREY_COLOR:uint = 0x5a595a;

// Usage
_color = Card.BLUE_COLOR;
```

**Kotlin:**
```kotlin
enum class CardColor {
    BLUE, RED, GREY
}

// Colors
val BlueColor = Color(0xFF43a7c8)
val RedColor = Color(0xFFbb594f)
val GreyColor = Color(0xFF5a595a)

// In Card class
@Transient
var color: CardColor = CardColor.GREY
```

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

## 🔧 Utility Functions

### Clamping Values

**AS3:**
```actionscript
// From tools.as
public static function madmax(value:int):int {
    return Math.max(0, Math.min(value, 10));
}
```

**Kotlin:**
```kotlin
fun madmax(value: Int): Int = maxOf(0, minOf(value, 10))
```

### Random Number

**AS3:**
```actionscript
// From tools.as
public static function rand(max:int):int {
    return Math.floor(Math.random() * (max + 1));
}
```

**Kotlin:**
```kotlin
fun rand(max: Int): Int = Random.nextInt(max + 1)
```

### Array Contains

**AS3:**
```actionscript
// Using Adobe ArrayUtil
if (ArrayUtil.arrayContainsValue(array, value)) { ... }
```

**Kotlin:**
```kotlin
if (value in array) { ... }
// or
if (array.contains(value)) { ... }
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
- Verify all 15+ rules work identically

### 3. Handle State Carefully
- AS3 uses a lot of global static state
- Use ViewModel + StateFlow in Kotlin
- Avoid global variables where possible

### 4. Plan for Animations
- Compose has good animation support
- Use `animate*AsState` for simple animations
- Use `InfiniteTransition` for repeating animations
- For complex animations, consider custom `Animatable`

### 5. Network Protocol
- XMLSocket → WebSocket
- XML parsing → JSON parsing
- Need to understand server message format
- Consider creating a protocol specification document

### 6. Performance
- Compose is efficient but has overhead
- Use `remember` and `derivedStateOf` for optimization
- Lazy loading for large lists (cards)
- Profile early and often

---

## 📚 Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)

---

*This cheat sheet is optimized for AI agent consumption. For more details, see the specific migration phase documents.*
