# Technical Stack - Migration Decisions

## 📋 Document Information

- **Project**: Triple Triad Online Migration
- **Date**: 2026-07-21
- **Status**: APPROVED

---

## 🎯 Technology Selection Rationale

The migration from **ActionScript 3 / Adobe AIR** to **Kotlin Multiplatform** with **Compose Multiplatform** is driven by the following requirements:

1. **Mobile Platform Support**: Target both Android and iOS
2. **Modern Development**: Replace legacy Flash/AIR technology
3. **Code Reuse**: Share maximum code between platforms
4. **Performance**: Maintain or improve game performance
5. **Maintainability**: Easier to maintain and extend
6. **Developer Ecosystem**: Strong community and tooling support

---

## ✅ Selected Technologies

### Core Platform

| Requirement | Selected Technology | Alternatives Considered | Decision Reasoning |
|-------------|---------------------|------------------------|-------------------|
| **Language** | Kotlin 2.0+ | Dart, JavaScript/TypeScript, Swift+Java | Kotlin MP enables code sharing between Android and iOS. Strong typing, modern features, excellent tooling. |
| **Multiplatform Framework** | Kotlin Multiplatform | Flutter, React Native, NativeScript | KMP allows sharing business logic, with native UI for each platform. Better performance than cross-platform solutions. |
| **UI Framework** | Compose Multiplatform | SwiftUI, Jetpack Compose, Flutter | Compose MP allows sharing UI code between Android and iOS. Declarative paradigm matches well with game UI. |

### Build System

| Component | Selected Technology | Version | Notes |
|-----------|---------------------|---------|-------|
| **Build Tool** | Gradle | 8.5+ | Industry standard, Kotlin DSL support |
| **Kotlin Plugin** | Kotlin Multiplatform | 2.0+ | Required for KMP |
| **Compose Plugin** | JetBrains Compose | 1.6.0 | Compose Multiplatform support |
| **Android Gradle Plugin** | AGP | 8.2.0 | Android support |

---

## 🏗️ Architecture Overview

### Project Structure

```
triple-triad-kotlin/
├── build.gradle.kts                    # Root project configuration
├── settings.gradle.kts                # Project settings and includes
├── gradle.properties                  # Gradle properties
│
├── shared/                            # 🎯 KMP Shared Module (80-90% of code)
│   ├── build.gradle.kts              # Shared module build config
│   ├── proguard-rules.pro            # ProGuard rules for shared
│   └── src/
│       ├── commonMain/               # 🎯 Shared code for all platforms
│       │   ├── kotlin/
│       │   │   └── com/tripletriad/
│       │   │       ├── core/        # Game logic (100% shared)
│       │   │       ├── data/        # Models, repositories (100% shared)
│       │   │       ├── network/     # Network layer (100% shared)
│       │   │       ├── ui/          # UI components (~80% shared)
│       │   │       └── utils/       # Utilities (100% shared)
│       │   └── resources/            # Shared assets (JSON, etc.)
│       │
│       ├── commonTest/               # Tests for shared code
│       │   └── kotlin/
│       │
│       ├── androidMain/              # Android-specific code
│       │   └── kotlin/
│       │       └── com/tripletriad/platform/android/
│       │           ├── audio/       # Android audio
│       │           └── file/        # Android file system
│       │
│       ├── iosMain/                  # iOS-specific code
│       │   └── kotlin/
│       │       └── com/tripletriad/platform/ios/
│       │           ├── audio/       # iOS audio
│       │           └── file/        # iOS file system
│       │
│       └── jvmMain/                  # JVM for desktop testing
│           └── kotlin/
│
├── androidApp/                       # 📱 Android Application
│   ├── build.gradle.kts              # Android app build config
│   ├── AndroidManifest.xml          # Android manifest
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/tripletriad/android/
│       │   │   └── MainActivity.kt
│       │   └── res/                  # Android resources
│       │       ├── drawable/
│       │       ├── layout/
│       │       ├── mipmap/
│       │       ├── font/
│       │       └── values/
│       │
│       └── assets/                  # Android assets
│           ├── cards/
│           ├── sounds/
│           └── locales/
│
└── iosApp/                          # 🍎 iOS Application
    ├── iosApp/
    │   ├── Info.plist               # iOS configuration
    │   ├── Assets.xcassets/         # iOS assets
    │   └── ContentView.swift         # Main SwiftUI view
    │
    └── iosAppTests/                 # iOS tests
        └── iosAppTests.swift
```

---

## 🔧 Technology Stack Details

### 1. Kotlin Multiplatform (KMP)

**Purpose**: Share code between Android, iOS, and other platforms

**Key Features**:
- Share business logic (100%)
- Share UI code (~80% with Compose MP)
- Platform-specific implementations when needed
- First-class support for Android, iOS, JS, Native

**Configuration**:

```kotlin
// shared/build.gradle.kts
kotlin {
    android()
    ios()
    jvm()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Shared dependencies
            }
        }
        val androidMain by getting {
            dependencies {
                // Android-specific dependencies
            }
        }
        val iosMain by getting {
            dependencies {
                // iOS-specific dependencies
            }
        }
    }
}
```

**Use Cases in Triple Triad**:
- Game core logic (TTOCore)
- Data models (Card, Tile, Board, etc.)
- Game rules
- Network layer
- Repositories
- Utility functions

---

### 2. Compose Multiplatform

**Purpose**: Declarative UI framework for KMP

**Key Features**:
- Write UI once, use on Android and iOS
- Reactive state management
- Rich animation support
- Material Design components
- Custom drawing with Canvas

**Configuration**:

```kotlin
// shared/build.gradle.kts
plugins {
    id("org.jetbrains.compose") version "1.6.0"
}

compose {
    kotlinCompilerExtensionVersion = "1.5.3"
}
```

**Use Cases in Triple Triad**:
- All UI screens
- Card components
- Board display
- Animations
- Theme system
- Navigation

### Platform-Specific UI

| Platform | Rendering | Integration |
|----------|-----------|-------------|
| Android | Native Compose | Direct integration |
| iOS | Compose MP + SwiftUI interop | Via SwiftUI |

---

### 3. State Management

**Selected**: **Koin + ViewModel + StateFlow/SharedFlow**

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Dependency Injection** | Koin | Service location, dependency management |
| **View Layer** | ViewModel | UI state and logic |
| **State Holding** | StateFlow | Observable state |
| **Event Stream** | SharedFlow | One-time events |
| **Reactive UI** | Compose | UI updates on state change |

**Rationale**:
- **Koin**: Lightweight, multiplatform, Kotlin-first DI
- **ViewModel**: Standard Android pattern, also works on iOS via KMP
- **Flow**: Kotlin's official reactive streams, excellent coroutine support
- **Compose Integration**: Native support for StateFlow in Compose

**Example**:

```kotlin
// ViewModel
class GameViewModel(
    private val ttoCore: TTOCore,
    private val cardRepository: CardRepository
) : ViewModel() {
    private val _state = MutableStateFlow<GameState>(GameState.Initial)
    val state: StateFlow<GameState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<GameEvent>()
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()
    
    fun placeCard(card: Card, tile: Tile) {
        viewModelScope.launch {
            val result = ttoCore.applyRules(tile, card.color.name)
            _state.value = state.value.copy(
                board = state.value.board.copy(
                    tiles = state.value.board.tiles.mapIndexed { i, t ->
                        if (i == tile.id.toInt()) tile.copy(card = card) else t
                    }
                )
            )
            _events.emit(GameEvent.CardPlaced(card, tile))
        }
    }
}

// Compose UI
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.CardPlaced -> { /* handle */ }
            }
        }
    }
    
    BoardComponent(
        board = state.board,
        onCardPlaced = { card, tile -> viewModel.placeCard(card, tile) }
    )
}

// DI Setup (Koin)
val appModule = module {
    single { TTOCore() }
    single { CardRepository() }
    viewModel { GameViewModel(get(), get()) }
}

// Start Koin
startKoin {
    modules(appModule)
}
```

---

### 4. Network Layer

**Selected**: **Ktor Client with WebSocket**

| Component | Technology | Purpose |
|-----------|------------|---------|
| **HTTP Client** | Ktor Client | REST API calls (if needed) |
| **WebSocket** | Ktor WebSocket | Real-time game communication |
| **Serialization** | Kotlinx Serialization | JSON encoding/decoding |
| **Reconnection** | Custom | Handle connection issues |

**Configuration**:

```kotlin
// shared/build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.10")
    implementation("io.ktor:ktor-client-websockets:2.3.10")
    implementation("io.ktor:ktor-client-serialization:2.3.10")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.10")
}
```

**Implementation**:

```kotlin
// SocketManager.kt
class SocketManager(
    private val client: HttpClient
) {
    private var webSocket: WebSocketSession? = null
    private val _messages = MutableSharedFlow<SocketMessage>()
    val messages: SharedFlow<SocketMessage> = _messages.asSharedFlow()
    
    suspend fun connect(serverUrl: String) {
        webSocket = client.webSocket(serverUrl) {
            incoming.consumeAsFlow().collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val message = Json.decodeFromString<SocketMessage>(frame.readText())
                        _messages.emit(message)
                    }
                }
            }
        }
    }
    
    suspend fun send(message: SocketMessage) {
        webSocket?.send(Frame.Text(Json.encodeToString(message)))
    }
    
    fun disconnect() {
        webSocket?.close()
    }
}

// Message types
sealed class SocketMessage {
    data class Connected(val users: List<User>) : SocketMessage()
    data class GameStateUpdate(val game: GameState) : SocketMessage()
    data class CardMove(val cardIndex: Int, val position: Int) : SocketMessage()
    data class ChatMessage(val from: String, val message: String) : SocketMessage()
    data class Error(val message: String) : SocketMessage()
}
```

**Platform-Specific Setup**:

```kotlin
// Android
expect fun createHttpClient(): HttpClient

actual fun createHttpClient(): HttpClient = HttpClient(Android) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    engine {
        // Android-specific config
    }
}

// iOS
actual fun createHttpClient(): HttpClient = HttpClient(Ios) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    engine {
        // iOS-specific config
    }
}
```

---

### 5. Local Storage

**Selected**: **SQLDelight** (with SharedPreferences for simple data)

| Data Type | Technology | Purpose |
|-----------|------------|---------|
| **Structured Data** | SQLDelight | Save games, user profiles, decks |
| **Preferences** | Multiplatform Settings | Simple key-value settings |
| **Encryption** | Custom | Encrypt save files |

**Configuration**:

```kotlin
// shared/build.gradle.kts
plugins {
    id("app.cash.sqldelight") version "2.0.1"
}

sqldelight {
    databases {
        create("TripleTriad") {
            packageName.set("com.tripletriad.data.db")
        }
    }
}

dependencies {
    implementation("app.cash.sqldelight:runtime:2.0.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
    implementation("app.cash.sqldelight:primitive-adapters:2.0.1")
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
}
```

**Database Schema** (`shared/src/commonMain/sqldelight/com/tripletriad/data/db/`):

```sql
-- GameSave.sq
CREATE TABLE GameSave (
    username TEXT PRIMARY KEY,
    creationDate INTEGER NOT NULL,
    lastSave INTEGER NOT NULL,
    saveNumber INTEGER NOT NULL,
    mode TEXT NOT NULL,
    admin INTEGER NOT NULL,
    cards TEXT NOT NULL,  -- JSON array
    decks TEXT NOT NULL,   -- JSON array
    stats TEXT NOT NULL,   -- JSON object
    bag TEXT NOT NULL,     -- JSON array
    boons TEXT NOT NULL,   -- JSON object
    mgp INTEGER NOT NULL,
    xp INTEGER NOT NULL,
    level INTEGER NOT NULL,
    pvpXp INTEGER NOT NULL,
    rank INTEGER NOT NULL,
    avatarId TEXT NOT NULL,
    startedMatches INTEGER NOT NULL,
    endedMatches INTEGER NOT NULL,
    pveMatches INTEGER NOT NULL,
    pvpMatches INTEGER NOT NULL,
    achievements TEXT NOT NULL, -- JSON object
    npcWins TEXT NOT NULL,    -- JSON object
    rulesWins TEXT NOT NULL    -- JSON object
);

-- Cards.sq (for caching)
CREATE TABLE Card (
    id INTEGER PRIMARY KEY,
    collection TEXT NOT NULL,
    nameKey TEXT NOT NULL,
    power TEXT NOT NULL,  -- JSON array
    rarity INTEGER NOT NULL,
    type TEXT
);
```

---

### 6. Asset Management

**Selected**: **Coil + Compose AsyncImage**

| Asset Type | Technology | Purpose |
|------------|------------|---------|
| **Images** | Coil | Loading images from assets/resources |
| **SVG** | Accompanist (if needed) | Vector graphics |
| **Fonts** | Compose Text | Custom fonts |

**Configuration**:

```kotlin
// shared/build.gradle.kts
dependencies {
    implementation("io.github.qdsfdhvh:image-loader:1.7.0")
}
```

**Usage**:

```kotlin
// Image loading in Compose
@Composable
fun CardImage(card: Card) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data("file:///android_asset/cards/${card.collection}/${card.id}.png")
            .placeholder(R.drawable.card_back)
            .crossfade(true)
            .build()
    )
    
    Image(
        painter = painter,
        contentDescription = card.name,
        modifier = Modifier.size(104.dp, 128.dp)
    )
}
```

**Asset Organization**:

```
androidApp/src/main/assets/
├── cards/
│   ├── ff14/
│   │   ├── 1.png
│   │   ├── 2.png
│   │   └── ...
│   └── ff8/
│       ├── 1.png
│       └── ...
├── card_rarities/
│   ├── 1stars.png
│   ├── 2stars.png
│   └── ...
├── card_types/
│   ├── type-beast.png
│   ├── type-fire.png
│   └── ...
├── sounds/
│   ├── se_ttriad.scd_1.mp3
│   ├── se_ttriad.scd_2.mp3
│   └── ...
└── locales/
    ├── en.json
    └── fr.json
```

---

### 7. Audio

**Selected**: **Media3 ExoPlayer (Android) + AVFoundation (iOS)**

| Platform | Technology | Purpose |
|----------|------------|---------|
| Android | Media3 ExoPlayer | Sound effects and music |
| iOS | AVFoundation | Sound effects and music |
| Shared | Custom wrapper | Unified API |

**Android Implementation**:

```kotlin
// androidMain
class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            volume = 1.0f
        }
    }
    
    override fun playSound(soundId: String, loop: Boolean) {
        val mediaItem = MediaItem.fromUri("asset:///sounds/$soundId.mp3")
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.prepare()
        exoPlayer.play()
    }
    
    override fun stopSound() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }
    
    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }
    
    override fun release() {
        exoPlayer.release()
    }
}
```

**Shared Interface**:

```kotlin
// commonMain
expect class AudioPlayer {
    fun playSound(soundId: String, loop: Boolean = false)
    fun stopSound()
    fun setVolume(volume: Float)
    fun release()
}
```

---

### 8. Internationalization

**Selected**: **JSON-based Localization**

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Storage** | JSON files | Translation strings |
| **Loading** | Resource access | Load locale files |
| **Lookup** | Map | Fast string lookup |

**Implementation**:

```kotlin
// i18n.kt (commonMain)
class I18n(private val locale: String = "en_US") {
    private val translations: Map<String, String> by lazy {
        loadTranslations(locale)
    }
    
    fun get(key: String): String = translations[key] ?: key
    fun get(key: String, vararg args: Any): String = 
        translations[key]?.format(*args) ?: key
    
    private suspend fun loadTranslations(locale: String): Map<String, String> {
        val json = assetManager.readAsset("locales/$locale.json")
        return Json.decodeFromString(json)
    }
}

// Usage
val i18n = I18n("fr_FR")
val greeting = i18n.get("STR_HELLO") // "Bonjour"
```

**Locale Files** (`shared/src/commonMain/resources/locales/`):

```json
{
  "STR_CARD": "Card",
  "STR_CARD_ITEM_DESC": "A card that can be used in Triple Triad",
  "STR_CONNECT": "Connect",
  "STR_DISCONNECT": "Disconnect",
  "STR_HELLO": "Hello",
  "STR_FF14_CARD_1": "Garula Sirus",
  "STR_FF14_CARD_2": "Tataru Taru",
  "...": "..."
}
```

---

### 9. Testing Framework

**Selected**: **Kotest + Turbine + Compose Testing**

| Test Type | Technology | Purpose |
|-----------|------------|---------|
| **Unit Tests** | Kotest | Test individual functions |
| **Coroutine Tests** | Turbine | Test Flows |
| **UI Tests** | Compose Testing | Test Compose components |
| **Integration Tests** | Custom | Test component interactions |

**Configuration**:

```kotlin
// shared/build.gradle.kts
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.koin:koin-test:3.5.6")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
}
```

**Test Examples**:

```kotlin
// Unit test with Kotest
class TTOCoreTest {
    private lateinit var core: TTOCore
    
    @BeforeTest
    fun setup() {
        core = TTOCore()
    }
    
    @Test
    fun `basic rule should flip adjacent cards with lower power`() {
        val rules = GameRules()
        val board = Board()
        
        // Setup
        val blueCard = Card(id = 1u, power = listOf("6", "6", "6", "6"))
        val redCard = Card(id = 2u, power = listOf("4", "4", "4", "4"))
        
        board[0].card = blueCard
        board[1].card = redCard
        
        // Test
        val result = core.basicRule(board[0], "BLUE")
        
        // Assert
        result.size shouldBe 1
        result[0].card shouldBe redCard
    }
}

// Flow test with Turbine
@Test
fun `viewModel should emit card placed events`() = runTest {
    val viewModel = GameViewModel()
    
    viewModel.placeCard(card, tile)
    
    viewModel.cardPlaced.test {
        awaitItem() shouldBe Pair(card, tile)
    }
}

// Compose UI test
@RunWith(AndroidJUnit4::class)
class CardComponentTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun cardComponent_displaysCardCorrectly() {
        val card = Card(id = 1u, nameKey = "test", power = listOf("5", "5", "5", "5"))
        
        composeTestRule.setContent {
            CardComponent(card = card)
        }
        
        composeTestRule.onNodeWithText("5").assertExists()
    }
}
```

---

### 10. Logging

**Selected**: **Napier**

**Configuration**:

```kotlin
// shared/build.gradle.kts
dependencies {
    implementation("io.github.aakira:napier:2.6.1")
}
```

**Usage**:

```kotlin
// Initialize
Napier.base(DebugAntilog())

// Logging
Napier.d("Debug message")
Napier.i("Info message")
Napier.w("Warning message")
Napier.e("Error message", throwable)
```

---

### 11. Navigation

**Selected**: **Compose Navigation**

**Configuration**:

```kotlin
// androidApp/build.gradle.kts
dependencies {
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
```

**Implementation**:

```kotlin
// AppNavigation.kt
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") { SplashScreen(navController) }
        composable("menu") { MenuScreen(navController) }
        composable("game/{mode}", arguments = listOf(
            navArgument("mode") { type = NavType.StringType }
        )) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "ff14"
            GameScreen(mode, navController)
        }
        // ... other routes
    }
}
```

---

## 📊 Technology Comparison

### Why Kotlin Multiplatform?

| Criteria | Kotlin MP | Flutter | React Native | Native (Separate) |
|----------|-----------|---------|--------------|------------------|
| **Code Sharing** | 80-90% | 90-95% | 80-90% | 0% |
| **Performance** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **UI Flexibility** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Native Look** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Game Support** | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Kotlin Knowledge** | Required | Not needed | Not needed | Partially needed |
| **Learning Curve** | Medium | High | Medium | High |
| **Ecosystem** | Growing | Mature | Mature | Mature |
| **Maintenance** | JetBrains | Google | Meta | Self |

**Verdict**: Kotlin MP is the best choice for a game like Triple Triad because:
1. Excellent performance (native code)
2. Full access to platform APIs
3. Growing ecosystem with Compose MP
4. Strong typing reduces bugs
5. Natural fit for game development

### Why Compose Multiplatform?

| Criteria | Compose MP | SwiftUI | Jetpack Compose | Flutter |
|----------|------------|---------|-----------------|---------|
| **Code Sharing** | ⭐⭐⭐⭐⭐ | ⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Declarative** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Animation Support** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Custom Drawing** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Learning Curve** | Medium | Medium | Low | Medium |
| **Maturity** | Growing | Mature | Mature | Mature |

**Verdict**: Compose MP is the best choice because:
1. Allows sharing UI code between Android and iOS
2. Excellent animation support (critical for games)
3. Declarative paradigm matches game UI well
4. Seamless integration with Kotlin MP

---

## 🎯 Final Technology Stack Summary

| Category | Technology | Version | Platform |
|----------|------------|---------|----------|
| **Language** | Kotlin | 2.0+ | All |
| **Multiplatform** | Kotlin Multiplatform | 2.0+ | All |
| **UI Framework** | Compose Multiplatform | 1.6.0 | Android, iOS |
| **UI (Android)** | Jetpack Compose | 1.6.0 | Android |
| **UI (iOS)** | Compose MP + SwiftUI | 1.6.0 | iOS |
| **Build System** | Gradle | 8.5+ | All |
| **DI** | Koin | 3.5.6 | All |
| **State Management** | StateFlow + SharedFlow | 1.8.0 | All |
| **Coroutines** | Kotlin Coroutines | 1.8.0 | All |
| **Serialization** | Kotlinx Serialization | 1.6.0 | All |
| **Network** | Ktor Client | 2.3.10 | All |
| **WebSocket** | Ktor WebSocket | 2.3.10 | All |
| **Database** | SQLDelight | 2.0.1 | All |
| **Settings** | Multiplatform Settings | 1.1.1 | All |
| **Image Loading** | Coil | 2.5.0 | All |
| **Audio (Android)** | Media3 ExoPlayer | 2.19.1 | Android |
| **Audio (iOS)** | AVFoundation | Native | iOS |
| **Logging** | Napier | 2.6.1 | All |
| **Navigation** | Compose Navigation | 2.7.7 | All |
| **Testing** | Kotest + Turbine | 5.8.0 + 1.0.0 | All |

---

## 📝 Implementation Guidelines

### 1. Code Organization

```
com.tripletriad/
├── core/                    # Pure business logic (100% shared)
│   ├── game/                # Game rules, state, logic
│   └── utils/               # General utilities
│
├── data/                   # Data layer (100% shared)
│   ├── models/             # Data models
│   ├── repository/         # Repository interfaces
│   └── datasource/         # Data sources (local/remote)
│
├── network/                # Network layer (100% shared)
│   ├── api/                # API definitions
│   ├── socket/             # WebSocket client
│   └── dto/                # Data Transfer Objects
│
├── ui/                     # UI layer (~80% shared)
│   ├── theme/              # Theme, colors, typography
│   ├── components/         # Reusable components
│   ├── screens/            # All game screens
│   └── navigation/         # Navigation system
│
└── platform/              # Platform-specific code
    ├── android/           # Android-specific
    │   ├── audio/         # Audio implementation
    │   └── file/          # File system
    │
    └── ios/               # iOS-specific
        ├── audio/         # Audio implementation
        └── file/          # File system
```

### 2. File Naming Conventions

| Type | Convention | Example |
|------|-------------|---------|
| **Kotlin File** | PascalCase | `Card.kt`, `TTOCore.kt` |
| **Composable** | PascalCase | `CardComponent.kt` |
| **ViewModel** | PascalCase + ViewModel | `GameViewModel.kt` |
| **Repository** | PascalCase + Repository | `CardRepository.kt` |
| **Data Class** | PascalCase | `Card.kt` |
| **Enum** | PascalCase | `CardColor.kt` |
| **Sealed Class** | PascalCase | `GameEvent.kt` |
| **Interface** | PascalCase | `CardRepository.kt` |
| **Object** | PascalCase | `AppConfig.kt` |
| **Test File** | PascalCase + Test | `TTOCoreTest.kt` |

### 3. Package Structure

```
com.tripletriad
├── core
│   ├── game
│   │   ├── TTOCore.kt
│   │   ├── TripleTriadRules.kt
│   │   └── GameState.kt
│   └── utils
│       ├── Tools.kt
│       └── extensions/
│
├── data
│   ├── models
│   │   ├── Card.kt
│   │   ├── Tile.kt
│   │   ├── Board.kt
│   │   └── GameRules.kt
│   ├── repository
│   │   ├── CardRepository.kt
│   │   └── SaveRepository.kt
│   └── datasource
│       ├── local
│       │   └── SaveLocalDataSource.kt
│       └── remote
│           └── NetworkDataSource.kt
│
└── ui
    ├── theme
    │   ├── AppTheme.kt
    │   ├── Colors.kt
    │   └── Typography.kt
    ├── components
    │   ├── cards
    │   │   ├── CardComponent.kt
    │   │   └── CardThumb.kt
    │   ├── tiles
    │   │   └── TileComponent.kt
    │   └── animations
    │       └── FlipAnimation.kt
    └── screens
        ├── menu
        │   └── MenuScreen.kt
        ├── game
        │   ├── BaseMatchScreen.kt
        │   ├── PVEMatchScreen.kt
        │   └── PVPMatchScreen.kt
        └── common
            └── LoadingScreen.kt
```

---

## 🎯 Next Steps

1. **Set up project structure** - Phase 1
2. **Configure all dependencies** - Phase 1
3. **Create base classes** - Phase 2
4. **Implement first component** - Phase 2

---

*This document defines the technical stack for the migration. For implementation details, see the specific phase documents.*
