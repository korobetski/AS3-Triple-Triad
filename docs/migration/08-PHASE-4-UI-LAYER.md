# Phase 4: UI Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 4 - UI Layer
- **Duration**: 8 weeks (Weeks 13-20)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phases 1-3

---

## 🎯 Phase Overview

### Purpose
Phase 4 migrates all user interface components from Feathers UI (AS3) to Compose
Multiplatform: 32 screen/panel classes (22 navigable destinations + 9 embedded
components + 1 abstract base), custom components, theme system, and navigation.

### Key Objectives
1. Migrate all 32 screen/panel classes from AS3 to Compose
2. Create reusable Compose components
3. Implement theme system
4. Set up navigation
5. Implement drag and drop
6. Create all UI animations

---

## 📅 Timeline

| Weeks | Focus | Screens | Owner |
|-------|-------|---------|-------|
| 13-14 | Foundation: Theme, Components, Navigation | 4 (Tier 1) | UI/UX + Team |
| 15-16 | Core Screens: Menu, Game, Match | 10 (Tier 2) | Senior Devs |
| 17-18 | Collection + Multiplayer screens | 15 (Tiers 3-4) | Team |
| 19-20 | Secondary screens, Polish, Animations, Testing | 3 (Tier 5) + all | QA + Team |

**Total: 32** (4 + 10 + 15 + 3), matching the 32 files in `sources/src/tto/screens/`.

---

## 📝 Screen Migration Priority

> ⚠️ **The lists below were incomplete.** They named 28 items, but `screens/`
> contains **32** files. Missing entirely were **PVEScreen** (the PvE lobby — while
> `PVEMatchScreen` *was* listed), **shopScreen**, **CCGroupRematchPanel** and
> **GSGroupRematchPanel**. All four are now included. The timeline table above also
> did not add up (4 + 10 + 12 = 26 ≠ 28); it is corrected against the tiers below.

### Tier 1: Foundation (Week 13)
- **LoadScreen** - Loading screen
- **MenuScreen** - Main menu
- **SettingsScreen** - Settings
- **HelpScreen** - Help

### Tier 2: Core Game (Weeks 14-16)
- **BaseMatchScreen** - Base match class
- **PVEMatchScreen** - PvE match
- **PVPMatchScreen** - PvP match
- **Board** - Game board
- **playerPanel** - Player status
- **DeckSelector** - Deck selection
- **RulesDigest** - Rules display
- **cardPanel** - Card display
- **NewGameScreen** - New game creation
- **dashboardScreen** - Dashboard

### Tier 3: Collection Management (Weeks 17-18)
- **DecksScreen** - Deck management
- **InventoryScreen** - Inventory
- **cardListScreen** - Card list
- **profileScreen** - Player profile
- **shopScreen** - Item shop *(was missing from this plan)*

### Tier 4: Multiplayer (Weeks 17-18)
- **PVEScreen** - PvE lobby / opponent selection *(was missing from this plan)*
- **PVPScreen** - PvP lobby
- **GSGroupScreen** - Group selection
- **CCGroupScreen** - Custom group
- **CCGroupMatchScreen** - Custom group match
- **GSGroupMatchScreen** - Group match
- **RematchPanel** - Rematch panel
- **CCGroupRematchPanel** - CC group rematch *(was missing from this plan)*
- **GSGroupRematchPanel** - GS group rematch *(was missing from this plan)*
- **TutorialRematchPanel** - Tutorial rematch

> **Tier 4 note**: everything here except `PVEScreen` depends on the network layer.
> Per **TR-007** in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md), multiplayer
> does not function in the AS3 source. If PvP is descoped for v1 (recommended),
> this tier shrinks to `PVEScreen` + `RematchPanel` + `TutorialRematchPanel` and
> frees roughly 1.5 weeks.

### Tier 5: Secondary (Weeks 19-20)
- **TutorialScreen** - Tutorial
- **BackstageScreen** - Backstage
- **EmptyScreen** - Empty state

---

## 🎨 Component Mapping (Feathers → Compose)

See [14-COMPONENT-MAPPING.md](./14-COMPONENT-MAPPING.md) for detailed mappings.

### Common Mappings

| Feathers Component | Compose Equivalent | Notes |
|-------------------|-------------------|-------|
| `Screen` | `@Composable` function | Use Box, Column, Row |
| `ScreenNavigator` | `NavHost` + `NavController` | Jetpack Navigation |
| `Button` | `Button` | Material 3 |
| `Label` | `Text` | Material Typography |
| `Image` | `Image` + Coil | Async image loading |
| `LayoutGroup` | `Column` / `Row` / `Box` | Flexible layouts |
| `Sprite` | `Box` + `Canvas` | Custom drawing |
| `Quad` | `Box` with background | Solid color |

---

## 📝 Key Tasks

### Week 13: Foundation

#### Task 4.1: Theme System
**Owner**: UI/UX Designer | **Duration**: 2 days | **Priority**: CRITICAL

**TTOTheme.as Analysis**:
- Theme configuration for Feathers UI
- Colors, fonts, styling
- Two themes: BaseTTOTheme, TTOTheme

**Compose Theme Implementation**:

> ⚠️ **Three errors in the previous version of this snippet**:
> 1. `val AppTheme = MaterialTheme(...)` — `MaterialTheme` is a `@Composable`
>    function, not a constructor. It cannot be assigned to a top-level `val`.
> 2. `TripleTriadTheme` referenced `AppColors`, which was never defined anywhere.
> 3. `Font(R.font.ff14)` uses the Android `R` class inside code destined for
>    `commonMain`. Use Compose Resources (`Res.font.*`), which is multiplatform.
>
> **Also, the card colours were wrong.** `0xFF43a7c8` / `0xFFbb594f` are the
> *text* colours `largeBlueElementFormat` / `largeRedElementFormat` from
> `theme/BaseTTOTheme.as:1537-1544`. The actual card background colours are
> declared in `display/Card.as:29-31`:
>
> | Constant | AS3 value | Source |
> |----------|-----------|--------|
> | `Card.GREY_COLOR` | `0x5a595a` | `display/Card.as:29` |
> | `Card.BLUE_COLOR` | `0x2d4660` | `display/Card.as:30` |
> | `Card.RED_COLOR`  | `0x602d2d` | `display/Card.as:31` |
> | `PRIMARY_BACKGROUND_COLOR` | `0x202020` | `theme/BaseTTOTheme.as:124` |
> | `LIGHT_TEXT_COLOR` | `0xe5e5e5` | `theme/BaseTTOTheme.as:125` |
> | `SELECTED_TEXT_COLOR` | `0xff9900` | `theme/BaseTTOTheme.as:127` |
> | `DISABLED_TEXT_COLOR` | `0x8a8a8a` | `theme/BaseTTOTheme.as:128` |
> | `LIST_BACKGROUND_COLOR` | `0x383430` | `theme/BaseTTOTheme.as:130` |
> | `MODAL_OVERLAY_COLOR` | `0x29241e` | `theme/BaseTTOTheme.as:135` |
>
> `0xFF1a1a1a` and `0xFF2a2a2a` in the old snippet were invented. Transcribe the
> remaining constants from `BaseTTOTheme.as:124-137` rather than approximating.

```kotlin
// Colors.kt — values transcribed from the AS3 source
val CardBlue   = Color(0xFF2D4660)   // Card.BLUE_COLOR
val CardRed    = Color(0xFF602D2D)   // Card.RED_COLOR
val CardGrey   = Color(0xFF5A595A)   // Card.GREY_COLOR
val TextBlue   = Color(0xFF43A7C8)   // largeBlueElementFormat
val TextRed    = Color(0xFFBB594F)   // largeRedElementFormat
val BackgroundColor  = Color(0xFF202020)  // PRIMARY_BACKGROUND_COLOR
val SurfaceColor     = Color(0xFF383430)  // LIST_BACKGROUND_COLOR
val LightTextColor   = Color(0xFFE5E5E5)  // LIGHT_TEXT_COLOR
val SelectedTextColor = Color(0xFFFF9900) // SELECTED_TEXT_COLOR
val DisabledTextColor = Color(0xFF8A8A8A) // DISABLED_TEXT_COLOR

// Game colours that Material's ColorScheme has no slot for.
@Immutable
data class TtoColors(
    val cardBlue: Color = CardBlue,
    val cardRed: Color = CardRed,
    val cardGrey: Color = CardGrey,
    val textBlue: Color = TextBlue,
    val textRed: Color = TextRed
)

val LocalTtoColors = staticCompositionLocalOf { TtoColors() }

// Typography.kt — @Composable, because Compose Resources font loading is.
@Composable
fun appTypography(): Typography {
    val gameFont = FontFamily(Font(Res.font.eurostile))
    return Typography(
        headlineLarge = TextStyle(fontFamily = gameFont, fontSize = 24.sp),
        bodyLarge     = TextStyle(fontFamily = gameFont, fontSize = 16.sp),
        labelSmall    = TextStyle(fontFamily = gameFont, fontSize = 12.sp)
    )
}

// Theme.kt
private val TtoColorScheme = darkColorScheme(
    primary    = TextBlue,
    secondary  = TextRed,
    background = BackgroundColor,
    surface    = SurfaceColor,
    onBackground = LightTextColor,
    onSurface    = LightTextColor
)

@Composable
fun TripleTriadTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTtoColors provides TtoColors()) {
        MaterialTheme(
            colorScheme = TtoColorScheme,
            typography = appTypography(),
            content = content
        )
    }
}
```

> **Note**: do not set `color` inside `TextStyle` *and* rely on
> `colorScheme.onBackground` — pick one source of truth for text colour, otherwise
> the typography silently overrides the scheme everywhere.
>
> **Font caveat**: `Eurostile` (used in `display/Card.as:81`) has no CJK coverage,
> so the `ja_JA` locale needs a fallback family. Audit
> `sources/bin/assets/fonts/` during Task 4.1, and note that redistributing
> Eurostile may itself require a licence.

**Acceptance Criteria**:
- [ ] Theme colors match original
- [ ] Typography matches original
- [ ] Theme is applied consistently

---

#### Task 4.2: Common Components
**Owner**: UI/UX + Team | **Duration**: 3 days | **Priority**: CRITICAL

**Components to Create** (from `controls/` and `display/`):
- **MainButton** - Primary button
- **MGPLabel** - MGP (currency) display
- **XPLabel** - XP display
- **RoundChart** - Round progress chart
- **TouchLabel** - Interactive label
- **AvatarChooser** - Avatar selection
- **CardDigits** - Card power digits
- **CardThumb** - Card thumbnail
- **CardListThumb** - Card list thumbnail
- **ImageExtended** - Extended image
- **InventoryItem** - Inventory item
- **ItemIcon** - Item icon
- **UserBar** - User info bar

**Example Component**:
```kotlin
// CardComponent.kt
@Composable
fun CardComponent(
    card: Card,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isDraggable: Boolean = false,
    onClick: () -> Unit = {},
    onDragStart: () -> Unit = {}
) {
    // See the dimensions note below.
    val cardWidth = 88.dp
    val cardHeight = 118.dp
    
    Box(
        modifier = modifier
            .size(cardWidth, cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(when (card.color) {
                CardColor.BLUE -> BlueColor
                CardColor.RED -> RedColor
                CardColor.GREY -> GreyColor
            })
            .then(if (isDraggable) Modifier.draggable() else Modifier)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = Color.White
            ),
        contentAlignment = Alignment.Center
    ) {
        // Card image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("file:///android_asset/cards/${card.collection}/${card.id}.png")
                .placeholder(R.drawable.card_back)
                .build(),
            contentDescription = card.nameKey,
            modifier = Modifier.fillMaxSize()
        )
        
        // Card power digits
        CardDigits(
            top = card.topPow,
            right = card.rightPow,
            bottom = card.bottomPow,
            left = card.leftPow,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
```

> **Dimensions**: verified against the AS3 source — card `88 x 118`
> (`display/Card.as:73`, `new Quad(88, 118, 0x5a595a)`), tile `136 x 136`
> (`display/Tile.as:51`). Earlier revisions used `104 x 128` for cards, which
> appears nowhere in the source. Note that AS3 values are **pixels at a fixed
> 1280x720 landscape stage**, not density-independent units — treat them as design
> ratios (card ≈ 0.65 x tile) and scale to the viewport rather than hardcoding dp.

**Acceptance Criteria**:
- [ ] All common components created
- [ ] Components match original look
- [ ] Components are reusable

---

#### Task 4.3: Navigation System — 🔄 **partly done, without Navigation Compose**

Splash → menu → match / options is implemented in
[`ui/App.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/App.kt) as a `remember`ed
`Screen` enum. Nine tests in
[`NavigationTest`](../../shared/src/desktopTest/kotlin/com/tripletriad/ui/NavigationTest.kt).
Write-up in the [README](../../README.md#screens-and-navigation).

> ⚠️ **No `NavHost`, deliberately.** Four destinations, no deep links, no arguments, no back stack
> worth the name. A navigation library plus a route-string layer would earn nothing here; the point
> to reconsider is when this approaches the 32 screens below, and the enum will have become
> unpleasant by then rather than silently wrong.
>
> **What the plan's sketch left out**: the Android system back gesture. Nothing in Task 4.3
> mentions it, and without handling it, back during a match finishes the activity — the app appears
> to quit mid-game. `androidx.compose.ui.backhandler.BackHandler` is multiplatform in Compose 1.9,
> so it needs no Android-only source set, but it does need the `ui-backhandler` artifact, which
> `compose.ui` does not pull in.

**Owner**: Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

**Navigation Implementation**:
```kotlin
// AppNavigation.kt
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        // Splash
        composable("splash") { SplashScreen(navController) }
        
        // Main menu
        composable("menu") { MenuScreen(navController) }
        
        // Game
        composable(
            "game/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "ff14"
            GameScreen(mode = GameMode.valueOf(mode.uppercase()), navController)
        }
        
        // PvE
        composable("pve") { PVEScreen(navController) }
        composable("pve/match") { PVEMatchScreen(navController) }
        
        // PvP
        composable("pvp") { PVPScreen(navController) }
        composable("pvp/match") { PVPMatchScreen(navController) }
        
        // Collection
        composable("decks") { DecksScreen(navController) }
        composable("inventory") { InventoryScreen(navController) }
        composable("cards") { CardListScreen(navController) }
        
        // Settings
        composable("settings") { SettingsScreen(navController) }
        composable("help") { HelpScreen(navController) }
        composable("profile") { ProfileScreen(navController) }
        
        // Tutorial
        composable("tutorial") { TutorialScreen(navController) }
    }
}

// Navigation extensions
fun NavController.navigateToGame(mode: GameMode) {
    navigate("game/${mode.name.lowercase()}")
}

fun NavController.navigateToPvE() {
    navigate("pve")
}

fun NavController.navigateToPvP() {
    navigate("pvp")
}
```

**Acceptance Criteria**:
- [ ] All screens are navigable
- [ ] Navigation works on both platforms
- [ ] Back stack works correctly

---

### Week 14: Core Screens

#### Task 4.4: Menu Screen — ✅ **done, with three actions**

[`MainMenuScreen.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/MainMenuScreen.kt) and
[`OptionsScreen.kt`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/OptionsScreen.kt), plus
a [`SplashScreen`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/SplashScreen.kt) that
Tier 1's **LoadScreen** entry does not actually describe — `LoadScreen.as` is a *save-game list*,
not a loading screen, so the splash is new work rather than a port.

**Play / Options / Quit**, not the eight buttons listed below: New Game, Load Game, Decks and
Inventory all need save games or a collection, and neither exists yet. `MenuScreen.as:52-58` is the
order to grow the list back in.

`SettingsScreen.as` is 243 lines against this port's options pane, because the original also
carried a resolution picker, a fullscreen toggle and an account section. What is here is what
`UserSettings.json` holds: language and the two volumes.

**Owner**: Senior Kotlin Dev | **Duration**: 2 days | **Priority**: HIGH

> ⚠️ **Week 14 is over-allocated**: Task 4.4 (2 d) + Task 4.5 (5 d) + Task 4.6 (3 d)
> = 10 days in a single week, and Task 4.5 and 4.6 share the Tech Lead / Senior Dev
> pool. The Tier 2 heading says "Weeks 14-16" while these tasks are all filed under
> Week 14. Re-level against the tier schedule.

**MenuScreen.as Features**:
- Main menu with multiple options
- New Game button
- PvP button
- Decks button
- Inventory button
- Settings button
- Help button
- Exit button

**Compose Implementation**:
```kotlin
@Composable
fun MenuScreen(navController: NavController) {
    TripleTriadTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Menu buttons
            MainButton(
                text = stringResource(R.string.new_game),
                onClick = { navController.navigateToNewGame() }
            )
            
            MainButton(
                text = stringResource(R.string.pvp),
                onClick = { navController.navigateToPvP() }
            )
            
            MainButton(
                text = stringResource(R.string.decks),
                onClick = { navController.navigateToDecks() }
            )
            
            MainButton(
                text = stringResource(R.string.inventory),
                onClick = { navController.navigateToInventory() }
            )
            
            MainButton(
                text = stringResource(R.string.settings),
                onClick = { navController.navigateToSettings() }
            )
            
            MainButton(
                text = stringResource(R.string.help),
                onClick = { navController.navigateToHelp() }
            )
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Menu displays all options
- [ ] Navigation works
- [ ] Matches original design

---

#### Task 4.5: BaseMatchScreen (Most Complex)
**Owner**: Tech Lead + Senior Kotlin Devs | **Duration**: 5 days | **Priority**: CRITICAL

**BaseMatchScreen.as Analysis**:
- Base class for all match screens
- Manages game flow through phases
- Handles card placement and rules
- Manages turn system
- 447 lines
- Uses setTimeout for phase delays
- Complex event handling

**Components to Create**:
- `BaseMatchScreen.kt` - Base class
- `BoardComponent.kt` - Board display
- `PlayerPanel.kt` - Player status
- `DeckSelectorComponent.kt` - Deck selection UI
- `CardScoreComponent.kt` - Score display

**Compose Implementation**:
```kotlin
@Composable
fun BaseMatchScreen(
    viewModel: GameViewModel,
    navController: NavController
) {
    TripleTriadTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            
            // Board in center
            BoardComponent(
                board = viewModel.state.collectAsState().value.board,
                onTileClick = { tile -> viewModel.onTileClicked(tile) },
                modifier = Modifier.align(Alignment.Center)
            )
            
            // Blue player panel (top)
            PlayerPanel(
                player = viewModel.state.collectAsState().value.bluePlayer,
                color = CardColor.BLUE,
                isActive = viewModel.state.collectAsState().value.turn.currentPlayer == CardColor.BLUE,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // Red player panel (bottom)
            PlayerPanel(
                player = viewModel.state.collectAsState().value.redPlayer,
                color = CardColor.RED,
                isActive = viewModel.state.collectAsState().value.turn.currentPlayer == CardColor.RED,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            
            // Selected card (draggable)
            viewModel.state.collectAsState().value.selectedCard?.let { card ->
                DraggableCard(
                    card = card,
                    onDragStart = { viewModel.onCardDragStart(card) },
                    onDragEnd = { viewModel.onCardDragEnd() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Deck selector (when in deck selection phase)
            if (viewModel.state.collectAsState().value.phase == GamePhase.DECK_SELECTION) {
                DeckSelectorComponent(
                    cards = viewModel.state.collectAsState().value.blueDeck,
                    onCardSelected = { card -> viewModel.selectCard(card) },
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
            
            // Phase indicators
            PhaseIndicator(
                phase = viewModel.state.collectAsState().value.phase,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}
```

**Phase-Specific UI**:
```kotlin
@Composable
fun PhaseIndicator(phase: GamePhase, modifier: Modifier = Modifier) {
    val phaseText = when (phase) {
        GamePhase.DECK_SELECTION -> stringResource(R.string.select_deck)
        GamePhase.OPEN_PHASE -> stringResource(R.string.open_phase)
        GamePhase.ORDER_PHASE -> stringResource(R.string.order_phase)
        // ... etc
        GamePhase.PLAYING -> stringResource(R.string.your_turn)
        GamePhase.ENDED -> stringResource(R.string.game_over)
    }
    
    Box(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp)
    ) {
        Text(
            text = phaseText,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
```

**Acceptance Criteria**:
- [ ] All match UI components created
- [ ] Game flow UI works
- [ ] Card placement works
- [ ] Phase indicators work

---

#### Task 4.6: Board Component
**Owner**: Senior Kotlin Dev | **Duration**: 3 days | **Priority**: CRITICAL

**Board.as Analysis**:
- Manages 3x3 game board
- Contains 9 tiles
- Handles board layout
- Connects adjacent tiles
- 82 lines

**Compose Implementation**:
```kotlin
@Composable
fun BoardComponent(
    board: Board,
    onTileClick: (Tile) -> Unit,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true
) {
    val tileSize = 136.dp
    val boardSize = tileSize * 3
    
    Box(
        modifier = modifier.size(boardSize, boardSize),
        contentAlignment = Alignment.Center
    ) {
        // Board background
        Box(
            modifier = Modifier
                .size(boardSize, boardSize)
                .background(Color.Black.copy(alpha = 0.3f))
        )
        
        // Grid of tiles
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.size(boardSize, boardSize)
        ) {
            items(board.tiles.size) { index ->
                val tile = board.tiles[index]
                TileComponent(
                    tile = tile,
                    onClick = { if (isInteractive) onTileClick(tile) },
                    modifier = Modifier.size(tileSize)
                )
            }
        }
    }
}

@Composable
fun TileComponent(
    tile: Tile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCard = tile.card != null
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(when (tile.color) {
                CardColor.BLUE -> BlueColor.copy(alpha = 0.3f)
                CardColor.RED -> RedColor.copy(alpha = 0.3f)
                CardColor.GREY -> Color.Transparent
            })
            .border(
                width = 1.dp,
                color = when (tile.element) {
                    Element.NONE -> Color.Transparent
                    else -> ElementColor.get(tile.element)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (hasCard) {
            CardComponent(
                card = tile.card!!,
                color = tile.color,
                modifier = Modifier.size(88.dp, 118.dp)
            )
        }
    }
}
```

**Acceptance Criteria**:
- [ ] Board displays correctly
- [ ] Tiles are clickable
- [ ] Element borders work
- [ ] Adjacent connections visible

---

### Week 15-18: Remaining Screens

**Screens to Migrate**: See priority list above.

Each screen follows similar pattern:
1. Analyze AS3 implementation
2. Map to Compose components
3. Implement with ViewModel
4. Test functionality

**Acceptance Criteria per Screen**:
- [ ] UI matches original design
- [ ] All functionality works
- [ ] Navigation works
- [ ] Responsive layout

---

### Week 19-20: Drag & Drop and Animations

#### Task 4.7: Drag and Drop Implementation
**Owner**: UI/UX + Senior Kotlin Devs | **Duration**: 3 days | **Priority**: CRITICAL

**AS3 Drag & Drop** (from Feathers):
- `IDragSource` interface for draggable items
- `IDropTarget` interface for drop targets
- `DragDropManager` for coordination
- Drag ghost visualization
- Drop validation

**Compose Implementation**:
```kotlin
// DraggableCard.kt
@Composable
fun DraggableCard(
    card: Card,
    onDragStart: (Card) -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragPosition = offset
                        onDragStart(card)
                    },
                    onDragEnd = {
                        isDragging = false
                        dragPosition = Offset.Zero
                        onDragEnd()
                    },
                    onDrag = { change, offset ->
                        change.consume()
                        dragPosition += offset
                        onDrag(dragPosition)
                    }
                )
            }
    ) {
        CardComponent(
            card = card,
            modifier = Modifier
                .offset { dragPosition.toIntOffset() }
                .alpha(if (isDragging) 0.7f else 1f)
        )
    }
}

// ⚠️ `detectDragGestures` has NO onDragEnter / onDragExit parameters. Its
// signature is (onDragStart, onDragEnd, onDragCancel, onDrag) — the previous
// version of this snippet would not compile.
//
// Compose has no built-in "drop target" for in-process drags (the
// Modifier.dragAndDropTarget API targets cross-application drag & drop). The
// standard approach is a single shared drag state plus per-tile bounds
// registration, with hit-testing done in the parent's coordinate space.

// DragState.kt — one instance per board, hoisted above both card and tiles.
class BoardDragState {
    var draggedCard by mutableStateOf<Card?>(null)
        private set
    var dragPosition by mutableStateOf(Offset.Unspecified)
        private set

    /** Tile bounds in the board's coordinate space, keyed by tile id. */
    private val tileBounds = mutableMapOf<Int, Rect>()

    fun registerTile(id: Int, bounds: Rect) { tileBounds[id] = bounds }
    fun unregisterTile(id: Int) { tileBounds.remove(id) }

    fun startDrag(card: Card, position: Offset) {
        draggedCard = card
        dragPosition = position
    }

    fun updateDrag(delta: Offset) {
        if (dragPosition != Offset.Unspecified) dragPosition += delta
    }

    /** Tile currently under the pointer, or null. */
    fun hoveredTileId(): Int? =
        if (dragPosition == Offset.Unspecified) null
        else tileBounds.entries.firstOrNull { it.value.contains(dragPosition) }?.key

    /** Returns the drop target, then clears the drag. */
    fun endDrag(): Pair<Card, Int>? {
        val card = draggedCard
        val tileId = hoveredTileId()
        draggedCard = null
        dragPosition = Offset.Unspecified
        return if (card != null && tileId != null) card to tileId else null
    }

    fun cancelDrag() {
        draggedCard = null
        dragPosition = Offset.Unspecified
    }
}

// DropTargetTile.kt — registers its bounds; no gesture detector of its own.
@Composable
fun DropTargetTile(
    tile: Tile,
    dragState: BoardDragState,
    boardCoordinates: LayoutCoordinates?,
    modifier: Modifier = Modifier
) {
    val isHovered = dragState.hoveredTileId() == tile.id && !tile.isTaken

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                // Convert to the board's coordinate space so hit-testing matches
                // the drag position.
                boardCoordinates?.let {
                    val topLeft = it.localPositionOf(coords, Offset.Zero)
                    dragState.registerTile(
                        tile.id,
                        Rect(topLeft, coords.size.toSize())
                    )
                }
            }
            .border(
                width = if (isHovered) 2.dp else 0.dp,
                color = if (isHovered) Color.Green else Color.Transparent
            )
    ) {
        TileComponent(tile = tile, onClick = {})
    }

    DisposableEffect(tile.id) {
        onDispose { dragState.unregisterTile(tile.id) }
    }
}

// DraggableCard.kt
@Composable
fun DraggableCard(
    card: Card,
    dragState: BoardDragState,
    onDrop: (Card, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDragging = dragState.draggedCard == card

    Box(
        modifier = modifier.pointerInput(card.id) {
            detectDragGestures(
                onDragStart = { offset -> dragState.startDrag(card, offset) },
                onDrag = { change, delta ->
                    change.consume()
                    dragState.updateDrag(delta)
                },
                onDragEnd = { dragState.endDrag()?.let { (c, id) -> onDrop(c, id) } },
                onDragCancel = { dragState.cancelDrag() }
            )
        }
    ) {
        CardComponent(
            card = card,
            modifier = Modifier.alpha(if (isDragging) 0.7f else 1f)
        )
    }
}
```

> **Also support tap-to-select + tap-tile-to-place.** The AS3 code offers both
> interactions (`Card.onTouch` for drag, `Tile.onTouch` + `BaseMatchScreen.tileTouched`
> for tap), and tapping is significantly easier on a phone than dragging a card to
> a 3×3 grid. Do not ship drag-only.

**Acceptance Criteria**:
- [ ] Cards can be dragged
- [ ] Cards can be dropped on tiles
- [ ] Drop validation works
- [ ] Visual feedback during drag

---

#### Task 4.8: UI Animations
**Owner**: UI/UX Designer + Team | **Duration**: 5 days | **Priority**: HIGH

**Animations to Implement** (all 24 classes in `anims/`):
- AllOpenAnim - All cards revealed
- AscensionAnim - Ascension rule
- BlueTurnAnim - Blue player's turn
- BlueWinAnim - Blue player wins
- ChaosAnim - Chaos rule
- ComboAnim - Combo chain
- DescensionAnim - Descension rule
- DrawAnim - Draw/game tie
- FallenAceAnim - Fallen Ace rule
- Mogu - Special animation
- OrderAnim - Order rule
- PileOuFace - Coin flip
- PlusAnim - Plus rule
- RandomAnim - Random rule
- RedTurnAnim - Red player's turn
- RedWinAnim - Red player wins
- ReverseAnim - Reverse rule
- SameAnim - Same rule
- StartAnim - Game start
- SuddenDeathAnim - Sudden Death rule
- SwapAnim - Swap rule
- TalkAnim - Chat/talk
- ThreeOpenAnim - Three Open rule *(was missing from this list)*
- UnlockCardAnim - Card unlocked reward *(was missing from this list)*

**Animation Implementation**:
```kotlin
// CardFlipAnimation.kt
//
// ⚠️ The previous version of this snippet had two bugs:
//   1. `.graphicsLayer { rotationY = rotationY }` — inside the graphicsLayer
//      lambda, `rotationY` resolves to the SCOPE's own property, so this is a
//      self-assignment that does nothing. The animated value is shadowed and
//      never applied. The state must have a different name.
//   2. Without `cameraDistance`, a 180° Y-rotation looks like a flat squash
//      rather than a card turning, and the back face renders mirrored.
@Composable
fun CardFlipAnimation(
    card: Card,
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 400,
    onFlipFinished: () -> Unit = {}
) {
    val angle by animateFloatAsState(          // note: NOT named rotationY
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing),
        finishedListener = { onFlipFinished() },
        label = "cardFlip"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = angle                  // scope property = animated state
            cameraDistance = 12f * density     // avoids the flat-squash look
        }
    ) {
        if (angle <= 90f) {
            CardFront(card = card)
        } else {
            // Counter-rotate, otherwise the back face is drawn mirrored.
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                CardBack()
            }
        }
    }
}

// CardFlyAnimation.kt
@Composable
fun CardFlyAnimation(
    card: Card,
    from: Offset,
    to: Offset,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(from) }
    
    LaunchedEffect(Unit) {
        animate(
            initialValue = from,
            targetValue = to,
            animationSpec = tween(400),
            typeConverter = Offset.VectorConverter
        ) { value, _ ->
            position = value
        }
        onComplete()
    }
    
    CardComponent(
        card = card,
        modifier = modifier.offset { position.toIntOffset() }
    )
}

// FlipAndChangeAnimation.kt
@Composable
fun FlipAndChangeAnimation(
    oldCard: Card,
    newCard: Card,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animation that flips card and shows new card
    // Uses AnimatedVisibility or custom animation
}
```

**Animation System**:
```kotlin
// AnimationManager.kt
class AnimationManager {
    private val activeAnimations = mutableListOf<AnimationJob>()
    
    fun playCardFlip(card: Card, onComplete: () -> Unit): AnimationJob {
        val job = AnimationJob("card_flip_${card.id}")
        activeAnimations.add(job)
        // Start animation
        return job
    }
    
    fun playCardFly(card: Card, from: Offset, to: Offset, onComplete: () -> Unit): AnimationJob {
        val job = AnimationJob("card_fly_${card.id}")
        activeAnimations.add(job)
        // Start animation
        return job
    }
    
    fun cancelAll() {
        activeAnimations.forEach { it.cancel() }
        activeAnimations.clear()
    }
}

data class AnimationJob(val id: String) {
    private var isCancelled = false
    
    fun cancel() {
        isCancelled = true
    }
    
    fun isActive(): Boolean = !isCancelled
}
```

**Acceptance Criteria**:
- [ ] All animations implemented
- [ ] Animations match original look
- [ ] Performance > 60 FPS
- [ ] Animations can be cancelled

---

## 📊 Phase 4 Deliverables

### Code Deliverables
- [ ] Theme system
- [ ] All common components (13 listed in Task 4.2)
- [ ] Navigation system
- [ ] All 32 screen/panel classes
- [ ] Drag and drop implementation
- [ ] All 24 animations
- [ ] Screen tests
- [ ] Animation tests

### Documentation Deliverables
- [ ] Component library documentation
- [ ] Screen migration notes
- [ ] Animation guide

---

## ✅ Phase 4 Completion Criteria

### Technical
- [ ] All screens migrated
- [ ] All components created
- [ ] All animations implemented
- [ ] Navigation works
- [ ] Drag and drop works
- [ ] Performance > 60 FPS

### Testing
- [ ] All UI tests pass
- [ ] Test coverage >80% for UI
- [ ] Manual testing complete

### Approvals
- [ ] Tech Lead approval
- [ ] UI/UX Designer approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 5 - Network

**Phase 5 Focus** (Weeks 21-23):
- Migrate Socket.as (WebSocket)
- Implement network layer
- Message handling
- Connection management
- Testing

**Prerequisites**: All Phase 4 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 3**: [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md)
- **Phase 5**: [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md)
- **Component Mapping**: [14-COMPONENT-MAPPING.md](./14-COMPONENT-MAPPING.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
