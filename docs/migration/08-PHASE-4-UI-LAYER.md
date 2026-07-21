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
Phase 4 migrates all user interface components from Feathers UI (AS3) to Compose Multiplatform, including 28 screens, custom components, theme system, and navigation.

### Key Objectives
1. Migrate all 28 screens from AS3 to Compose
2. Create reusable Compose components
3. Implement theme system
4. Set up navigation
5. Implement drag and drop
6. Create all UI animations

---

## 📅 Timeline

| Weeks | Focus | Screens | Owner |
|-------|-------|---------|-------|
| 13-14 | Foundation: Theme, Components, Navigation | 4 screens | UI/UX + Team |
| 15-16 | Core Screens: Menu, Game, Match | 10 screens | Senior Devs |
| 17-18 | Remaining Screens | 12 screens | Team |
| 19-20 | Polish, Animations, Testing | All screens | QA + Team |

---

## 📝 Screen Migration Priority

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

### Tier 4: Multiplayer (Weeks 17-18)
- **PVPScreen** - PvP lobby
- **GSGroupScreen** - Group selection
- **CCGroupScreen** - Custom group
- **CCGroupMatchScreen** - Custom group match
- **GSGroupMatchScreen** - Group match
- **RematchPanel** - Rematch panel
- **TutorialRematchPanel** - Tutorial rematch

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
```kotlin
// Colors.kt
val BlueColor = Color(0xFF43a7c8)
val RedColor = Color(0xFFbb594f)
val GreyColor = Color(0xFF5a595a)
val BackgroundColor = Color(0xFF1a1a1a)
val SurfaceColor = Color(0xFF2a2a2a)

// Typography.kt
val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.ff14)),
        fontSize = 24.sp,
        color = Color.White
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.ff14)),
        fontSize = 16.sp,
        color = Color.White
    )
)

// Theme.kt
val AppTheme = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = BlueColor,
        secondary = RedColor,
        background = BackgroundColor,
        surface = SurfaceColor
    ),
    typography = AppTypography
)

// AppTheme.kt
@Composable
fun TripleTriadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content
    )
}
```

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
    val cardWidth = 104.dp
    val cardHeight = 128.dp
    
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

**Acceptance Criteria**:
- [ ] All common components created
- [ ] Components match original look
- [ ] Components are reusable

---

#### Task 4.3: Navigation System
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

#### Task 4.4: Menu Screen
**Owner**: Senior Kotlin Dev | **Duration**: 2 days | **Priority**: HIGH

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
- ~448 lines
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
- ~83 lines

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
                modifier = Modifier.size(104.dp, 128.dp)
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

// DropTargetTile.kt
@Composable
fun DropTargetTile(
    tile: Tile,
    onCardDrop: (Card) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingOver by remember { mutableStateOf(false) }
    var draggedCard by remember { mutableStateOf<Card?>(null) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnter = {
                        isDraggingOver = true
                        true
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
        TileComponent(tile = tile)
    }
}

// Drag state management
class DragManager {
    private val _draggedCard = MutableStateFlow<Card?>(null)
    val draggedCard: StateFlow<Card?> = _draggedCard.asStateFlow()
    
    fun startDrag(card: Card) {
        _draggedCard.value = card
    }
    
    fun endDrag() {
        _draggedCard.value = null
    }
    
    fun dropOnTile(tile: Tile, card: Card) {
        // Handle drop
        endDrag()
    }
}
```

**Acceptance Criteria**:
- [ ] Cards can be dragged
- [ ] Cards can be dropped on tiles
- [ ] Drop validation works
- [ ] Visual feedback during drag

---

#### Task 4.8: UI Animations
**Owner**: UI/UX Designer + Team | **Duration**: 5 days | **Priority**: HIGH

**Animations to Implement** (25+ from `anims/`):
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

**Animation Implementation**:
```kotlin
// CardFlipAnimation.kt
@Composable
fun CardFlipAnimation(
    card: Card,
    isFlipping: Boolean,
    modifier: Modifier = Modifier
) {
    val rotationY by animateFloatAsState(
        targetValue = if (isFlipping) 180f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = LinearOutSlowInEasing
        )
    )
    
    Box(
        modifier = modifier
            .graphicsLayer { rotationY = rotationY }
    ) {
        if (rotationY <= 90f) {
            // Front of card
            CardFront(card = card)
        } else {
            // Back of card
            CardBack()
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
- [ ] All common components (20+)
- [ ] Navigation system
- [ ] All 28 screens
- [ ] Drag and drop implementation
- [ ] All 25+ animations
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
