# Dependency Matrix — `sources/src/tto`

> **Generated file.** Produced by
> [`docs/analysis/tools/analyse_as3.py`](./tools/analyse_as3.py); run
> `python docs/analysis/tools/analyse_as3.py` from the repository root to
> refresh it. Do not edit by hand — edit the script.

- Files analysed: **103**
- Lines: **17,066**
- Classes found: **103**
- Distinct external imports: **167** (718 import statements)

Only the game's own package is analysed. The wider `sources/src/` tree also
contains vendored Starling, Feathers UI, as3crypto and Adobe corelib, which are
dependencies to be *replaced*, not code to be migrated.

## 1. Size by package

| Package | Files | Lines | Share |
|---|--:|--:|--:|
| `(root)` | 3 | 425 | 2.5% |
| `anims` | 24 | 1,478 | 8.7% |
| `controls` | 8 | 844 | 4.9% |
| `datas` | 12 | 2,539 | 14.9% |
| `display` | 10 | 1,257 | 7.4% |
| `net` | 2 | 700 | 4.1% |
| `screens` | 32 | 6,375 | 37.4% |
| `theme` | 2 | 2,406 | 14.1% |
| `utils` | 10 | 1,042 | 6.1% |
| **total** | **103** | **17,066** | 100% |

## 2. Package-to-package coupling

Rows import columns. The cell is the number of `import` statements, so a high
number means many files in that package reach into the target.

| from \ to | `(root)` | `anims` | `controls` | `datas` | `display` | `net` | `screens` | `theme` | `utils` |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| `(root)` | · | · | · | · | · | · | · | 1 | 7 |
| `anims` | 1 | · | · | · | 2 | · | · | · | 27 |
| `controls` | 1 | · | · | 1 | 1 | · | · | · | 10 |
| `datas` | 3 | · | · | · | 1 | · | 1 | · | 12 |
| `display` | 2 | · | 1 | 2 | 2 | · | · | · | 16 |
| `net` | 1 | · | · | 2 | 2 | · | 3 | · | 1 |
| `screens` | 26 | 26 | 11 | 61 | 31 | 3 | · | · | 76 |
| `theme` | · | · | · | · | · | · | · | · | 1 |
| `utils` | · | 3 | · | 1 | 2 | · | 1 | · | · |

## 3. Most-depended-on classes (migration order)

Fan-in is the number of files that import a class. These have to be migrated
first: everything else is waiting on them.

| Fan-in | File | Lines | Class | Extends |
|--:|---|--:|---|---|
| 57 | `utils/Assets.as` | 36 | `Assets` | `EventDispatcher` |
| 36 | `utils/i18n.as` | 42 | `i18n` | — |
| 34 | `Game.as` | 142 | `Game` | `Sprite` |
| 26 | `utils/tools.as` | 159 | `tools` | — |
| 22 | `datas/Save.as` | 98 | `Save` | — |
| 14 | `utils/SoundManager.as` | 133 | `SoundManager` | — |
| 13 | `display/Card.as` | 424 | `Card` | `Sprite` |
| 13 | `datas/tripleTriadRules.as` | 116 | `tripleTriadRules` | — |
| 11 | `display/UserBar.as` | 142 | `UserBar` | `FeathersControl` |
| 9 | `display/Tile.as` | 302 | `Tile` | `Sprite` |
| 7 | `datas/Achievements.as` | 94 | `Achievements` | — |
| 6 | `utils/TTOFiles.as` | 132 | `TTOFiles` | — |
| 6 | `controls/MGPLabel.as` | 63 | `MGPLabel` | `LayoutGroup` |
| 5 | `utils/conf.as` | 55 | `conf` | — |
| 5 | `datas/cards.as` | 321 | `cards` | — |
| 5 | `anims/BlueWinAnim.as` | 58 | `BlueWinAnim` | `Sprite` |
| 5 | `anims/RedWinAnim.as` | 58 | `RedWinAnim` | `Sprite` |
| 5 | `anims/DrawAnim.as` | 58 | `DrawAnim` | `Sprite` |
| 5 | `datas/NPC.as` | 300 | `NPC` | `Object` |
| 4 | `display/CardThumb.as` | 90 | `CardThumb` | `Sprite` |

## 4. Highest fan-out (hardest single files)

Files that import the most other `tto` classes. High fan-out plus high line
count is where migration effort concentrates.

| tto imports | File | Lines | External imports |
|--:|---|--:|--:|
| 18 | `screens/PVPMatchScreen.as` | 377 | 3 |
| 15 | `screens/CCGroupMatchScreen.as` | 280 | 1 |
| 15 | `screens/GSGroupMatchScreen.as` | 281 | 1 |
| 14 | `screens/PVEMatchScreen.as` | 255 | 1 |
| 14 | `screens/TutorialScreen.as` | 260 | 1 |
| 11 | `screens/profileScreen.as` | 254 | 14 |
| 10 | `screens/BaseMatchScreen.as` | 448 | 6 |
| 10 | `screens/InventoryScreen.as` | 288 | 9 |
| 10 | `screens/RematchPanel.as` | 132 | 10 |
| 10 | `screens/shopScreen.as` | 166 | 9 |
| 9 | `net/Socket.as` | 650 | 13 |
| 9 | `screens/DecksScreen.as` | 424 | 21 |
| 9 | `screens/PVEScreen.as` | 374 | 12 |
| 8 | `screens/cardListScreen.as` | 237 | 20 |
| 7 | `display/Card.as` | 424 | 14 |
| 7 | `screens/PVPScreen.as` | 363 | 15 |
| 7 | `utils/TTOCore.as` | 396 | 2 |
| 6 | `screens/CCGroupScreen.as` | 122 | 9 |
| 6 | `screens/GSGroupScreen.as` | 123 | 11 |
| 6 | `screens/MenuScreen.as` | 131 | 10 |

## 5. Largest files

| Lines | File | tto imports | External imports |
|--:|---|--:|--:|
| 2,290 | `theme/BaseTTOTheme.as` | 1 | 70 |
| 1,162 | `datas/NPCs.as` | 1 | 1 |
| 650 | `net/Socket.as` | 9 | 13 |
| 448 | `screens/BaseMatchScreen.as` | 10 | 6 |
| 424 | `display/Card.as` | 7 | 14 |
| 424 | `screens/DecksScreen.as` | 9 | 21 |
| 403 | `controls/MainButton.as` | 3 | 15 |
| 396 | `utils/TTOCore.as` | 7 | 2 |
| 377 | `screens/PVPMatchScreen.as` | 18 | 3 |
| 374 | `screens/PVEScreen.as` | 9 | 12 |
| 363 | `screens/PVPScreen.as` | 7 | 15 |
| 326 | `screens/playerPanel.as` | 4 | 10 |
| 321 | `datas/cards.as` | 1 | 1 |
| 302 | `display/Tile.as` | 3 | 13 |
| 300 | `datas/NPC.as` | 3 | 2 |
| 288 | `screens/InventoryScreen.as` | 10 | 9 |
| 281 | `screens/GSGroupMatchScreen.as` | 15 | 1 |
| 280 | `screens/CCGroupMatchScreen.as` | 15 | 1 |
| 260 | `screens/TutorialScreen.as` | 14 | 1 |
| 255 | `screens/PVEMatchScreen.as` | 14 | 1 |

## 6. External runtimes to replace

| Runtime | Import statements | Distinct imports |
|---|--:|--:|
| Starling (GPU display list) | 310 | 26 |
| Feathers UI (widget toolkit) | 244 | 67 |
| Adobe AIR / Flash runtime | 148 | 71 |
| Adobe corelib (vendored) | 14 | 1 |
| as3crypto (vendored) | 2 | 2 |

The 20 most-used external types, which are the ones worth a documented
Kotlin equivalent in [api-mapping.md](./api-mapping.md):

| Uses | Import | Runtime |
|--:|---|---|
| 58 | `starling.events.Event` | Starling (GPU display list) |
| 52 | `starling.display.Image` | Starling (GPU display list) |
| 35 | `starling.display.Sprite` | Starling (GPU display list) |
| 30 | `flash.utils.setTimeout` | Adobe AIR / Flash runtime |
| 27 | `starling.core.Starling` | Starling (GPU display list) |
| 22 | `starling.display.DisplayObject` | Starling (GPU display list) |
| 20 | `feathers.controls.Label` | Feathers UI (widget toolkit) |
| 20 | `feathers.controls.Header` | Feathers UI (widget toolkit) |
| 18 | `feathers.controls.Panel` | Feathers UI (widget toolkit) |
| 18 | `feathers.controls.Screen` | Feathers UI (widget toolkit) |
| 17 | `starling.animation.Transitions` | Starling (GPU display list) |
| 14 | `com.adobe.utils.ArrayUtil` | Adobe corelib (vendored) |
| 14 | `feathers.data.ListCollection` | Feathers UI (widget toolkit) |
| 12 | `feathers.controls.LayoutGroup` | Feathers UI (widget toolkit) |
| 12 | `starling.textures.Texture` | Starling (GPU display list) |
| 11 | `starling.events.Touch` | Starling (GPU display list) |
| 11 | `starling.events.TouchEvent` | Starling (GPU display list) |
| 11 | `starling.events.TouchPhase` | Starling (GPU display list) |
| 11 | `feathers.controls.Button` | Feathers UI (widget toolkit) |
| 10 | `starling.display.Button` | Starling (GPU display list) |

## 7. Base classes

What the game's classes extend, which decides what each one becomes in Compose.

| Count | Extends |
|--:|---|
| 34 | `Sprite` |
| 18 | `(nothing)` |
| 18 | `Screen` |
| 5 | `LayoutGroup` |
| 4 | `DisplayObjectContainer` |
| 3 | `Item` |
| 3 | `Panel` |
| 3 | `PVEMatchScreen` |
| 3 | `RematchPanel` |
| 2 | `Image` |
| 2 | `BaseMatchScreen` |
| 1 | `MovieClip` |
| 1 | `Label` |
| 1 | `Object` |
| 1 | `Button` |
| 1 | `FeathersControl` |
| 1 | `StyleNameFunctionTheme` |
| 1 | `BaseTTOTheme` |
| 1 | `EventDispatcher` |

## 8. Full file list

| File | Lines | Class | Extends | Implements | tto deps | ext deps |
|---|--:|---|---|---|--:|--:|
| `anims/AllOpenAnim.as` | 57 | `AllOpenAnim` | `Sprite` | — | 1 | 6 |
| `anims/AscensionAnim.as` | 57 | `AscensionAnim` | `Sprite` | — | 1 | 5 |
| `anims/BlueTurnAnim.as` | 56 | `BlueTurnAnim` | `Sprite` | — | 1 | 6 |
| `anims/BlueWinAnim.as` | 58 | `BlueWinAnim` | `Sprite` | — | 1 | 6 |
| `anims/ChaosAnim.as` | 58 | `ChaosAnim` | `Sprite` | — | 1 | 6 |
| `anims/ComboAnim.as` | 57 | `ComboAnim` | `Sprite` | — | 1 | 5 |
| `anims/DescensionAnim.as` | 57 | `DescensionAnim` | `Sprite` | — | 1 | 5 |
| `anims/DrawAnim.as` | 58 | `DrawAnim` | `Sprite` | — | 1 | 6 |
| `anims/FallenAceAnim.as` | 58 | `FallenAceAnim` | `Sprite` | — | 1 | 6 |
| `anims/Mogu.as` | 56 | `Mogu` | `MovieClip` | — | 2 | 3 |
| `anims/OrderAnim.as` | 58 | `OrderAnim` | `Sprite` | — | 1 | 6 |
| `anims/PileOuFace.as` | 134 | `PileOuFace` | `DisplayObjectContainer` | — | 3 | 4 |
| `anims/PlusAnim.as` | 57 | `PlusAnim` | `Sprite` | — | 1 | 5 |
| `anims/RandomAnim.as` | 58 | `RandomAnim` | `Sprite` | — | 1 | 6 |
| `anims/RedTurnAnim.as` | 56 | `RedTurnAnim` | `Sprite` | — | 1 | 6 |
| `anims/RedWinAnim.as` | 58 | `RedWinAnim` | `Sprite` | — | 1 | 6 |
| `anims/ReverseAnim.as` | 58 | `ReverseAnim` | `Sprite` | — | 1 | 6 |
| `anims/SameAnim.as` | 57 | `SameAnim` | `Sprite` | — | 1 | 5 |
| `anims/StartAnim.as` | 57 | `StartAnim` | `Sprite` | — | 1 | 5 |
| `anims/SuddenDeathAnim.as` | 59 | `SuddenDeathAnim` | `Sprite` | — | 1 | 6 |
| `anims/SwapAnim.as` | 58 | `SwapAnim` | `Sprite` | — | 1 | 6 |
| `anims/TalkAnim.as` | 88 | `TalkAnim` | `Sprite` | — | 2 | 8 |
| `anims/ThreeOpenAnim.as` | 56 | `ThreeOpenAnim` | `Sprite` | — | 1 | 6 |
| `anims/UnlockCardAnim.as` | 52 | `UnlockCardAnim` | `DisplayObjectContainer` | — | 3 | 3 |
| `controls/AvatarChooser.as` | 91 | `AvatarChooser` | `Image` | — | 4 | 10 |
| `controls/cardAvatar.as` | 43 | `cardAvatar` | `Sprite` | — | 1 | 2 |
| `controls/cardScore.as` | 39 | `cardScore` | `DisplayObjectContainer` | — | 1 | 1 |
| `controls/MainButton.as` | 403 | `MainButton` | `DisplayObjectContainer` | — | 3 | 15 |
| `controls/MGPLabel.as` | 63 | `MGPLabel` | `LayoutGroup` | — | 2 | 5 |
| `controls/RoundChart.as` | 100 | `RoundChart` | `Sprite` | — | 0 | 5 |
| `controls/TouchLabel.as` | 48 | `TouchLabel` | `Label` | `IFeathersEventDispatcher` | 1 | 7 |
| `controls/XPLabel.as` | 57 | `XPLabel` | `LayoutGroup` | — | 1 | 5 |
| `datas/Achievements.as` | 94 | `Achievements` | — | — | 4 | 2 |
| `datas/BoosterItem.as` | 78 | `BoosterItem` | `Item` | — | 1 | 0 |
| `datas/CardItem.as` | 49 | `CardItem` | `Item` | — | 1 | 0 |
| `datas/cards.as` | 321 | `cards` | — | — | 1 | 1 |
| `datas/Item.as` | 187 | `Item` | `Sprite` | — | 1 | 7 |
| `datas/Level.as` | 36 | `Level` | — | — | 0 | 0 |
| `datas/NPC.as` | 300 | `NPC` | `Object` | — | 3 | 2 |
| `datas/NPCs.as` | 1,162 | `NPCs` | — | — | 1 | 1 |
| `datas/PotionItem.as` | 62 | `PotionItem` | `Item` | — | 2 | 0 |
| `datas/Rank.as` | 36 | `Rank` | — | — | 0 | 0 |
| `datas/Save.as` | 98 | `Save` | — | — | 2 | 1 |
| `datas/tripleTriadRules.as` | 116 | `tripleTriadRules` | — | — | 1 | 0 |
| `display/AchievementIcon.as` | 50 | `AchievementIcon` | `Sprite` | — | 1 | 4 |
| `display/Card.as` | 424 | `Card` | `Sprite` | `IDragSource` | 7 | 14 |
| `display/CardDigits.as` | 38 | `CardDigits` | `Sprite` | — | 2 | 2 |
| `display/CardListThumb.as` | 80 | `CardListThumb` | `Sprite` | — | 1 | 8 |
| `display/CardThumb.as` | 90 | `CardThumb` | `Sprite` | — | 1 | 8 |
| `display/ImageExtended.as` | 47 | `ImageExtended` | `Image` | — | 1 | 5 |
| `display/InventoryItem.as` | 39 | `InventoryItem` | `Button` | — | 1 | 7 |
| `display/ItemIcon.as` | 45 | `ItemIcon` | `Sprite` | — | 1 | 3 |
| `display/Tile.as` | 302 | `Tile` | `Sprite` | `IDropTarget` | 3 | 13 |
| `display/UserBar.as` | 142 | `UserBar` | `FeathersControl` | — | 5 | 12 |
| `Game.as` | 142 | `Game` | `Sprite` | — | 4 | 7 |
| `net/Socket.as` | 650 | `Socket` | — | — | 9 | 13 |
| `net/TTONet.as` | 50 | `TTONet` | — | — | 0 | 5 |
| `screens/BackstageScreen.as` | 109 | `BackstageScreen` | `Screen` | — | 4 | 7 |
| `screens/BaseMatchScreen.as` | 448 | `BaseMatchScreen` | `Screen` | — | 10 | 6 |
| `screens/Board.as` | 83 | `Board` | `LayoutGroup` | — | 2 | 2 |
| `screens/cardListScreen.as` | 237 | `cardListScreen` | `Screen` | — | 8 | 20 |
| `screens/cardPanel.as` | 164 | `cardPanel` | `Panel` | — | 4 | 11 |
| `screens/CCGroupMatchScreen.as` | 280 | `CCGroupMatchScreen` | `PVEMatchScreen` | — | 15 | 1 |
| `screens/CCGroupRematchPanel.as` | 49 | `CCGroupRematchPanel` | `RematchPanel` | — | 4 | 4 |
| `screens/CCGroupScreen.as` | 122 | `CCGroupScreen` | `Screen` | — | 6 | 9 |
| `screens/dashboardScreen.as` | 89 | `dashboardScreen` | `Screen` | — | 4 | 5 |
| `screens/DeckSelector.as` | 153 | `DeckSelector` | `Panel` | `IFeathersEventDispatcher` | 4 | 11 |
| `screens/DecksScreen.as` | 424 | `DecksScreen` | `Screen` | — | 9 | 21 |
| `screens/EmptyScreen.as` | 34 | `EmptyScreen` | `Screen` | — | 0 | 1 |
| `screens/GSGroupMatchScreen.as` | 281 | `GSGroupMatchScreen` | `PVEMatchScreen` | — | 15 | 1 |
| `screens/GSGroupRematchPanel.as` | 48 | `GSGroupRematchPanel` | `RematchPanel` | — | 4 | 4 |
| `screens/GSGroupScreen.as` | 123 | `GSGroupScreen` | `Screen` | — | 6 | 11 |
| `screens/HelpScreen.as` | 131 | `HelpScreen` | `Screen` | — | 4 | 11 |
| `screens/InventoryScreen.as` | 288 | `InventoryScreen` | `Screen` | — | 10 | 9 |
| `screens/LoadScreen.as` | 150 | `LoadScreen` | `Screen` | — | 5 | 10 |
| `screens/MenuScreen.as` | 131 | `MenuScreen` | `Screen` | — | 6 | 10 |
| `screens/NewGameScreen.as` | 135 | `NewGameScreen` | `Screen` | — | 6 | 10 |
| `screens/playerPanel.as` | 326 | `playerPanel` | `LayoutGroup` | — | 4 | 10 |
| `screens/profileScreen.as` | 254 | `profileScreen` | `Screen` | — | 11 | 14 |
| `screens/PVEMatchScreen.as` | 255 | `PVEMatchScreen` | `BaseMatchScreen` | — | 14 | 1 |
| `screens/PVEScreen.as` | 374 | `PVEScreen` | `Screen` | — | 9 | 12 |
| `screens/PVPMatchScreen.as` | 377 | `PVPMatchScreen` | `BaseMatchScreen` | — | 18 | 3 |
| `screens/PVPScreen.as` | 363 | `PVPScreen` | `Screen` | — | 7 | 15 |
| `screens/RematchPanel.as` | 132 | `RematchPanel` | `Panel` | — | 10 | 10 |
| `screens/RulesDigest.as` | 100 | `RulesDigest` | `LayoutGroup` | — | 3 | 5 |
| `screens/SettingsScreen.as` | 244 | `SettingsScreen` | `Screen` | — | 6 | 19 |
| `screens/shopScreen.as` | 166 | `shopScreen` | `Screen` | — | 10 | 9 |
| `screens/TutorialRematchPanel.as` | 45 | `TutorialRematchPanel` | `RematchPanel` | — | 2 | 4 |
| `screens/TutorialScreen.as` | 260 | `TutorialScreen` | `PVEMatchScreen` | — | 14 | 1 |
| `theme/BaseTTOTheme.as` | 2,290 | `BaseTTOTheme` | `StyleNameFunctionTheme` | — | 1 | 70 |
| `theme/TTOTheme.as` | 116 | `TTOTheme` | `BaseTTOTheme` | — | 0 | 5 |
| `ttoboot.as` | 246 | `ttoboot` | `Sprite` | — | 3 | 26 |
| `ttoclient.as` | 37 | `ttoclient` | `Sprite` | — | 1 | 6 |
| `utils/Assets.as` | 36 | `Assets` | `EventDispatcher` | — | 0 | 3 |
| `utils/conf.as` | 55 | `conf` | — | — | 0 | 2 |
| `utils/CryptoHelper.as` | 21 | `CryptoHelper` | — | — | 0 | 5 |
| `utils/FilterProvider.as` | 28 | `FilterProvider` | — | — | 0 | 1 |
| `utils/gfx.as` | 40 | `gfx` | — | — | 0 | 3 |
| `utils/i18n.as` | 42 | `i18n` | — | — | 0 | 0 |
| `utils/SoundManager.as` | 133 | `SoundManager` | — | — | 0 | 8 |
| `utils/tools.as` | 159 | `tools` | — | — | 0 | 15 |
| `utils/TTOCore.as` | 396 | `TTOCore` | — | — | 7 | 2 |
| `utils/TTOFiles.as` | 132 | `TTOFiles` | — | — | 0 | 8 |

## 9. Anomalies

Files under `tto/` that do not declare a `tto.*` package. They do not belong
to the package tree they are filed under, so nothing can import them by a
`tto.*` name — check whether they are dead before budgeting for them.

| File | Declared package | Referenced elsewhere? |
|---|---|---|
| `net/TTONet.as` | `(default)` | **no — dead code** |

5 of 103 files carry a UTF-8 BOM:

- `net/TTONet.as`
- `ttoboot.as`
- `utils/gfx.as`
- `utils/tools.as`
- `utils/TTOFiles.as`

Worth knowing before any bulk transliteration: a BOM defeats regexes anchored
with `^` on the first line, and `java.util.Properties` folds it into the first
key name.

