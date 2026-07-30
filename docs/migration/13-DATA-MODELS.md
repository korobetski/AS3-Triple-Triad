# Data Models - AS3 to Kotlin Migration

## 📋 Document Information

- **Purpose**: Map all ActionScript 3 data classes to Kotlin data classes
- **Status**: IMPLEMENTED (Phase 2, 2026-07-30). Six factual errors found against the AS3 source
  while porting and corrected below; each is marked **CORRECTED 2026-07-30**. The Kotlin in this
  document is the *plan*, not the code — where they differ the code is right and says why in its
  KDoc.
- **Last Updated**: 2026-07-30
- **Related**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md),
  [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md) § What was built

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
| `cards` | `data/models/Card.kt` | CRITICAL | MEDIUM | Card data arrays (153 FF14 + 110 FF8) |
| `Item` | `data/models/Item.kt` | HIGH | MEDIUM | Base item class — **extends `starling.display.Sprite`**, so display and state must be split |
| `CardItem` | `data/models/Item.kt` | HIGH | LOW | Inherits `Item` |
| `BoosterItem` | `data/models/BoosterItem.kt` | HIGH | MEDIUM | Inherits `Item`; 9 kinds, each with a fixed card pool to transcribe |
| `PotionItem` | `data/models/PotionItem.kt` | HIGH | LOW | Inherits `Item` |
| `Save` | `model/GameSave.kt` | CRITICAL | HIGH | Save file structure |
| `Achievements` | `model/Achievement.kt` | MEDIUM | LOW | Achievement tracking. Split into a data catalogue plus `data/AchievementRepository.kt` — the AS3 class evaluates its conditions in its constructor and so cannot be re-run |
| `NPC` | `model/Npc.kt` | HIGH | MEDIUM | NPC data |
| `NPCs` | `data/NpcCatalog.kt` | HIGH | LOW | NPC collection, loaded from `npcs.json` (`tools/extract_npcs.py`) |
| `Rank` | `model/XpTable.kt` | LOW | LOW | **Merged with `Level`** — the two AS3 classes are byte-identical apart from the class and method name |
| `Level` | `model/XpTable.kt` | LOW | LOW | See `Rank`. The AS3 loop returns level **1** at maximum XP; fixed |

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

@Serializable
enum class Direction { TOP, RIGHT, BOTTOM, LEFT }
```

> ⚠️ **Runtime state removed from `Card`.** The previous version added
> `@Transient var color`, `isDraggable`, `isFlipping` and `isSelected` as mutable
> body properties. Three problems:
> - `@Transient` only applies to primary-constructor properties in
>   kotlinx.serialization; on body properties it is meaningless.
> - Mutable `var`s in a `data class` break `equals`/`hashCode`/`copy`, so two cards
>   with the same id but different `isSelected` would compare equal — and
>   `state.copy()` would silently share them.
> - `Card` is **immutable card data** (153 + 110 fixed definitions loaded from
>   JSON). Whose card it is and whether it is mid-animation are properties of the
>   *game*, not of the card definition.
>
> Ownership (`color`) belongs on `Tile` / the player's hand. Transient UI state
> belongs in the composable or the view model:
> ```kotlin
> // ownership: on the tile that holds the card, or on the hand entry
> data class HandCard(val card: Card, val color: CardColor, val played: Boolean = false)
>
> // ephemeral UI state: in the view model, keyed by card id
> data class UiState(
>     val selectedCardId: UInt? = null,
>     val flippingTileIds: Set<Int> = emptySet(),
>     val draggingCardId: UInt? = null
> )
> ```
>
> **Also**: `element` on `Card` and `type` on `Card` overlap. In `datas/cards.as`
> there is a **single** `type` field holding either a faction (`beast`, `garlean`,
> `primals`, `scions`) or an element (`fire`, `ice`, …). There is no separate
> element field on a card — board *tiles* carry elements. Either drop
> `Card.element` and derive it from `type`, or split `type` at import time; do not
> keep both populated from one source field.

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

> ⚠️ **Redesigned.** The previous version could not work:
> - `@Transient` in kotlinx.serialization applies only to **primary-constructor**
>   properties. On body properties it is at best a no-op and at worst a compile
>   error; body properties of a `@Serializable` class are never serialised anyway.
> - Consequently `element` (declared in the body, not annotated) would silently
>   **never persist**, contradicting the `element TEXT` column in the SQLDelight
>   schema in [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md).
> - `leftTile`/`rightTile`/`topTile`/`bottomTile` create reference cycles, and
>   mutable `var`s in a `data class` break `equals`/`hashCode`/`copy`, which
>   [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md) relies on for immutable
>   state transitions via `.copy()`.
>
> Adjacency is a **property of the 3×3 grid, not of a tile**. Store it as index
> arithmetic on `Board` and keep `Tile` a flat, fully-serialisable value type:

```kotlin
@Serializable
data class Tile(
    val id: Int,                            // 0..8, row-major
    val card: Card? = null,
    val color: CardColor = CardColor.GREY,
    val element: Element = Element.NONE,
    // Effective powers after Ascension/Descension/Elemental modifiers are applied.
    // Null while the tile is empty.
    val topPow: UInt? = null,
    val rightPow: UInt? = null,
    val bottomPow: UInt? = null,
    val leftPow: UInt? = null
) {
    val row: Int get() = id / 3
    val col: Int get() = id % 3
    val isTaken: Boolean get() = card != null

    fun placeCard(card: Card, color: CardColor): Tile = copy(
        card = card,
        color = color,
        topPow = card.topPow,
        rightPow = card.rightPow,
        bottomPow = card.bottomPow,
        leftPow = card.leftPow
    )

    fun cleared(): Tile = Tile(id = id, element = element)
    fun flipped(): Tile = copy(
        color = when (color) {
            CardColor.BLUE -> CardColor.RED
            CardColor.RED -> CardColor.BLUE
            CardColor.GREY -> CardColor.GREY
        }
    )
}
```

> **Verified against `display/Tile.as`**: `_taken` is assigned only inside
> `set card()` — `true` when a non-null card is set, `false` when cleared — so it
> is exactly `_card != null` and collapsing it to a derived property is safe.
> The same setter also confirms the rest of this model:
> - `_topPow`…`_leftPow` are copied from the card on placement and set to
>   `undefined` on clear → nullable `UInt?` above.
> - `_color` is set from `_card.color` on placement and to `null` on clear. AS3
>   uses `null`; the Kotlin model uses `CardColor.GREY` for "no owner", which is
>   consistent with `Card.as` initialising `_color = 'GREY'`.
> - `_card.tile = this` establishes a back-reference that the immutable model
>   deliberately drops — resolve tile-from-card via the board index instead.

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

> ⚠️ **Redesigned.** The previous version held its state in a `private val _tiles`
> **body** property of a `@Serializable data class`. kotlinx.serialization only
> serialises primary-constructor properties, so `_tiles` was never written; a
> round-tripped `Board` would come back empty and re-run `init`, silently
> discarding the entire board. Since `GameState` embeds a `Board`, **no game state
> was actually persistable**. Tiles must live in the constructor.

```kotlin
@Serializable
data class Board(
    val tiles: List<Tile> = List(SIZE * SIZE) { Tile(id = it) }
) {
    init {
        require(tiles.size == SIZE * SIZE) { "Board must have ${SIZE * SIZE} tiles" }
    }

    operator fun get(id: Int): Tile = tiles[id]

    fun tileAt(row: Int, col: Int): Tile? =
        if (row in 0 until SIZE && col in 0 until SIZE) tiles[row * SIZE + col] else null

    // Adjacency by index arithmetic — no cyclic references, no mutable back-links.
    // Column guards prevent wrapping from col 2 to col 0 across a row boundary.
    fun neighbourId(id: Int, direction: Direction): Int? {
        val row = id / SIZE
        val col = id % SIZE
        return when (direction) {
            Direction.TOP    -> if (row > 0) id - SIZE else null
            Direction.BOTTOM -> if (row < SIZE - 1) id + SIZE else null
            Direction.LEFT   -> if (col > 0) id - 1 else null
            Direction.RIGHT  -> if (col < SIZE - 1) id + 1 else null
        }
    }

    fun neighbour(id: Int, direction: Direction): Tile? =
        neighbourId(id, direction)?.let { tiles[it] }

    /** Adjacent tiles paired with the direction from `id` towards them. */
    fun neighbours(id: Int): List<Pair<Direction, Tile>> =
        Direction.entries.mapNotNull { dir -> neighbour(id, dir)?.let { dir to it } }

    fun emptyTiles(): List<Tile> = tiles.filterNot { it.isTaken }

    // All mutators return a new Board.
    fun withTile(tile: Tile): Board =
        copy(tiles = tiles.map { if (it.id == tile.id) tile else it })

    fun placeCard(card: Card, tileId: Int, color: CardColor): Board =
        withTile(tiles[tileId].placeCard(card, color))

    fun flipTile(tileId: Int): Board = withTile(tiles[tileId].flipped())

    /** Equivalent of AS3 `razBoard()` — clears cards, preserves elements. */
    fun cleared(): Board = copy(tiles = tiles.map { it.cleared() })

    companion object { const val SIZE = 3 }
}
```

> **Why `neighbours()` returns the direction too**: the flip comparison is
> direction-dependent (`source.rightPow` vs `target.leftPow`). Returning bare
> tiles, as the previous `getAdjacentTiles(): List<Tile>` did, loses the
> information needed to compare the correct edges — and that is exactly the bug
> present in the `canFlip` sample in
> [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md).

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

**Kotlin `model/GameSave.kt`** — the plan below; see the notes after it for what changed.
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

> ⚠️ **CORRECTED 2026-07-30 — four things about this class were wrong.**
>
> 1. **`achievements` is `Map<String, Long>`, not `Map<String, Boolean>`.** `Achievements.check()`
>    writes `ACHIEVEMENTS[id] = new Date().getTime()` (`Achievements.as:79`) — the instant it was
>    earned, which is what lets the UI say when. A Boolean throws that away.
> 2. **`npcWins` is keyed by the NPC's `iconID`, not its id.** Every write site does
>    `NPC_W[this._NPC.iconID]` (`PVEMatchScreen.as:110`, `CCGroupMatchScreen.as:207`,
>    `GSGroupMatchScreen.as:208`, `TutorialScreen.as:154`). This is not an eccentricity to normalise
>    away: **NPC ids are not unique** — the ff8 table declares `id:2` (Chocoboy, UFO) and `id:13`
>    twice each — so keying by id would silently merge two opponents' records.
> 3. **`STATS.FORFEITS` must not be a stored field.** `Save.load()` overwrites whatever the file said
>    with `STARTED_MATCHES - ENDED_MATCHES` on every load (`Save.as:59`), so a stored value never
>    survives a round trip. It is a computed property (`GameSave.forfeits`).
> 4. **`NPC_W_TOTAL` is in real save files** even though `setToDefaultValues()` never declares it:
>    `PVEMatchScreen.as:172` assigns the sum onto `PROFILE_DATAS`, and `JSON.stringify(DATAS)` writes
>    whatever is on that object. It is derived, so the port ignores it on load
>    (`ignoreUnknownKeys`) and does not write it back.
>
> Also: card ids are `Int`, not `UInt`, matching `Card.id` — `UInt` buys a range no card comes near
> and costs interoperability with every list API. `LEVEL` and `RANK` are kept as fields because they
> are in the file, but `GameSave.sane()` recomputes them from XP on load, so a hand-edited or stale
> value cannot disagree with the XP it claims to represent.

---

### Item Hierarchy

**AS3 `datas/Item.as`, `CardItem.as`, `BoosterItem.as`, `PotionItem.as`** — actual code:

```actionscript
// Item.as — note: extends starling.display.Sprite (display object, not a POJO)
public class Item extends Sprite {
    public static const ITEM_TYPE_CARD:String      = 'item-type-card';
    public static const ITEM_TYPE_BOOSTER:String   = 'item-type-booster';
    public static const ITEM_TYPE_POTION:String    = 'item-type-potion';
    public static const ITEM_TYPE_ACCESSORY:String = 'item-type-accessory';
    public static const ITEM_TYPE_MISC:String      = 'item-type-misc';

    private var _bagIndex:uint;
    private var _icon:ItemIcon;      // child display object
    private var _description:String;
    private var _type:String;        // one of ITEM_TYPE_*
    private var _stack:uint;         // default 1
    private var _sellable:Boolean;   // default true
    private var _stackable:Boolean;  // default true
    private var _useable:Boolean;    // default false
    private var _dropable:Boolean;   // default true
    private var _value:uint;         // default 1 (MGP price)

    public function __toJSON():Object {
        return {type: this.type, stack: this.stack};  // only these two persist
    }
}

// CardItem.as — extends Item
public class CardItem extends Item {
    private var _cardId:uint;
    public function CardItem(cardId:uint) { ... }
}

// BoosterItem.as — extends Item (NOT CardItem)
public class BoosterItem extends Item {
    // 9 booster kinds
    public static const BOOSTER_TYPE_BRONZE:String   = 'BRONZE_BOOSTER';
    public static const BOOSTER_TYPE_SILVER:String   = 'SILVER_BOOSTER';
    public static const BOOSTER_TYPE_GOLD:String     = 'GOLD_BOOSTER';
    public static const BOOSTER_TYPE_MITHRIL:String  = 'MITHRIL_BOOSTER';
    public static const BOOSTER_TYPE_PLATINUM:String = 'PLATINUM_BOOSTER';
    public static const BOOSTER_TYPE_BEAST:String    = 'BEAST_BOOSTER';
    public static const BOOSTER_TYPE_PRIMAL:String   = 'PRIMAL_BOOSTER';
    public static const BOOSTER_TYPE_SCION:String    = 'SCION_BOOSTER';
    public static const BOOSTER_TYPE_GARLEAN:String  = 'GARLEAN_BOOSTER';

    // each kind has a fixed card pool, e.g.
    public static const BRONZE_BOOSTER_CARDS:Vector.<uint> = new <uint>[4,5,8,12,27,38];
    // ...and an icon id, e.g. BEAST_BOOSTER_ICON = 'beast_booster'

    private var _boosterType:String;
    private var _boosterCards:Vector.<uint>;
    public function open():uint { ... }   // draws one card id from the pool
}

// PotionItem.as — extends Item
public class PotionItem extends Item {
    private var _potionType:String;
    public function get modifier():Object { ... }
}
```

> ⚠️ **Corrected.** The previous revision of this section invented the AS3 field
> names (`id`, `nameKey`, `descriptionKey`, `iconId`, `rarity`) and stated that
> `BoosterItem` inherits from `CardItem`. Neither is true. `Item` has no `id`,
> `nameKey` or `rarity` field, it extends `starling.display.Sprite`, and all three
> subclasses inherit from `Item` directly. The mapping table at the top of this
> document contained the same error and has been fixed.
>
> Two consequences for the migration:
> 1. **`Item` is a display object.** It carries a child `ItemIcon` and a
>    `TouchEvent` listener. Splitting it into a plain data model plus a composable
>    is real work, not a rename.
> 2. ~~**Persistence is minimal.** `__toJSON()` writes only `{type, stack}`...~~
>    **CORRECTED 2026-07-30: there is no data-loss bug.** The base `Item.__toJSON()` writes
>    `{type, stack}`, but **each subclass overrides it** to add its own discriminator:
>    `CardItem.as:33-36` adds `card`, `BoosterItem.as:67-70` adds `booster`, `PotionItem.as:46-49`
>    adds `potion` — which is exactly what `Item.itemize` (`Item.as:161-178`) reads back. Nothing is
>    lost and nothing needed deciding.
>
> A third consequence that *is* real: **the flags are not state.** `_sellable`, `_stackable`,
> `_useable`, `_dropable` and `_value` are assigned fixed values in each subclass constructor and
> never change, so the port makes them computed properties per subtype. That makes a sellable
> booster unconstructible, which the AS3 type system allowed and the AS3 code never did.
>
> And one bug worth naming here rather than in the model: **nothing in the AS3 ever merges stacks.**
> `Achievements.check()` and `shopScreen` both `BAG.push(...)` unconditionally, so a second copy of a
> stackable item becomes a second row showing "1". `data/Inventory.kt` merges in one place.

**Kotlin `data/models/Item.kt`** — modelled on the fields that actually exist:

```kotlin
@Serializable
sealed class Item {
    abstract val descriptionKey: String
    abstract val iconId: String
    abstract val stack: UInt
    abstract val value: UInt        // MGP price
    abstract val sellable: Boolean
    abstract val stackable: Boolean
    abstract val useable: Boolean
    abstract val dropable: Boolean
}

@Serializable
@SerialName("item-type-card")        // @SerialName, not Gson's @SerializedName
data class CardItem(
    val cardId: UInt,
    override val descriptionKey: String = "",
    override val iconId: String = "card_icon",
    override val stack: UInt = 1u,
    override val value: UInt = 1u,
    override val sellable: Boolean = true,
    override val stackable: Boolean = true,
    override val useable: Boolean = false,
    override val dropable: Boolean = true
) : Item()

@Serializable
@SerialName("item-type-booster")
data class BoosterItem(
    val boosterType: BoosterType,
    override val descriptionKey: String = "",
    override val iconId: String = "booster_pack_icon",
    override val stack: UInt = 1u,
    override val value: UInt = 1u,
    override val sellable: Boolean = true,
    override val stackable: Boolean = true,
    override val useable: Boolean = true,
    override val dropable: Boolean = true
) : Item() {
    val cardPool: List<UInt> get() = BOOSTER_POOLS.getValue(boosterType)
}

@Serializable
@SerialName("item-type-potion")
data class PotionItem(
    val potionType: PotionType,
    override val descriptionKey: String = "",
    override val iconId: String = "potion_icon",
    override val stack: UInt = 1u,
    override val value: UInt = 1u,
    override val sellable: Boolean = true,
    override val stackable: Boolean = true,
    override val useable: Boolean = true,
    override val dropable: Boolean = true
) : Item()

// 9 booster kinds, matching BoosterItem.BOOSTER_TYPE_* in AS3
@Serializable
enum class BoosterType { BRONZE, SILVER, GOLD, MITHRIL, PLATINUM, BEAST, PRIMAL, SCION, GARLEAN }

// Extract the actual pools from BoosterItem.as *_BOOSTER_CARDS constants.
val BOOSTER_POOLS: Map<BoosterType, List<UInt>> = mapOf(
    BoosterType.BRONZE to listOf(4u, 5u, 8u, 12u, 27u, 38u),
    // ... transcribe the remaining 8 pools verbatim; do not re-derive them
)

// Transcribed 2026-07-30 from datas/PotionItem.as:11-23. Two boons, three tiers each; the
// serial names are the raw AS3 strings, which are also the i18n key stems and the values stored
// in Save.DATAS.BAG and named by NPCs.as reward tables (`potion:'MGP_BOOST'`).
@Serializable
enum class PotionType(val modifier: BoonModifier) {
    @SerialName("SMALL_XP_BOOST")  SMALL_XP(BoonModifier(BoonType.XP, 2)),
    @SerialName("SMALL_MGP_BOOST") SMALL_MGP(BoonModifier(BoonType.MGP, 2)),
    @SerialName("XP_BOOST")        XP(BoonModifier(BoonType.XP, 5)),
    @SerialName("MGP_BOOST")       MGP(BoonModifier(BoonType.MGP, 5)),
    @SerialName("BIG_XP_BOOST")    BIG_XP(BoonModifier(BoonType.XP, 10)),
    @SerialName("BIG_MGP_BOOST")   BIG_MGP(BoonModifier(BoonType.MGP, 10)),
}
```

> **`Save.DATAS.BOONS` has a third slot, `LUCK`, that no potion grants** — there is no
> `LUCK_BOOST_MOD` and nothing ever writes it. `Boons.luck` exists so a profile round-trips;
> `BoonType` has no member for it, because nothing can produce one.
>
> **All nine booster pools are transcribed verbatim** (`BoosterItem.as:19-27`) rather than re-derived
> from rarity or type — several are inconsistent with any rule you might infer: card 51 is in Gold,
> Platinum *and* Garlean, and Silver and Scion overlap on 19, 50 and 56. Note also that the pools
> name ids without a collection, so opening a bronze booster on an `ff8_` profile yields *ff8* card 4;
> that is the original's behaviour and `BoosterItem.open` preserves it by returning a bare id.

> ⚠️ **Three corrections applied**:
> 1. **`@SerializedName` is a Gson annotation.** With kotlinx.serialization the
>    polymorphic discriminator annotation is **`@SerialName`**. The previous
>    version would not have compiled.
> 2. **`enum class EffectType { HEAL, DAMAGE, SHIELD, etc. }`** — `etc.` is not
>    valid Kotlin, and those effect names are invented; potions in this game modify
>    MGP/XP/LUCK gain (`Save.DATAS.BOONS`), they do not heal or shield anything.
>    Replaced with `PotionType`, to be transcribed from source.
> 3. A `@Serializable sealed class` hierarchy needs the subclasses registered for
>    polymorphic serialisation — either keep them in the same file/module (the
>    compiler plugin handles sealed hierarchies automatically) and configure
>    `Json { classDiscriminator = "type" }` to match the AS3 `{type, stack}` shape.

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
public static const RULE_TYPE:String = 'STR_TYPE';   // note: STR_ prefix, like RULE_OPEN
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
@Serializable
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

// Data classes for state — every type embedded in GameState must itself be
// @Serializable, or the GameState declaration will not compile.
@Serializable
data class Player(
    val name: String,
    val color: CardColor,
    val score: Int = 0
)

@Serializable
data class TurnState(
    /**
     * Index into [timeline], 0..9. The match ends after index 9 (10 cards placed).
     * This is NOT a modular player toggle — see the note below.
     */
    val turnIndex: Int = 0,
    val timeline: List<CardColor> = emptyList(),
    val cardsPlaced: Int = 0
) {
    val currentPlayer: CardColor? get() = timeline.getOrNull(turnIndex)
    val isComplete: Boolean get() = turnIndex >= timeline.size

    fun next(): TurnState = copy(turnIndex = turnIndex + 1, cardsPlaced = cardsPlaced + 1)
}

@Serializable
data class GameState(
    val id: String,                 // supplied by the caller — see note on UUID below
    val mode: GameMode = GameMode.FF14,
    val rules: GameRules = GameRules(),
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
    val timestamp: Long           // supplied by the caller — see note below
)
```

> ⚠️ **Four corrections applied**:
>
> 1. **`UUID.randomUUID()` and `System.currentTimeMillis()` are JVM-only.**
>    Neither exists in `commonMain`, so this class could not compile for iOS.
>    Both are also **impure defaults**, which makes the state untestable and
>    non-reproducible. Inject them instead:
>    ```kotlin
>    // commonMain
>    interface Clock { fun nowMillis(): Long }
>    interface IdGenerator { fun newId(): String }
>
>    fun newGame(mode: GameMode, clock: Clock, ids: IdGenerator) =
>        GameState(id = ids.newId(), mode = mode, timestamp = clock.nowMillis())
>    ```
>    Alternatively use `kotlinx-datetime` (`Clock.System.now().toEpochMilliseconds()`)
>    and `kotlin.uuid.Uuid` (stable from Kotlin 2.1; `@ExperimentalUuidApi` in 2.0).
>
> 2. **`Player` and `TurnState` were not `@Serializable`** but were embedded in a
>    `@Serializable GameState`. That is a compile error, not a warning.
>
> 3. **`rules = GameRules.roulette(GameMode.FF14)` as a default argument** calls
>    a randomised function every time the default is used — two `GameState()`
>    instances would silently get different rule sets, and serialisation
>    round-trips would not be reproducible. Defaulted to plain `GameRules()`;
>    apply `roulette()` explicitly when the Roulette rule is active.
>
> 4. **`TurnState` semantics were wrong.** The previous version had
>    `currentTurn = (currentTurn + 1) % timeline.size` with a 2-element timeline,
>    making `currentTurn` oscillate 0,1,0,1 forever — so nothing could ever detect
>    the end of the match. In the AS3 source (`BaseMatchScreen.as:242`) `timeline`
>    is a **10-element** array of alternating colours built from the coin-flip
>    result:
>    ```actionscript
>    timeline = (pof.blueOrRed == 'blue')
>        ? new Array("RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE")
>        : new Array("BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED");
>    ```
>    and `turn` is a monotonically increasing index into it. The exact mechanism
>    (`BaseMatchScreen.as:364-372`):
>    ```actionscript
>    protected function nextTurn():void {
>        selectedCard = null;
>        updateScores();
>        turn++;                         // pre-incremented, so indexing starts at 1
>        if (turn == 10) { endGame(); }
>        else { if ('RED' == timeline[turn]) { /* red's move */ } else { /* blue's */ } }
>    }
>    ```
>    `letsGetStarted()` calls `nextTurn()` once before any card is placed, so
>    `turn` runs 1..9 — nine placements for nine tiles — and `endGame()` fires when
>    it reaches 10. `timeline[0]` is never read; the array is 10 long only because
>    the index is 1-based. **The end-of-match condition is `turn == 10`, and it is
>    the only one** — there is no "board full" check.
>
>    The corrected model keeps a monotonic `turnIndex` and derives the current
>    player, preserving both the turn order and the end condition. If you keep the
>    Kotlin `timeline` 0-based (recommended), it holds **9** entries and the match
>    ends at `turnIndex == 9`; do not copy the off-by-one.

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

### Data Models — done in Phase 2, 2026-07-30
- [x] Item (base), plus `CardItem` and `MiscItem` (the `itemize` fallback)
- [x] BoosterItem — nine pools transcribed, `open()` bias reproduced
- [x] PotionItem — six kinds, modifiers transcribed
- [x] Save/GameSave — with the four corrections noted above
- [x] Achievements — all 22, as data + `AchievementRepository`
- [x] NPC — plus `NpcCatalog` and `tools/extract_npcs.py` (85 opponents)
- [x] Rank — merged into `XpTable`
- [x] Level — merged into `XpTable`; the max-XP bug fixed
- [x] MatchRecord — new, the `MatchHistory` row from
  [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)

### Utility Types
- [x] CardColor — Phase 1
- [x] CardType — Phase 1. **`Element` is not a separate type**: `cards.as` has one `type` field
  holding either an FF14 tribe or an FF8 element, and both are compared against `tile.element` by the
  same two lines of `TTOCore.as:48-49`
- [x] CardCollection — Phase 2, in `model/Card.kt`. Replaces `GameMode`, which was the same two
  values under another name (`Save.DATAS.MODE` is `"ff14_"` / `"ff8_"`)
- [x] Direction, and the turn/phase modelling — Phase 1, in `Board.kt` / `Match.kt` / `MatchState.kt`.
  `GamePhase` as listed does not exist: five of its eleven members exist only to play an animation,
  and `MatchState` models the match as a value with a monotonic placement count instead

---

## 📞 Related Documents

- **Current System Analysis**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 2 (Data Layer)**: [06-PHASE-2-DATA-LAYER.md](./06-PHASE-2-DATA-LAYER.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: IMPLEMENTED — Phase 2, 2026-07-30. See the CORRECTED notes above.*
