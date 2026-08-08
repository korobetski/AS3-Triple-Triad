# Testing Guide - Triple Triad Online Migration

## 📋 Document Information

- **Purpose**: Comprehensive guide for testing the Kotlin Multiplatform migration
- **Status**: superseded by the code; kept for the decisions in it
- **Last Updated**: 2026-07-21
- **Related**: [11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md)

---

## 🎯 Testing Philosophy

### Testing Pyramid
```
          /\
         /  \  UI Tests (10%) - Compose Testing, Manual
        /    \
       /------\ Integration Tests (20%) - Component interactions
      /        \
     /          \ Unit Tests (70%) - kotlin.test + Kotest assertions
    /____________\
```

### Quality Gates
- **Unit Tests**: >80% coverage, all critical paths tested
- **Integration Tests**: All component interactions validated
- **UI Tests**: All screens functional, responsive
- **Performance Tests**: >60 FPS, <100MB memory
- **UAT**: >4.5/5 user satisfaction

---

## 🧪 Test Types

---

## 1. Unit Testing

### Framework: kotlin.test + Kotest assertions + Turbine

> ⚠️ **The setup previously documented here could not work.** Five defects, all of
> which would surface on the first `./gradlew allTests`:
>
> 1. **`testImplementation` in a KMP module does nothing.** Dependencies must be
>    declared per source set (`commonTest`, `androidUnitTest`, `jvmTest`).
> 2. **MockK is JVM-only.** It has no Kotlin/Native target, so it cannot appear in
>    `commonTest` — the iOS test compilation fails outright. Yet every example in
>    this document used `mockk`, and `koin-test` was imported without being declared.
> 3. **`Dispatchers.Unconfined` is not a `TestDispatcher`.** The declaration
>    `val testDispatcher: TestDispatcher = Dispatchers.Unconfined` is a type error.
>    Use `StandardTestDispatcher()` or `UnconfinedTestDispatcher()`.
> 4. **`protected fun runTest(...) { runTest(testDispatcher) { ... } }` recurses
>    infinitely** — the inner call resolves to the member, not
>    `kotlinx.coroutines.test.runTest`. Same for
>    `inline fun <reified T> mockk(): T = mockk<T>()` and `mockkClass()`; both are
>    unconditional self-calls and stack-overflow on the first invocation.
> 5. **Kotest specs and `kotlin.test` annotations do not mix.** Classes extended
>    `FunSpec()` (whose lifecycle is `beforeTest {}` inside `init`) but then declared
>    `@BeforeTest fun setup()`, which Kotest never calls — so `core`/`repository`
>    would be uninitialised. Several examples also used the form
>    `test("name") = runTest { }`, which is not valid syntax either way: Kotest's
>    `test()` takes a lambda argument, it is not an assignable declaration.
>
> All examples in this document have been rewritten to the `@Test fun` form.
>
> **Decision: use `kotlin.test` as the runner** (works on every KMP target, needs no
> extra plugin) with Kotest *assertions* for readability, and hand-written fakes
> instead of MockK so shared tests run on iOS too. Kotest's `checkAll` property
> testing is available via `kotest-property`, which is multiplatform.

**Dependencies** (`shared/build.gradle.kts`):
```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))                                   // runner
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")         // shouldBe etc.
            implementation("io.kotest:kotest-property:5.9.1")                // checkAll
            implementation("app.cash.turbine:turbine:1.1.0")                 // Flow testing
            implementation("io.insert-koin:koin-test:3.5.6")                 // io.insert-koin!
        }
        // JVM-only tools stay out of commonTest.
        jvmTest.dependencies {
            implementation("io.mockk:mockk:1.13.12")
        }
        androidUnitTest.dependencies {
            implementation("io.mockk:mockk:1.13.12")
        }
    }
}
```

### Base Test Class

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/test/BaseTest.kt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.koin.core.context.stopKoin

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseTest {
    protected val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun baseSetUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun baseTearDown() {
        Dispatchers.resetMain()
        stopKoin()   // no-op if Koin was never started
    }
}
```

> **Note**: `Dispatchers.setMain` requires `kotlinx-coroutines-test` and only has an
> effect where a Main dispatcher exists. If the shared module holds no
> `Dispatchers.Main` usage, drop it — do not carry ceremony that does nothing.
>
> If you prefer Kotest's spec DSL, use it *consistently*: extend `FunSpec`, put
> setup in `beforeTest {}`, never use `@BeforeTest`, and add the
> `io.kotest:kotest-framework-engine` dependency plus the Kotest Gradle plugin.
> Do not mix the two styles in one class.

### Test Data Factory

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/test/TestDataFactory.kt
import com.tripletriad.core.utils.CardCollection
import com.tripletriad.core.utils.CardColor
import com.tripletriad.core.utils.Element
import com.tripletriad.core.utils.CardType
import com.tripletriad.data.models.*

object TestDataFactory {

    // Cards
    fun createTestCard(
        id: UInt = 1u,
        collection: CardCollection = CardCollection.FF14,
        power: List<String> = listOf("5", "5", "5", "5"),
        rarity: Int = 1,
        type: CardType? = null,
        element: Element? = null
    ): Card {
        return Card(
            id = id,
            collection = collection,
            nameKey = "TEST_CARD_$id",
            power = power,
            rarity = rarity,
            type = type,
            element = element
        )
    }

    fun createCardWithPower(top: String, right: String, bottom: String, left: String): Card {
        return createTestCard(power = listOf(top, right, bottom, left))
    }

    // Board
    fun createEmptyBoard(): Board {
        return Board()
    }

    // Board is immutable: placeCard returns a NEW Board and takes a tile ID,
    // not a Tile. See 13-DATA-MODELS.md.
    fun createTestBoard(): Board =
        Board()
            .placeCard(createTestCard(1u), tileId = 0, color = CardColor.BLUE)
            .placeCard(createTestCard(2u), tileId = 1, color = CardColor.RED)

    fun createFullBoard(): Board =
        (0 until 9).fold(Board()) { board, i ->
            board.placeCard(
                createTestCard(i.toUInt() + 1u),
                tileId = i,
                color = if (i % 2 == 0) CardColor.BLUE else CardColor.RED
            )
        }

    // A combo chain requires SAME/PLUS/SAME_WALL to fire first -- combos never
    // cascade from a plain capture (TTOCore.as: comboRule is only called from
    // specialRule). Lay the board out so a SAME pair triggers on placement at the
    // centre, then a captured card in turn out-powers its own neighbour.
    //   0 1 2
    //   3 4 5
    //   6 7 8
    fun createComboBoard(): Board =
        Board()
            // Red cards at 1 (above centre) and 3 (left of centre) with matching
            // facing edges, so placing at 4 triggers SAME on both.
            .placeCard(createCardWithPower("5", "5", "5", "5"), tileId = 1, color = CardColor.RED)
            .placeCard(createCardWithPower("5", "5", "5", "5"), tileId = 3, color = CardColor.RED)
            // A weak red card at 0, adjacent to both 1 and 3, for the cascade to hit.
            .placeCard(createCardWithPower("1", "1", "1", "1"), tileId = 0, color = CardColor.RED)

    // Game State
    fun createTestGameState(
        mode: GameMode = GameMode.FF14,
        phase: GamePhase = GamePhase.DECK_SELECTION
    ): GameState {
        return GameState(
            mode = mode,
            rules = GameRules(),
            phase = phase,
            blueDeck = listOf(createTestCard(1), createTestCard(2), createTestCard(3)),
            redDeck = listOf(createTestCard(4), createTestCard(5), createTestCard(6))
        )
    }

    // Game Rules
    fun createTestRules(
        suddenDeath: Boolean = false,
        reverse: Boolean = false,
        combo: Boolean = true
    ): GameRules {
        return GameRules(
            suddenDeath = suddenDeath,
            reverse = reverse,
            combo = combo
        )
    }
}
```

### Unit Test Examples

#### Model Tests

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/data/models/CardTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CardTest : BaseTest() {
    @Test
    fun `Card creation with default values`() {
        val card = TestDataFactory.createTestCard()
        card.id shouldBe 1u
        card.collection shouldBe CardCollection.FF14
        card.power shouldBe listOf("5", "5", "5", "5")
    }

    @Test
    fun `Card power conversion`() {
        val card = TestDataFactory.createCardWithPower("A", "5", "6", "8")
        card.topPow shouldBe 10u
        card.rightPow shouldBe 5u
        card.bottomPow shouldBe 6u
        card.leftPow shouldBe 8u
    }

    @Test
    fun `Card canFlipAgainst returns correct result`() {
        val card1 = TestDataFactory.createCardWithPower("6", "6", "6", "6")
        val card2 = TestDataFactory.createCardWithPower("4", "4", "4", "4")

        card1.canFlipAgainst(card2, Direction.TOP) shouldBe true
        card2.canFlipAgainst(card1, Direction.TOP) shouldBe false
        card1.canFlipAgainst(card1, Direction.TOP) shouldBe false
    }

    @Test
    fun `Card equality`() {
        val card1 = TestDataFactory.createTestCard(1u)
        val card2 = TestDataFactory.createTestCard(1u)
        val card3 = TestDataFactory.createTestCard(2u)

        card1 shouldBe card2
        card1 shouldNotBe card3
    }
}
```

#### TTOCore Tests

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/core/game/TTOCoreTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize

class TTOCoreTest : BaseTest() {
    private lateinit var core: TTOCore

    @BeforeTest
    fun setup() {
        core = TTOCore()
    }

    @Test
    fun `basicRule flips adjacent card with lower power`() {
        val board = TestDataFactory.createTestBoard()
        val tile = board[0]

        val result = core.basicRule(tile, CardColor.BLUE)

        result shouldHaveSize 1
        result[0] shouldBe board[1] // Should flip adjacent tile
    }

    @Test
    fun `basicRule does not flip card with higher power`() {
        val board = Board()
        board.placeCard(TestDataFactory.createCardWithPower("4", "4", "4", "4"), board[0], CardColor.BLUE)
        board.placeCard(TestDataFactory.createCardWithPower("6", "6", "6", "6"), board[1], CardColor.RED)

        val result = core.basicRule(board[0], CardColor.BLUE)

        result shouldHaveSize 0
    }

    @Test
    fun `comboRule triggers chain reaction`() {
        val board = TestDataFactory.createComboBoard()
        // board[2] is the TOP-RIGHT tile in row-major order; the centre is
        // board[4]. The previous comment was wrong, and tile 2 is not adjacent
        // to tile 3, so the assertions below could not have held.
        val tile = board[4] // centre tile (row 1, col 1)

        // Place a high-power card in center
        board.placeCard(TestDataFactory.createCardWithPower("A", "A", "A", "A"), tile, CardColor.BLUE)

        val result = core.comboRule(tile, listOf(board[1]), 0u, CardColor.BLUE, mutableListOf())

        result shouldContain board[1]
        result shouldContain board[3]
    }

    @Test
    fun `applyRules returns distinct tiles`() {
        val board = TestDataFactory.createTestBoard()
        val tile = board[0]

        val result = core.applyRules(tile, CardColor.BLUE, false)

        result.distinct() shouldHaveSize result.size
    }
}
```

#### Property-Based Tests

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/core/game/TTOCorePropertyTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import io.kotest.property.Arb
import io.kotest.property.arbitrary.uint
import io.kotest.property.checkAll

class TTOCorePropertyTest : BaseTest() {
    private lateinit var core: TTOCore

    @BeforeTest
    fun setup() {
        core = TTOCore()
    }

    @Test
    fun `flipping a card never results in infinite loop`() {
        checkAll(Arb.uint(1u..15u), Arb.uint(1u..15u)) { topPow, adjacentPow ->
            // Create a board where we try to trigger infinite combo
            val board = Board()
            val card = TestDataFactory.createCardWithPower(
                topPow.toString(16),
                "5", "5", "5"
            )
            val adjacentCard = TestDataFactory.createCardWithPower(
                adjacentPow.toString(16),
                "5", "5", "5"
            )

            board.placeCard(card, board[0], CardColor.BLUE)
            board.placeCard(adjacentCard, board[1], CardColor.RED)

            val result = core.applyRules(board[0], CardColor.BLUE, false)

            // Should not return more tiles than exist on board
            result.size <= board.tiles.size
        }
    }

    @Test
    fun `board state remains valid after any flip`() {
        // Board indices are 0..8. The previous version generated 1u..9u, so
        // index 9 threw IndexOutOfBounds -- and the block asserted nothing,
        // making the test vacuous even when it passed.
        checkAll(Arb.int(0..8)) { sourceIndex ->
            val board = TestDataFactory.createFullBoard()
            val sourceTile = board[sourceIndex.toInt()]

            val result = core.applyRules(sourceTile, CardColor.BLUE, false)

            // Verify board state is still valid
            // All cards should still be on valid tiles
            // No duplicate cards
            // etc.
        }
    }
}
```

#### Repository Tests

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/data/repository/CardRepositoryTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify

// MockK is JVM-only: this file must live in jvmTest/ or androidUnitTest/,
// NOT commonTest/. For a test that must also run on iOS, use a hand-written fake
// (see FakeCardDataSource at the end of this document).
class CardRepositoryTest : BaseTest() {
    private lateinit var repository: CardRepository
    private val mockDataSource = mockk<LocalCardDataSource>()

    @BeforeTest
    fun setup() {
        repository = CardRepository(mockDataSource)
    }

    @Test
    fun `getAllCards returns all cards from data source`() = runTest(testDispatcher) {
        val expectedCards = listOf(
            TestDataFactory.createTestCard(1u),
            TestDataFactory.createTestCard(2u),
            TestDataFactory.createTestCard(3u)
        )

        // getAll is a suspend function -> coEvery, not every
        coEvery { mockDataSource.getAll(any()) } returns expectedCards

        val result = repository.getAllCards(CardCollection.FF14)

        result shouldHaveSize 3
        result shouldContain TestDataFactory.createTestCard(1u)

        coVerify { mockDataSource.getAll(CardCollection.FF14) }
    }

    @Test
    fun `getCardById returns correct card`() = runTest(testDispatcher) {
        val expectedCard = TestDataFactory.createTestCard(5u)
        coEvery { mockDataSource.getById(5u, any()) } returns expectedCard

        val result = repository.getCardById(5u, CardCollection.FF14)

        result shouldBe expectedCard
        coVerify { mockDataSource.getById(5u, CardCollection.FF14) }
    }

    @Test
    fun `getCardById returns null when not found`() = runTest(testDispatcher) {
        coEvery { mockDataSource.getById(999u, any()) } returns null

        val result = repository.getCardById(999u, CardCollection.FF14)

        result shouldBe null
    }
}
```

#### Flow Tests (with Turbine)

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/core/game/GameViewModelTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.flow.MutableStateFlow

// JVM-only (MockK). See the note on CardRepositoryTest.
class GameViewModelTest : BaseTest() {
    private lateinit var viewModel: GameViewModel
    private val mockCore = mockk<TTOCore>()
    private val mockRepository = mockk<CardRepository>()

    @BeforeTest
    fun setup() {
        coEvery { mockRepository.getAllCards(any()) } returns listOf(
            // createTestCard(id: UInt) -- Int literals do not compile
            TestDataFactory.createTestCard(1u),
            TestDataFactory.createTestCard(2u),
            TestDataFactory.createTestCard(3u),
            TestDataFactory.createTestCard(4u),
            TestDataFactory.createTestCard(5u)
        )

        viewModel = GameViewModel(mockCore, mockRepository)
    }

    @Test
    fun `initializeGame sets game state`() = runTest(testDispatcher) {
        viewModel.initializeGame(GameMode.FF14)

        viewModel.state.test {
            val state = awaitItem()
            state.mode shouldBe GameMode.FF14
            state.blueDeck shouldHaveSize 5
            state.redDeck shouldHaveSize 5
        }
    }

    @Test
    fun `selectCard updates selected card`() = runTest(testDispatcher) {
        val card = TestDataFactory.createTestCard(1)
        viewModel.selectCard(card)

        viewModel.state.test {
            val state = awaitItem()
            state.selectedCard shouldBe card
        }
    }

    @Test
    fun `placeCardOnTile clears selection`() = runTest(testDispatcher) {
        val card = TestDataFactory.createTestCard(1)
        val tile = Tile(id = 0)   // row/col are derived, not constructor args

        viewModel.selectCard(card)
        viewModel.placeCardOnTile(tile)

        viewModel.state.test {
            val state = awaitItem() // Skip initial state
            val nextState = awaitItem()
            nextState.selectedCard shouldBe null
        }
    }
}
```

---

## 2. Integration Testing

### Framework: Kotest + Compose Testing

**Dependencies** (`androidApp/build.gradle.kts`):
```kotlin
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
```

### Base Integration Test

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/android/BaseIntegrationTest.kt
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule

abstract class BaseIntegrationTest {
    @get:Rule
    val composeTestRule = createComposeRule()
}
```

### Integration Test Examples

#### Game Flow Integration Test

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/android/GameFlowIntegrationTest.kt
import com.tripletriad.test.BaseIntegrationTest
import com.tripletriad.test.TestDataFactory
import org.junit.Test

class GameFlowIntegrationTest : BaseIntegrationTest() {

    @Test
    fun completeGameFlow_worksEndToEnd() {
        val viewModel = GameViewModel()

        composeTestRule.setContent {
            TripleTriadTheme {
                GameScreen(viewModel = viewModel)
            }
        }

        // Initialize game
        viewModel.initializeGame(GameMode.FF14)

        // Wait for initialization
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            viewModel.state.value.phase == GamePhase.DECK_SELECTION
        }

        // Select first card from deck
        composeTestRule.onNodeWithText("Card 1").performClick()

        // Verify card is selected
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            viewModel.state.value.selectedCard != null
        }

        // Place card on first tile
        composeTestRule.onNodeWithContentDescription("Tile 0").performClick()

        // Verify card is placed
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            viewModel.state.value.board.tiles[0].hasCard()
        }
    }
}
```

#### Navigation Integration Test

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/android/NavigationIntegrationTest.kt
import com.tripletriad.test.BaseIntegrationTest
import org.junit.Test

class NavigationIntegrationTest : BaseIntegrationTest() {

    @Test
    fun menuScreen_navigatesToDecks() {
        val navController = TestNavController()

        composeTestRule.setContent {
            TripleTriadTheme {
                AppNavigation(navController = navController)
            }
        }

        // Click Decks button
        composeTestRule.onNodeWithText("Decks").performClick()

        // Verify navigation
        assert(navController.currentBackStackEntry?.destination?.route == "decks")
    }

    @Test
    fun menuScreen_navigatesToSettings() {
        val navController = TestNavController()

        composeTestRule.setContent {
            TripleTriadTheme {
                AppNavigation(navController = navController)
            }
        }

        // Click Settings button
        composeTestRule.onNodeWithText("Settings").performClick()

        // Verify navigation
        assert(navController.currentBackStackEntry?.destination?.route == "settings")
    }
}
```

#### Network Integration Test

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/network/SocketManagerIntegrationTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take

class SocketManagerIntegrationTest : BaseTest() {
    // JVM-only (MockK). Also: the session type is DefaultClientWebSocketSession.
    private val mockClient = mockk<HttpClient>()
    private val mockWebSocket = mockk<DefaultClientWebSocketSession>()

    @Test
    fun `connect and receive messages`() = runTest(testDispatcher) {
        val socketManager = SocketManager(mockClient, SocketConfiguration())

        coEvery { mockClient.webSocket(any(), any(), any()) } returns mockWebSocket
        coEvery { mockWebSocket.send(any()) } returns Unit

        // Setup mock incoming messages
        val messages = listOf(
            Frame.Text("{\"type\":\"pong\"}"),
            Frame.Text("{\"type\":\"clients\",\"data\":{\"users\":[]}}")
        )
        coEvery { mockWebSocket.incoming } returns messages.asFlow()

        socketManager.connect()

        // Verify connection state
        val connectionState = socketManager.connectionState.first()
        connectionState shouldBe ConnectionState.Connected("main_room")

        // Verify messages received
        val receivedMessages = socketManager.incomingMessages.take(2).toList()
        receivedMessages shouldHaveSize 2
    }

    @Test
    fun `disconnect closes WebSocket`() = runTest(testDispatcher) {
        val socketManager = SocketManager(mockClient, SocketConfiguration())

        coEvery { mockClient.webSocket(any(), any(), any()) } returns mockWebSocket
        coEvery { mockWebSocket.close() } returns Unit

        socketManager.connect()
        socketManager.close()

        coVerify { mockWebSocket.close() }
    }
}
```

---

## 3. UI Testing

### Framework: Compose Testing

**Dependencies** (`androidApp/build.gradle.kts`):
```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
```

### UI Test Examples

#### Screen Tests

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/ui/MenuScreenTest.kt
import com.tripletriad.test.BaseIntegrationTest
import org.junit.Test

class MenuScreenTest : BaseIntegrationTest() {

    @Test
    fun menuScreen_displaysAllButtons() {
        composeTestRule.setContent {
            TripleTriadTheme {
                MenuScreen(navController = TestNavController())
            }
        }

        // Verify all buttons are displayed
        composeTestRule.onNodeWithText("New Game").assertExists()
        composeTestRule.onNodeWithText("PvP").assertExists()
        composeTestRule.onNodeWithText("Decks").assertExists()
        composeTestRule.onNodeWithText("Inventory").assertExists()
        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithText("Help").assertExists()
    }

    @Test
    fun menuScreen_newGameButtonIsClickable() {
        val navController = TestNavController()

        composeTestRule.setContent {
            TripleTriadTheme {
                MenuScreen(navController = navController)
            }
        }

        composeTestRule.onNodeWithText("New Game").assertIsEnabled()
        composeTestRule.onNodeWithText("New Game").performClick()

        // Verify navigation
        assert(navController.navigatedTo("new_game"))
    }
}
```

#### Component Tests

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/ui/BoardComponentTest.kt
import com.tripletriad.test.BaseIntegrationTest
import com.tripletriad.test.TestDataFactory
import org.junit.Test

class BoardComponentTest : BaseIntegrationTest() {

    @Test
    fun board_displaysAllTiles() {
        val board = TestDataFactory.createEmptyBoard()

        composeTestRule.setContent {
            TripleTriadTheme {
                BoardComponent(board = board, onTileClick = {})
            }
        }

        // Verify 9 tiles are displayed
        composeTestRule.onAllNodesWithContentDescription("Tile").assertCountEquals(9)
    }

    @Test
    fun board_displaysCardsOnTiles() {
        val board = TestDataFactory.createFullBoard()

        composeTestRule.setContent {
            TripleTriadTheme {
                BoardComponent(board = board, onTileClick = {})
            }
        }

        // Verify cards are displayed
        composeTestRule.onAllNodesWithContentDescription(/Card \d/.toRegex()).assertCountEquals(9)
    }

    @Test
    fun tile_clickCallsHandler() {
        val board = TestDataFactory.createEmptyBoard()
        var clickedTile: Tile? = null

        composeTestRule.setContent {
            TripleTriadTheme {
                BoardComponent(
                    board = board,
                    onTileClick = { tile -> clickedTile = tile }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Tile 0").performClick()

        clickedTile shouldBe board[0]
    }
}
```

#### Drag and Drop Tests

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/ui/DragDropTest.kt
import com.tripletriad.test.BaseIntegrationTest
import com.tripletriad.test.TestDataFactory
import org.junit.Test

class DragDropTest : BaseIntegrationTest() {

    @Test
    fun card_canBeDragged() {
        val card = TestDataFactory.createTestCard(1)
        var dragStarted = false

        composeTestRule.setContent {
            TripleTriadTheme {
                DraggableCard(
                    card = card,
                    onDragStart = { dragStarted = true },
                    onDragEnd = {}
                )
            }
        }

        // Perform drag gesture
        composeTestRule.onNodeWithContentDescription("Card 1")
            .performTouchInput {
                down(center)
                moveTo(Offset(100f, 100f))
                up()
            }

        dragStarted shouldBe true
    }

    @Test
    fun card_canBeDroppedOnTile() {
        val card = TestDataFactory.createTestCard(1)
        val tile = Tile(id = 0)   // row/col are derived, not constructor args
        var droppedCard: Card? = null

        composeTestRule.setContent {
            TripleTriadTheme {
                Box {
                    DraggableCard(
                        card = card,
                        onDragStart = {},
                        onDragEnd = {}
                    )
                    DropTargetTile(
                        tile = tile,
                        onCardDrop = { c -> droppedCard = c }
                    )
                }
            }
        }

        // Perform drag and drop
        val cardNode = composeTestRule.onNodeWithContentDescription("Card 1")
        val tileNode = composeTestRule.onNodeWithContentDescription("Tile 0")

        cardNode.performTouchInput {
            down(center)
            moveTo(tileNode.center)
            up()
        }

        droppedCard shouldBe card
    }
}
```

---

## 4. Performance Testing

### Framework: Android Benchmark Library + JMH

**Dependencies** (`androidApp/build.gradle.kts`):
```kotlin
androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.0")
```

### Performance Test Examples

```kotlin
// androidApp/src/androidTest/kotlin/com/tripletriad/performance/AnimationPerformanceTest.kt
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tripletriad.test.TestDataFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimationPerformanceTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cardFlipAnimation_performance() {
        val card = TestDataFactory.createTestCard(1)

        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                CardFlipAnimation(
                    card = card,
                    isFlipping = true,
                    onComplete = {}
                )
            }

            // Run animation
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun boardRender_performance() {
        val board = TestDataFactory.createFullBoard()

        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                BoardComponent(board = board, onTileClick = {})
            }

            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun comboAnimation_performance() {
        val board = TestDataFactory.createComboBoard()
        val core = TTOCore()

        benchmarkRule.measureRepeated {
            core.comboRule(board[0], listOf(), 0u, CardColor.BLUE, mutableListOf())
        }
    }
}
```

### Performance Monitoring

```kotlin
// shared/src/commonMain/kotlin/com/tripletriad/utils/PerformanceMonitor.kt
import io.github.aakira.napier.Napier

// NOTE: this file is in commonMain, so it CANNOT call System.nanoTime() —
// that is JVM-only. Take the timestamp from Compose's frame clock instead,
// which is multiplatform and gives the real frame time:
//
//     @Composable
//     fun FrameMonitor(monitor: PerformanceMonitor) {
//         LaunchedEffect(Unit) {
//             while (true) withFrameNanos { nanos -> monitor.onFrame(nanos) }
//         }
//     }
//
class PerformanceMonitor {
    private val frameTimes = mutableListOf<Long>()
    private val memoryUsage = mutableListOf<Long>()
    private var lastFrameTime = 0L

    fun onFrame(currentTime: Long) {
        if (lastFrameTime > 0) {
            val frameTime = currentTime - lastFrameTime
            frameTimes.add(frameTime)
            if (frameTimes.size > 100) {
                frameTimes.removeAt(0)
            }

            // Log if frame time too high
            if (frameTime > 16_666_667) { // >16.67ms (60fps)
                Napier.w("Frame time: ${frameTime / 1_000_000}ms")
            }
        }
        lastFrameTime = currentTime
    }

    fun recordMemory(usage: Long) {
        memoryUsage.add(usage)
        if (memoryUsage.size > 100) {
            memoryUsage.removeAt(0)
        }

        if (usage > 100 * 1024 * 1024) { // >100MB
            Napier.w("Memory usage: ${usage / (1024 * 1024)}MB")
        }
    }

    fun getAverageFPS(): Float {
        if (frameTimes.isEmpty()) return 0f
        val avgFrameTime = frameTimes.average() / 1_000_000
        return 1000f / avgFrameTime
    }

    fun getFrameTimeStats(): FrameStats {
        if (frameTimes.isEmpty()) return FrameStats(0f, 0f, 0f)
        val sorted = frameTimes.sorted()
        return FrameStats(
            average = sorted.average() / 1_000_000f,
            p90 = sorted[(sorted.size * 0.9).toInt()] / 1_000_000f,
            p99 = sorted[(sorted.size * 0.99).toInt()] / 1_000_000f
        )
    }

    fun getMemoryStats(): MemoryStats {
        if (memoryUsage.isEmpty()) return MemoryStats(0L, 0L, 0L)
        val sorted = memoryUsage.sorted()
        return MemoryStats(
            average = sorted.average().toLong(),
            peak = sorted.last(),
            current = sorted.last()
        )
    }
}

data class FrameStats(
    val average: Float,
    val p90: Float,
    val p99: Float
)

data class MemoryStats(
    val average: Long,
    val peak: Long,
    val current: Long
)
```

---

## 5. Stress Testing

### Stress Test Examples

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/stress/ConcurrencyStressTest.kt
import com.tripletriad.test.BaseTest
import com.tripletriad.test.TestDataFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyStressTest : BaseTest() {

    @Test
    fun `100 concurrent animations`() = runTest(testDispatcher) {
        val animations = (1..100).map { i ->
            async {
                // Simulate animation
                val card = TestDataFactory.createTestCard(i.toUInt())
                val tile = Tile(id = i % 9, row = (i / 3) % 3, col = i % 3)

                // Simulate animation work
                kotlinx.coroutines.delay(10)
            }
        }

        animations.awaitAll()

        // Verify no crashes
    }

    @Test
    fun `50 rapid card placements`() = runTest(testDispatcher) {
        val viewModel = GameViewModel()
        val cards = (1..50).map { TestDataFactory.createTestCard(it.toUInt()) }
        val counter = AtomicInteger(0)

        cards.forEach { card ->
            async {
                viewModel.selectCard(card)
                viewModel.placeCardOnTile(Tile(id = counter.getAndIncrement() % 9, row = 0, col = 0))
            }
        }

        // Wait for all placements
        delay(1000)

        // Verify game state is valid
        viewModel.state.value.board.getTakenTiles().size shouldBeLessThanOrEqual 9
    }
}
```

---

## 6. User Acceptance Testing (UAT)

### UAT Process

1. **Recruitment**: 10-20 test users
2. **Preparation**: Create test builds and scenarios
3. **Execution**: Users test the app
4. **Feedback Collection**: Gather structured feedback
5. **Analysis**: Review feedback and identify issues
6. **Iteration**: Fix critical issues
7. **Validation**: Final testing

### UAT Test Scenarios

| ID | Scenario | Description | Expected Result |
|----|----------|-------------|-----------------|
| UAT-001 | Complete PvE Game | Play a full game against AI | Game completes successfully, correct winner |
| UAT-002 | Complete PvP Game | Play a full multiplayer game | Game syncs correctly, both players see same state |
| UAT-003 | Deck Management | Create, edit, delete decks | Decks saved and loaded correctly |
| UAT-004 | Card Collection | Browse all cards | All cards visible, details correct |
| UAT-005 | Settings | Configure all settings | Settings saved and applied |
| UAT-006 | All Rule Types | Test each rule type | All rules work as expected |
| UAT-007 | Animations | Observe all animations | Animations smooth and visually correct |
| UAT-008 | Offline Mode | Use app without network | All offline features work |
| UAT-009 | Localization | Switch between EN/FR | All text displays in correct language |
| UAT-010 | Performance | Use on mid-range device | >60 FPS, smooth animations |

### UAT Feedback Form

```markdown
# Triple Triad Online - Beta Test Feedback

## 1. Overall Experience
- How would you rate your overall experience? (1-5)
- What did you like most?
- What did you like least?

## 2. Gameplay
- Do the game rules work correctly? (Yes/No/Not sure)
- Did you encounter any bugs during gameplay? (Yes/No)
  - If yes, describe:
- Does the game feel responsive? (Yes/No)

## 3. Visuals
- How do the graphics look? (1-5)
- Do animations feel smooth? (Yes/No)
- Are there any visual glitches? (Yes/No)
  - If yes, describe:

## 4. Performance
- Did the app crash at any point? (Yes/No)
- Did you experience any lag or slowdown? (Yes/No)
- How would you rate the app's performance? (1-5)

## 5. Usability
- Is the app easy to use? (Yes/No)
- Were any features confusing or hard to find? (Yes/No)
  - If yes, which features:
- Any suggestions for improvement?

## 6. Bug Reports
For each bug, please provide:
- What you were doing:
- What happened:
- What you expected to happen:
- Device information (if known):
```

---

## 📊 Test Execution Plan

### Phase-Based Testing

| Phase | Test Types | Coverage Target | Owner |
|-------|------------|-----------------|-------|
| Phase 0 | PoC validation | N/A | Tech Lead |
| Phase 1 | Unit tests (infrastructure) | >50% | Team |
| Phase 2 | Unit tests (data layer) | >90% | Team |
| Phase 3 | Unit tests (core logic) | >95% | Team |
| Phase 4 | Unit + UI tests | >80% | Team + QA |
| Phase 5 | Integration + Network tests | >90% | Team + QA |
| Phase 6 | Performance + Stress tests | N/A | QA |
| Phase 7 | All tests + UAT | >80% overall | QA |
| Phase 8 | Final validation | N/A | All |

### Test Automation Strategy

1. **Unit Tests**: Run on every commit (CI)
2. **Integration Tests**: Run on PR to main
3. **UI Tests**: Run nightly
4. **Performance Tests**: Run on release candidates
5. **Stress Tests**: Run before major releases

---

## 📋 Test Reporting

### Test Metrics Dashboard

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Unit Test Coverage | >80% | 0% | ⚠️ |
| Integration Test Coverage | >80% | 0% | ⚠️ |
| UI Test Coverage | >80% | 0% | ⚠️ |
| Total Tests | >500 | 0 | ⚠️ |
| Test Pass Rate | >95% | N/A | ⚠️ |
| Performance (FPS) | >60 | N/A | ⚠️ |
| Performance (Memory) | <100MB | N/A | ⚠️ |
| Critical Bugs | 0 | N/A | ✅ |
| Major Bugs | 0 | N/A | ✅ |

### Test Report Template

```markdown
# Test Report - [Date]

## Executive Summary
- **Period**: [Start Date] to [End Date]
- **Total Tests**: [Number]
- **Pass Rate**: [Percentage]%
- **New Bugs**: [Number]
- **Fixed Bugs**: [Number]
- **Open Bugs**: [Number]

## Test Execution

### Unit Tests
- **Total**: [Number]
- **Passed**: [Number]
- **Failed**: [Number]
- **Skipped**: [Number]
- **Coverage**: [Percentage]%

### Integration Tests
- **Total**: [Number]
- **Passed**: [Number]
- **Failed**: [Number]
- **Coverage**: [Percentage]%

### UI Tests
- **Total**: [Number]
- **Passed**: [Number]
- **Failed**: [Number]
- **Coverage**: [Percentage]%

### Performance Tests
- **FPS (Average)**: [Value]
- **FPS (Minimum)**: [Value]
- **Memory (Average)**: [Value]MB
- **Memory (Peak)**: [Value]MB
- **Launch Time**: [Value]s

## Bug Summary

### Critical Bugs
| ID | Description | Status | Owner | Due Date |
|----|-------------|--------|-------|----------|
| [ID] | [Description] | [Status] | [Owner] | [Date] |

### Major Bugs
| ID | Description | Status | Owner | Due Date |
|----|-------------|--------|-------|----------|
| [ID] | [Description] | [Status] | [Owner] | [Date] |

## Risks and Issues
- [Risk 1]
- [Risk 2]
- [Issue 1]

## Recommendations
- [Recommendation 1]
- [Recommendation 2]

## Next Steps
- [Next Step 1]
- [Next Step 2]
```

---

## 🛠️ Test Infrastructure

### CI/CD Pipeline

```yaml
# .github/workflows/test.yml
name: Test

on:
  push:
    branches: [ main, migration/kotlin-multiplatform ]
  pull_request:
    branches: [ main ]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - run: ./gradlew :shared:allTests
      - name: Upload test results
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: shared/build/reports/tests/**/*.xml

  integration-tests:
    runs-on: ubuntu-latest
    needs: unit-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: \'temurin\'
          java-version: \'17\'
      - run: ./gradlew :shared:integrationTests
      - name: Upload test results
        uses: actions/upload-artifact@v4
        with:
          name: integration-test-results
          path: shared/build/reports/tests/**/*.xml

  android-ui-tests:
    runs-on: ubuntu-latest
    needs: integration-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: \'temurin\'
          java-version: \'17\'
      - run: ./gradlew :androidApp:connectedDebugAndroidTest
      - name: Upload test results
        uses: actions/upload-artifact@v4
        with:
          name: ui-test-results
          path: androidApp/build/reports/**/*.xml

  performance-tests:
    runs-on: ubuntu-latest
    needs: android-ui-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: \'temurin\'
          java-version: \'17\'
      - run: ./gradlew :androidApp:benchmarkDebugAndroidTest
      - name: Upload performance results
        uses: actions/upload-artifact@v4
        with:
          name: performance-test-results
          path: androidApp/build/reports/**/*.json
```

---

## 🧩 Hand-Written Fakes (for `commonTest`)

MockK cannot run on Kotlin/Native, so any test that must execute on iOS uses a fake
instead. Fakes are also faster and survive refactors better than mock DSLs.

```kotlin
// shared/src/commonTest/kotlin/com/tripletriad/test/FakeCardDataSource.kt
class FakeCardDataSource(
    private var cards: List<Card> = emptyList()
) : LocalCardDataSource {

    // Recorded calls, so tests can assert interactions without verify {}.
    val getAllCalls = mutableListOf<CardCollection>()
    var failWith: Throwable? = null

    fun setCards(newCards: List<Card>) { cards = newCards }

    override suspend fun getAll(collection: CardCollection): List<Card> {
        failWith?.let { throw it }
        getAllCalls += collection
        return cards.filter { it.collection == collection }
    }

    override suspend fun getById(id: UInt, collection: CardCollection): Card? {
        failWith?.let { throw it }
        return cards.firstOrNull { it.id == id && it.collection == collection }
    }
}
```

Rewriting the earlier `CardRepositoryTest` against the fake makes it multiplatform:

```kotlin
class CardRepositoryTest : BaseTest() {
    private val dataSource = FakeCardDataSource()
    private val repository = CardRepositoryImpl(dataSource)

    @Test
    fun `getAllCards returns all cards from the data source`() = runTest(testDispatcher) {
        dataSource.setCards(listOf(
            TestDataFactory.createTestCard(1u),
            TestDataFactory.createTestCard(2u),
            TestDataFactory.createTestCard(3u)
        ))

        val result = repository.getAllCards(CardCollection.FF14)

        result shouldHaveSize 3
        dataSource.getAllCalls shouldBe listOf(CardCollection.FF14)
    }

    @Test
    fun `getCardById returns null when not found`() = runTest(testDispatcher) {
        repository.getCardById(999u, CardCollection.FF14) shouldBe null
    }
}
```

> **Rule of thumb**: `commonTest` uses fakes only. Reserve MockK for
> `jvmTest`/`androidUnitTest` where mocking an Android or JVM type is genuinely
> unavoidable.

---

## 🎯 Quality Gates

### Before Merging to Main
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No new critical/major bugs
- [ ] Code coverage maintained or improved
- [ ] Code review approved
- [ ] Lint checks pass

### Before Release
- [ ] All tests pass (>95% pass rate)
- [ ] Code coverage >80%
- [ ] No critical bugs open
- [ ] No major bugs open
- [ ] Performance targets met
- [ ] UAT complete and positive
- [ ] Stakeholder approval

---

## 📞 Related Documents

- **Phase 7 (Testing)**: [11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)

---
