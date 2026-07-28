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
| **Build Tool** | Gradle | 8.7+ | Industry standard, Kotlin DSL support |
| **Kotlin Plugin** | Kotlin Multiplatform | 2.0.21 | Required for KMP |
| **Compose Compiler Plugin** | `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | **Versioned with Kotlin from 2.0 onward** |
| **Compose Plugin** | JetBrains Compose | 1.6.11 | Compose Multiplatform support |
| **Android Gradle Plugin** | AGP | 8.5.2 | Android support |

> ⚠️ **Compatibility correction.** An earlier revision paired "Kotlin 2.0+" with
> "Compose MP 1.6.0". That combination does not build: Compose MP 1.6.0 is
> compiled against Kotlin 1.9.2x. Compose MP 1.6.11 is the first release in the
> 1.6 line that supports Kotlin 2.0.
>
> Also note that from Kotlin 2.0 the Compose compiler moved **into** the Kotlin
> repository. `composeOptions { kotlinCompilerExtensionVersion = … }` and the
> `androidx.compose.compiler:compiler` artifact are obsolete; apply the
> `org.jetbrains.kotlin.plugin.compose` plugin instead and let it track the
> Kotlin version.
>
> Pick **one** set and record it in a version catalog. Set C is the one to use:
> it is the only one that has actually been compiled here.
>
> | | Set A (conservative) | Set B | **Set C (measured — use this)** |
> |---|---|---|---|
> | Kotlin | 1.9.24 | 2.0.21 | **2.2.20** |
> | Compose MP | 1.6.11 | 1.6.11 | **1.9.3** |
> | Compose compiler | `composeOptions` ext. 1.5.14 | `kotlin.plugin.compose` 2.0.21 | **`kotlin.plugin.compose` 2.2.20** |
> | Compose resources | — | — | **`compose.components.resources` 1.9.3** |
> | kotlinx.serialization | — | — | **1.9.0** (plugin tracks Kotlin) |
> | AGP | 8.4.2 | 8.5.2 | **9.3.1** |
> | Gradle | 8.7 | 8.9 | **9.6.1** |
> | JDK | 17 | 17 | **17** |
> | compileSdk / minSdk | — | — | **36 / 24** |
> | ktlint plugin | — | — | **`org.jlleitschuh.gradle.ktlint` 12.1.2** |
> | detekt | — | — | **1.23.8** |
> | Status | reasoned only | reasoned only | **built: Android debug+release APK, JVM desktop, 47 test executions green, static analysis clean at `maxIssues = 0`, run on a physical Pixel 6a** |
>
> Sets A and B are internally consistent on paper but were never built; the whole
> point of the Set C column is that it was. See
> [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) and
> [README.md § Verified build results](../../README.md#verified-build-results).
>
> **What Set C now covers**, beyond the base Compose UI stack: `kotlinx.serialization`
> for JSON, and `compose.components.resources` for loading a file out of
> `commonMain/composeResources` — which is also the mechanism the 263 card *images* will
> need, so this is not an incidental addition. Both are exercised end-to-end by tests
> that read the shipped 263-card catalog, on desktop and androidHostTest.
>
> **What Set C still does not cover.** Do not assume these work with the versions above
> until someone has compiled them: **Ktor**, **SQLDelight**, **Koin**, **Media3**, and
> **Kover** (needed for coverage, since JaCoCo does not cover Kotlin/Native).
>
> Set C's Apple coverage is now **narrow but real**: the `ios-framework` CI job links
> `shared.framework` for `iosSimulatorArm64` and runs its common tests on `macos-latest`,
> and passes. So Compose Multiplatform, kotlinx.serialization and Compose resources compile
> for an Apple target at these versions. What is still unproven there is everything above
> the framework boundary — no `.xcodeproj`, no simulator run, no UI ever rendered on iOS.
> Nothing Apple can be compiled from the Windows host used for local development.
> Verifying the remaining libraries is Phase 1 work — see
> [04-PHASE-0-PREPARATION.md](./04-PHASE-0-PREPARATION.md).

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
    // `android()` was removed in Kotlin 1.9 — use androidTarget().
    androidTarget()

    // The `ios()` shortcut was removed in Kotlin 1.9.20 — declare targets explicitly.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm() // desktop / test target

    sourceSets {
        commonMain.dependencies {
            // Shared dependencies
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // Android-specific dependencies
        }
        // iosMain is created automatically by the default hierarchy template
        // and is the parent of iosX64Main / iosArm64Main / iosSimulatorArm64Main.
        iosMain.dependencies {
            // iOS-specific dependencies
        }
    }
}
```

> **Note**: in KMP, test dependencies go in `commonTest`/`androidUnitTest` source
> sets — a bare top-level `dependencies { testImplementation(...) }` block does
> nothing in a multiplatform module.

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
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose") version "1.6.11"
    // Required from Kotlin 2.0: the Compose compiler ships with Kotlin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

// No `compose { kotlinCompilerExtensionVersion = ... }` block:
// that property does not exist on the Compose Multiplatform extension.
// The compiler version is determined by the kotlin.plugin.compose version.
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
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            // NOTE: ktor-client-serialization is the deprecated Ktor 1.x artifact.
            // Use ktor-client-content-negotiation + ktor-serialization-kotlinx-json.
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.12")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.12")
        }
    }
}
```

**Implementation**:

```kotlin
// SocketManager.kt
class SocketManager(
    private val client: HttpClient,
    private val scope: CoroutineScope
) {
    private var session: DefaultClientWebSocketSession? = null
    private var sessionJob: Job? = null
    private val _messages = MutableSharedFlow<SocketMessage>()
    val messages: SharedFlow<SocketMessage> = _messages.asSharedFlow()

    // `client.webSocket(url) { ... }` returns Unit and closes the session when the
    // block ends — it cannot be assigned to a field. Use webSocketSession() to get
    // a long-lived session, and pump `incoming` in a separate coroutine.
    suspend fun connect(serverUrl: String) {
        val s = client.webSocketSession(urlString = serverUrl)
        session = s
        sessionJob = scope.launch {
            try {
                for (frame in s.incoming) {
                    if (frame is Frame.Text) {
                        _messages.emit(Json.decodeFromString<SocketMessage>(frame.readText()))
                    }
                }
            } finally {
                session = null
            }
        }
    }

    suspend fun send(message: SocketMessage) {
        session?.send(Frame.Text(Json.encodeToString(message)))
    }

    // WebSocketSession.close() is a suspend function.
    suspend fun disconnect() {
        session?.close()
        sessionJob?.cancelAndJoin()
        session = null
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
// commonMain
expect fun createHttpClient(): HttpClient

// androidMain
actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// iosMain — the engine is `Darwin` (io.ktor:ktor-client-darwin).
// The old `Ios` engine was deprecated in Ktor 2.0 and removed in 3.0.
actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
```

> **Caveat**: `install(WebSockets)` on the Darwin engine is supported from Ktor
> 2.3.x onward. Verify WebSocket behaviour on a physical iOS device during Phase 1,
> not just the simulator.

---

### 5. Local Storage

**Selected**: **SQLDelight** (with SharedPreferences for simple data)

| Data Type | Technology | Purpose |
|-----------|------------|---------|
| **Structured Data** | SQLDelight | Save games, user profiles, decks |
| **Preferences** | Multiplatform Settings | Simple key-value settings |
| **Encryption** | expect/actual AES wrapper | Read **legacy** `.sav` files — must reproduce `com.hurlant.crypto.symmetric.AESKey` key/mode/padding exactly, see [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) |

**Configuration**:

```kotlin
// shared/build.gradle.kts
plugins {
    id("app.cash.sqldelight") version "2.0.2"
}

sqldelight {
    databases {
        create("TripleTriad") {
            packageName.set("com.tripletriad.data.db")
        }
    }
}

dependencies {
    implementation("app.cash.sqldelight:runtime:2.0.2")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
    implementation("app.cash.sqldelight:primitive-adapters:2.0.2")
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
}

// Platform drivers are required and easy to forget:
//   androidMain: app.cash.sqldelight:android-driver:2.0.2
//   iosMain:     app.cash.sqldelight:native-driver:2.0.2
//   jvmMain:     app.cash.sqldelight:sqlite-driver:2.0.2
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

**Selected**: **Compose Multiplatform Resources** (`org.jetbrains.compose.components.resources`)

> ⚠️ **Corrected.** An earlier revision selected "Coil + Compose AsyncImage" but
> then listed the `io.github.qdsfdhvh:image-loader` dependency, and the usage
> example called `LocalContext.current`, `ImageRequest.Builder` and `R.drawable` —
> all Android-only APIs, in code that was supposed to live in `commonMain`.
> Coil **2.x is Android-only**; only Coil 3 supports KMP.
>
> All card art ships **inside the app** — there is no network image loading in this
> game. An async image loader is the wrong tool: use Compose Resources, which is
> multiplatform, compile-time checked, and requires no third-party dependency.

| Asset Type | Technology | Purpose |
|------------|------------|---------|
| **Bundled images** | Compose Resources (`painterResource`) | Card art, UI, backgrounds |
| **Remote images** (if ever needed) | Coil 3 (`coil3.compose.AsyncImage`) | Avatars from server — not required for v1 |
| **Fonts** | Compose Resources (`Font(Res.font.…)`) | Custom fonts |
| **Vector graphics** | Compose `ImageVector` / bundled SVG via Resources | Icons. *Accompanist is Android-only and not an SVG library* |

**Configuration**:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.components.resources)
        }
    }
}
```

Resources live in `shared/src/commonMain/composeResources/` and generate a typed
`Res` accessor:

```
shared/src/commonMain/composeResources/
├── drawable/
│   ├── card_back.png
│   ├── ff14_card_1.png
│   └── ...
├── font/
│   └── eurostile.ttf
└── values/
    ├── strings.xml        # en (default)
    └── strings-fr.xml     # etc.
```

**Usage** (fully multiplatform — no `Context`, no `R`):

```kotlin
@Composable
fun CardImage(card: Card) {
    Image(
        painter = painterResource(cardDrawable(card)),
        contentDescription = null, // decorative; the name is rendered as text
        modifier = Modifier.size(88.dp, 118.dp) // matches AS3 Card.as
    )
}
```

> **Atlas note**: the AS3 build packs card art into Starling texture atlases
> (`sources/bin/assets/atlas/ff14_cards.xml` + `.png`). Phase 1 needs a one-off
> script to slice those atlases into individual images keyed by `SubTexture name`,
> or a small Compose helper that draws a sub-rectangle of the atlas bitmap. Decide
> which before Task 1.9 — the atlas route saves memory but needs custom drawing
> code. This work is **not currently in any task estimate**.

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
└── locales/          # 4 locales, matching application.xml supportedLanguages
    ├── de_DE.json
    ├── en_US.json
    ├── fr_FR.json
    └── ja_JA.json
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

**Locale Files** (`shared/src/commonMain/resources/locales/`) — **4 locales**:
`de_DE`, `en_US`, `fr_FR`, `ja_JA`, per `application.xml`
(`<supportedLanguages>de en fr ja</supportedLanguages>`) and
`utils/conf.as::supportedLanguages`. Earlier revisions of this plan listed only
EN/FR and would have dropped German and Japanese support. Japanese also requires a
CJK-capable font — the bundled `Eurostile` face has no CJK coverage, and
`sources/bin/assets/fonts/` must be audited during Task 1.10.

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
kotlin {
    sourceSets {
        // Multiplatform tests: must be KMP-compatible libraries only.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")
            implementation("io.kotest:kotest-property:5.9.1")
            implementation("app.cash.turbine:turbine:1.1.0")
            implementation("io.insert-koin:koin-test:3.5.6") // group is io.insert-koin, NOT org.koin
        }
        // MockK is JVM-only — it cannot go in commonTest.
        androidUnitTest.dependencies {
            implementation("io.mockk:mockk:1.13.12")
        }
        jvmTest.dependencies {
            implementation("io.mockk:mockk:1.13.12")
        }
    }
}

// androidApp/build.gradle.kts — instrumented tests
dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
}
```

> ⚠️ **Three corrections applied**:
> 1. `org.koin:koin-test` → **`io.insert-koin:koin-test`**. The `org.koin` group
>    does not exist on Maven Central; the previous coordinate would fail to resolve.
> 2. **MockK is JVM-only.** It has no Kotlin/Native target, so it cannot be used
>    in `commonTest` for iOS. For shared code, prefer hand-written fakes, or use
>    [mokkery](https://mokkery.dev/) which is KMP-native. See
>    [17-TESTING-GUIDE.md](./17-TESTING-GUIDE.md).
> 3. Test dependencies must be declared **per source set** in a KMP module. A
>    top-level `dependencies { testImplementation(…) }` block is silently ignored.
>
> **Also**: "Unit Tests: Kotest" is only half-true as written. The examples below
> use `kotlin.test` annotations (`@Test`, `@BeforeTest`) with *Kotest assertions*
> (`shouldBe`). That is a valid and common combination, but it is **not** the Kotest
> spec runner (`FunSpec`, `StringSpec`) — you cannot mix `@BeforeTest` with a Kotest
> spec class and expect it to run. Pick one style per test class and state which.

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
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.aakira:napier:2.7.1")
        }
    }
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

All versions below are mutually compatible (Set B from the Build System section).
Put them in `gradle/libs.versions.toml` so they cannot drift apart.

| Category | Technology | Version | Platform |
|----------|------------|---------|----------|
| **Language** | Kotlin | 2.0.21 | All |
| **Multiplatform** | Kotlin Multiplatform | 2.0.21 | All |
| **UI Framework** | Compose Multiplatform | 1.6.11 | Android, iOS |
| **UI (Android)** | Jetpack Compose BOM | 2024.06.00 | Android |
| **UI (iOS)** | Compose MP + SwiftUI interop | 1.6.11 | iOS |
| **Compose compiler** | `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | All |
| **Build System** | Gradle | 8.9 | All |
| **AGP** | Android Gradle Plugin | 8.5.2 | Android |
| **DI** | Koin (`io.insert-koin`) | 3.5.6 | All |
| **State Management** | StateFlow + SharedFlow | 1.8.1 | All |
| **Coroutines** | Kotlin Coroutines | 1.8.1 | All |
| **Serialization** | Kotlinx Serialization | 1.7.1 | All |
| **Network** | Ktor Client | 2.3.12 | All |
| **WebSocket** | Ktor WebSocket | 2.3.12 | All |
| **HTTP engine** | ktor-client-okhttp / ktor-client-darwin | 2.3.12 | Android / iOS |
| **Database** | SQLDelight | 2.0.2 | All |
| **Settings** | Multiplatform Settings | 1.1.1 | All |
| **Images** | Compose Resources (`compose.components.resources`) | 1.6.11 | All |
| **Audio (Android)** | Media3 ExoPlayer (`androidx.media3`) | **1.3.1** | Android |
| **Audio (iOS)** | AVFoundation | Native | iOS |
| **Logging** | Napier | 2.7.1 | All |
| **Navigation** | Compose Navigation | 2.7.7 (Android) — see note | Android |
| **Testing** | kotlin.test + Kotest assertions + Turbine | 5.9.1 + 1.1.0 | All |

> **Version corrections from the previous revision**:
> - **Media3 was listed as `2.19.1`.** That is an `com.google.android.exoplayer`
>   version number. `androidx.media3` uses its own 1.x scheme — the correct
>   coordinate is `androidx.media3:media3-exoplayer:1.3.1`. `2.19.1` does not exist.
> - **Coil 2.5.0 was listed as "All" platforms.** Coil 2.x is Android-only.
>   Replaced with Compose Resources (see §6).
> - **Kotlin 2.0 + Compose MP 1.6.0** is not a valid pairing (see §Build System).
>
> **Navigation caveat**: `androidx.navigation:navigation-compose` 2.7.7 is
> **Android-only**. For shared navigation in Compose MP either
> (a) use `org.jetbrains.androidx.navigation:navigation-compose` (the KMP port,
> 2.7.0-alpha0x at time of writing — alpha, so validate in the PoC), or
> (b) implement navigation as a `StateFlow<Screen>` in shared code with a
> `when` on the current destination, which is trivial for this app's ~22 routes
> and avoids an alpha dependency. **Option (b) is recommended for v1.**

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
