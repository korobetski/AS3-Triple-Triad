# Phase 4: UI Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 4 - UI Layer
- **Duration**: 8 weeks (Weeks 13-20)
- **Status**: IN PROGRESS — 2026-08-06. The playable loop, all of Tier 3, the deck selector, the
  theme system, drag-and-drop and the turn timer are done: **20 of the 32 screens**. Of the twelve
  left, only two are blocked on Phase 5 — six more are filed under multiplayer and are single-player
  ladders, two wait on Phase 6, and two will not be ported. See § What was built.
- **Version**: 1.2
- **Last Updated**: 2026-08-06
- **Prerequisites**: Phases 1-3

---

## 🔨 What was built

**2026-08-02 — the game is playable end to end.** A player creates a character, picks its card
collection, chooses an opponent from that collection, plays a match under the opponent's own rules
against the Phase 3 AI, and the result is written to disk. That was the thing missing after Phase 3:
the logic all existed and none of it was reachable.

**2026-08-06 — the character now has somewhere to live.** The dashboard and the six screens behind
it: the collection browser, the deck editor, the bag, the shop, the record with its achievements,
and the rules. Everything Phase 2 built a data layer for is now reachable, and everything those
screens change is written through `ProfileSession` like the match result already was. The **deck
selector** landed with them, which is what turns "I built four decks" into a choice.

**20 of the 32 screens exist and 12 are left**, of which only two — `PVPScreen` and
`PVPMatchScreen` — are blocked on Phase 5. Six more are filed under multiplayer and are nothing of
the kind; two will not be ported at all. See § Screens, against the plan's tiers.

---

## 🔨 Tier 3, and the dashboard that makes it reachable (2026-08-06)

### Why the dashboard came first

It is the piece the first pass left out, and its absence is what made the six screens behind it
impossible to place. `dashboardScreen.as:49-59` builds exactly this stack, and **every one of the
screens it opens returns to it** — `dispatchEventWith('gotoScreen', false, 'DASHBOARD')` appears in
all seven. So the original's flow is Menu → Load → *Dashboard* → everything, and putting Play on the
main menu — which is what this port did while it had one destination — gives the collection, the
decks, the bag and the shop nowhere to hang.

The flow is now menu → characters → dashboard → one of seven, a tree of depth three. `Screen` has
fourteen members and the routing is three functions: `Destination` for the five screens ahead of a
loaded character, `CharacterDestination` for the nine behind one, and `CollectionDestination` for
the four of those that read the card table. Still no navigation library — see § Six decisions, which
named "a screen reachable from two places with a different back destination from each" as the point
to reconsider, and the dashboard is what keeps that from happening: every screen behind it has
exactly one way in.

### Files

| File | What it is | AS3 counterpart |
|---|---|---|
| `ui/DashboardScreen.kt` | The nine entries, Multiplayer drawn disabled | `dashboardScreen` |
| `ui/CardListScreen.kt` | The whole card table, owned and not | `cardListScreen` |
| `ui/DecksScreen.kt` | Five slots, and an editor behind each | `DecksScreen` |
| `ui/InventoryScreen.kt` | The bag: Use, Sell, Discard | `InventoryScreen` |
| `ui/ShopScreen.kt` | The two shelves, and buying from them | `shopScreen` |
| `ui/StatsScreen.kt` | The record and all 22 achievements | `profileScreen` |
| `ui/HelpScreen.kt` | The seventeen rules, as an accordion | `HelpScreen` |
| `ui/ItemRow.kt` | Naming, keying and refusing a bag item | `Item` + `InventoryItem` |
| `data/ShopCatalog.kt` | The two price tables, and an atomic purchase | `shopScreen`'s statics |
| `ui/Controls.kt` | `CharacterBar`, `CharacterScaffold`, `rowSurface`, `EmptyNote` | `UserBar` |

`model/GameSave.kt` grew `withDeck`, `clearingDeck` and `Deck.plusCard` / `minusCardAt` /
`emptied`; `data/Inventory.kt`'s `use` now returns `ItemUse.PackOpened`.

### Six AS3 defects fixed, each stated where it lives

| Where | What the original does | What this does |
|---|---|---|
| `shopScreen.as:144-146` | **Deducts the price, then checks it could be paid.** The check only ever decided whether the button stayed lit | `ShopCatalog.buy` is one operation that either happens or does not |
| `shopScreen.as:149` | Ends on a commented-out `//Save.save(…)`, so **a purchase was never written** — the MGP and the item were both gone on quit | Persisted through `ProfileSession`, like every other mutation |
| `DecksScreen.as:342-362` | `resetDeckHandler` pushes five zeroes onto the deck's *existing* list, then calls `slice` where `splice` was meant. The list is rebuilt empty, the file is not, and **the deck comes back on the next load** | `GameSave.clearingDeck` empties the cards and keeps the slot, which is what the button claims |
| `InventoryScreen.as:220-234` | Discard opens on `// TODO : afficher une Alert` and destroys the item on the first tap | Two taps, in the shape the character list already uses for deletion |
| Three `push` sites | The bag grew a **new row per item**, so two of the same potion showed two rows of "1" | `Inventory.add` stacks and sorts on every insert, so the state `sortBag()` repaired is unreachable — and the Sort button with it |
| `profileScreen.as:181-184` | `_total` is 0 on a fresh profile, so the pie chart's three ratios are `NaN` | `Stats.winRate` returns 0f, and the chart is a number — see below |

### Four places this port shows more than the original could

1. **The collection browser lists what you do *not* own.** `cardListScreen.as:101-106` already
   walked the whole table, but `CardThumb.enabled = false` made an unowned thumb *untouchable* — so
   the description of the card you were hunting for was the one thing you could not read. Every cell
   is tappable here, and unowned ones are dimmed rather than desaturated: `ColorMatrixFilter` has no
   portable Compose Multiplatform equivalent for a multi-layer composable.

2. **Every achievement is listed, with progress.** `profileScreen.as:210-220` walks
   `PROFILE_DATAS.ACHIEVEMENTS`, so an unearned achievement was invisible and the screen could not
   say what there was to aim at. `Requirement.progress` exists precisely so it can — see the note in
   `model/Achievement.kt` on why the AS3's `condition` is a Boolean computed in a constructor and
   therefore cannot answer "how close".

3. **A win rate.** `RoundChart` drew wins, defeats and draws as three arcs with the *total* in the
   middle and no percentage anywhere. The arcs are decoration over numbers the list beside them
   already printed; the thing they stood in for is the rate, which the original never wrote down.

4. **A pack says what came out of it.** Opening one yields a bag *entry*, not a collection card —
   `InventoryScreen.as:252-258`, and it is deliberate: what comes out can be used or sold, which is
   the only sink for a duplicate the game has. Without a line saying which card, the pack simply
   vanishes and a row appears further up a scrolled list.

### Three things deliberately not reproduced

- **Item icons.** `ItemIcon` resolves `potionItem`, `booster_pack_icon` and `card_r{n}_icon` out of
  the UI atlas, which `tools/import_card_art.py` does not import — it imports the card art. A card
  item draws its actual card instead, which is more information than the icon carried. Same reason
  the collection grid scales a real card rather than slicing the three 8.3 MB thumbnail atlases.
- **The `UserBar` jump menu**, which listed every dashboard screen except the current one. It
  existed because these screens had no back button; this port has one, so returning and picking
  again is two taps against the callout's two.
- **The avatar.** `AVATAR_ID` names one of forty FFXIV portraits, and nothing but the bar reads one.

### Two AS3 keys that do not exist

`profileScreen.as:191` asks for `STR_MATCHES` and `DecksScreen.as:344`/`:366` ask for
`STR_NEW_DECK`. **Neither is in any of the four bundles**, so the original captioned its own chart
`STR_MATCHES` and named a fresh deck `STR_NEW_DECK`. Both are `APP_*` keys or numbered labels here;
a dangling key is not a translation to preserve. `RULE_SAME_WALL_HELP`, `RULE_COMBO_HELP` and
`RULE_ELEMENTAL_HELP` are the opposite case — they *resolve*, to the rule's own name, in all four
bundles, so the original showed the title twice and explained nothing. Shown as-is: the bundles are
imported Square Enix wording and writing three paragraphs of our own into them would be inventing
source text.

---

## The deck selector (2026-08-06)

`DeckSelectorScreen` — which deck to play this match with.

### It is a step inside the match, not a destination ahead of it

`BaseMatchScreen.deckSelectionPhase` (`:113-143`) is where the AS3 opens it, and the placement is
load-bearing rather than incidental: **under `RULE_RANDOM` the panel never opens** — the hand is
dealt from the whole collection and any chosen deck is ignored. Since the roulette can *add* Random
to an opponent's declared rules, whether the player is asked at all is not known until the roulette
has been drawn.

So `PveMatches.assemble` was split. `rulesFor` resolves the rules — roulette included — and the
result travels with the chosen deck as a `MatchPlan` into `assemble`. `MatchScreen` resolves the
rules, shows the selector if they permit it, and assembles once a deck is settled. A caller that
does not care still gets the old behaviour from one call: `assemble`'s default plan is the drawn
rules plus the first complete deck.

That split has to hold one property, and `PveMatchTest.resolvingTheRulesFirstDoesNotChangeTheMatch`
is the test for it: resolving the rules separately must not cost a **second roulette draw**, which
would play the match under rules the player was never shown.

### The counter, and where it is incremented

`PVEScreen.as:244` increments `STARTED_MATCHES` when the match screen is *launched* — which in the
original is before the selector opens, since the panel is a child of that screen. So the write moved
from match assembly to screen entry, and backing out of the selector is a forfeit. That is the
behaviour the counter was designed for and it is asserted directly.

### Three departures

| | |
|---|---|
| **The first offered deck starts selected** | `chooseBtn.isEnabled = false` with nothing picked (`:117`), so the original always cost two taps. One deck is the common case and should be one tap |
| **An empty list says why** | `if (deckCollection.length == 0) { }` is an empty block, so a player with no complete deck saw a blank panel. Random is always offered and always works — it draws from the collection, and every profile owns at least five cards |
| **A deck's label follows its save slot** | Filtering the incomplete decks out would otherwise renumber the survivors, so an unnamed deck in slot 5 would read `Deck 2`. `playableDecks` returns `IndexedValue`s for this reason |

The opponent's name and the rules in force are shown on the panel, which the original did not do
here — `RulesDigest` is on the board, one screen later. Reverse or Fallen Ace turns a deck of aces
into the wrong deck, so it is exactly what the choice should be made against.

---

## The theme system — Task 4.1 (2026-08-06)

`ui/theme/` — `Colors.kt`, `Typography.kt`, `Theme.kt`, plus `tools/import_fonts.py`.

### What was there instead

`darkColorScheme()`, Material's default, whose primary is a lavender purple. Every Material control
in the tree was hand-coloured at its call site to hide it — two `OutlinedTextField`s, a `Slider`, a
`FilterChip` and the `Button` behind `WideButton` — and the shared palette was a handful of invented
`Color(0xFF…)` constants at the bottom of `Controls.kt`. That is the shape of a missing theme: each
new control is one more place to remember, and the one that forgets is purple.

Fourteen screens now read `MaterialTheme.colorScheme`, `MaterialTheme.typography` and
`MaterialTheme.shapes`. Ninety-odd `fontSize = 13.sp` literals and every `Color.White` are gone.

### Two corrections to this document's Task 4.1

| What the task says | What the source says |
|---|---|
| The theme font is **Eurostile**, and redistributing it may need a licence | `BaseTTOTheme.as:118` declares `FONT_NAME = "Raleway"` and `:115-116` embed exactly two weights of it. Eurostile appears **once** in the whole source, at `Card.as:81`, drawing the `±N` modifier on a card — a field this port does not render. And the licence caveat is about the wrong font: Raleway ships with its own `OFL.txt` and is redistributable, where `eurostile.TTF` ships with nothing |
| `0xFF1a1a1a` and `0xFF2a2a2a` among the theme colours | Neither appears in any AS3 file. The transcription in `theme/Colors.kt` is from `BaseTTOTheme.as:124-137`, and `ThemeTest` holds it to those values |

The task's own notes were right about the rest: the card colours *were* mixed up with the two
`large*ElementFormat` text colours in an earlier revision, `MaterialTheme` cannot be assigned to a
top-level `val`, and a `TextStyle` carrying a colour would silently override the scheme. All three
are honoured.

### Three decisions worth naming

1. **`primary` is the card blue, not the AS3's accent.** `SELECTED_TEXT_COLOR` is an orange used for
   the *selected* item in a list; Material's `primary` drives every filled button, slider and chip,
   selected or not. Painting them orange would read as "everything is selected". The orange is
   `secondary`, doing the job it does in the original, and the bright `largeBlueElementFormat` cyan
   is `tertiary` — the affirmative accent behind a filled meter, an affordable price and a complete
   deck. `primary` is the *dark* card blue and a bar filled with it reads as empty.
2. **The type scale is re-anchored, not transcribed.** `BaseTTOTheme.as:669-672` declares 18/24/28/36
   — but those are **pixels at 326 DPI**, which convert to roughly 9/12/14/18 dp. Nine dp is too
   small on anything that is not a 2013 Retina display, so the ladder's *shape* is kept and its
   anchor is not. The same judgement Task 4.2 records for the card geometry.
3. **Every type slot carries the family, including the eight nothing names.** `Text`'s default style
   is `typography.bodyLarge`, and a `Text` that sets `fontSize` without setting `style` still takes
   its *family* from there — so naming seven slots would have left most of the screen in the platform
   font while the theme claimed to have set one.

### The `ja_JA` question the task raised, answered

Raleway has no CJK coverage, and the task asks what happens to `ja_JA`. The AS3 needed the two
`Noto-ja` bitmap fonts in `sources/bin/assets/fonts` for it. This port needs nothing: Skia and
Android substitute per glyph, so Latin takes Raleway and kana and kanji take the system face in the
same line. Checked by rendering the `ja_JA` screens and reading them — no test asserts it, because
none of them can look at a glyph.

---

## Drag and drop — Task 4.7 (2026-08-06)

`ui/BoardDragState.kt`, plus the hand and the board in the new `ui/MatchBoard.kt`.

Pick a card up, carry it to a cell, let go. A ghost card follows the finger, the cell under it takes
a highlight, and the card left in the hand dims rather than vanishing — pulling it out would
re-lay-out the four beside it in the middle of the gesture.

### Tapping is not replaced, which the task asks for and the original does

Task 4.7 ends on "do not ship drag-only", and `Card.onTouch` dispatches `TRIGGERED` on a tap *and*
starts a drag on a move (`Card.as:126-151`), with `Tile.onTouch` handling the second tap. Both are
here. Compose keeps them apart on its own: `clickable` gives up once the pointer passes touch slop,
which is the same threshold `detectDragGestures` starts at.

### One correction to the task's sketch

It hit-tests in "the board's coordinate space" and accumulates `dragPosition += delta` from
`onDragStart`'s offset — but **that offset is local to the dragged card**, so the pointer and the
cell bounds are measured from different origins and the hit test is wrong by the card's position on
screen. Everything here is in **root** coordinates instead: a dragged card converts its pointer with
`localToRoot`, a cell registers `boundsInRoot`, and no shared parent has to be found and threaded
through. The task's larger point stands — `Modifier.dragAndDropTarget` is for drags *between
applications* and is not what this needs.

### An occupied cell refuses earlier than the original's

`Tile.onDragDrop` accepts the drop and then checks `this.card == null` (`Tile.as:115`), so the
refusal happens after the finger lifts. Here a taken cell does not register its bounds at all, so it
never highlights — the player sees the refusal while still holding the card.

The same gate covers the rules: only the player's own **playable** cards can be lifted, which is
`Card._draggable` (`:137`) plus `RULE_ORDER` and `RULE_CHAOS`. Dragging a card the rules forbid and
having the drop silently do nothing is worse feedback than not being able to lift it.

### `MatchScreen.kt` was split

It crossed detekt's twenty-functions-per-file, which was the right moment: the board, the hands, the
drag and the layout arithmetic are now `MatchBoard.kt`, and `MatchScreen.kt` keeps the match — its
state, its effects, the status bar and the result panel. `canPlay` is the one guard behind both ways
of playing a card, because a pair of guards that check the same three things is exactly the pair
that drifts apart.

---

## The turn timer — `playerPanel` (2026-08-06)

Tier 2's `playerPanel` is listed in the plan as a screen, and read as one it is a nothing: a name
label and a hand, both of which this port already draws. Read as *code* it holds a **game mechanic
that was missing** — a thirty-second turn limit that plays a card for you when it runs out.

| | |
|---|---|
| `playerPanel.as:37` | `_timer = 30` |
| `BaseMatchScreen.as:377-387` | `setTimer()` on the side to move, `razTimer()` on the other |
| `BaseMatchScreen.as:93` | `TIME_UP_EVENT` on the blue player → `timeUp_play` |
| `BaseMatchScreen.as:422-437` | `autoPlay()` — a **random** remaining card on a **random** free cell |

So letting the clock run out does not pass the turn: it plays a move you did not choose. That
randomness is the penalty, which is why the port draws the card at random too rather than reusing
`MatchAi` — the opponent's AI would make a *good* move, and being rewarded for inattention is not
what the original does. Under `RULE_ORDER` the AS3 takes `remainingCards[0]`, which `playable()`
already narrows to, so the rule is honoured without being restated.

### One bar, not two

The original arms **both** players' timers but listens to only one: `:93` attaches the handler to
`bluePlayer` and to nothing else, so red's bar counts down and expiring does nothing. Red is driven
by `opponentPhase` instead, which in this port answers in 700 ms and could never reach thirty
seconds. A bar that cannot expire is decoration, so there is one.

It sits under the status line rather than over the hand, where `playerPanel` put it: the hand here is
sized to the cards by `MatchLayout`, and a bar inside it would either shrink them or be drawn across
them. It turns red under a quarter remaining, which the original does not do — its bar is one colour
the whole way down, and thirty seconds is long enough that a shortening bar is easy to miss.

### The limit is a parameter

`MatchScreen(turnLimit = …)`, defaulting to the AS3's thirty seconds. `TutorialScreen.as:58` raises
it to sixty for its lesson and `PVPScreen.as:277` sets it back to thirty, so the number is already
per-match in the original. It is also what lets `TurnTimerTest` reach the expiry without waiting for
it — that test composes `MatchScreen` directly rather than threading a test-only argument down four
screens.

### Verification (2026-08-06)

| | |
|---|---|
| Build | `./gradlew build` — ktlint, detekt at `maxIssues: 0`, all tests, `coverageVerify` |
| Tests | **617** in `:shared` on desktop (up from 529), **458** on the Android host source set. New: `CollectionUiTest`, `DecksUiTest`, `InventoryUiTest`, `ShopUiTest`, `StatsUiTest`, `HelpUiTest`, `DeckSelectorUiTest`, `ThemeTest`, `DragAndDropTest`, `TurnTimerTest`, `ShopCatalogTest`, plus routing tests in `NavigationTest` and four in `PveMatchTest` |
| Coverage | 97.8% line / 86.2% branch against the 90/75 gate — line up 1.0 point on the last pass |
| i18n | The app-owned key count went 17 → **28**. Everything else these screens show was already translated four ways: the whole dashboard stack, `STR_USE` / `STR_SELL` / `STR_DISCARD` / `STR_BUY`, `STR_DECK_POWER`, `STR_CHOOSE_DECK`, every `RULE_*` name |

---

## What the first pass built (2026-08-02)

### Files

| File | What it is | AS3 counterpart |
|---|---|---|
| `time/Clock.kt` | Instant + local hour, injected. `FixedClock` for tests | `new Date()` |
| `ui/ProfileSession.kt` | The loaded character, and the only thing that writes it | the global `Game.PROFILE_DATAS` |
| `ui/ProfileScreen.kt` | Character list and creation, with the collection choice | `LoadScreen` + `NewGameScreen` |
| `ui/OpponentScreen.kt` | Who can be challenged, filtered by collection and by hour | `PVEScreen` |
| `ui/Controls.kt` | `WideButton`, `ScreenScaffold`, the shared row palette | `TouchLabel`, `MainButton` |
| `data/PveMatch.kt` | Profile + opponent + catalog → a playable match | `Game.prepareMatch` + a global read |
| `data/MatchRewards.kt` | End-of-match crediting: MGP, XP, boons, stats, drops, achievements | `PVEMatchScreen.endGame` |
| `androidApp/AndroidClock.kt`, `desktopApp/JvmClock.kt` | The real clock, per host | — |

Reworked: `ui/App.kt` (seven destinations, routing split out of the shell), `ui/MatchScreen.kt` (an
opponent that plays itself, Open visibility, the rules strip, the result panel),
`ui/MainMenuScreen.kt` (names the loaded character), `ui/Startup.kt` (loads `npcs.json`).

### Screens, against the plan's tiers

| Screen | State |
|---|---|
| `MenuScreen` | ✅ Tier 1. Four actions, and it names the loaded character |
| `SettingsScreen` | ✅ Tier 1, as `OptionsScreen` (Phase 1) |
| `LoadScreen` | ✅ Tier 1, as `ProfileListScreen` — a *character* list, which is what the original's was |
| `NewGameScreen` | ✅ Tier 2, as `ProfileCreateScreen`, **plus the collection choice the original never offered** |
| `PVEScreen` | ✅ Tier 4, as `OpponentScreen` — the one item of that tier that needs no network |
| `BaseMatchScreen` + `PVEMatchScreen` + `Board` + `RulesDigest` + `RematchPanel` | ✅ Tier 2, as `MatchScreen` and its result panel |
| `HelpScreen` | ✅ Tier 1, as an accordion over the seventeen rules |
| `dashboardScreen` | ✅ Tier 2 — the hub the other six hang off |
| `cardPanel` | ✅ Tier 2, as `CardListScreen`'s detail panel |
| `DecksScreen`, `InventoryScreen`, `cardListScreen`, `profileScreen`, `shopScreen` | ✅ Tier 3, all five |
| `DeckSelector` | ✅ Tier 2 — a step inside the match, as in the original |
| `playerPanel` | ✅ Tier 2 — its **turn timer** is what it held; the hand and the name were already drawn |
| `PVPScreen`, `PVPMatchScreen` | ⏳ Tier 4 / Tier 2 — the only two that touch the socket, so the only two Phase 5 blocks |
| `CCGroupScreen`, `GSGroupScreen`, `CCGroupMatchScreen`, `GSGroupMatchScreen`, `CCGroupRematchPanel`, `GSGroupRematchPanel` | ⏳ Tier 4 on paper, **single-player in fact** — see below |
| `TutorialScreen`, `TutorialRematchPanel` | ⏳ Tier 5 / Tier 4 — scripted PvE; waits on `TalkAnim`, which is Phase 6 |
| `BackstageScreen`, `EmptyScreen` | ⏳ Tier 5, and **neither is reachable** — see below |

**20 of the 32 are done and 12 are left.** That tally was wrong in earlier revisions of this
document — it read fifteen and seventeen — because each pass incremented the previous number instead
of recounting against the plan's own tier lists. Per tier: 4 of 4, 9 of 10, 5 of 5, 2 of 10, 0 of 3.

### Six of the eight "multiplayer" screens are not multiplayer

`grep -c Socket` is **0** on all six of the group screens and panels, and `CCGroupScreen.as:98` /
`GSGroupScreen.as:98` increment `PVE_MATCHES`, not `PVP_MATCHES`. They are single-player tournament
ladders — pay 500 MGP, then play five to seven fixed opponents in sequence — and nothing blocks
them. Only `PVPScreen` and `PVPMatchScreen` reach `tto.net.Socket`.

The work in them is **data, not UI**: `CCGroupMatchScreen.as:30-70` declares its opponents inline as
`NPC` records with their own rules, card pools and drop tables. That is the shape `shopScreen` had
before Phase 2 pulled its price tables out into `ShopCatalog`, and it wants the same treatment
first.

### Two Tier 5 screens will not be ported, and here is why

- **`EmptyScreen`** is an empty `Screen` subclass — three overrides that call `super` and nothing
  else — and it has **no `addScreen` entry in `Game.as`**, so nothing can navigate to it. It is a
  template left in the tree. There is nothing to port.
- **`BackstageScreen`** dumps `JSON.stringify(Game.PROFILE_DATAS)` into a `TextArea` and
  `JSON.parse`s it back on Save. It is gated on `PROFILE_DATAS.ADMIN`, which `Save.as` sets to 0 and
  **nothing ever writes**. It is a save-file editor that the shipped game cannot open; porting it
  would be shipping a cheat console.

### The collection choice is new, not ported

`Save.setToDefaultValues()` hard-codes `DATAS.MODE = 'ff14_'` and **nothing in the original ever
changes it**. So the entire `ff8_` card table and its 25 opponents shipped with the game and were
unreachable. Offering the choice at creation is the smallest change that makes them playable, and it
is put at creation rather than in a settings pane because `MODE` decides which table a profile's card
ids index — switching it later would silently reinterpret every card the profile owns.

### Six decisions worth naming

1. **No navigation library.** Seven destinations, a linear flow, one `up` per screen. `Screen.up` and
   one `when` are what Compose Navigation would replace. The point to reconsider is a screen reachable
   from two places with a different back destination from each; the original's fourteen have several.

2. **`ProfileSession` owns the profile, and it is the only thing that writes.** `SaveRepository.save`
   stamps `LAST_SAVE` and increments `SAVE_NUMBER`, so a caller that keeps the copy it passed in stops
   advancing the save number and rewrites a stale timestamp. Every mutation goes through `persist` and
   adopts what came back. The AS3 has the same hazard from the other end: `Game.PROFILE_DATAS` is a
   global that eleven screens mutate and four save, so "what is on disk" and "what is on screen" are
   equal only by convention.

3. **The opponent's thinking time is 700 ms, not one to five seconds.**
   `PVEMatchScreen.as:42` waits `1000 + tools.rand(4) * 1000`, which covered a `setTimeout` cascade of
   turn announcements this port does not have. Five seconds of a static board is dead time. Pacing is
   Phase 6's business and this is the number it will want.

4. **A match is persisted when it *starts*, which the original never did.** `PVEScreen.as:244`
   increments `STARTED_MATCHES` at launch but only `endGame` saves, so an abandoned match loses the
   increment and `STATS.FORFEITS` — defined as `STARTED_MATCHES - ENDED_MATCHES` — can never be
   anything but zero. Writing at the start is what the field was designed for.

5. **Deck selection is not offered; the first complete deck is played.** With a fallback the original
   lacks: `DeckSelector` refuses to start on a partial deck and offers nothing else, so a player whose
   only deck is half-built cannot play at all. Five owned cards is a better answer than a dead end.
   A real deck screen needs the collection browser to be worth having — Tier 3.

6. **The clock is host-supplied, and `kotlinx-datetime` was tried and dropped.** The local hour is
   needed because 27 of the 60 `ff14` opponents declare an availability window. Reading the instant
   through `kotlin.time.Clock` and the zone through `kotlinx-datetime` **compiles as common metadata
   and fails on every platform target**: as of 0.6.2 `kotlinx.datetime.Instant` is a deprecated
   typealias onto the stdlib's in the metadata view and a distinct type in the platform views, so
   `toLocalDateTime` accepts a stdlib instant in one and refuses it in the other. Rather than carry a
   dependency that disagrees with the stdlib about its own types, the two JVM hosts supply three lines
   each — which also avoids an iOS `actual` that cannot be built from this machine.

### Two AS3 facts corrected in the data layer

| Where | What | Decision |
|---|---|---|
| `NPC.matchFee` | Declared for all 85 opponents, exposed by a getter, and **read by nothing**. `endGame` pays `MGPReward.w + rand(20)` on a win and `MGPReward.l + rand(5)` on a loss and subtracts nothing, so every result is a net gain. `Npc.mgpFor` had been ported as `reward - fee`, inventing a deduction | **Fixed to match the source.** The fee is carried as data and shown in the opponent list. Charging it would turn an economy that only grows into one with real downside — a design change, not a migration |
| `PVEMatchScreen.endGame` | Its three near-identical branches **disagree on purpose**: only the win branch records `NPC_W`, loops `RULES_W` and rolls the drop table | **Reproduced, and stated once** rather than implied by which of three duplicated blocks a line sits in. It matters because `RULES_W` is what the Wheel-of-Fortune achievements count |

### Verification

| | |
|---|---|
| Build | `./gradlew clean build` — ktlint, detekt at `maxIssues: 0`, all tests, `coverageVerify` |
| Tests | **529** in `:shared` on desktop (up from 458), **432** on the Android host source set. New: `MatchRewardsTest`, `PveMatchTest`, `ProfileUiTest`, `OpponentUiTest` |
| Coverage | 96.8% line / 86.7% branch against a 90/75 gate. Line is down 0.5 points on Phase 3 and branch up 0.1: the new UI is 49 uncovered lines, almost all of them error and empty-state branches a happy-path test does not reach |
| The UI suite | **Reworked, which was the debt Phase 3 recorded.** `playOut` and `sideToPlay` assumed a human drove both hands. Now `awaitPlayer` waits for the turn and `playOneCard` probes for a free cell and confirms by watching the hand shrink. Whose turn it is is read off a `turn-blue` tag rather than the words "blue to play" — that scraping pinned every match test to `en_US` and stopped the French and German ones from asking |

### What is not done, and is not hidden

*Updated 2026-08-06.*

- **Twelve screens**, listed in the table above — down from twenty-six, and not the seventeen
  earlier revisions of this document claimed. Two are blocked on Phase 5 and on **TR-007**
  (multiplayer does not function in the AS3 source either); six are single-player ladders that
  nothing blocks; two wait on Phase 6's `TalkAnim`; two will not be ported.
- **The pre-match animations.** `MatchIntroStep` is computed and handed to the UI, which ignores it.
  Those are the twenty-three `anims/` classes, and they are Phase 6. The drag has no *animation*
  either: a card dropped on nothing disappears from under the finger rather than flying home.
- **`DesktopDocumentStore` and `AndroidDocumentStore` have no tests.** Neither host module has a test
  source set. The `DocumentStore` *contract* is covered by `InMemoryDocumentStore` and the whole
  profile flow runs against it, but the two real file implementations are exercised only by hand.

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
- [x] Theme colors match original — transcribed from `BaseTTOTheme.as:124-137`, pinned by `ThemeTest`
- [x] Typography matches original — Raleway, the two weights the AS3 embeds; the *scale* is
      re-anchored for density rather than transcribed, and § The theme system says why
- [x] Theme is applied consistently — no `Color.White`, no `fontSize` literal and no per-call-site
      Material override left in the fourteen screens

Done 2026-08-06. See § The theme system for what this document got wrong about the font.

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
- [x] Cards can be dragged — the player's own playable ones; see § Drag and drop for the gate
- [x] Cards can be dropped on tiles
- [x] Drop validation works — an occupied cell never registers, so it never highlights either
- [x] Visual feedback during drag — a ghost on the finger, a highlight on the target, the source
      card dimmed in the hand

Done 2026-08-06, tapping kept alongside it as the task asks. `DragAndDropTest` covers all four.

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

**Nothing here is reviewed or approved**, as in Phases 1-3. The work is done and self-verified;
sign-off is a separate step and has not happened.

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
*Status: IN PROGRESS — 2026-08-06. The playable loop, Tier 3, the deck selector, the theme, drag-and-drop and the turn timer are done: 20 of 32 screens. Of the 12 left, only 2 are blocked on Phase 5. See § What was built.*
