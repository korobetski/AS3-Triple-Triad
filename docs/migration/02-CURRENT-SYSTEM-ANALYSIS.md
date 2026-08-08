# Current System Analysis - Triple Triad Online

## 📋 Document Information

- **Project**: Triple Triad Online
- **Analysis Date**: 2026-07-21
- **Analyst**: Migration Planning Agent
- **Status**: COMPLETE

---

## 🏗️ System Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     ADOBE AIR APPLICATION                        │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐          │
│  │    ttoboot.as        │    │   ttoclient.as       │          │
│  │ (AIR Bootstrap)     │───▶│ (Starling Entry)    │          │
│  └─────────────────────┘    └──────────┬──────────┘          │
│                                          │                     │
│                                          ▼                     │
│                              ┌─────────────────────────┐         │
│                              │      Game.as             │         │
│                              │ (Main Game Class)       │         │
│                              └────────────┬───────────┘         │
│                                       │                        │
│            ┌──────────────────────────┼────────────────────────┐  │
│            │                          │                        │  │
│            ▼                          ▼                        ▼  │
│  ┌─────────────────┐   ┌─────────────┐    ┌─────────────┐        │
│  │   screens/       │   │   datas/    │    │   utils/    │        │
│  │ (32 files)      │   │ (12 files)  │    │ (10 files)  │        │
│  └─────────────────┘   └─────────────┘    └─────────────┘        │
│            │                          │                        │  │
│            ▼                          ▼                        ▼  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    FRAMEWORK LAYER                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │   │
│  │  │ Feathers UI  │  │ Starling     │  │ Flash API   │  │   │
│  │  │ (Components) │  │ (Rendering)  │  │ (Platform)  │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 File Inventory

### Directory Structure: `sources/src/tto/`

```
sources/src/tto/
├── Game.as                    # Main game container
├── ttoboot.as                 # AIR bootstrap
├── ttoclient.as               # Starling entry point
│
├── anims/                     # 24 animation classes
│   ├── AllOpenAnim.as
│   ├── AscensionAnim.as
│   ├── BlueTurnAnim.as
│   ├── BlueWinAnim.as
│   ├── ChaosAnim.as
│   ├── ComboAnim.as
│   ├── DescensionAnim.as
│   ├── DrawAnim.as
│   ├── FallenAceAnim.as
│   ├── Mogu.as
│   ├── OrderAnim.as
│   ├── PileOuFace.as
│   ├── PlusAnim.as
│   ├── RandomAnim.as
│   ├── RedTurnAnim.as
│   ├── RedWinAnim.as
│   ├── ReverseAnim.as
│   ├── SameAnim.as
│   ├── StartAnim.as
│   ├── SuddenDeathAnim.as
│   ├── SwapAnim.as
│   ├── TalkAnim.as
│   ├── ThreeOpenAnim.as
│   └── UnlockCardAnim.as
│
├── controls/                  # 8 UI control classes
│   ├── AvatarChooser.as
│   ├── MGPLabel.as
│   ├── MainButton.as
│   ├── RoundChart.as
│   ├── TouchLabel.as
│   ├── XPLabel.as
│   ├── cardAvatar.as
│   └── cardScore.as
│
├── datas/                    # 12 data model classes
│   ├── Achievements.as
│   ├── BoosterItem.as
│   ├── CardItem.as
│   ├── Item.as
│   ├── Level.as
│   ├── NPC.as
│   ├── NPCs.as
│   ├── PotionItem.as
│   ├── Rank.as
│   ├── Save.as
│   ├── cards.as
│   └── tripleTriadRules.as
│
├── display/                  # 10 display classes
│   ├── AchievementIcon.as
│   ├── Card.as
│   ├── CardDigits.as
│   ├── CardListThumb.as
│   ├── CardThumb.as
│   ├── ImageExtended.as
│   ├── InventoryItem.as
│   ├── ItemIcon.as
│   ├── Tile.as
│   └── UserBar.as
│
├── net/                      # 2 network classes
│   ├── Socket.as
│   └── TTONet.as
│
├── screens/                  # 32 screen/panel classes
│   ├── BackstageScreen.as
│   ├── BaseMatchScreen.as
│   ├── Board.as
│   ├── CCGroupMatchScreen.as
│   ├── CCGroupRematchPanel.as
│   ├── CCGroupScreen.as
│   ├── DeckSelector.as
│   ├── DecksScreen.as
│   ├── EmptyScreen.as
│   ├── GSGroupMatchScreen.as
│   ├── GSGroupRematchPanel.as
│   ├── GSGroupScreen.as
│   ├── HelpScreen.as
│   ├── InventoryScreen.as
│   ├── LoadScreen.as
│   ├── MenuScreen.as
│   ├── NewGameScreen.as
│   ├── PVEMatchScreen.as
│   ├── PVEScreen.as
│   ├── PVPMatchScreen.as
│   ├── PVPScreen.as
│   ├── RematchPanel.as
│   ├── RulesDigest.as
│   ├── SettingsScreen.as
│   ├── TutorialRematchPanel.as
│   ├── TutorialScreen.as
│   ├── cardListScreen.as
│   ├── cardPanel.as
│   ├── dashboardScreen.as
│   ├── playerPanel.as
│   ├── profileScreen.as
│   └── shopScreen.as
│
├── theme/                    # 2 theme classes
│   ├── BaseTTOTheme.as       # 2,290 lines - Feathers theme, largest file in the project
│   └── TTOTheme.as
│
└── utils/                    # 10 utility classes
    ├── Assets.as
    ├── CryptoHelper.as
    ├── FilterProvider.as
    ├── SoundManager.as
    ├── TTOCore.as
    ├── TTOFiles.as
    ├── conf.as
    ├── gfx.as
    ├── i18n.as
    └── tools.as
```

**Total**: 103 files, 16,965 lines in `tto/` directory

> **Note**: `screens/` mixes navigable screens with in-screen panels. Actual
> breakdown by Feathers base class:
>
> | Base class | Count | Files |
> |------------|-------|-------|
> | `Screen` (direct) | 18 | BackstageScreen, BaseMatchScreen, CCGroupScreen, DecksScreen, EmptyScreen, GSGroupScreen, HelpScreen, InventoryScreen, LoadScreen, MenuScreen, NewGameScreen, PVEScreen, PVPScreen, SettingsScreen, cardListScreen, dashboardScreen, profileScreen, shopScreen |
> | `Screen` (inherited) | 5 | PVEMatchScreen, PVPMatchScreen (← BaseMatchScreen); CCGroupMatchScreen, GSGroupMatchScreen, TutorialScreen (← PVEMatchScreen) |
> | `Panel` | 6 | DeckSelector, RematchPanel, cardPanel, CCGroupRematchPanel, GSGroupRematchPanel, TutorialRematchPanel |
> | `LayoutGroup` | 3 | Board, RulesDigest, playerPanel |
>
> That is **22 concrete navigation destinations** (23 `Screen` subclasses minus
> `BaseMatchScreen`, which is only a base class) plus **9 embedded components**
> that become reusable composables, not routes. Phase 4 planning must use this
> split, not the raw file count.

---

## 🎯 Key Classes Analysis

### Critical Classes (High Priority)

| Class | File | Purpose | Complexity | Dependencies |
|-------|------|---------|------------|--------------|
| `TTOCore` | `utils/TTOCore.as` | Core game logic, rules engine | **VERY HIGH** | `tripleTriadRules`, `Card`, `Tile`, `tools` |
| `tripleTriadRules` | `datas/tripleTriadRules.as` | All rule definitions + roulette logic | **HIGH** | `tools` |
| `Card` | `display/Card.as` | Card display + drag/drop + animations | **VERY HIGH** | `CardDigits`, `SoundManager`, `Assets`, `tools`, `FilterProvider` |
| `Tile` | `display/Tile.as` | Board tile + card drop target | **HIGH** | `Card`, `DragDropManager`, `Assets`, `tools` |
| `Board` | `screens/Board.as` | 3x3 game board | **MEDIUM** | `Tile`, `TiledRowsLayout` |
| `BaseMatchScreen` | `screens/BaseMatchScreen.as` | Base class for all matches | **VERY HIGH** | `Board`, `playerPanel`, `TTOCore`, `RulesDigest`, `DeckSelector` |
| `Game` | `Game.as` | Main game container + navigation | **HIGH** | All screens, `TTOTheme`, `Assets`, `i18n`, `conf` |

### Medium Priority Classes

| Class | File | Purpose | Complexity | Dependencies |
|-------|------|---------|------------|--------------|
| `PVEMatchScreen` | `screens/PVEMatchScreen.as` | PvE match screen | **HIGH** | `BaseMatchScreen`, `NPC` |
| `PVPMatchScreen` | `screens/PVPMatchScreen.as` | PvP match screen | **HIGH** | `BaseMatchScreen`, `Socket` |
| `Socket` | `net/Socket.as` | XMLSocket communication (mostly dead code — see §8) | **MEDIUM** | `Game`, `TTONet`, `i18n`, `Save` |
| `Save` | `datas/Save.as` | Save/load system | **MEDIUM** | `CryptoHelper`, `TTOFiles` |
| `Cards` | `datas/cards.as` | All card data (FF8 + FF14) | **MEDIUM** | `Game`, `ArrayUtil` |

### Low Priority Classes

| Class | File | Purpose | Complexity | Dependencies |
|-------|------|---------|------------|--------------|
| `MenuScreen` | `screens/MenuScreen.as` | Main menu | LOW | `Game` |
| `NewGameScreen` | `screens/NewGameScreen.as` | New game creation | LOW | `Game` |
| `LoadScreen` | `screens/LoadScreen.as` | Load game | LOW | `Game`, `Save` |
| `SettingsScreen` | `screens/SettingsScreen.as` | Settings | LOW | `Game` |
| `HelpScreen` | `screens/HelpScreen.as` | Help | LOW | `Game` |

---

## 🔍 Detailed Class Analysis

### 1. TTOCore.as - Core Game Logic

**Location**: `sources/src/tto/utils/TTOCore.as`

**Purpose**: 
- Primary rules engine for Triple Triad
- Handles card flipping logic based on game rules
- Manages special rule combinations
- Coordinates animations with the screen

**Key Methods**:
- `applyRules(tile:Tile, color:String, checking:Boolean):uint` - Main entry point
- `basicRule(tile:Tile, COLOR:String):Array` - Standard flipping logic
- `specialRule(tile:Tile, COLOR:String):Array` - SAME, PLUS, SAME_WALL rules
- `comboRule(tile:Tile, enqueue:Array, bounce:uint, COLOR:String, tileComboted:Array):Array` - Combo chain logic
- `animate(tile:Tile, color:String):void` - Trigger animations

**Complexity**: **VERY HIGH**
- ~396 lines of complex logic
- Multiple rule combinations
- Recursive combo logic
- State management across multiple cards

**Migration Notes**:
- This is the **most critical** class to migrate correctly
- Must maintain exact same behavior as original
- All 17 rules must work identically
- Requires extensive testing
- Consider using property-based testing for rule validation

---

### 2. tripleTriadRules.as - Rule Definitions

**Location**: `sources/src/tto/datas/tripleTriadRules.as`

**Purpose**:
- Constants for all game rules
- Rule combinations (roulette function)
- Rule type definitions

**Key Constants**:
- `RULE_OPEN`, `RULE_DEFAULT_OPEN`, `RULE_ALL_OPEN`, `RULE_THREE_OPEN`
- `RULE_SUDDEN_DEATH`, `RULE_RANDOM`, `RULE_DEFAULT_ORDER`
- `RULE_ORDER`, `RULE_CHAOS`, `RULE_REVERSE`, `RULE_FALLEN_ACE`
- `RULE_SAME`, `RULE_SAME_WALL`, `RULE_PLUS`, `RULE_COMBO`
- `RULE_TYPE`, `RULE_DEFAULT_TYPE`, `RULE_ASCENSION`, `RULE_DESCENSION`, `RULE_ELEMENTAL`
- `RULE_SWAP`, `RULE_ROULETTE`

**Key Methods**:
- `roulette(mode:String, gameRules:Object):Object` - Random rule selection

**Complexity**: **HIGH**
- ~116 lines
- Defines all game rules
- Roulette logic for random rule selection
- Two modes: FF14 and FF8 with different rule sets

**Migration Notes**:
- Should be migrated as an `enum class` or `sealed class` in Kotlin
- `roulette` function can use Kotlin's random functions
- Rule combinations must be tested thoroughly

---

### 3. Card.as - Card Display

**Location**: `sources/src/tto/display/Card.as`

**Purpose**:
- Visual representation of a card
- Drag and drop functionality (IDragSource)
- Card animations (flip, fly, etc.)
- Power display (CardDigits)

**Key Properties**:
- `_id:uint` - Card ID
- `_collection:String` - Collection (ff14_ or ff8_)
- `_texId:String` - Texture ID
- `_data:Object` - Card data from cards.DATAS
- `_color:String` - BLUE, RED, or GREY
- `_tile:Tile` - Reference to tile
- `_draggable:Boolean` - Can be dragged
- `_flipping:Boolean` - Currently animating
- `_selected:Boolean` - Selected state

**Key Methods**:
- `draw(newID:String, collection:String):void` - Draw card with ID
- `fly(_x:int, _y:int):void` - Animate card flying to position
- `flyAndSwap(_x:int, _y:int, newId:uint):void` - Fly and change card
- `flipTo(horizon:Boolean, color:String):void` - Flip to specific color
- `flip(horizon:Boolean):void` - Flip with color switch
- `backToFront():void` - Flip back to front
- `onTouch(event:TouchEvent):void` - Handle touch for drag/drop

**Complexity**: **VERY HIGH**
- ~424 lines
- Multiple animation types
- Drag and drop integration
- Touch handling
- Complex state management

**Migration Notes**:
- Split into display (`@Composable fun CardComponent`) and state (data class)
- Animations should use Compose Animation API
- Drag and drop needs custom implementation in Compose
- Touch handling via `pointerInput` and gesture detectors

---

### 4. Tile.as - Board Tile

**Location**: `sources/src/tto/display/Tile.as`

**Purpose**:
- Represents a tile on the game board
- Drop target for cards (IDropTarget)
- Manages card placement and powers
- Handles element types

**Key Properties**:
- `_card:Card` - Card on this tile
- `_taken:Boolean` - Whether a card is placed
- `_element:String` - Element type (earth, fire, etc.)
- `_id:uint` - Tile ID (0-8)
- `_color:String` - BLUE or RED (when taken)
- `_leftTile:Tile`, `_rightTile:Tile`, `_topTile:Tile`, `_bottomTile:Tile` - Adjacent tiles
- `_leftPow:uint`, `_rightPow:uint`, `_bottomPow:uint`, `_topPow:uint` - Current powers

**Key Methods**:
- `onTouch(e:TouchEvent):void` - Handle tile touch
- `onDragEnter(event:DragDropEvent):void` - Handle drag enter
- `onDragDrop(event:DragDropEvent):void` - Handle card drop
- `onDragExit(event:DragDropEvent):void` - Handle drag exit
- Property getters/setters for all properties

**Complexity**: **HIGH**
- ~302 lines
- Drag and drop implementation
- Adjacent tile references
- Power management
- Element handling

**Migration Notes**:
- Data class for state + `@Composable` for display
- Adjacent references need special handling in Kotlin (use weak references or indexes)
- Drag and drop needs custom implementation
- Power calculations must match original exactly

---

### 5. Board.as - Game Board

**Location**: `sources/src/tto/screens/Board.as`

**Purpose**:
- Manages 3x3 game board
- Contains 9 tiles
- Handles board layout
- Connects adjacent tiles

**Key Properties**:
- `tiles:Vector.<Tile>` - Array of 9 tiles

**Key Methods**:
- `Board()` - Constructor, creates all tiles
- `razBoard():void` - Clear all cards from board
- `elements(predefined:Array):void` - Set element types on tiles
- `getRemainingTiles():Array` - Get tiles without cards

**Complexity**: **MEDIUM**
- ~83 lines
- Simple board management
- Tile creation and connection

**Migration Notes**:
- Straightforward migration to Kotlin class
- Use `List<Tile>` instead of Vector
- Board size is fixed at 3x3
- TiledRowsLayout → LazyVerticalGrid with GridCells.Fixed(3)

---

### 6. BaseMatchScreen.as - Base Match Screen

**Location**: `sources/src/tto/screens/BaseMatchScreen.as`

**Purpose**:
- Base class for all match screens (PvE, PvP)
- Manages game flow through phases
- Handles card placement and rules
- Manages turn system

**Key Properties**:
- `bluePlayer:playerPanel`, `redPlayer:playerPanel` - Player panels
- `board:Board` - Game board
- `deckSelector:DeckSelector` - Deck selection UI
- `timeline:Array` - Turn order
- `turn:int` - Current turn
- `boardScores:cardScore` - Score display
- `ascensionByType:Object` - Type bonuses
- `selectedCard:Card` - Currently selected card
- `RULES:Object` - Game rules
- `CORE:TTOCore` - Rules engine

**Key Methods**:
- `initialize():void` - Setup screen
- `deckSelectionPhase():void` - Start deck selection
- `openPhase():void` - Handle open rule
- `orderPhase():void` - Handle order rule
- `reversePhase():void` - Handle reverse rule
- `fallenAcePhase():void` - Handle fallen ace rule
- `swapPhase():void` - Handle swap rule
- `pileOuFace():void` - Handle coin flip
- `letsGetStarted():void` - Start the match
- `cardTouched(e:Event):void` - Handle card touch
- `tileTouched(e:Event):void` - Handle tile touch
- `cardDropedOnTile(event:Event):void` - Handle card drop
- `cardOnTile(card:Card, tile:Tile):void` - Place card from network
- `unselect():void` - Clear selection
- `updateScores():Object` - Calculate scores
- `nextTurn():void` - Advance to next turn
- `opponentPhase():void` - AI/opponent turn
- `endGame():void` - End the match
- `rematch(params:Object):void` - Show rematch panel
- `suddenDeathDispatcher():void` - Handle sudden death
- `autoPlay():void` - Auto-play for AI/testing

**Complexity**: **VERY HIGH**
- ~448 lines
- Complex game flow through multiple phases
- Turn management
- Score calculation
- Rule application
- Card placement logic

**Migration Notes**:
- **Most complex screen** to migrate
- Game flow uses `setTimeout` for phases → Use Kotlin Coroutines with delays
- Must maintain exact same phase sequence
- Turn management is critical
- Network integration point (for PvP)

---

### 7. Game.as - Main Game Container

**Location**: `sources/src/tto/Game.as`

**Purpose**:
- Main game class
- Manages screen navigation
- Global state management
- Asset loading

**Key Properties**:
- `nav:ScreenNavigator` - Feathers screen navigator
- `asset:Assets` - Asset loader
- `LOGGED_IN:Boolean` - Login state (static)
- `PROFILE_DATAS:Object` - Player profile (static)
- `MATCHES:Object` - Active matches (static)
- `USERS:Array` - Online users (static)

**Key Methods**:
- `Game()` - Constructor
- `init(e:Event):void` - Initialize on add to stage
- `assetsLoaded(e:Event):void` - Handle asset loading complete
- `showMenu():void` - Show main menu
- `connection(e:Event):void` - Handle connection event
- `gotoScreenHandler(e:Event, data:String):void` - Screen navigation handler
- `gotoScreen(ScreenName:String):void` - Navigate to screen
- `prepareMatch(e:Event, data:Object):void` - Prepare match (instance method)
- `prepareMatch(data:Object):void` - Prepare match (static method)
- `ccMatch(e:Event, data:Object):void` - CC group match
- `gsMatch(e:Event, data:Object):void` - GS group match

**Complexity**: **HIGH**
- ~142 lines
- Screen management
- Global state (static properties)
- Asset loading coordination

**Migration Notes**:
- ScreenNavigator → NavHost + NavController
- Static properties → Singleton or dependency injection
- Asset loading → Compose + Coil/Accompanist
- Navigation → Compose Navigation

---

## 📊 Statistics

### File Count by Directory

Measured with `wc -l` over `sources/src/tto/**/*.as`:

| Directory | Files | Lines | Complexity |
|-----------|-------|-------|------------|
| Root | 3 | 422 | Medium |
| anims/ | 24 | 1,454 | Medium |
| controls/ | 8 | 836 | Medium |
| datas/ | 12 | 2,527 | Medium |
| display/ | 10 | 1,247 | High |
| net/ | 2 | 698 | High |
| screens/ | 32 | 6,343 | Very High |
| theme/ | 2 | 2,406 | Low (mechanical) |
| utils/ | 10 | 1,032 | High |
| **Total** | **103** | **16,965** | **High** |

### Largest Files (migration effort concentrates here)

| File | Lines | Notes |
|------|-------|-------|
| `theme/BaseTTOTheme.as` | 2,290 | Feathers skinning boilerplate — **mostly discardable**, replaced by a ~200-line Compose theme |
| `datas/NPCs.as` | 1,161 | Static NPC/opponent data — convert to JSON, near-zero logic |
| `net/Socket.as` | 649 | See §8: most of it is dead code |
| `screens/BaseMatchScreen.as` | 447 | Highest genuine complexity |
| `screens/DecksScreen.as` | 423 | |
| `display/Card.as` | 423 | |
| `controls/MainButton.as` | 402 | Hand-rolled Starling button → one Compose `Button` |
| `utils/TTOCore.as` | 395 | Rules engine — the critical path |
| `screens/PVPMatchScreen.as` | 376 | |
| `screens/PVEScreen.as` | 373 | |

**Effective migration surface**: of the 16,965 lines, roughly 3,450 (`BaseTTOTheme`,
`NPCs`, `MainButton`) are boilerplate or static data that shrink dramatically or
convert to resources. Line count alone overstates the work; `TTOCore.as` +
`BaseMatchScreen.as` + `Card.as` + `Tile.as` (~1,570 lines) carry most of the risk.

---

## 🔗 Dependencies Analysis

### Framework Dependencies

| Framework | Usage | Replacement |
|-----------|-------|-------------|
| **Flash API** | DisplayObject, MovieClip, Sprite, Loader, Stage, etc. | Compose + Android/iOS APIs |
| **Feathers UI** | Screen, ScreenNavigator, Button, Alert, etc. | Compose Navigation + Material |
| **Starling** | Sprite, Image, Texture, Quad, TouchEvent, etc. | Compose Canvas + Skia |
| **Adobe Utils** | ArrayUtil | Kotlin Collections |

### External Dependencies

The `sources/src/` tree contains **579 `.as` files / ~186,000 lines** in total;
only 103 files / 16,965 lines are game code under `tto/`. The rest are vendored
third-party libraries that must be accounted for:

| Library | Package | Usage | Replacement |
|---------|---------|-------|-------------|
| **Starling** | `starling.*` | GPU rendering, display list, TextField, Texture, TouchEvent | Compose Canvas / Skia |
| **Feathers UI** | `feathers.*` | Screen, ScreenNavigator, Panel, LayoutGroup, Label, Alert, DragDropManager, themes | Compose + Navigation |
| **as3crypto** | `com.hurlant.*` | `AESKey`, `Hex` — used by `utils/CryptoHelper.as` to encrypt `.sav` files | `javax.crypto` (Android) / CryptoKit (iOS) via expect/actual, or Krypto (KMP) |
| **Adobe corelib** | `com.adobe.*` | `ArrayUtil` (`copyArray`, `arrayContainsValue`) in `tools.as`, `cards.as` | Kotlin Collections |
| **Adobe AIR SDK** | `flash.*`, `flash.filesystem.*`, `flash.desktop.*` | File I/O, XMLSocket, Capabilities, NativeApplication | Ktor, okio/expect-actual file APIs |

> **Note**: `CryptoHelper` uses AES from as3crypto. Existing `.sav` files are
> AES-encrypted JSON. To read legacy saves the Kotlin implementation must
> reproduce the exact key derivation, mode and padding used by
> `com.hurlant.crypto.symmetric.AESKey` — inspect `utils/CryptoHelper.as` before
> assuming save-file compatibility is free.

### Internal Dependencies

```
Game.as
├── All screens (32 files)
├── TTOTheme.as
├── Assets (utils/Assets.as)
├── i18n (utils/i18n.as)
└── conf (utils/conf.as)

TTOCore.as (utils/TTOCore.as)
├── tripleTriadRules (datas/tripleTriadRules.as)
├── Card (display/Card.as)
├── Tile (display/Tile.as)
└── tools (utils/tools.as)

BaseMatchScreen.as (screens/BaseMatchScreen.as)
├── Board (screens/Board.as)
├── playerPanel (screens/playerPanel.as)
├── TTOCore (utils/TTOCore.as)
├── RulesDigest (screens/RulesDigest.as)
├── DeckSelector (screens/DeckSelector.as)
├── cardScore (controls/cardScore.as)
└── SoundManager (utils/SoundManager.as)

Card.as (display/Card.as)
├── CardDigits (display/CardDigits.as)
├── Assets (utils/Assets.as)
├── SoundManager (utils/SoundManager.as)
├── FilterProvider (utils/FilterProvider.as)
└── tools (utils/tools.as)

Tile.as (display/Tile.as)
├── Card (display/Card.as)
├── DragDropManager (Feathers)
├── Assets (utils/Assets.as)
└── tools (utils/tools.as)

Socket.as (net/Socket.as)
├── Game.as
├── TTONet (net/TTONet.as)
├── i18n (utils/i18n.as)
├── Save (datas/Save.as)
├── tripleTriadRules (datas/tripleTriadRules.as)
├── Card (display/Card.as)
├── Tile (display/Tile.as)
├── BaseMatchScreen (screens/BaseMatchScreen.as)
├── PVPScreen (screens/PVPScreen.as)
├── playerPanel (screens/playerPanel.as)
└── tools (utils/tools.as)
```

---

## 📝 Key Findings

### Strengths of Current System

1. **Well-Structured**: Clear separation of concerns
2. **Event-Driven**: Good use of events for decoupling
3. **Component-Based**: Feathers UI components are reusable
4. **GPU-Accelerated**: Starling provides good performance
5. **Complete Feature Set**: All Triple Triad rules implemented

### Challenges for Migration

1. **Tight Coupling**: Some classes have many dependencies
2. **Static Properties**: Global state in Game.as
3. **Callback Hell**: Heavy use of event listeners
4. **Legacy APIs**: Flash API and XMLSocket are outdated
5. **Embedded Assets**: Cards and images are embedded
6. **Drag & Drop**: Custom implementation needed for Compose

### Critical Migration Points

1. **TTOCore**: Must be migrated perfectly - contains core game logic
2. **BaseMatchScreen**: Complex game flow - must maintain exact behavior
3. **Socket**: Network protocol - need to understand server communication
4. **Card**: Visual + logic combined - split into display and state
5. **Tile**: Adjacent references - careful handling in Kotlin

---
