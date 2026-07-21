# Data Models - AS3 to Kotlin Migration

## 📋 Document Information

- **Purpose**: Map all ActionScript 3 data classes to Kotlin data classes
- **Status**: PLANNING COMPLETE
- **Last Updated**: 2026-07-21
- **Related**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)

---

## 🗃️ Model Categories

### 1. Core Game Models
| AS3 Class | Kotlin File | Priority | Complexity | Notes |
|-----------|-------------|----------|------------|-------|
| `Card` | `data/models/Card.kt` | CRITICAL | HIGH | Display + state |
| `Tile` | `data/models/Tile.kt` | CRITICAL | HIGH | Board tile + drop target |
| `Board` | `data/models/Board.kt` | CRITICAL | MEDIUM | 3x3 game board |
| `TTOCore` | `core/game/TTOCore.kt` | CRITICAL | VERY HIGH | Rules engine |
| `tripleTriadRules` | `core/game/TripleTriadRules.kt` | CRITICAL | HIGH | Rule definitions |

---

### 2. Data Models (from `datas/`)
| AS3 Class | Kotlin File | Priority | Complexity | Notes |
|-----------|-------------|----------|------------|-------|
| `cards` | `data/models/Card.kt` | CRITICAL | MEDIUM | Card data arrays |
| `CardItem` | `data/models/Item.kt` | HIGH | MEDIUM | Item base class |
| `BoosterItem` | `data/models/BoosterItem.kt` | HIGH | LOW | Inherits CardItem |
| `PotionItem` | `data/models/PotionItem.kt` | HIGH | LOW | Inherits CardItem |
| `Item` | `data/models/Item.kt` | HIGH | LOW | Base item class |
| `Save` | `data/models/Save.kt` | CRITICAL | HIGH | Save file structure |
| `Achievements` | `data/models/Achievement.kt` | MEDIUM | LOW | Achievement tracking |
| `NPC` | `data/models/NPC.kt` | HIGH | MEDIUM | NPC data |
| `NPCs` | `data/models/NPC.kt` | HIGH | LOW | NPC collection |
| `Rank` | `data/models/Rank.kt` | LOW | LOW | Rank definitions |
| `Level` | `data/models/Level.kt` | LOW | LOW | Level data |

---

### 3. UI Models (from `display/` and `screens/`)
| AS3 Class | Kotlin File | Priority | Complexity | Notes |
|-----------|-------------|----------|------------|-------|
| `CardDigits` | `ui/components/cards/CardDigits.kt` | MEDIUM | LOW | Power digit display |
| `CardThumb` | `ui/components/cards/CardThumb.kt` | MEDIUM | LOW | Card thumbnail |
| `CardListThumb` | `ui/components/cards/CardListThumb.kt` | MEDIUM | LOW | Card list thumbnail |
| `ImageExtended` | `ui/components/ImageExtended.kt` | LOW | LOW | Extended image |
| `InventoryItem` | `ui/components/InventoryItem.kt` | MEDIUM | LOW | Inventory display |
| `ItemIcon` | `ui/components/ItemIcon.kt` | LOW | LOW | Item icon |
| `UserBar` | `ui/components/UserBar.kt` | LOW | LOW | User info bar |

---

### 4. Control Models (from `controls/`)
| AS3 Class | Kotlin File | Priority | Complexity | Notes |
|-----------|-------------|----------|------------|-------|
| `MainButton` | `ui/components/controls/MainButton.kt` | HIGH | LOW | Primary button |
| `MGPLabel` | `ui/components/controls/MGPLabel.kt` | MEDIUM | LOW | MGP display |
| `XPLabel` | `ui/components/controls/XPLabel.kt` | MEDIUM | LOW | XP display |
| `RoundChart` | `ui/components/controls/RoundChart.kt` | LOW | LOW | Progress chart |
| `TouchLabel` | `ui/components/controls/TouchLabel.kt` | LOW | LOW | Interactive label |
| `AvatarChooser` | `ui/components/controls/AvatarChooser.kt` | MEDIUM | MEDIUM | Avatar selection |
| `cardScore` | `ui/components/cards/CardScore.kt` | MEDIUM | LOW | Score display |

---

## 📝 Detailed Model Mappings

### Card Model

**AS3 `display/Card.as`** (Partial):
```actionscript
public class Card extends Sprite implements IDragSource {
    private var _id:uint;
    private var _collection:String;
    private var _texId:String;
    private var _data:Object;
    private var _color:String; // BLUE, RED, GREY
    private var _tile:Tile;
    private var _draggable:Boolean;
    private var _flipping:Boolean;
    private var _selected:Boolean;
    
    // Methods
    public function draw(newID:String, collection:String):void { ... }
    public function fly(_x:int, _y:int):void { ... }
    public function flipTo(horizon:Boolean, color:String):void { ... }
    public function flip(horizon:Boolean):void { ... }
    
    // Getters/Setters
    public function get topPow():uint { return uint("0x" + _data.power[0]); }
    public function get rightPow():uint { return uint("0x" + _data.power[1]); }
    // ... etc
}
```

**Kotlin `data/models/Card.kt`**:
```kotlin
@Serializable
data class Card(
    val id: UInt,
    val collection: CardCollection,
    val nameKey: String,
    val power: List<String>, // [top, right, bottom, left]
    val rarity: Int,
    val type: CardType?,
    val element: Element? = null
) {
    // Computed properties
    val topPow: UInt get() = power[0].hexToUInt()
    val rightPow: UInt get() = power[1].hexToUInt()
    val bottomPow: UInt get() = power[2].hexToUInt()
    val leftPow: UInt get() = power[3].hexToUInt()
    
    // Runtime properties (not serialized)
    @Transient
    var color: CardColor = CardColor.GREY
    
    @Transient
    var isDraggable: Boolean = true
    
    @Transient
    var isFlipping: Boolean = false
    
    @Transient
    var isSelected: Boolean = false
    
    // Methods
    fun canFlipAgainst(other: Card, direction: Direction): Boolean {
        val thisPower = when (direction) {
            Direction.TOP -> topPow
            Direction.RIGHT -> rightPow
            Direction.BOTTOM -> bottomPow
            Direction.LEFT -> leftPow
        }
        val otherPower = when (direction) {
            Direction.TOP -> other.bottomPow
            Direction.RIGHT -> other.leftPow
            Direction.BOTTOM -> other.topPow
            Direction.LEFT -> other.rightPow
        }
        return thisPower > otherPower
    }
}

@Serializable
enum class CardCollection { FF14, FF8 }

enum class Direction { TOP, RIGHT, BOTTOM, LEFT }
```

---

### Tile Model

**AS3 `display/Tile.as`** (Partial):
```actionscript
public class Tile extends Sprite implements IDropTarget {
    private var _card:Card;
    private var _taken:Boolean;
    private var _element:String;
    private var _id:uint;
    private var _color:String;
    private var _leftTile:Tile, _rightTile:Tile, _topTile:Tile, _bottomTile:Tile;
    private var _leftPow:uint, _rightPow:uint, _bottomPow:uint, _topPow:uint;
    
    public function onDragDrop(event:DragDropEvent):void { ... }
}
```

**Kotlin `data/models/Tile.kt`**:
```kotlin
@Serializable
data class Tile(
    val id: Int,
    val row: Int,
    val col: Int
) {
    // Card on this tile
    @Transient
    var card: Card? = null
    
    // Tile state
    @Transient
    var isTaken: Boolean = false
    var element: Element = Element.NONE
    
    // Adjacent tiles
    @Transient
    var leftTile: Tile? = null
    @Transient
    var rightTile: Tile? = null
    @Transient
    var topTile: Tile? = null
    @Transient
    var bottomTile: Tile? = null
    
    // Current powers
    @Transient
    var leftPow: UInt = 0u
    @Transient
    var rightPow: UInt = 0u
    @Transient
    var topPow: UInt = 0u
    @Transient
    var bottomPow: UInt = 0u
    
    @Transient
    var color: CardColor = CardColor.GREY
    
    fun hasCard(): Boolean = card != null
    fun clear() { /* ... */ }
    fun placeCard(card: Card, color: CardColor) { /* ... */ }
    fun getAdjacentTiles(): List<Tile> = listOfNotNull(topTile, rightTile, bottomTile, leftTile)
}
```

---

### Board Model

**AS3 `screens/Board.as`**:
```actionscript
public class Board {
    public var tiles:Vector.<Tile>;
    
    public function Board() {
        tiles = new Vector.<Tile>();
        for (var i:uint = 0; i < 9; i++) {
            tiles.push(new Tile(i));
        }
        // Connect adjacent tiles
    }
    
    public function razBoard():void { /* clear all cards */ }
    public function elements(predefined:Array):void { /* set elements */ }
    public function getRemainingTiles():Array { /* get empty tiles */ }
}
```

**Kotlin `data/models/Board.kt`**:
```kotlin
@Serializable
data class Board(val size: Int = 3) {
    private val _tiles: MutableList<Tile> = mutableListOf()
    val tiles: List<Tile> get() = _tiles.toList()
    
    init {
        createTiles()
    }
    
    private fun createTiles() {
        _tiles.clear()
        for (row in 0 until size) {
            for (col in 0 until size) {
                val id = row * size + col
                _tiles.add(Tile(id, row, col))
            }
        }
        connectAdjacentTiles()
    }
    
    private fun connectAdjacentTiles() { /* ... */ }
    
    fun getTile(row: Int, col: Int): Tile? { /* ... */ }
    fun getTile(id: Int): Tile? = _tiles.getOrNull(id)
    fun getEmptyTiles(): List<Tile> = _tiles.filter { !it.isTaken }
    fun clear() { _tiles.forEach { it.clear() } }
}
```

---

### Save Model

**AS3 `datas/Save.as`** (Partial):
```actionscript
public class Save {
    public static var DATAS:Object = {
        CREATION_DATE: new Date().getTime(),
        LAST_SAVE: new Date().getTime(),
        SAVE_NUMBER: 0,
        USERNAME: 'Kuplu Kopo',
        MODE: "ff14_",
        ADMIN: 0,
        CARDS: [1, 3, 6, 7, 10],
        DECKS: [{name:'Starter deck', cards:[1, 3, 6, 7, 10]}],
        STATS: { WINS:0, DEFEATS:0, DRAWS:0, FORFEITS:0 },
        BAG: [],
        BOONS: {MGP:0, XP:0, LUCK:0},
        MGP: 100,
        XP: 0,
        LEVEL: 1,
        PVP_XP: 0,
        RANK: 1,
        AVATAR_ID: 'ffxiv_twi03005',
        STARTED_MATCHES: 0,
        ENDED_MATCHES: 0,
        PVE_MATCHES: 0,
        PVP_MATCHES: 0,
        ACHIEVEMENTS: {},
        NPC_W: {},
        RULES_W: {}
    };
    
    public static function save():void { /* ... */ }
    public static function load(profile_name:String):Object { /* ... */ }
}
```

**Kotlin `data/models/Save.kt`**:
```kotlin
@Serializable
data class GameSave(
    val username: String,
    val creationDate: Long,
    val lastSave: Long,
    val saveNumber: Int,
    val mode: CardCollection,
    val admin: Int,
    val cards: List<UInt>,
    val decks: List<Deck>,
    val stats: Stats,
    val bag: List<UInt>,
    val boons: Boons,
    val mgp: Int,
    val xp: Int,
    val level: Int,
    val pvpXp: Int,
    val rank: Int,
    val avatarId: String,
    val startedMatches: Int,
    val endedMatches: Int,
    val pveMatches: Int,
    val pvpMatches: Int,
    val achievements: Map<String, Boolean>,
    val npcWins: Map<String, Int>,
    val rulesWins: Map<String, Int>
)

@Serializable
data class Deck(val name: String, val cards: List<UInt>)

@Serializable
data class Stats(
    val wins: Int = 0,
    val defeats: Int = 0,
    val draws: Int = 0,
    val forfeits: Int = 0
)

@Serializable
data class Boons(
    val mgp: Int = 0,
    val xp: Int = 0,
    val luck: Int = 0
)
```

---

### Item Hierarchy

**AS3 `datas/Item.as`, `BoosterItem.as`, `PotionItem.as`**:
```actionscript
// Item.as
public class Item {
    public var id:uint;
    public var nameKey:String;
    public var descriptionKey:String;
    public var iconId:String;
    public var rarity:uint;
}

// BoosterItem.as
public class BoosterItem extends Item {
    public var type:String; // XP, MGP, LUCK
    public var value:uint;
    public var duration:uint;
}

// PotionItem.as
public class PotionItem extends Item {
    public var effectType:String;
    public var effectValue:uint;
}
```

**Kotlin `data/models/Item.kt`**:
```kotlin
@Serializable
sealed class Item {
    abstract val id: UInt
    abstract val nameKey: String
    abstract val descriptionKey: String
    abstract val iconId: String
    abstract val rarity: Int
}

@Serializable
@SerializedName("booster")
data class BoosterItem(
    override val id: UInt,
    override val nameKey: String,
    override val descriptionKey: String,
    override val iconId: String,
    override val rarity: Int,
    val type: BoosterType,
    val value: UInt,
    val duration: UInt
) : Item()

@Serializable
@SerializedName("potion")
data class PotionItem(
    override val id: UInt,
    override val nameKey: String,
    override val descriptionKey: String,
    override val iconId: String,
    override val rarity: Int,
    val effectType: EffectType,
    val effectValue: UInt
) : Item()

enum class BoosterType { XP, MGP, LUCK }
enum class EffectType { HEAL, DAMAGE, SHIELD, etc. }
```

---

### Game Rules Model

**AS3 `datas/tripleTriadRules.as`** (Constants):
```actionscript
public static const RULE_OPEN:String = 'STR_OPEN';
public static const RULE_DEFAULT_OPEN:String = 'RULE_DEFAULT_OPEN';
public static const RULE_ALL_OPEN:String = 'RULE_ALL_OPEN';
public static const RULE_THREE_OPEN:String = 'RULE_THREE_OPEN';
public static const RULE_SUDDEN_DEATH:String = 'RULE_SUDDEN_DEATH';
public static const RULE_RANDOM:String = 'RULE_RANDOM';
public static const RULE_DEFAULT_ORDER:String = 'RULE_DEFAULT_ORDER';
public static const RULE_ORDER:String = 'RULE_ORDER';
public static const RULE_CHAOS:String = 'RULE_CHAOS';
public static const RULE_REVERSE:String = 'RULE_REVERSE';
public static const RULE_FALLEN_ACE:String = 'RULE_FALLEN_ACE';
public static const RULE_SAME:String = 'RULE_SAME';
public static const RULE_SAME_WALL:String = 'RULE_SAME_WALL';
public static const RULE_PLUS:String = 'RULE_PLUS';
public static const RULE_COMBO:String = 'RULE_COMBO';
public static const RULE_TYPE:String = 'RULE_TYPE';
public static const RULE_DEFAULT_TYPE:String = 'RULE_DEFAULT_TYPE';
public static const RULE_ASCENSION:String = 'RULE_ASCENSION';
public static const RULE_DESCENSION:String = 'RULE_DESCENSION';
public static const RULE_ELEMENTAL:String = 'RULE_ELEMENTAL';
public static const RULE_SWAP:String = 'RULE_SWAP';
public static const RULE_ROULETTE:String = 'RULE_ROULETTE';
```

**Kotlin `core/game/GameRules.kt`**:
```kotlin
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
    var combo: Boolean = false,
    var swap: Boolean = false,
    var roulette: Boolean = false
) {
    fun hasSpecialRules(): Boolean = listOf(
        suddenDeath, random, reverse, fallenAce,
        same, sameWall, plus, combo, swap, roulette
    ).any { it }
    
    companion object {
        fun roulette(mode: GameMode): GameRules { /* ... */ }
    }
}

enum class OpenRule { DEFAULT_OPEN, ALL_OPEN, THREE_OPEN }
enum class OrderRule { DEFAULT_ORDER, ORDER, CHAOS }
enum class TypeRule { DEFAULT_TYPE, ASCENSION, DESCENSION, ELEMENTAL }
```

---

## 🔧 Utility Types

```kotlin
// Enums for game types
enum class CardColor { BLUE, RED, GREY }

enum class Element {
    NONE, EARTH, FIRE, HOLY, ICE, LIGHTNING, POISON, WATER, WIND
}

enum class CardType {
    NONE, BEAST, GARLEAN, PRIMALS, SCIONS,
    EARTH, FIRE, HOLY, ICE, LIGHTNING, POISON, WATER, WIND
}

enum class GameMode { FF14, FF8 }

enum class GamePhase {
    DECK_SELECTION, OPEN_PHASE, ORDER_PHASE, REVERSE_PHASE,
    FALLEN_ACE_PHASE, SWAP_PHASE, PILE_OU_FACE, STARTING, PLAYING, ENDED
}

// Data classes for state
data class Player(
    val name: String,
    val color: CardColor,
    val score: Int = 0
)

data class TurnState(
    val currentTurn: Int = 0,
    val currentPlayer: CardColor = CardColor.BLUE,
    val timeline: List<CardColor> = listOf(CardColor.BLUE, CardColor.RED),
    val cardsPlaced: Int = 0
)

@Serializable
data class GameState(
    val id: String = UUID.randomUUID().toString(),
    val mode: GameMode = GameMode.FF14,
    val rules: GameRules = GameRules.roulette(GameMode.FF14),
    val phase: GamePhase = GamePhase.DECK_SELECTION,
    val turn: TurnState = TurnState(),
    val board: Board = Board(),
    val bluePlayer: Player = Player("Player", CardColor.BLUE),
    val redPlayer: Player = Player("Opponent", CardColor.RED),
    val blueDeck: List<Card> = emptyList(),
    val redDeck: List<Card> = emptyList(),
    val selectedCard: Card? = null,
    val scores: Map<CardColor, Int> = mapOf(CardColor.BLUE to 0, CardColor.RED to 0),
    val isGameOver: Boolean = false,
    val winner: CardColor? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## ✅ Migration Checklist

### Core Models
- [ ] Card
- [ ] Tile
- [ ] Board
- [ ] GameRules
- [ ] GameState
- [ ] TurnState
- [ ] Player

### Data Models
- [ ] Item (base)
- [ ] BoosterItem
- [ ] PotionItem
- [ ] Save/GameSave
- [ ] Achievements
- [ ] NPC
- [ ] Rank
- [ ] Level

### Utility Types
- [ ] CardColor
- [ ] Element
- [ ] CardType
- [ ] GameMode
- [ ] GamePhase
- [ ] Direction
- [ ] CardCollection

---

## 📞 Related Documents

- **Current System Analysis**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 2 (Data Layer)**: [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
