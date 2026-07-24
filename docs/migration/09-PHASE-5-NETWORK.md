# Phase 5: Network Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 5 - Network Layer
- **Duration**: 3 weeks (Weeks 21-23)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phases 1-4

---

## 🎯 Phase Overview

### 🔴 Read this before estimating the phase

**This phase is not a migration.** `net/Socket.as` declares 29 `Socket_On_*`
handlers, but `dataHandler()` — the only inbound entry point — dispatches to
exactly **two** of them (`pong`, `clients`). The remaining 27 have a single
reference each: their own declaration. They are orphaned remnants of an abandoned
XML→JSON protocol refactor.

**Multiplayer does not work in the AS3 source.** No card synchronisation, no match
start, no swap, no elements, no trade. There is nothing to reach parity with.

Two further blockers:
1. **Transport incompatibility.** `flash.net.XMLSocket` is a raw TCP socket. Ktor
   WebSocket cannot connect to it — no HTTP upgrade, no frame protocol. Either the
   server gains a WebSocket endpoint or a TCP↔WebSocket proxy is introduced. Both
   are **backend work**, contradicting the "server remains as-is" scope in
   [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md).
2. **Unknown server availability.** The production endpoint is commented out in
   `PVPScreen.as:315` (`triple-triad-online.com:2468`); the live code points at
   `localhost:3000`. Confirm a server exists before planning against it.

**Recommended re-scope**: drop PvP from v1 (removes 3 weeks, loses no working
functionality) and treat multiplayer as a post-launch greenfield feature with its
own design, client **and server** budget — realistically 8-12 weeks, not 3.

If PvP is retained, read the rest of this document as a **design proposal for new
work**, not a port. See **TR-007** in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md).

---

### Purpose
Replace the AS3 XMLSocket transport with a WebSocket implementation and **specify**
the game protocol, since the existing one is incomplete and largely unreachable.

### Key Objectives
1. Port the *working* parts of `Socket.as` — connect, ping/pong, user list (~120 of
   its 649 lines are live)
2. **Specify** the server protocol (the dead handlers are design input, not a spec)
3. Coordinate the required server-side changes
4. Implement Ktor WebSocket client
5. Design and implement the ~15 game message types that were never wired up
6. Connection management (ping/pong, reconnection)
7. Integrate with game state
8. Test network communication

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 21 | Protocol analysis, Socket migration | Tech Lead + Network Team |
| Week 22 | Message handling, Connection management | Network Team |
| Week 23 | Integration, Testing | Network Team + QA |

---

## 📝 Tasks

### Week 21: Protocol Analysis & Socket Migration

#### Task 5.1: Reverse-Engineer Server Protocol
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**Socket.as Analysis** (from 02-CURRENT-SYSTEM-ANALYSIS.md):
- XMLSocket protocol (old Flash technology)
- Many message types to handle (20+)
- Connection state management
- Ping/pong for keepalive (1000 ms - `Socket.as:31 pingDelay`)
- Room-based system; default room value is `'Gold Saucer'` (`Socket.as:30`)

**Message Types Identified** - WARNING: only `pong` and `clients` are actually
dispatched today; the rest are declared but unreachable (see the warning at the top
of this document). The full set of 29 declared handlers is listed in
[02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) section 8; the
subset below is what a v2 protocol would need:
- `pong` - Ping response
- `clients` - User list update
- `new_game` - New game created
- `actu_game` - Game state update
- `start_game` - Game start
- `ready` - Opponent ready
- `setCards` - Cards set
- `initiative` - Initiative result
- `swap` - Card swap
- `elements` - Element setup
- `cardMove` - Card movement
- `tradeCards` - Card trade
- `chat` - Chat message
- `error` - Error message
- `connection` - Connection event

**Protocol Documentation to Create**:
```
# Server Protocol Specification

## Connection
- Protocol: WebSocket (replaces raw-TCP XMLSocket) - REQUIRES SERVER CHANGES
- Port: 3000 in the AS3 dev config; 2468 for the commented-out production host.
  (An earlier revision guessed "1935 or 8080" - neither appears in the source.)
- Format: JSON. Note the AS3 client already sends JSON for most outbound messages
  and parses JSON inbound; the XML paths are the dead ones.

## Message Format
All messages are JSON objects with a "type" field:
{
  "type": "message_type",
  "data": { ... }
}

## Message Types

### Connection Messages
- **connect**: Client connects to server
  - Request: {"type": "connect", "data": {"username": "...", "room": "main_room"}}
  - Response: {"type": "connected", "data": {"users": [...], "sessionId": "..."}}

- **pong**: Response to ping
  - Request: {"type": "ping"}
  - Response: {"type": "pong"}

### Game Messages
- **new_game**: New game created
  - Data: {"gameId": "...", "creator": "...", "rules": {...}}

- **actu_game**: Game state update
  - Data: {"gameId": "...", "state": {...}}

- **start_game**: Game start
  - Data: {"gameId": "...", "players": [...], "rules": {...}}

- **setCards**: Cards set
  - Data: {"gameId": "...", "player": "...", "cards": [...]}

- **cardMove**: Card played
  - Data: {"gameId": "...", "player": "...", "cardId": 1, "position": 0}

- **game_over**: Game ended
  - Data: {"gameId": "...", "winner": "...", "scores": {...}}

### Chat Messages
- **chat**: Chat message
  - Data: {"from": "...", "message": "...", "timestamp": 1234567890}

### Lobby Messages
- **clients**: User list update
  - Data: {"users": [{"username": "...", "status": "..."}, ...]}
```

**Deliverables**:
- `docs/protocol/server-protocol.md`
- Protocol test suite
- Example message captures

**Acceptance Criteria**:
- [ ] All message types documented
- [ ] Protocol validated with server
- [ ] Message formats defined

---

#### Task 5.2: Migrate Socket.as to SocketManager
**Owner**: Network Team | **Duration**: 4 days | **Priority**: CRITICAL

**AS3 Socket.as Key Properties**:
- `_socket: XMLSocket` - Flash XMLSocket
- `main_room: String` - Default room
- `pingDelay: int` - Ping interval (1000ms)
- `_isInit: Boolean` - Initialization state
- `_connected: Boolean` - Connection state
- `_usersList: Array` - User list

**AS3 Socket.as Key Methods**:
- `connect(param: Object): void` - Connect to server
- `send(value: Object): void` - Send message
- `close(): void` - Close connection
- `sendPing(): void` - Send ping
- `dataHandler(e: DataEvent): void` - Handle incoming data
- Multiple `Socket_On_*` methods for message types

**Kotlin Implementation**:
```kotlin
// SocketManager.kt
class SocketManager(
    private val client: HttpClient,
    private val configuration: SocketConfiguration
) {
    private var session: DefaultClientWebSocketSession? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Messages
    private val _incomingMessages = MutableSharedFlow<SocketMessage>()
    val incomingMessages: SharedFlow<SocketMessage> = _incomingMessages.asSharedFlow()
    
    // Users
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    
    // Current game
    private val _currentGame = MutableStateFlow<GameState?>(null)
    val currentGame: StateFlow<GameState?> = _currentGame.asStateFlow()
    
    suspend fun connect(room: String = configuration.defaultRoom) {
        close()
        
        _connectionState.value = ConnectionState.Connecting
        
        try {
            // `client.webSocket(...) { }` returns Unit and tears the session down
            // when the block exits — it cannot be assigned to a field, and there is
            // no `room` property on the session. Use webSocketSession() instead.
            session = client.webSocketSession {
                url {
                    protocol = if (configuration.useSSL) URLProtocol.WSS else URLProtocol.WS
                    host = configuration.host
                    port = configuration.port
                    path(configuration.path)
                }
            }

            send(SocketMessage.Connect(room))   // room is a message, not a session field

            _connectionState.value = ConnectionState.Connected(room)
            startPing()
            startMessageHandler()

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e)
            scheduleReconnect()
        }
    }
    
    suspend fun send(message: SocketMessage) {
        try {
            session?.send(Frame.Text(Json.encodeToString(message)))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e)
        }
    }
    
    // WebSocketSession.close() is a suspend function, so this must suspend too.
    suspend fun close() {
        pingJob?.cancelAndJoin()
        reconnectJob?.cancelAndJoin()
        session?.close()
        session = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    private fun startPing() {
        pingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(configuration.pingInterval)
                try {
                    send(SocketMessage.Ping)
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error(e)
                    close()
                    break
                }
            }
        }
    }
    
    private fun startMessageHandler() {
        CoroutineScope(Dispatchers.IO).launch {
            session?.incoming?.consumeAsFlow()?.collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val message = Json.decodeFromString<SocketMessage>(frame.readText())
                        handleMessage(message)
                    }
                    else -> {
                        AppLogger.w("Unknown frame type: ${frame::class.simpleName}")
                    }
                }
            }
        }
    }
    
    private suspend fun handleMessage(message: SocketMessage) {
        when (message) {
            is SocketMessage.Pong -> handlePong()
            is SocketMessage.Clients -> handleClients(message.users)
            is SocketMessage.NewGame -> handleNewGame(message.game)
            is SocketMessage.GameUpdate -> handleGameUpdate(message.state)
            is SocketMessage.GameStart -> handleGameStart(message.game)
            is SocketMessage.OpponentReady -> handleOpponentReady(message.player)
            is SocketMessage.CardsSet -> handleCardsSet(message)
            is SocketMessage.Initiative -> handleInitiative(message)
            is SocketMessage.CardSwap -> handleCardSwap(message)
            is SocketMessage.Elements -> handleElements(message)
            is SocketMessage.CardMove -> handleCardMove(message)
            is SocketMessage.CardTrade -> handleCardTrade(message)
            is SocketMessage.Chat -> handleChat(message)
            is SocketMessage.Error -> handleError(message.error)
            else -> AppLogger.w("Unknown message type: ${message::class.simpleName}")
        }
        
        _incomingMessages.emit(message)
    }
    
    private fun scheduleReconnect() {
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            while (attempts < configuration.maxReconnectAttempts && isActive) {
                attempts++
                delay(configuration.reconnectDelay)
                try {
                    connect()
                    return@launch // Success
                } catch (e: Exception) {
                    AppLogger.e("Reconnect attempt $attempts failed", e)
                }
            }
            _connectionState.value = ConnectionState.Disconnected
        }
    }
}
```

**SocketMessage Sealed Class**:
```kotlin
// SocketMessage.kt
@Serializable
sealed class SocketMessage {
    // Connection
    @Serializable object Ping : SocketMessage()
    @Serializable object Pong : SocketMessage()
    @Serializable data class Connect(val room: String) : SocketMessage()
    @Serializable data class Connected(val users: List<User>) : SocketMessage()
    
    // Lobby
    @Serializable data class Clients(val users: List<User>) : SocketMessage()
    
    // Game
    @Serializable data class NewGame(val game: GameInfo) : SocketMessage()
    @Serializable data class GameUpdate(val state: GameState) : SocketMessage()
    @Serializable data class GameStart(val game: GameStartInfo) : SocketMessage()
    @Serializable data class OpponentReady(val player: String) : SocketMessage()
    @Serializable data class CardsSet(
        val player: String,
        val cards: List<Card>
    ) : SocketMessage()
    @Serializable data class Initiative(val result: String) : SocketMessage()
    @Serializable data class CardSwap(
        val player: String,
        val cardIndex: Int,
        val newCard: Card
    ) : SocketMessage()
    @Serializable data class Elements(val elements: List<Element>) : SocketMessage()
    @Serializable data class CardMove(
        val gameId: String,
        val player: String,
        val cardIndex: Int,
        val position: Int
    ) : SocketMessage()
    @Serializable data class CardTrade(
        val player: String,
        val cardIndex: Int
    ) : SocketMessage()
    
    // Chat
    @Serializable data class Chat(
        val from: String,
        val message: String,
        val timestamp: Long
    ) : SocketMessage()
    
    // Errors
    @Serializable data class Error(val error: String) : SocketMessage()
}

// WARNING: these were declared `@JvmInline value class` with 2-3 constructor
// parameters. A `value class` may have EXACTLY ONE parameter, and `@JvmInline`
// is a JVM-only annotation with no place in commonMain. Plain data classes:
@Serializable
data class User(val username: String, val status: String)

@Serializable
data class GameInfo(val id: String, val creator: String, val rules: GameRules)

@Serializable
data class GameStartInfo(val id: String, val players: List<User>, val rules: GameRules)
```

**Connection State**:
```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val room: String) : ConnectionState()
    data class Error(val exception: Throwable) : ConnectionState()
}
```

**Acceptance Criteria**:
- [ ] SocketManager compiles
- [ ] All message types defined
- [ ] Connection handling works
- [ ] Message routing works

---

### Week 22: Message Handling & Connection Management

#### Task 5.3: Implement Message Handlers
**Owner**: Network Team | **Duration**: 3 days | **Priority**: CRITICAL

**Message Handlers to Implement** (from Socket.as):
- `Socket_On_pong()` - Pong response
- `Socket_On_clients(clients: Array)` - User list update
- `Socket_On_new_game(node: XML)` - New game created
- `Socket_On_actu_game(node: XML)` - Game state update
- `Socket_On_start_game(node: XML)` - Game start
- `Socket_On_ready(node: XML)` - Opponent ready
- `Socket_On_setCards(node: XML)` - Cards set
- `Socket_On_initiative(node: XML)` - Initiative result
- `Socket_On_swap(node: XML)` - Card swap
- `Socket_On_elements(node: XML)` - Element setup
- `Socket_On_cardMove(node: XML)` - Card movement
- `Socket_On_tradeCards(node: XML)` - Card trade

**Handler Implementation**:
```kotlin
// In SocketManager.kt
private fun handlePong() {
    // Update last ping time
    // Reset connection timeout
}

private fun handleClients(users: List<User>) {
    _users.value = users
}

private fun handleNewGame(game: GameInfo) {
    // Notify that new game is available
    // Can join or ignore
}

private fun handleGameUpdate(state: GameState) {
    _currentGame.value = state
}

private fun handleGameStart(game: GameStartInfo) {
    // Start the game
    // Set up board, players, etc.
}

private fun handleOpponentReady(player: String) {
    // Opponent is ready
    // Can start game if both ready
}

private fun handleCardsSet(message: SocketMessage.CardsSet) {
    // Opponent has set their cards
}

private fun handleInitiative(message: SocketMessage.Initiative) {
    // Handle initiative result (who goes first)
}

private fun handleCardSwap(message: SocketMessage.CardSwap) {
    // Handle card swap for Swap rule
}

private fun handleElements(message: SocketMessage.Elements) {
    // Set element types on board
}

private fun handleCardMove(message: SocketMessage.CardMove) {
    // Opponent played a card
    // Update board state
}

private fun handleCardTrade(message: SocketMessage.CardTrade) {
    // Handle card trade
}

private fun handleChat(message: SocketMessage.Chat) {
    // Display chat message
}

private fun handleError(error: String) {
    // Display error to user
    AppLogger.e("Server error: $error")
}
```

**Acceptance Criteria**:
- [ ] All message handlers implemented
- [ ] Handlers update state correctly
- [ ] Error handling works

---

#### Task 5.4: Connection Management
**Owner**: Network Team | **Duration**: 2 days | **Priority**: HIGH

**Features to Implement**:
- Automatic reconnection
- Connection timeout
- Network status monitoring
- Room management
- Multiple connection handling

**Connection Configuration**:
```kotlin
@Serializable
data class SocketConfiguration(
    // AS3 dev target was PVPScreen.as:315 -> {ip:"localhost", port:"3000"};
    // the commented-out production target was triple-triad-online.com:2468.
    // 8080 was a guess in an earlier revision and matches nothing in the source.
    val host: String = "localhost",
    val port: Int = 3000,
    val path: String = "/socket",
    // Socket.as:30 -> main_room = 'Gold Saucer'. "main_room" is the VARIABLE
    // name, not the room name - an earlier revision used it as the value.
    val defaultRoom: String = "Gold Saucer",
    val pingInterval: Long = 1000,   // Socket.as:31 pingDelay
    val connectionTimeout: Long = 5000,
    val maxReconnectAttempts: Int = 5,
    val reconnectDelay: Long = 3000,
    val useSSL: Boolean = false
)
```

**Connection Monitoring**:
```kotlin
// NetworkMonitor.kt
interface NetworkMonitor {
    val isConnected: Flow<Boolean>
    val connectionType: Flow<ConnectionType>
}

enum class ConnectionType {
    NONE, WIFI, CELLULAR, ETHERNET, UNKNOWN
}

// WARNING: the previous version wrote:
//     expect class AndroidNetworkMonitor() : NetworkMonitor
//     actual class IosNetworkMonitor()   : NetworkMonitor
// That is not how expect/actual works: an `actual` must have the SAME name as its
// `expect`, and platform-prefixed names defeat the point. Declare one expected
// factory in commonMain and provide platform actuals:

// commonMain
expect fun createNetworkMonitor(): NetworkMonitor

// androidMain - needs a Context, so inject it (e.g. via Koin)
actual fun createNetworkMonitor(): NetworkMonitor = AndroidNetworkMonitor(appContext)

// iosMain - backed by NWPathMonitor
actual fun createNetworkMonitor(): NetworkMonitor = IosNetworkMonitor()
```

**Acceptance Criteria**:
- [ ] Reconnection works
- [ ] Timeout handling works
- [ ] Network status updates
- [ ] Room management works

---

### Week 23: Integration & Testing

#### Task 5.5: Network Integration with Game
**Owner**: Network Team + Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**Integration Points**:
1. **PVPMatchScreen** - Network game screen
2. **PVPScreen** - Lobby screen
3. **GameViewModel** - Game state updates from network
4. **SocketManager** - Connection to game

**Integration Implementation**:
```kotlin
// PVPMatchScreen.kt
@Composable
fun PVPMatchScreen(
    navController: NavController,
    socketManager: SocketManager = koinInject()
) {
    val connectionState by socketManager.connectionState.collectAsState()
    val currentGame by socketManager.currentGame.collectAsState()
    
    LaunchedEffect(Unit) {
        socketManager.connect()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            socketManager.close()
        }
    }
    
    // Collect network messages
    LaunchedEffect(Unit) {
        socketManager.incomingMessages.collect { message ->
            when (message) {
                is SocketMessage.GameStart -> {
                    // Initialize game
                }
                is SocketMessage.GameUpdate -> {
                    // Update game state
                }
                is SocketMessage.CardMove -> {
                    // Process opponent's move
                }
                // ... etc
            }
        }
    }
    
    // Display based on state
    when (connectionState) {
        ConnectionState.Disconnected -> DisconnectedScreen()
        ConnectionState.Connecting -> LoadingScreen()
        is ConnectionState.Connected -> {
            if (currentGame != null) {
                ActiveGameScreen(game = currentGame!!)
            } else {
                LobbyScreen()
            }
        }
        is ConnectionState.Error -> ErrorScreen(error = (connectionState as ConnectionState.Error).exception)
    }
}
```

**Acceptance Criteria**:
- [ ] Network messages update game state
- [ ] Game state syncs between players
- [ ] All game actions send network messages

---

#### Task 5.6: Network Testing
**Owner**: QA Engineer | **Duration**: 3 days | **Priority**: CRITICAL

**Testing Strategy**:
1. **Unit Tests**: Test SocketManager in isolation
2. **Integration Tests**: Test with mock server
3. **End-to-End Tests**: Test with real server
4. **Stress Tests**: Test with many connections
5. **Edge Cases**: Test error conditions

**Test Types**:
```kotlin
// SocketManagerTest.kt
class SocketManagerTest : BaseTest() {
    private lateinit var socketManager: SocketManager
    // MockK is JVM-only - these tests can only live in jvmTest/androidUnitTest,
    // not commonTest. Also the helper is `mockk<T>()`; `mockkClass` takes a KClass.
    private val mockClient = mockk<HttpClient>()
    private val mockWebSocket = mockk<DefaultClientWebSocketSession>()
    
    @BeforeTest
    fun setup() {
        socketManager = SocketManager(mockClient, SocketConfiguration())
    }
    
    @Test
    fun `connect emits Connected state on success`() = runTest {
        coEvery { mockClient.webSocket(any(), any(), any()) } returns mockWebSocket
        coEvery { mockWebSocket.send(any()) } just Runs
        coEvery { mockWebSocket.incoming } returns emptyFlow()
        
        val states = mutableListOf<ConnectionState>()
        socketManager.connectionState.onEach { states.add(it) }.launchIn(this)
        
        socketManager.connect()
        
        // Wait for state changes
        delay(100)
        
        states shouldContain ConnectionState.Connecting
        states.last() shouldBe ConnectionState.Connected("main_room")
    }
    
    @Test
    fun `reconnect attempts on connection failure`() = runTest {
        coEvery { mockClient.webSocket(any(), any(), any()) } throws Exception("Connection failed")
        
        val states = mutableListOf<ConnectionState>()
        socketManager.connectionState.onEach { states.add(it) }.launchIn(this)
        
        socketManager.connect()
        delay(100)
        
        // `ConnectionState.Error` is a data class, not an object - you cannot
        // compare a value to the class itself. Match on the type instead:
        states.any { it is ConnectionState.Error } shouldBe true
        // Reconnect should be attempted
    }
    
    @Test
    fun `ping pong maintains connection`() = runTest {
        // Test ping/pong flow
    }
    
    @Test
    fun `message routing works correctly`() = runTest {
        // Test message handling
    }
}

// Mock WebSocket for testing
class MockWebSocket : WebSocketSession {
    override val call: HttpClientCall get() = error("Not implemented")
    override val closeReason: Throwable? get() = null
    override val customAttributes: Attributes get() = Attributes()
    override val extensions: List<WebSocketExtension<*>> get() = emptyList()
    // `Channel.Factory.receiveChannel {}` / `sendChannel {}` do not exist.
    // Use a real Channel and expose it as both ends.
    private val frames = Channel<Frame>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<Frame> get() = frames
    override val outgoing: SendChannel<Frame> get() = frames
    override val isActive: Boolean get() = !frames.isClosedForSend
    override val maxFrameSize: Long get() = Long.MAX_VALUE
    
    override suspend fun close(cause: Throwable?) {}
    override suspend fun flush() {}
    override suspend fun send(frame: Frame) {}
}
```

**Acceptance Criteria**:
- [ ] All network tests pass
- [ ] Test coverage >90% for network layer
- [ ] Tested with real server
- [ ] Error conditions handled

---

## 📊 Phase 5 Deliverables

### Code Deliverables
- [ ] Protocol specification document
- [ ] SocketManager implementation
- [ ] All message types and handlers
- [ ] Connection management
- [ ] Network integration
- [ ] Network tests

### Documentation Deliverables
- [ ] `docs/protocol/server-protocol.md`
- [ ] Network layer documentation
- [ ] API documentation

---

## ✅ Phase 5 Completion Criteria

### Technical
- [ ] All network functionality migrated
- [ ] Protocol fully documented
- [ ] Connection management works
- [ ] Message handling works
- [ ] Integration complete

### Testing
- [ ] All network tests pass
- [ ] Test coverage >90%
- [ ] Tested with real server

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 6 - Animations

**Phase 6 Focus** (Weeks 24-26):
- Complete any remaining animations
- Polish existing animations
- Performance optimization
- Testing

**Prerequisites**: All Phase 5 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 6**: [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
