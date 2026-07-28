# Event Catalog — `sources/src/tto`

Phase 0, Task 1.3 deliverable 2. Every event the game dispatches or listens for, and
what it becomes in Kotlin.

Counts come from
[`docs/analysis/tools/analyse_as3.py`](./tools/analyse_as3.py)-adjacent greps over
`sources/src/tto`; each entry cites the file and line it was read from so it can be
checked.

---

## 1. Summary

The game defines **three** custom event types of its own. Everything else is a
Starling, Feathers or Flash built-in. There is no event bus, no typed event classes and
no payload objects: events carry either nothing or a single untyped `data` field.

| Kind | Distinct types | Notes |
|---|--:|---|
| Custom (declared in `tto`) | 3 | plain `String` constants, listed in §3 |
| Starling built-ins | 8 | `TRIGGERED`, `ADDED_TO_STAGE`, `CHANGE`, `COMPLETE`, `TOUCH`, `ENTER_FRAME`, `SCROLL`, `ACTIVATE`/`DEACTIVATE` |
| Feathers built-ins | 3 | the three `DragDropEvent` phases |
| Flash/AIR built-ins | 12 | socket, file I/O, keyboard, idle, network-change |
| Navigation strings | 7 | not events in the type system — see §2 |

`Event.TRIGGERED` alone accounts for 57 of the ~150 `addEventListener` calls: it is
this codebase's universal "a button was pressed" signal.

---

## 2. Navigation: `gotoScreen` and friends

The single most-used event string in the codebase is not an event class at all.

```as3
// Game.as:55
nav.addScreen('MENU_SCREEN', new ScreenNavigatorItem(MenuScreen, {gotoScreen: gotoScreenHandler}));

// Game.as, gotoScreenHandler
private function gotoScreenHandler(e:Event, data:String):void {
    nav.showScreen(data);
}
```

Feathers' `ScreenNavigatorItem` takes a map of *event name to handler*. A screen calls
`dispatchEventWith('gotoScreen', false, 'DASHBOARD')` and the navigator resolves the
string. This happens **31 times** across `screens/`.

| String | Dispatches | Registered on | Handler |
|---|--:|---|---|
| `gotoScreen` | 31 | all 21 registered screens | `Game.gotoScreenHandler` → `nav.showScreen(data)` |
| `pve_free_mode` | 3 | `PVE_SCREEN`, `PVE_MATCH_SCREEN` | `Game.prepareMatch` |
| `pvp_free_mode` | 2 | `PVP_SCREEN`, `PVP_MATCH_SCREEN` | `Game.prepareMatch` |
| `sudden_death` | 3 | both match screens | `Game.prepareMatch` |
| `cc_next_match` | 1 | `CCGROUP_MATCH_SCREEN` | `Game.ccMatch` |
| `cc_sudden_death` | 1 | `CCGROUP_MATCH_SCREEN` | `Game.ccMatch` |
| `gs_next_match` | 1 | `GSGROUP_MATCH_SCREEN` | `Game.gsMatch` |
| `gs_sudden_death` | 1 | `GSGROUP_MATCH_SCREEN` | `Game.gsMatch` |

**Migration.** These are string-keyed and unchecked; a typo compiles and silently does
nothing. In Kotlin this becomes a sealed navigation type, checked at compile time:

```kotlin
sealed interface Destination {
    data object Menu : Destination
    data object Dashboard : Destination
    data class PveMatch(val config: MatchConfig) : Destination
    data class SuddenDeath(val previous: MatchState) : Destination
    // …
}
```

That also removes the `prepareMatch(e, data)` overloading, where one handler serves
three different events and re-reads global state to work out which case it is in.

---

## 3. Custom events declared in `tto`

Three, all `public static const … :String`.

### 3.1 `Tile.CARD_DROPED_ON_TILE_EVENT`

```as3
// display/Tile.as:24
public static const CARD_DROPED_ON_TILE_EVENT:String = 'CARD_DROPED_ON_TILE_EVENT';

// display/Tile.as:122, inside onDragDrop
this.parent.dispatchEventWith(Tile.CARD_DROPED_ON_TILE_EVENT, false, this);
```

- **Payload**: the `Tile` itself, in the untyped `data` slot.
- **Dispatched on**: `this.parent` (the `Board`), not the tile — a deliberate re-target so
  the board can arbitrate.
- **Listeners**: 1, in `screens/Board.as`.
- **Preceded by**: `DragDropEvent.DRAG_ENTER` → `DragDropManager.acceptDrag(this)` →
  `DragDropEvent.DRAG_DROP` (`Tile.as:93-95`).

**Migration.** The whole drag-and-drop chain (`IDragSource` on `Card`, `IDropTarget` on
`Tile`, `DragDropManager` as broker) has no Compose equivalent and must be rebuilt on
`Modifier.pointerInput` with `detectDragGestures`, with the board owning the drop
resolution as a state hoist rather than an event. This is the single largest UI-layer
risk in the plan and it is **not** covered by the Kotlin PoC.

### 3.2 `cardPanel.CARD_SELECTED_EVENT`

```as3
// screens/cardPanel.as
public static const CARD_SELECTED_EVENT:String = 'cardPanel_cardSelected';
```

- **Payload**: the selected card.
- Note the constant name and its value disagree in style; both spellings appear in
  `grep`, so search for either.

### 3.3 `playerPanel.TIME_UP_EVENT`

```as3
// screens/playerPanel.as
public static const TIME_UP_EVENT:String = 'TIME_UP';
```

- **Payload**: none.
- Fires when a turn timer expires; drives the forced-move path.
- **Migration**: a coroutine timer in the match ViewModel, not an event.

---

## 4. Built-in events, by frequency

Counted over all `addEventListener` call sites in `sources/src/tto`.

| Uses | Event | Origin | Kotlin equivalent |
|--:|---|---|---|
| 57 | `Event.TRIGGERED` | Starling / Feathers | `onClick` lambda parameter |
| 24 | `Event.ADDED_TO_STAGE` | Starling | `LaunchedEffect(Unit)` in the composable |
| 14 | `Event.CHANGE` | Feathers | state hoisting — `onValueChange` |
| 10 | `TouchEvent.TOUCH` | Starling | `Modifier.pointerInput` / `clickable` |
| 8 | `Event.COMPLETE` | Starling / Flash | `suspend fun` return |
| 3 | `DragDropEvent.*` | Feathers | `detectDragGestures` (see §3.1) |
| 3 | `ProgressEvent.PROGRESS` | Flash | `Flow<Float>` |
| 3 | `IOErrorEvent.IO_ERROR` | Flash | `Result` / thrown exception |
| 2 | `Event.ENTER_FRAME` | Starling | `withFrameNanos` / `Animatable` |
| 2 | `Event.CLOSE`, `Event.CLOSING` | Flash (NativeWindow) | Android/desktop lifecycle |
| 2 | `Event.SCROLL` | Feathers | `LazyListState` |
| 1 | `Event.CONNECT`, `DataEvent.DATA`, `SecurityErrorEvent` | Flash `XMLSocket` | see [network-protocol.md](./network-protocol.md) |
| 1 | `KeyboardEvent.KEY_DOWN` | Flash | `Modifier.onKeyEvent` |
| 1 | `Event.USER_IDLE`, `Event.USER_PRESENT`, `Event.NETWORK_CHANGE` | AIR `NativeApplication` | **dead** — only in `net/TTONet.as`, which nothing references |
| 1 | `Event.ACTIVATE` / `Event.DEACTIVATE` | Flash | lifecycle |
| 1 | `Event.INIT` | Flash `Loader` | — |
| 1 | `MouseEvent.CLICK` | Flash (not Starling) | `clickable` |
| 1 | `"triggered"` | **string literal** | typo-prone duplicate of `Event.TRIGGERED` |

Two findings worth acting on:

- **`"triggered"` as a bare string.** One call site listens on the literal instead of the
  constant. It happens to match, but nothing enforces that. Migration should not carry
  the pattern over.
- **`Event.USER_IDLE` / `USER_PRESENT` / `NETWORK_CHANGE` are unreachable.** They live
  only in `net/TTONet.as`, which declares the *default* package (`package {`) while
  sitting in `tto/net/`, and whose class name appears in no other file. It is dead. Do
  not budget for idle detection or network-change handling as "existing behaviour to
  preserve" — it was never wired up.

---

## 5. Anti-patterns to leave behind

| Pattern | Where | Why it matters |
|---|---|---|
| Untyped `data` payloads | all three custom events | `dispatchEventWith(type, bubbles, data:Object)`; every listener casts. Compose state is typed. |
| String-keyed navigation | 31 `gotoScreen` sites | unchecked; a typo is a silent no-op |
| Screens reaching into `PVPScreen`'s static fields | `net/Socket.as:86-90` | the socket layer writes UI labels directly (`PVPScreen.pingLabel.text = …`). This inverted dependency has to be cut before either layer can be migrated independently. |
| `dispatchEventWith` on `this.parent` | `Tile.as:122` | the child assumes its parent's type |
| One handler, three events | `Game.prepareMatch` | serves `pve_free_mode`, `pvp_free_mode`, `sudden_death` and re-derives the case from globals |

---

## 6. Related

- [dependency-matrix.md](./dependency-matrix.md) — who imports whom
- [api-mapping.md](./api-mapping.md) — AS3 → Kotlin type translations
- [network-protocol.md](./network-protocol.md) — the socket events in detail
- [docs/migration/14-COMPONENT-MAPPING.md](../migration/14-COMPONENT-MAPPING.md) — Feathers → Compose
