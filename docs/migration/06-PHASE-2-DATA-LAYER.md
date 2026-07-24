# Phase 2: Data Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 2 - Data Layer
- **Duration**: 2 weeks (Weeks 7-8)
- **Status**: NOT STARTED
- **Version**: 1.0
- **Last Updated**: 2026-07-21
- **Prerequisites**: Phase 1 - Infrastructure

---

## 🎯 Phase Overview

### Purpose
Phase 2 focuses on completing the data layer, including all remaining models, repository implementations, database setup, and data migration from the AS3 version.

### Key Objectives
1. Complete all data models (from AS3 classes)
2. Implement repository pattern for data access
3. Set up SQLDelight database
4. Create data migration scripts
5. Implement caching and offline support
6. Test all data operations thoroughly

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 7 | Remaining data models, Repositories, Database setup | Tech Lead + Team |
| Week 8 | Migration scripts, Caching, Testing | Tech Lead + QA |

---

## 📝 Tasks

### Week 7: Models and Repositories

> ⚠️ **Week 7 is over-allocated**: Tasks 2.1 (3 d) + 2.2 (2 d) + 2.3 (2 d) +
> 2.4 (1 d) = **8 days**, all four naming the Tech Lead as owner, inside a 5-day
> week. Re-level or delegate before committing to this schedule.

#### Task 2.1: Complete Data Models
**Owner**: Tech Lead + Senior Kotlin Devs | **Duration**: 3 days | **Priority**: CRITICAL

**Models to Complete** (from AS3 analysis):
- Item hierarchy (Item.kt, **CardItem.kt**, BoosterItem.kt, PotionItem.kt).
  ⚠️ Note `Item` extends `starling.display.Sprite` in the original, so display and
  state must be separated; and all three subclasses extend `Item` directly, not
  `CardItem`. See the corrected hierarchy in
  [13-DATA-MODELS.md](./13-DATA-MODELS.md)
- Achievements.kt
- NPC.kt, NPCs.kt
- Rank.kt
- Level.kt
- Save.kt (save file model)
- cardScore.kt
- Match state models

**Acceptance Criteria**:
- [ ] All AS3 data classes have Kotlin equivalents
- [ ] All models are serializable
- [ ] Unit tests for all models
- [ ] See [13-DATA-MODELS.md](./13-DATA-MODELS.md) for detailed mappings

---

#### Task 2.2: Implement Repository Pattern
**Owner**: Tech Lead | **Duration**: 2 days | **Priority**: CRITICAL

**Repositories to Implement**:
- `CardRepository` - Card data access
- `SaveRepository` - Save/load operations
- `AchievementRepository` - Achievement tracking
- `ItemRepository` - Inventory management
- `MatchRepository` - Match history
- `UserRepository` - User profiles

**Repository Interface Example**:
```kotlin
interface CardRepository {
    suspend fun getAllCards(collection: CardCollection): List<Card>
    suspend fun getCardById(id: UInt, collection: CardCollection): Card?
    suspend fun getCardsByRarity(rarity: Int): List<Card>
    suspend fun getCardsByType(type: CardType): List<Card>
    suspend fun getCardsByElement(element: Element): List<Card>
    suspend fun searchCards(query: String): List<Card>
}
```

**Implementation Strategy**:
- Use interface + implementation pattern
- Support both in-memory (for testing) and persistent storage
- Use Flow for observable data where appropriate

**Acceptance Criteria**:
- [ ] All repository interfaces defined
- [ ] At least one implementation per repository
- [ ] Repositories are testable (mock implementations)

---

#### Task 2.3: SQLDelight Database Setup
**Owner**: Tech Lead + DevOps | **Duration**: 2 days | **Priority**: HIGH

**Database Schema** (SQLDelight files in `shared/src/commonMain/sqldelight/com/tripletriad/data/db/`):

**GameSave.sq**:
```sql
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
    achievements TEXT NOT NULL,
    npcWins TEXT NOT NULL,
    rulesWins TEXT NOT NULL
);
```

**CardCache.sq**:

> ⚠️ **Naming inconsistency**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
> calls this table `Card`, this document calls it `CardCache`. Pick one — SQLDelight
> generates types from the table name, so the mismatch propagates into the code.
>
> ⚠️ **Do you need this table at all?** Card definitions are static bundled data
> loaded from JSON. Mirroring them into SQLite adds a migration surface and a
> synchronisation bug class for no benefit. Recommended: **drop it** and keep only
> `GameSave` and `MatchHistory` in the database.

```sql
CREATE TABLE CardCache (
    id INTEGER PRIMARY KEY,
    collection TEXT NOT NULL,
    nameKey TEXT NOT NULL,
    power TEXT NOT NULL,  -- JSON array
    rarity INTEGER NOT NULL,
    type TEXT,
    element TEXT
);
```

**MatchHistory.sq**:
```sql
CREATE TABLE MatchHistory (
    id TEXT PRIMARY KEY,
    mode TEXT NOT NULL,
    opponentType TEXT NOT NULL,  -- NPC, PVP
    opponentName TEXT,
    timestamp INTEGER NOT NULL,
    result TEXT NOT NULL,  -- WIN, LOSE, DRAW
    duration INTEGER NOT NULL,
    rules TEXT NOT NULL   -- JSON object
);
```

**Database Queries**:
```kotlin
// In GameSaveQueries.kt (generated by SQLDelight)
interface GameSaveQueries {
    fun selectAll(): List<GameSave>
    fun selectByUsername(username: String): GameSave?
    fun insert(gameSave: GameSave)
    fun update(gameSave: GameSave)
    fun delete(username: String)
}
```

**Acceptance Criteria**:
- [ ] Database schema created for all entities
- [ ] SQLDelight configuration complete
- [ ] Database builds successfully
- [ ] All CRUD operations work

---

#### Task 2.4: Data Source Layer
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: HIGH

**Data Sources to Implement**:
- `LocalCardDataSource` - Card data from JSON files
- `LocalSaveDataSource` - Save data from SQLDelight
- `NetworkDataSource` - Remote data (if applicable)
- `CacheDataSource` - In-memory caching

**Layer Architecture**:
```
Repository
    ↑
DataSource (Local/Remote)
    ↑
Database / Files / Network
```

**Example Implementation**:
```kotlin
class CardRepositoryImpl(
    private val localDataSource: LocalCardDataSource,
    private val cacheDataSource: CacheCardDataSource
) : CardRepository {
    override suspend fun getAllCards(collection: CardCollection): List<Card> {
        return cacheDataSource.getAll(collection)
            ?: localDataSource.getAll(collection).also {
                cacheDataSource.cacheAll(collection, it)
            }
    }
}
```

**Acceptance Criteria**:
- [ ] All data sources implemented
- [ ] Caching strategy in place
- [ ] Data flows correctly through layers

---

### Week 8: Migration and Testing

#### Task 2.5: AS3 Data Migration Scripts
**Owner**: Tech Lead | **Duration**: 2 days | **Priority**: HIGH

**Migration Scripts to Create**:
1. **Card Data Extractor** - Extract card data from AS3 `cards.as` to JSON
   (263 entries: 153 FF14 + 110 FF8, plus the `"Back"` placeholder at index 0 of
   each array). Note power values are **hex** and mix integers with quoted letters
   (`power:[1,8,'A',8]`), parsed in AS3 via `uint("0x" + value)`
2. **Texture Atlas Slicer** - Extract individual card images from the Starling
   atlases (`sources/bin/assets/atlas/ff14_cards.xml` + `.png`, and equivalents for
   FF8, thumbs, avatars, NPCs). ⚠️ **This was missing from the plan entirely** and
   is a prerequisite for any card rendering — see
   [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md) §6
3. **Localization Extractor** - Convert the 4 `rulesAtlas.xml` string bundles under
   `sources/bin/assets/{de_DE,en_US,fr_FR,ja_JA}/` to JSON
4. **Save File Converter** - Convert AS3 `.sav` files to the new format.
   ⚠️ These are **AES-encrypted** JSON (`CryptoHelper` → `com.hurlant.crypto.symmetric.AESKey`).
   Reading legacy saves requires reproducing the exact key, mode and padding; read
   `utils/CryptoHelper.as` before assuming compatibility is free. If legacy saves
   are not required, say so explicitly and skip this script
5. **Asset Migrator** - Help migrate assets from old to new structure
6. **Configuration Converter** - Convert `UserSettings.json`
   (`My Games/Triple Triad Online/UserSettings.json`: `background_volume`,
   `noise_volume`, `language`) to Multiplatform Settings

**Migration Strategy**:
- Create standalone scripts (can run independently)
- Preserve all data from original
- Validate migration results
- Create migration reports

**Acceptance Criteria**:
- [ ] All AS3 data can be migrated
- [ ] Migration preserves all original data
- [ ] Migration is automated
- [ ] Validation scripts confirm data integrity

---

#### Task 2.6: Caching Implementation
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: MEDIUM

**Caching Strategy**:
- **Card Data**: Cache in memory (loaded once at startup)
- **Image Assets**: Cache using Coil
- **Network Responses**: Cache using Ktor client caching
- **Database Queries**: Use SQLDelight efficiently

**Cache Implementation**:

> ⚠️ **`LruCache` is `android.util.LruCache` — Android-only.** It does not exist in
> `commonMain` and would break the iOS build. There is no LRU cache in the Kotlin
> stdlib either.
>
> More to the point, an LRU cache is the wrong tool here. There are **263 cards
> total** (153 FF14 + 110 FF8) and they are static, bundled, immutable data of a few
> hundred bytes each — well under 100 KB for the whole set. Load all of them once at
> startup into a plain map. Eviction logic adds complexity and buys nothing.
>
> (Card *images* are a different matter and are handled by Compose Resources, not
> by this cache.)

```kotlin
// commonMain — all 263 cards fit comfortably in memory; no eviction needed.
class CardCache(private val dataSource: LocalCardDataSource) {
    private val mutex = Mutex()
    private var byCollection: Map<CardCollection, List<Card>>? = null

    private suspend fun ensureLoaded(): Map<CardCollection, List<Card>> =
        byCollection ?: mutex.withLock {
            byCollection ?: CardCollection.entries
                .associateWith { dataSource.getAll(it) }
                .also { byCollection = it }
        }

    suspend fun getAll(collection: CardCollection): List<Card> =
        ensureLoaded()[collection].orEmpty()

    suspend fun getById(id: UInt, collection: CardCollection): Card? =
        getAll(collection).firstOrNull { it.id == id }

    suspend fun clear() = mutex.withLock { byCollection = null }
}
```

> If a bounded cache is ever genuinely needed (e.g. for decoded bitmaps), use a
> multiplatform implementation or an `expect`/`actual` pair — do not reach for
> `android.util.LruCache` from shared code.

**Acceptance Criteria**:
- [ ] Caching implemented for all data types
- [ ] Cache invalidation works
- [ ] Memory usage is controlled

---

#### Task 2.7: Offline Support
**Owner**: Tech Lead | **Duration**: 1 day | **Priority**: MEDIUM

**Offline Features**:
- All card data available offline (bundled with app)
- Save files stored locally
- Match history available offline
- Last known game state cached
- Offline mode indicator in UI

**Offline Strategy**:
- Bundle essential data with app
- Use SQLDelight for persistent storage
- Sync with server when connection restored
- Graceful degradation when offline

**Acceptance Criteria**:
- [ ] App works without network connection
- [ ] All essential data available offline
- [ ] Offline mode clearly indicated

---

#### Task 2.8: Data Layer Testing
**Owner**: QA Engineer + Team | **Duration**: 2 days | **Priority**: CRITICAL

**Testing Approach**:
- Unit tests for all models
- Unit tests for all repositories
- Integration tests for data flow
- Migration test scripts
- Performance tests for data operations

**Test Coverage Targets**:
- Models: 100%
- Repositories: >90%
- Data Sources: >90%
- Migration Scripts: 100%

**Test Types**:
```kotlin
// Model tests
class CardTest : BaseTest() {
    init {
        test("Card serialization") { /* ... */ }
        test("Card equality") { /* ... */ }
        test("Card power comparison") { /* ... */ }
    }
}

// Repository tests
class CardRepositoryTest : BaseTest() {
    init {
        test("getAllCards returns all cards") { /* ... */ }
        test("getCardById returns correct card") { /* ... */ }
        test("caching works correctly") { /* ... */ }
    }
}

// Migration tests
class MigrationTest : BaseTest() {
    init {
        test("AS3 card data converts correctly") { /* ... */ }
        test("AS3 save file converts correctly") { /* ... */ }
    }
}
```

**Acceptance Criteria**:
- [ ] All data layer tests pass
- [ ] Test coverage meets targets
- [ ] Migration scripts tested with real AS3 data

---

## 📊 Phase 2 Deliverables

### Code Deliverables
- [ ] All data models complete
- [ ] Repository implementations
- [ ] SQLDelight database and queries
- [ ] Data source implementations
- [ ] Migration scripts
- [ ] Caching implementation
- [ ] Offline support
- [ ] Data layer tests

### Documentation Deliverables
- [ ] Data model mapping document
- [ ] Database schema documentation
- [ ] Migration guide
- [ ] Caching strategy document

---

## ✅ Phase 2 Completion Criteria

### Technical
- [ ] All data models implemented and tested
- [ ] Repository pattern fully implemented
- [ ] Database operational
- [ ] Migration scripts work
- [ ] Caching in place
- [ ] Offline support functional

### Testing
- [ ] All data layer tests pass
- [ ] Test coverage >90% for data layer
- [ ] Migration validated with real data

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 3 - Core Logic

**Phase 3 Focus** (Weeks 9-12):
- Migrate TTOCore (core game logic)
- Migrate tripleTriadRules
- Implement game state management
- Create game flow system
- Test all game rules thoroughly

**Prerequisites**: All Phase 2 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 1**: [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md)
- **Phase 3**: [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md)
- **Data Models**: [13-DATA-MODELS.md](./13-DATA-MODELS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*
*Status: PLANNING COMPLETE*
