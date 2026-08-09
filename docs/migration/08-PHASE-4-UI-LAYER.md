
# Phase 4: UI Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 4 - UI Layer
- **Duration**: 8 weeks (Weeks 13-20)
- **Status**: IN PROGRESS — 2026-08-09. The playable loop, all of Tier 3, the deck selector, the
  theme system, drag-and-drop, the turn timer, the tutorial and both tournament ladders are done:
  **28 of the 32 screens**. Of the four left, **two are blocked on Phase 5** (`PVPScreen`,
  `PVPMatchScreen` — the only two that touch a socket) and two will not be ported. See § What was
  built.
- **Version**: 1.8
- **Last Updated**: 2026-08-09
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

**2026-08-08 — the tutorial teaches.** `TutorialScreen` and `TutorialRematchPanel`, which had been
waiting on Phase 6's speech bubble. They are not a second match screen: everything the AS3 expressed
by subclassing `PVEMatchScreen` is one data object the ordinary match screen takes as a parameter.
See § The tutorial.

**22 of the 32 screens exist and 10 are left**, of which only two — `PVPScreen` and
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
| `TutorialScreen`, `TutorialRematchPanel` | ✅ Tier 5 / Tier 4 — scripted PvE, on `TalkBubble` (Phase 6) |
| `BackstageScreen`, `EmptyScreen` | ⏳ Tier 5, and **neither is reachable** — see below |

**22 of the 32 are done and 10 are left.** That tally was wrong in earlier revisions of this
document — it read fifteen and seventeen — because each pass incremented the previous number instead
of recounting against the plan's own tier lists. Per tier: 4 of 4, 9 of 10, 5 of 5, 3 of 10, 1 of 3.

## The tutorial (2026-08-08)

`TutorialScreen extends PVEMatchScreen` and overrides four methods. Composables do not subclass,
so what the overrides *say* is now one data object — [`MatchScript`] — that an ordinary
[`MatchScreen`] takes as a parameter: the deal, who starts, how long a turn is, how the opponent
plays, and what is said before each placement. `TutorialRematchPanel` is not a second panel either;
it swapped one button, so [`MatchScreen`] takes the replacement.

The alternative was a second copy of a 400-line screen with five lines different, which is the same
trade the phase refused for `RematchPanel` and for the deck selector.

### The opponent loses on purpose

`// c'est le tuto, le pnj jour toujours la pire solution` — it scores all 45 placements exactly as
the real AI does and then plays `powers[powers.length - 1]`, the bottom of the ranking. That is
`MatchAiOptions.TUTOR`, and it is a better answer than a random opponent would be: random would
occasionally capture three cards in a row and teach the opposite of what the script is saying at
that moment.

### Three of its nine lines never appeared in the original

`opponentPhase` is reached from exactly one place, the **red** branch of
`BaseMatchScreen.nextTurn` (`:380`). `TutorialScreen` overrides it with branches on `turn == 2` and
`turn == 4`, which are the *player's* turns — so they never ran. The three lines behind them are
also the three most instructional ones the tutorial has:

> "Now it's your turn! Place a card in one of the empty spaces adjacent to my card."
> "The numbers you can see each correspond to one side of the card…"
> "Try to capture and control more cards than your opponent!"

Written for the player's turn, hooked to a callback that only fires on the opponent's. Here the
lesson is driven off the placement count for both sides, so all nine play — and `TutorialLessonTest`
asserts that, because a UI test can see that a bubble appeared but not that every line the author
wrote is reachable.

### Its nine lines were never translated either

They are Flash string literals in the middle of the screen class, with no `i18n.gettext` around them
and no matching key in any of the four bundles: a French player was taught Triple Triad in English.
They enter through `app-*` here, in English and French, with German and Japanese falling through as
the other app-owned strings do.

### Two smaller departures

- **It charges the fee and pays the full reward.** The AS3 declares its own `NPC` inline with
  `matchFee: 0` and a third of the catalogue's drop rates — data written into a screen, the shape
  `shopScreen` had before Phase 2 pulled its prices into `ShopCatalog`. Reading the shipped
  `tt-master` costs five MGP against a starting balance of several hundred, and avoids a second
  record whose only purpose is to be slightly different from the first. It is not farmable either
  way: the end panel's Rematch is *replaced* by the rule book, which is what the original does.
- **An `ff8_` character is taught by Kid.** The tutor is the collection's id-1 opponent, and the
  choice is not arbitrary — `tt-master` and `kid` are both `LEVEL_NOVICE` and both declare
  `RULE_ALL_OPEN` and nothing else, which is what the lesson's second line announces. The original
  could only ever have been the first, since it hard-codes `MODE = 'ff14_'`.

The transcript is **not** submitted. A script forces the coin flip, fixes the deal and hands the
opponent a different strategy, none of which the seed carries — the server would replay it, fail,
and a rejection is indistinguishable from being caught cheating.

---

## The tournament ladders (2026-08-08)

### Six of the eight "multiplayer" screens were not multiplayer

`grep -c Socket` is **0** on all six of the group screens and panels, and `CCGroupScreen.as:98` /
`GSGroupScreen.as:98` increment `PVE_MATCHES`, not `PVP_MATCHES`. They are single-player tournament
ladders — pay 500 MGP, then play six or seven fixed opponents in sequence — and nothing blocked
them. Only `PVPScreen` and `PVPMatchScreen` reach `tto.net.Socket`, which is why Phase 4 now has
**two** screens waiting on Phase 5 rather than eight.

**One ladder per collection**, which is easy to read backwards from the class names: the Card Club
(`cc`, seven rungs) is the FF8 tournament and the Gold Saucer (`gs`, six) the FF14 one. A character
sees one entry on the opponent list, never two.

The six files are **two screens, not six**. `CCGroupScreen` and `GSGroupScreen` are the same 121
lines with a different title and a different list of names; `CCGroupMatchScreen` and
`GSGroupMatchScreen` are `PVEMatchScreen` with a step counter; the two rematch panels swap one
button. So the port is one entry screen and one match screen over a `Campaign`, plus the extractor
that turned the AS3 into data.

### The work in them was data

`CCGroupMatchScreen.as:30-70` declares its opponents inline as `NPC` records with their own rules,
card pools and drop tables — the shape `shopScreen` had before Phase 2 pulled its prices into
`ShopCatalog`. `tools/extract_campaigns.py` now emits `campaigns.json` from those declarations, and
`Campaign`/`CampaignStep`/`CampaignMessages` in `:core` are the model.

**A rung is a whole record, not an override.** All thirteen ladder opponents also exist in
`npcs.json` under the same `iconID`, and **every one of them differs** — rules, fee, card pools,
rewards. Sharing an icon is the only thing they share, so the extractor carries the full record and
reports the differences against `npcs.json` on every run rather than silently preferring one.

### The 500 MGP is the only money the game ever takes

`NPC.matchFee` is declared on all 85 catalogue opponents and **charged nowhere**, in the original or
in this port. The ladders' entry fee is the one exception: `Game.PROFILE_DATAS.MGP -= 500` in both
entry screens. It is what makes a ladder a stake — a defeat sends the player back to the first rung
(`nextStep`), and a second attempt costs another 500. `cc/spade`'s own `matchFee: 15` is dead data;
it is carried through unchanged and pinned by a test that says so.

### A rung is a `MatchScript`, like the lesson

`TutorialScreen`, `CCGroupMatchScreen` and `GSGroupMatchScreen` all extend `PVEMatchScreen`.
Composables do not subclass, so all three overrides are one lambda-free `MatchScript` on the
ordinary match screen — the alternative was three copies of a 400-line screen. The ladders use
almost the opposite half of it from the tutorial: they change no deal, no flip and no clock, and
say at most two sentences. `ScriptExit` is what replaces Rematch, and **null past the last rung**,
which is how `CCGroupRematchPanel` ends a ladder: `if (_params.NEXT_STEP < 7)` simply does not build
the button. Their transcripts are not submitted, for the reason given above.

### Both ladders' titles were broken in the shipped game

- **`STR_CCGROUP` is defined in none of the four bundles.** The Card Club's panel title rendered as
  the literal key, in every language the game shipped in.
- **`STR_GSGROUP` exists only in `fr_FR`.** English, German and Japanese showed the key.

`campaignTitle` prefers the AS3 key where a bundle has it — so a French player still reads the
sentence Square Enix wrote — and falls back to an `APP_CAMPAIGN_<KEY>` this port authored. Nobody
reads a key.

The rung dialogue is **untranslated by construction**: the three lines the Gold Saucer's opponents
speak are Flash string literals with no `gettext` and no bundle key, exactly like the tutorial's
nine. They are carried as literals and pass through `Strings[key]`, whose documented fallback is to
return the key itself.

---

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

#### Task 4.1: Theme System

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

> **Note**: do not set `color` inside `TextStyle` *and* rely on
> `colorScheme.onBackground` — pick one source of truth for text colour, otherwise
> the typography silently overrides the scheme everywhere.
>
> **Font caveat**: `Eurostile` (used in `display/Card.as:81`) has no CJK coverage,
> so the `ja_JA` locale needs a fallback family. Audit
> `sources/bin/assets/fonts/` during Task 4.1, and note that redistributing
> Eurostile may itself require a licence.

- [x] Theme colors match original — transcribed from `BaseTTOTheme.as:124-137`, pinned by `ThemeTest`
- [x] Typography matches original — Raleway, the two weights the AS3 embeds; the *scale* is
      re-anchored for density rather than transcribed, and § The theme system says why
- [x] Theme is applied consistently — no `Color.White`, no `fontSize` literal and no per-call-site
      Material override left in the fourteen screens

Done 2026-08-06. See § The theme system for what this document got wrong about the font.

---

#### Task 4.2: Common Components

> **Dimensions**: verified against the AS3 source — card `88 x 118`
> (`display/Card.as:73`, `new Quad(88, 118, 0x5a595a)`), tile `136 x 136`
> (`display/Tile.as:51`). Earlier revisions used `104 x 128` for cards, which
> appears nowhere in the source. Note that AS3 values are **pixels at a fixed
> 1280x720 landscape stage**, not density-independent units — treat them as design
> ratios (card ≈ 0.65 x tile) and scale to the viewport rather than hardcoding dp.

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

---

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

---

#### Task 4.5: BaseMatchScreen (Most Complex)

---

#### Task 4.6: Board Component

---

**Screens to Migrate**: See priority list above.

Each screen follows similar pattern.
1. Analyze AS3 implementation
2. Map to Compose components
3. Implement with ViewModel
4. Test functionality

- [ ] UI matches original design
- [ ] All functionality works
- [ ] Navigation works
- [ ] Responsive layout

---

#### Task 4.7: Drag and Drop Implementation

> **Also support tap-to-select + tap-tile-to-place.** The AS3 code offers both
> interactions (`Card.onTouch` for drag, `Tile.onTouch` + `BaseMatchScreen.tileTouched`
> for tap), and tapping is significantly easier on a phone than dragging a card to
> a 3×3 grid. Do not ship drag-only.

- [x] Cards can be dragged — the player's own playable ones; see § Drag and drop for the gate
- [x] Cards can be dropped on tiles
- [x] Drop validation works — an occupied cell never registers, so it never highlights either
- [x] Visual feedback during drag — a ghost on the finger, a highlight on the target, the source
      card dimmed in the hand

Done 2026-08-06, tapping kept alongside it as the task asks. `DragAndDropTest` covers all four.

---

#### Task 4.8: UI Animations

---

---

## The interface artwork (2026-08-08)

The screens around a match were drawn entirely in text and coloured surfaces. Everything the AS3
put a picture on — the profile's avatar, the opponent's face, an item's icon, a card's thumbnail —
was in the asset tree and nothing read it. This is that plumbing; it is not the Material 3 pass,
which is layout and is still ahead.

### What was imported, and the one place the reasoning inverts

`tools/import_ui_art.py`, the companion to `import_card_art.py`:

| Folder | Count | Form |
|---|---|---|
| `art/avatars/` | 27 | individual 128x128 files |
| `art/npcs/` | 84 | individual 50x50 files, named by `iconId` |
| `art/icons/` | 17 | individual 40x40 files |
| `art/thumbs/` | 263 frames | **three atlases** plus `thumbs.json` |

`import_card_art.py` argues for unpacking atlases, and is right for card faces: a match shows
nineteen cards out of 263, so a resident sheet pays 8 MB of bitmap for 1 MB of use. Thumbnails
invert every term of it — the collection browser is a grid of *the whole table*, so there is no
subset to load lazily, and `BitmapPainter` takes a source rectangle, so a slice costs no bitmap of
its own. The frame table is emitted as JSON rather than left in the three TexturePacker XMLs, so
the app parses one small document with the reader it already has.

### Nothing here invents an identifier

An avatar is `GameSave.avatarId`, a portrait is `Npc.iconId`, an icon is `Item.iconId` or
`Achievement.iconId`, a thumbnail is the card's own texture id. Every one of those was in the model
before there was an image to go with it, which is why `UiArt` is a set of lookups and not a mapping
table.

One exception, and it is reconciled in exactly one place: `ac-fob` names `ff14_thumb_37`, which is
the frame the atlas calls `ff14_37`. `AchievementIcon` resolves both spellings.

### A missing image is a normal state

Eleven opponents — the Card Club's rungs and the card suits — have no 50px portrait anywhere in the
asset tree, and `PotionItem.as` names a texture that was never shipped. So the fallback is not an
error path: `AvatarBadge` and `NpcPortrait` draw the subject's initial on a tinted plate, `ItemIcon`
keeps its plate empty, and the row stays the same height either way.

Both gaps are **enumerated on both sides**. The importer lists them and exits 0; `UiArtTest`
asserts the same eleven as an exact set, so a twelfth fails and an eleventh that turns up also
fails — a gap that is expected should not silence the check.

### Where it landed

| Screen | Before | Now |
|---|---|---|
| Record | level bar alone | `AvatarBadge` beside it; a badge on every achievement row |
| Collection | card face at 0.33 | the card's own thumbnail |
| Opponents | name only | `NpcPortrait` leading the row |
| Campaign | step number | portrait after the number |
| Bag | card face at 0.3 | thumbnail, or the item's icon |
| Shop | card face, nothing for a booster | thumbnail, or the item's icon |
| Decks, deck selector | card face at 0.42 | thumbnail, empty slots the same size |

The deck screens are the one place this is also *more faithful*: `DecksScreen.as` drew `CardThumb`
there, and the port had been scaling a full face down to a size its digits could not survive.

Covered by `UiArtTest` (bundle completeness, no composition) and `ArtworkUiTest` (the artwork
reaches the screens, and the monogram is drawn when it cannot).

## The Material 3 shell and adaptive navigation (2026-08-09)

Three proposals were drawn up first — habiller l'arbre, quatre destinations, adaptatif — and the
third was chosen, which is the first two plus a width threshold. Built in that order, each step
green before the next.

### A — the shell

- **`ScreenScaffold` is a real `Scaffold`.** The header was two `Text`s in a `Row`; the back
  chevron was a 20 sp glyph with 4 dp of padding, so about 28 dp of tappable width against
  Material's 48. It is now a `TopAppBar` with an `IconButton`, an `actions` slot and a
  `snackbarHost`. The bar is held to the content's own width and centred rather than spanning the
  window: a full-bleed bar would strand the title a hand's width from its own list on a desktop.
- **`CharacterBar` became `CharacterActions`** and moved into the app bar's corner, which is where
  `display/UserBar.as` had it. The purse is drawn with `icons/PGS.png` rather than the letters MGP.
  `CHARACTER_BAR_TEST_TAG` is unchanged, so every test that asked "is this a screen behind the
  dashboard" still asks it.
- **Nine buttons became a grid of cards.** Nine identical full-width bars said that nine things
  were equally likely; Play spans the grid, the rest are cards with a glyph, and the header is the
  profile — `LevelBar`, which the record screen already had and which now has a second caller.
- **A snackbar for the shop.** The original's purchase handler deducted, pushed and returned, so a
  50 MGP potion and a 30 000 MGP card looked identical from the player's side. `NoteHost` is a
  `SnackbarHostState` with its tag attached, and it replaces rather than queues.
- **Nine glyphs, drawn here.** `TtoIcons` — `material-icons-extended` is ~1 200 vectors in one
  artifact and nothing strips it from a desktop jar or an iOS framework. `Play` and `Collection`
  are drawn as *cards*, which is the one shape this game has and Material's set does not.

### B — four destinations

`Screen` still has nineteen entries; what changed is that four of them are two screens.

| Was | Is |
| --- | --- |
| `CARDS`, `DECKS` | `CollectionScreen`, two tabs |
| `SHOP`, `INVENTORY` | `StoreScreen`, two tabs |

Both pairs are the two ends of one activity — a deck is built out of the collection, a pack is
bought on one tab and opened on the other — and `DecksScreen.as` proves it by growing its own
owned-card pager. The merge is also what makes four bar entries possible without hiding anything.

**The cost I predicted did not materialise.** The proposal said `Screen.up` would have to become a
back stack, because with tabs "up" stops being a property of a screen. It does not here: every tab
root already had `DASHBOARD` as its `up`, since the dashboard was the parent of all eight. Back
from any tab lands on Home, which is exactly what Material prescribes for a bar
(`popUpTo(startDestination)`). `ComposeTestSupport.openFromDashboard` survived unchanged; what it
gained is a sibling, `openFromBar`.

The bar reaches the tree through `LocalNavigation` rather than two more parameters on eleven
screens — the trade `LocalUiArt` already makes. `Screen.tab` answers null on the three match
screens, so no bar is drawn over a board and none of them has to know a bar exists.

The home screen no longer lists the collection or the shelf: the bar reaches both in one tap. It
keeps the decks and the bag, which are a tab *inside* a bar destination, and it keeps Play, which
is the one repetition worth having.

### C — the 600 dp threshold

`BoxWithConstraints` in `App`, measured once off the whole window and published as
`LocalWideLayout`. Above it: `NavigationRail` instead of `NavigationBar`, and the collection lays
its grid and its card detail side by side — `card_list.jpg`'s own arrangement, which the original
could take for granted on a 1024-wide stage.

`material3-adaptive` was **not** taken as a dependency. `NavigationSuiteScaffold` and
`ListDetailPaneScaffold` are separate artifacts whose Compose Multiplatform publication would have
to be checked target by target, and what they would replace is one comparison and one `if`. Worth
revisiting the day there is a third pane or a real back stack.

Only the screens that lay out two panes get the wider column (`WideContentMaxWidth`, 920 dp). A
list does not become more readable at 920 dp; it becomes a row of text with a gap in the middle.

### Two new strings

`APP_CARDS` and `APP_HOME`, both `APP_`-owned for the same reason: the original had no tabbed cards
screen and no navigation bar, so neither name exists in the four imported bundles. English and
French only, falling through for German and Japanese like the other 41 — `StringsBundleTest` is
what says so.

### Covered by

`TabsUiTest` (the merges, and that back leaves the deck editor before the screen), `AdaptiveUiTest`
(bar below the threshold, rail above it, and the collection's two panes asserted on **geometry** —
both are on screen either way, which is why a presence check would have passed before the layout
existed), plus two new cases in `NavigationTest` for the bar's four entries and for its absence on
the menu and during a match.

## The lobby and the board (2026-08-09)

The two screens the Material 3 shell had not reached. Six changes, proposed as an artifact and
implemented in the order they unblock each other.

### B1 — the board's colours joined the theme

`MatchBoard.kt` held `EmptyTile`, `TileBorder` and `SelectionRing` as private top-level `Color`s,
and `MatchScreen.kt` held a fourth — `RuleStripText` — that was the *same value* as `SelectionRing`
written out twice. They are now `TtoColors.boardTile`, `boardTileOutline` and `selectionRing`. The
board was the one screen that could never follow a theme change, and it is the screen with the most
time spent on it.

### B2 — a real match banner

The back control was a `Text("‹")`: a 20 dp touch target where every other screen has a 48 dp
`IconButton`, and the reason recorded for it was width, which an icon does not cost either. It is
now `TtoIcons.Back`, and the opponent's 50 px portrait — already loaded for the opponent list —
sits beside their name. The board named an opponent and pictured nobody.

### B4 — the rules open

`RulesStrip` was one line of dot-separated names that elided past two rules. It is one small
`Surface` per rule in a `FlowRow`, and **tapping the strip opens the `RULE_*_HELP` sentence for
each**. Those sentences have been in the bundles since the import and nothing had ever shown one
during a match; naming Fallen Ace and explaining it are different services. Closed is the default,
and closed costs what the old line cost.

Not `AssistChip`: a chip is a control with a ripple and a 32 dp minimum, and six of them above a
board would cost the phone layout more than the rules are worth.

### B5 — the cells that could take the card

While a card is held — tapped *or* dragged — every free cell outlines at 38 % of the selection
ring. "Where can this go" answered before the attempt rather than by it, which matters most on a
phone where the finger covers the cell it is aiming at.

### B6 — the outcome panel, **not** as an `AlertDialog`

This was the plan and it was wrong. `AlertDialog` opens in a popup above everything in the
composition, and three things are deliberately drawn after the panel and over it —
`MatchBannerOverlay`, `LessonBubbles` and `OutcomeBubble`, the last of which the AS3 puts on screen
*at the same time as* the panel (`endGame` adds the `TalkAnim`, then schedules `rematch` behind
`intervalDuration`). A dialog would have put the opponent's parting line behind a scrim.

So the panel kept its place in the tree and took what the dialog was wanted for: the theme's `scrim`
behind it instead of a live board, a `Surface` at 6 dp instead of a hand-mixed `0xFF11141C`, and the
two actions in Material's own order — leave quiet and first, play again filled and second. That
needed a `filled` parameter on `WideButton`, which is the first time this app has had a quiet
button.

### B3 — a side panel above 600 dp

`matchLayout` has always adapted the *scale*: it is handed measured bounds and derives one factor
everything fits inside, so a desktop window already produced a bigger board. What it could not
produce was a different **arrangement**, and past a certain width bigger stops being better. Above
the threshold the board keeps a weighted column and a 200 dp panel takes the rest: the opponent's
portrait and name, the rule strip, and a move log.

The log is the one thing on that panel that exists nowhere else, and it is read off
`MatchState.lastPlay` rather than off the transcript `MatchScreen` keeps. That is the whole reason
it was affordable: `moves` records what the *screen* played — the opponent's turns go through
`MatchAi` and never touch it — whereas every state the match passes through carries the play that
produced it, whichever side made it.

`MatchScreen.kt` hit both of detekt's ceilings on the way (twenty functions in a file, cyclomatic
complexity 15), so the chrome moved to `MatchChrome.kt` along a seam that was already there: nothing
in that file reads a `MatchState` or can affect one.

### L1 + L2 — the menu, and the account the app remembers

`MainMenuScreen` was the last pre-M3 screen: a logo over five stacked `WideButton`s. It is now the
dashboard's own `HomeCard` grid — Play accented and full width, four cards below.

It does **not** take `ScreenScaffold`, and that is deliberate: the menu is the root, so there is no
up to draw, and its title is the wordmark. A `TopAppBar` here would be an empty bar with a back
arrow that quit the game.

Above the grid, when the app remembers a name, a **resume card**. This is the visible half of a
feature that has worked silently since sessions landed: `AccountSession.restore()` loads the stored
token on launch, calls `accounts.me`, and the form is simply never shown — which from the outside is
indistinguishable from the form being broken. Three states, read off the session rather than stored:
signed in, still asking, and lapsed. The lapsed card offers a sign-in rather than a continue,
because the app stores no password and has nothing to try.

`onSwitch` navigates **before** signing out. Signing out clears `lastUsername`, which removes the
card, and a player who tapped it and watched the menu quietly rearrange itself would not know
whether anything had happened.

### L3 + L4 — the sign-in form, in the player's language

`AccountScreen` was the only screen in the app still written in hard-coded English — "Sign in",
"Password", "New here? Create an account" — and `AccountResult.message()` was six more English
sentences in a `when`. Thirteen new `APP_*` keys, English and French, falling through for German and
Japanese like the other 49.

One message is still not translated and cannot be: `MALFORMED_CREDENTIALS` shows `failure.detail`,
a sentence the **server** wrote, because it is the only refusal whose reason the client cannot know.
It arrives in the server's locale, which the protocol would have to grow a key to fix.

L4 is two smaller things: a `LinearProgressIndicator` under the title while `isBusy` — always laid
out, so the form does not shift when a request starts — and the refusal moved from a `Text` wedged
between the password field and the button (which pushed the button down as the player read it) to a
`Snackbar` through the same `NoteHost` the shop uses.

### Covered by

Three new cases in `MatchUiTest` (the strip opening and closing, the help sentence being the
bundle's, the opponent's portrait on the board), three in `AdaptiveUiTest` (the panel present above
the threshold and absent below it, and the log recording **both** sides — a log that silently
omitted the opponent's moves would look like a working log), a new `ResumeCardTest` with five cases
driven against `MainMenuScreen` directly, and a new `AccountUiTest` case asserting a refusal is
worded in French.

`ResumeCardTest` does not go through `App`: a `RememberedAccount` needs a server and the full-app
tests deliberately run on an offline build, so there is no way to reach a lapsed session through the
real launch path without standing up a host that refuses a token. What the card *does* with each
state is the part with decisions in it, and it is all there.


## The tidy-up, the missing strings and the level gate (2026-08-09)

### Four files that were no longer screens

Merging Cards+Decks and Shop+Inventory into tabbed screens left `CardListScreen.kt`,
`DecksScreen.kt`, `InventoryScreen.kt` and `ShopScreen.kt` declaring only a `ColumnScope.*Body`
each. They are `CardListBody.kt`, `DecksBody.kt`, `InventoryBody.kt` and `ShopBody.kt` now.

A scan for unreferenced top-level composables and constants across `:shared`, `:core` and both hosts
turned up **exactly one** genuinely dead symbol — `UNLOCKED_CARD_TOTAL_MILLIS` — which is gone. No
screen was unused: every `Screen` value is navigated to and every `*Screen` composable is routed.

### Twenty-three strings that were being asked for and resolving to nothing

`StringsBundleTest` walks `StringKeys.all`, so it can only see keys a screen names *directly*. It
cannot see one a model **composes** — and that blind spot was not theoretical:

- `NpcLevel.labelKey` produced `STR_NPC_LEVEL_AVERAGE`, which no bundle defines. **25 of the 60
  ff14 opponent rows drew that raw key** as their level band.
- `BoosterType.descriptionKey` / `PotionType.descriptionKey` / `CardItem.descriptionKey` produced
  `STR_*_DESC`, which no bundle defines either. **Every row of the shop drew a raw key as its
  description.**

The original never defined them: `sources/bin/datas/locales/en_US.json` has none of the twenty-two.
So they are the port's to write, and they are `APP_`-owned for the reason `APP_MATCHES` already
carries — the `@SerialName`s in `npcs.json` keep their `STR_NPC_LEVEL_*` spelling, only what is
*shown* moved. `DerivedKeysTest` is the new test that walks the enums rather than the constant list.

The server list contributed fifteen more: it had **no translated string on it at all** — title,
blurb, probe button, one phrase per `ServerStatus`, and the whole update notice were English
literals. The AS3 build talked to one hard-coded host and never had a list to describe.

### Opponents are gated on the character's level

`PVEScreen` lists the whole table from the first match onward, and the ff14 table runs from
difficulty 1 to **19**. A new character was shown sixty opponents with no way to tell which were
worth the fee — which is charged either way.

`NpcCatalog.available` now also filters `difficulty <= level + 1`. One ahead rather than none,
because a list with nothing above your weight has nothing to aim at: at level 1 the five easiest
are open, and each level opens what the last made plausible. `lockedByLevel` counts what is held
back and the list says so under itself — a filter with no explanation is just a short list.

The ff8 table declares `difficulty` **0 for all twenty-five of its opponents**, a field that data
never filled in, so the gate is inert there and that collection is unchanged. That is right by
accident rather than by design, and the KDoc says so, so a later pass that fills those numbers in
knows it is switching a gate on.

Two test call sites needed care rather than a parameter: `OpponentUiTest`'s hour-window tests seed a
character past the gate, and they seed **XP** rather than a level, because `GameSave.sane()`
recomputes the level from experience on every load and write — a `copy(level = 3)` is normalised
straight back to 1 before the screen sees it.

### Options and Servers on the shell

`OptionsScreen` drew its own centred title and its own `‹ Back` text button, which made it the one
screen whose back control was somewhere different from every other screen's. It is a
[ScreenScaffold] now, and its two groups are cards with the heading outside — Material puts a
group's label above its container, and a label inside one reads as the first row of it.
`OPTIONS_BACK_TEST_TAG` is gone; four call sites moved to `SCREEN_BACK_TEST_TAG`.

`ServersScreen`'s refresh moved into the scaffold's `bottomBar`, where a long update notice can no
longer push it off screen, and it is the quiet half of the pair — `WideButton(filled = false)`,
which the outcome panel introduced.

### Covered by

`DerivedKeysTest` (three cases over the enums), two new `NpcCatalogTest` cases for the gate and its
count, and two new `OpponentUiTest` cases for the screen honouring it — the footnote present at
level 1 with the difficulty-4 opponent unreachable, and both the other way round at level 3. Both
halves matter: a filter with no explanation is a short list, and an explanation with no filter is a
lie.


## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 3**: [07-PHASE-3-CORE-LOGIC.md](./07-PHASE-3-CORE-LOGIC.md)
- **Phase 5**: [09-PHASE-5-NETWORK.md](./09-PHASE-5-NETWORK.md)
- **Component Mapping**: [14-COMPONENT-MAPPING.md](./14-COMPONENT-MAPPING.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Status: IN PROGRESS — 2026-08-06. The playable loop, Tier 3, the deck selector, the theme, drag-and-drop and the turn timer are done: 20 of 32 screens. Of the 12 left, only 2 are blocked on Phase 5. See § What was built.*
