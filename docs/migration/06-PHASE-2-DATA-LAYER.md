
# Phase 2: Data Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 2 - Data Layer
- **Duration**: 2 weeks (Weeks 7-8)
- **Status**: MOSTLY DONE — 2.1, 2.2, 2.4, 2.6, 2.7 and 2.8 delivered; 2.3 **deliberately not
  implemented as specified** (documents, not SQLDelight) and 2.5 partly pre-existing. See
  § What was built for the deviations, and § Phase 2 Deliverables for the checklist.
- **Version**: 1.1
- **Last Updated**: 2026-07-30
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

## ✅ What was built (2026-07-30)

### Files

| Area | Files |
|------|-------|
| Storage | `storage/DocumentStore.kt`, `storage/SaveCodec.kt`, `AndroidDocumentStore.kt`, `DesktopDocumentStore.kt` |
| Models | `model/GameSave.kt`, `model/Item.kt`, `model/Npc.kt`, `model/Achievement.kt`, `model/MatchRecord.kt`, `model/XpTable.kt`, plus `CardCollection` in `model/Card.kt` and `RuleKeys` in `model/GameRules.kt` |
| Data access | `data/CardRepository.kt`, `data/SaveRepository.kt`, `data/MatchHistoryRepository.kt`, `data/AchievementRepository.kt`, `data/Inventory.kt`, `data/NpcCatalog.kt`, `data/CardCatalog.kt` (renamed from `CardRepository.kt`) |
| Extraction | `tools/extract_npcs.py` → `composeResources/files/npcs.json` (85 opponents) |
| Tests | 15 new test classes, 209 tests (374 in `:shared` in total). Coverage 97.6% line / 88.3% branch, up from 96.6 / 85.3 |

### Deviations from this document, and why

1. **Task 2.3 — no SQLDelight.** Decided 2026-07-30. `GameSave`'s 23 columns include eight holding
   JSON, and nothing is ever queried by column: the AS3 reads a whole profile or none of it. A
   relational schema buys indexes and partial reads no caller wants, and costs a plugin, a generator
   and one driver per platform — the iOS one unbuildable from the Windows host this is developed on.
   Instead `DocumentStore` extends the Phase 1 `SettingsStore` pattern (shared module declares,
   host supplies) with `keys`/`delete`. `CardCache` is dropped, as this document already
   recommended. `MatchHistoryRepository` names the conditions under which SQL would earn its place
   and is the seam it would go behind.
2. **Task 2.5 point 4 — legacy `.sav` files are not read.** Reproducing `CryptoHelper` means
   decoding a GIF for 31×31 ARGB pixels — 3844 bytes handed to a cipher taking 16, 24 or 32 — with
   no `.sav` in the repository to validate against. Per the instruction of 2026-07-30 the save
   *system* and its obfuscation are preserved, by a new format: `SaveCodec` writes
   `TTO1<salt><hex>`, a salted xorshift keystream over checksummed UTF-8. It is obfuscation, not
   encryption, and says so — as was already true of the original, whose key shipped in the binary.
3. **Task 2.6 — no LRU cache.** 263 immutable bundled cards, well under 100 KB; eviction logic
   would guard a condition that cannot arise. `BundledCardRepository` loads once behind a mutex.
   (`android.util.LruCache` is also Android-only, as this document notes.)
4. **Task 2.5 points 1, 2, 3, 5, 6 were already delivered** in Phase 1 —
   `tools/extract_cards.py`, `import_card_art.py`, `import_locales.py`, `import_sounds.py`,
   `make_launcher_icons.py`. `extract_npcs.py` is new and was **not in the plan**; without it there
   is no PvE opponent data at all.

### Bugs found in the AS3 while porting

Each is fixed rather than reproduced, and each fix is commented at the site with the AS3 line.

| Where | Bug | Decision |
|-------|-----|----------|
| `Level.as:16-33`, `Rank.as` | `xpToLevel` returns **1** at maximum XP: at the last index `xp < steps[i+1]` compares against `undefined`, the loop `continue`s off the end, and `level` keeps its initial value. A maxed profile displays as a fresh one. | Fixed — `XpTable.levelFor` returns `MAX_LEVEL`. The two byte-identical classes are one table. |
| `NPCs.as:1141` | `if (begins > hour < ends)` parses as `((begins > hour) < ends)` — a Boolean compared against an hour, so **always true** for any `ends >= 2`. Every availability window is evaluated as if it wrapped midnight, and the non-wrapping branch is dead. An opponent available 14:00–19:00 is also available at 23:00. | Fixed — `Availability.isOpenAtHour` implements the window the data intends. Reproducing it would mean shipping a filter that does not filter. |
| `NPC.as:280-296` | `getRandomCards()` splices from the card pool without checking it is non-empty, so an NPC with fewer than five fetish cards and an empty pool **loops forever**. Unreachable with the shipped data (all fifteen empty-pool entries have five fetish cards) and reachable by adding one entry. | Fixed — the top-up stops when the pool empties and returns a short hand. `NpcBundleTest` asserts every shipped opponent can field five. |
| `Achievements.as:75-91` + shop | Bag entries are always `push`ed, never merged, so a second copy of a stackable item becomes a second row showing "1". | Fixed — `Inventory.add` merges on identity-minus-stack, in one place. |
| `Achievements.as:45-72` | Conditions are evaluated **in the constructor**, so an `Achievements` object is a snapshot and `check()` on a stale one reports stale results. | Redesigned — `Requirement` is data, evaluated against any `GameSave` on demand, and can report partial progress (which a pre-computed Boolean cannot). |

### Facts this document and 13-DATA-MODELS.md had wrong

Corrected in [13-DATA-MODELS.md](./13-DATA-MODELS.md); listed here because they changed the code.

- **`ACHIEVEMENTS` is `Map<String, Long>`, not `Map<String, Boolean>`.** `Achievements.as:79` writes
  `new Date().getTime()` — the instant it was earned.
- **`NPC_W` is keyed by the NPC's `iconID`, not its `id`** (`PVEMatchScreen.as:110`). That turns out
  to matter: the ff8 table declares `id:2` and `id:13` **twice each**, so keying by id would merge
  two opponents' records.
- **`Item.__toJSON()` does not lose the item's kind.** This document claimed only `{type, stack}`
  persists; `CardItem`, `BoosterItem` and `PotionItem` each override it to add `card`, `booster` or
  `potion`. There is no data-loss bug to decide about.
- **`availability` is an hour-of-day window, not a date range** — `NPCs.as` annotates it
  `// in hours` and `toListCollection()` compares it against `getHours()`.
- **`NPC_W_TOTAL` ends up in the save file** even though `setToDefaultValues()` never declares it:
  `PVEMatchScreen.as:172` assigns it onto `PROFILE_DATAS`, which `JSON.stringify` then writes. It is
  derived, so it is ignored on load and not written back.
- **The Queen of Cards is declared in both tables** with the same id, name and `iconID`, differing
  only in her card pool. Her wins therefore pool across collections — correct, being one character,
  but worth knowing before assuming `iconID` is unique globally.

---

## 📝 Tasks

> ⚠️ **Week 7 is over-allocated**: Tasks 2.1 (3 d) + 2.2 (2 d) + 2.3 (2 d) +
> 2.4 (1 d) = **8 days**, all four naming the Tech Lead as owner, inside a 5-day
> week. Re-level or delegate before committing to this schedule.

#### Task 2.1: Complete Data Models

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

- [x] All AS3 data classes have Kotlin equivalents — `Item`/`CardItem`/`BoosterItem`/`PotionItem`
  (+ `MiscItem`, the `itemize` fallback), `Achievement`, `Npc`, `XpTable` (`Level` + `Rank` merged —
  they are byte-identical), `GameSave`, `MatchRecord`
- [x] All models are serializable, with the AS3 `SCREAMING_CASE` / `item-type-*` names as
  `@SerialName`s so an existing `.sav` payload still parses. `GameSaveTest` pins every key
- [x] Unit tests for all models
- [x] See [13-DATA-MODELS.md](./13-DATA-MODELS.md) — corrected where it was wrong; see
  the § What was built section above

---

#### Task 2.2: Implement Repository Pattern

- [x] `CardRepository` (interface, two implementations), `SaveRepository`, `MatchHistoryRepository`,
  `AchievementRepository`
- [x] **`ItemRepository` is `Inventory`** — an object of `GameSave -> GameSave` functions, not a
  stateful repository. The bag is a field of the profile; a second owner for it is precisely the AS3
  problem (`InventoryScreen`, `Achievements.check()` and `shopScreen` all mutate it independently)
- [x] **`UserRepository` is `SaveRepository`** — a profile *is* the user in this game; there is no
  second identity to hold
- [x] Testable: `InMemoryDocumentStore` and `InMemoryCardRepository` are real working
  implementations, not stubs. No mocking framework is used or needed

---

#### Task 2.3: SQLDelight Database Setup

**Database Schema** (SQLDelight files in `shared/src/commonMain/sqldelight/com/tripletriad/data/db/`):

> ⚠️ **Naming inconsistency**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
> calls this table `Card`, this document calls it `CardCache`. Pick one — SQLDelight
> generates types from the table name, so the mismatch propagates into the code.
>
> ⚠️ **Do you need this table at all?** Card definitions are static bundled data
> loaded from JSON. Mirroring them into SQLite adds a migration surface and a
> synchronisation bug class for no benefit. Recommended: **drop it** and keep only
> `GameSave` and `MatchHistory` in the database.

- [x] Storage exists for every entity that needs it: profiles and match history as JSON documents
  behind `DocumentStore`, cards as bundled read-only data
- [x] Builds and runs on Android and desktop; `SaveRepositoryTest` and
  `MatchHistoryRepositoryTest` cover create / read / update / delete / list
- [x] `CardCache` dropped, as this document recommended

---

#### Task 2.4: Data Source Layer

- [x] `LocalCardDataSource` + `CacheCardDataSource` are **one class**, `BundledCardRepository`: with
  263 immutable records the cache *is* the source, and a separate data-source layer would have been
  two interfaces forwarding to each other. `NetworkDataSource` belongs to Phase 5
- [x] Caching in place — loaded once behind a `Mutex`, `invalidate()` for a mode switch
- [x] `CardRepositoryTest` holds both implementations to the same assertions

---

#### Task 2.5: AS3 Data Migration Scripts

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

- [x] Cards, atlases, locales, sounds, icons — delivered in Phase 1. **NPCs — new**
  (`extract_npcs.py`, 85 opponents, resolving the two `getCardsByRarities(...)` pools and the
  `tripleTriadRules.*` / `NPC.LEVEL_*` constant references rather than copying them)
- [x] Automated and re-runnable; each script asserts counts read out of the AS3 source, and
  `NpcBundleTest` / `CardBundleTest` re-assert them against what is actually packaged, because the
  scripts are run by hand and a stale bundle is the one failure their own assertions cannot catch
- [x] `UserSettings.json` needs no converter — `UserSettings` reads the original file as-is
  (Phase 1, `UserSettingsTest`)

---

#### Task 2.6: Caching Implementation

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

> If a bounded cache is ever genuinely needed (e.g. for decoded bitmaps), use a
> multiplatform implementation or an `expect`/`actual` pair — do not reach for
> `android.util.LruCache` from shared code.

- [x] Cards cached in memory; images by Compose Resources; profiles and history read on demand
- [x] `BundledCardRepository.invalidate()`, covered by `CardRepositoryTest`
- [x] Bounded by construction: the card set is fixed at 263 records, and match history is capped at
  `DEFAULT_LIMIT` rows per profile with the drop count **returned** rather than trimmed silently

---

#### Task 2.7: Offline Support

- [x] Nothing in the data layer touches the network — cards, artwork, locales and sounds are bundled,
  profiles and history are local files
- [x] All essential data available offline
  a permanent "offline" badge would be noise; it belongs with the thing it qualifies

---

#### Task 2.8: Data Layer Testing

- [x] All pass, on desktop and on the Android host source set
- [x] Coverage 97.6% line / 88.3% branch across `:shared`, gated by `coverageVerify`. Models and
  repositories are among the best-covered packages; the target was >90%, and 100% for models
- [x] `extract_npcs.py` runs against the real `NPCs.as`; `NpcBundleTest` and `CardBundleTest` verify
  the packaged output

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Technical Stack**: [03-TECHNICAL-STACK.md](./03-TECHNICAL-STACK.md)
- **Phase 1**: [05-PHASE-1-INFRASTRUCTURE.md](./05-PHASE-1-INFRASTRUCTURE.md)
- **Phase 3**: [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md)
- **Data Models**: [13-DATA-MODELS.md](./13-DATA-MODELS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---
