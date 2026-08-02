# Game Rules Specification

Phase 0, Task 1.3 deliverable — the last one outstanding.

**What this is.** A testable specification of the Triple Triad rules *as the AS3 source
actually implements them*, derived by reading the code rather than the FF14/FF8 manuals. Every
claim cites `file:line` so the port can be checked against the original.

**Why it matters.** This is the correctness core of the game. Everything else — atlases,
layout, animation — is cosmetic by comparison: a wrong pixel is visible, a wrong capture is
not. The rules engine is also the one part of the migration that can be ported with full test
coverage before any UI exists.

**Read § 15 before porting.** Nine defects, hazards and resolved questions are recorded there. Several are
places where the AS3 behaviour differs from the published Triple Triad rules, so "port it
faithfully" and "port it correctly" are not the same instruction and you have to choose per
item.

**Status: fully ported as of 2026-08-02.** § 15b records how each item was resolved, three
corrections to this document found while porting, and the § 16 matrix as it now stands.

---

## 1. Method and confidence

Read in full: [`tto/datas/tripleTriadRules.as`](../../sources/src/tto/datas/tripleTriadRules.as)
(116 lines), [`tto/utils/TTOCore.as`](../../sources/src/tto/utils/TTOCore.as) (395 lines).
Read in the relevant parts: `tto/screens/BaseMatchScreen.as`, `tto/screens/playerPanel.as`,
`tto/screens/Board.as`, `tto/display/Tile.as`, `tto/display/Card.as`, `tto/utils/tools.as`.

**Not verified by execution.** The AIR client is abandoned (see
[04-PHASE-0-PREPARATION.md § Decisions taken](../migration/04-PHASE-0-PREPARATION.md#-decisions-taken-2026-07-25)),
so nothing here was confirmed by running the game. Every statement is a reading of source. Where
a reading is uncertain it says so; where behaviour depends on a call order I did not trace, it
is listed as a hazard in § 15 rather than asserted.

---

## 2. The rule set: 20 constants, 16 rules

`tripleTriadRules.as:9-30` declares 20 constants. They are not 20 rules.

| Constant | Kind | Flag it sets |
|---|---|---|
| `RULE_OPEN` (`'STR_OPEN'`) | **UI category label**, not a rule | — |
| `RULE_DEFAULT_OPEN` | default value | `OPEN_RULE` |
| `RULE_ALL_OPEN` | rule | `OPEN_RULE` |
| `RULE_THREE_OPEN` | rule | `OPEN_RULE` |
| `RULE_SUDDEN_DEATH` | rule | `SUDDEN_DEATH` (bool) |
| `RULE_RANDOM` | rule | `RANDOM` (bool) |
| `RULE_DEFAULT_ORDER` | default value | `ORDER` |
| `RULE_ORDER` | rule | `ORDER` |
| `RULE_CHAOS` | rule | `ORDER` |
| `RULE_REVERSE` | rule | `REVERSE` (bool) |
| `RULE_FALLEN_ACE` | rule | `FALLEN_ACE` (bool) |
| `RULE_SAME` | rule | `SAME` (bool) |
| `RULE_SAME_WALL` | rule | `SAME_WALL` (bool) |
| `RULE_PLUS` | rule | `PLUS` (bool) |
| `RULE_COMBO` | **dead constant** | none — see § 10 |
| `RULE_TYPE` (`'STR_TYPE'`) | **UI category label**, not a rule | — |
| `RULE_DEFAULT_TYPE` | default value | `TYPE_RULE` |
| `RULE_ASCENSION` | rule | `TYPE_RULE` |
| `RULE_DESCENSION` | rule | `TYPE_RULE` |
| `RULE_ELEMENTAL` | rule | `TYPE_RULE` |
| `RULE_SWAP` | rule | `SWAP` (bool) |
| `RULE_ROULETTE` | meta-rule | `ROULETTE` (bool) |

Note the two odd constants: `RULE_OPEN` and `RULE_TYPE` carry `STR_`-prefixed values rather
than `RULE_`-prefixed ones, because they are i18n keys for the *section headings* in the rule
picker (`PVPScreen.as:139`), not selectable rules.

**The rules object has 12 slots**, three of which are enumerations rather than booleans
(`tripleTriadRules.as:38-51`):

```
OPEN_RULE    : RULE_DEFAULT_OPEN | RULE_ALL_OPEN | RULE_THREE_OPEN
ORDER        : RULE_DEFAULT_ORDER | RULE_ORDER | RULE_CHAOS
TYPE_RULE    : RULE_DEFAULT_TYPE | RULE_ASCENSION | RULE_DESCENSION | RULE_ELEMENTAL
SUDDEN_DEATH : Boolean
RANDOM       : Boolean
REVERSE      : Boolean
FALLEN_ACE   : Boolean
SAME         : Boolean
SAME_WALL    : Boolean
PLUS         : Boolean
SWAP         : Boolean
ROULETTE     : Boolean
```

**This is the shape the Kotlin model should take** — three enums and nine booleans, not 20
booleans. Modelling it as a flat set of 20 flags would allow `ASCENSION` and `ELEMENTAL`
simultaneously, which the AS3 structure makes impossible by construction.

Usage counts across the codebase confirm the shape and reveal which rules carry weight:
`TYPE_RULE` 31 references, `ORDER` 25, `REVERSE` 22, `OPEN_RULE` 20, then `SUDDEN_DEATH`,
`FALLEN_ACE`, `SAME`, `SAME_WALL`, `PLUS` at 8-9 each, `SWAP` and `RANDOM` at 7, `ROULETTE` 5.

---

## 3. Where each rule lives

Only **8 of 16** rules are implemented in the rules engine. The rest are lifecycle or
presentation concerns, which is why a port that starts with `TTOCore` gets less than half the
behaviour.

| Rule | Implemented in |
|---|---|
| `REVERSE` | `TTOCore.as:177-201` (basic), `:220-256` (special), `:312-364` (combo) |
| `FALLEN_ACE` | `TTOCore.as:27-31`, `playerPanel.as:161-165` |
| `SAME` | `TTOCore.as:271-280` |
| `SAME_WALL` | `TTOCore.as:294-300`, `Tile.as:229-247` |
| `PLUS` | `TTOCore.as:282-291` |
| Combo | `TTOCore.as:308-373` — **unconditional**, § 10 |
| `ASCENSION` / `DESCENSION` | `TTOCore.as:38-45`, `BaseMatchScreen.as:330-356`, `playerPanel.as:153-178` |
| `ELEMENTAL` | `TTOCore.as:47-56`, `Board.as:52-65` |
| `OPEN_RULE` | `BaseMatchScreen.as:156-178` (presentation only) |
| `ORDER` / `CHAOS` | `BaseMatchScreen.as:388-393`, `PVEMatchScreen.as:185-187`, `:427` |
| `RANDOM` | `BaseMatchScreen.as:120-135` (deck selection) |
| `SWAP` | `BaseMatchScreen.as:218-235` (pre-match) |
| `SUDDEN_DEATH` | `PVEMatchScreen.as:63-67`, `:175`; `PVPMatchScreen.as:369`; base stub at `BaseMatchScreen.as:415-420` |
| `ROULETTE` | `tripleTriadRules.as:36-114` (rule generation) |

---

## 4. Board and turn model

**The board is 3×3 = 9 tiles**, indexed 0..8 (`Board.as:70`). Tiles hold `topTile`,
`rightTile`, `bottomTile`, `leftTile` references; a null reference means a wall
(`Tile.as:189-194`).

**Each player holds exactly 5 cards** (`playerPanel.as:155`, `:250` — hard-coded `i < 5`).

**Turn order is a pre-computed 10-entry array**, not a toggle
(`BaseMatchScreen.as:242`):

```actionscript
timeline = (pof.blueOrRed == 'blue')
    ? ["RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE"]
    : ["BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED","BLUE","RED"];
```

### The off-by-one is load-bearing

`turn` is declared `protected var turn:int` with no initialiser (`BaseMatchScreen.as:32`), so it
starts at **0**. `letsGetStarted` calls `nextTurn` without placing anything
(`BaseMatchScreen.as:253`), and `nextTurn` increments *before* reading the timeline
(`BaseMatchScreen.as:368-373`):

```actionscript
turn++;
if (turn == 10) { endGame(); }
else { if ('RED' == timeline[turn]) { ... } }
```

Consequences, all of which the port must reproduce or deliberately change:

1. **`timeline[0]` is never read.** It exists only to make the parity work out. When the coin
   says `blue`, index 0 holds `"RED"` and index 1 holds `"BLUE"` — so blue moves first, as
   intended, but only because the array is effectively 1-indexed.
2. **Placements happen on turns 1..9** — nine placements for nine tiles. `endGame()` fires when
   `turn` reaches 10, immediately after the ninth.
3. **The player who moves first places 5 cards; the other places 4.** Turns 1,3,5,7,9 versus
   2,4,6,8.
4. **The second player therefore ends with one card still in hand**, and that card still counts
   for its owner (§ 12). This is the standard Triple Triad asymmetry and it favours the second
   player: their fifth card can never be captured.

A cleaner port would use a turn index 0..8 and derive the colour from parity plus the coin
flip. That is a *behaviour-preserving* change and is recommended — but it must be written as a
deliberate deviation, because reading `timeline[turn]` with a 1-based `turn` is exactly the kind
of detail that gets "fixed" by accident and shifts who moves first.

---

## 5. Power model: two different ranges

This distinction is easy to miss and breaks the domain model if missed.

**Card powers are 1..10** and are stored as **hexadecimal digits** in the card data. `Card.as`
reads each side with `uint("0x" + _data.power[i])` (`Card.as:316-318`), so the literal `'A'` in
`cards.as` means 10. There is no 0 and no value above 10 in the source data — verified across
all 263 cards by [`extract_cards.py`](../../tools/extract_cards.py).

**Tile powers are 0..10.** `Tile` carries its own `topPow`/`rightPow`/`bottomPow`/`leftPow`,
computed from the card plus every active modifier, and clamped by
`tools.madmax` (`tools.as:74-76`):

```actionscript
public static function madmax(value:int):int {
    return Math.min(10, Math.max(0, value));
}
```

**The floor is 0, not 1.** Fallen Ace produces 0 directly, and Descension can drive a 1 down to
0. So:

- `Card.top/right/bottom/left` ∈ **1..10** — immutable card data
- `Tile.topPow/...` ∈ **0..10** — effective power, recomputed on placement and on every
  ascension phase

The current Kotlin `Card` enforces `POWER_RANGE = 1..ACE_POWER` in its `init` block, which is
right for cards and would be **wrong** if reused for the tile. Model the effective power
separately.

---

## 6. The capture pipeline

`TTOCore.applyRules(tile, color, checking)` (`TTOCore.as:23-91`) is the single entry point.
Order of operations, which is significant:

```
1. tile.color = color                                    (:25)
2. Copy card powers onto the tile, applying FALLEN_ACE    (:27-37)
       tile.pow[side] = (card.pow[side] == 10) ? 0 : card.pow[side]   if FALLEN_ACE
       tile.pow[side] = card.pow[side]                                otherwise
3. If ASCENSION or DESCENSION: tile.pow += card.modifier, clamped     (:38-45)
4. If ELEMENTAL: compute card.modifier from the tile element,
   then tile.pow += card.modifier, clamped                           (:47-56)
5. Branch:
   - checking == true  → return a flip count for the AI, no side effects  (:58-85)
   - checking == false → play sound, animate(), return 0                  (:86-90)
6. animate() resolves captures:                                       (:93-172)
       if (SAME || SAME_WALL || PLUS) specialRule()   else   basicRule()
7. After all flips, setTimeout(ascensionPhase, ...)                   (:171)
```

**Step 2 and 3 compose.** Fallen Ace zeroes a 10 *before* Ascension adds its modifier, so an
ace on a +1 board reads as 1, not 0. Order is fixed by the code and must be preserved.

**Step 6 is an either/or, not a sum.** When any of Same, Same Wall or Plus is active,
`basicRule` is never called — `specialRule` performs the basic comparison itself and tags those
results `type:'ZZ'` (`TTOCore.as:221`, `:232`, `:243`, `:254`). The literal `'ZZ'` is a sorting
hack: `cardToFlip.sortOn(['type'])` at `:304` orders `PLUS < SAME < SAME_WALL < ZZ`
alphabetically, and the de-duplication in `animate` (`:112`) keeps the first entry per tile. So
a card capturable both by Plus and by raw power is attributed to **Plus**, which is what drives
the animation and the combo chain. Port this as an explicit precedence, not as a string sort.

---

## 7. Basic rule and Reverse

`basicRule` (`TTOCore.as:174-206`). For each of the four neighbours, capture if the neighbour is
occupied, is a different colour, and loses the power comparison:

| | Normal | `REVERSE` |
|---|---|---|
| Capture when | `neighbour.facingPow < placed.pow` | `neighbour.facingPow > placed.pow` |

Facing sides pair as: placed `top` ↔ neighbour `bottom`; placed `right` ↔ neighbour `left`; and
symmetrically.

**Ties never capture, in either direction.** Both comparisons are strict, so
`REVERSE` is not a logical negation of the normal rule — equal powers hold under both. A test
must cover the tie case explicitly; a naïve port using `if (reverse) a > b else a <= b` is wrong.

The `horizon` boolean on each result (`false` for top/bottom, `true` for left/right) selects the
flip axis for the animation only. It carries no rules meaning.

---

## 8. Same, Same Wall and Plus

`specialRule` (`TTOCore.as:211-306`).

For each **occupied** neighbour, two values are computed (`:216-259`):

```
same.value = neighbour.card.facingPow - placed.card.pow      → 0 means equal
plus.value = neighbour.card.facingPow + placed.card.pow
```

**These use raw card powers, not tile powers.** `tile.topTile.card.bottomPow` and
`tile.card.topPow`, never `tile.topPow`. The author left a comment saying this is deliberate
(`:215`: *"need to verify with card digit (without modifiers) instead of tile power values"*).
The consequence is that **Same and Plus ignore Elemental and Ascension modifiers while basic
capture respects them.** See § 15.4 — this is probably a defect.

### Gate

```actionscript
if (same.length > 1) {   // :261
```

At least **two occupied neighbours** are required before Same, Plus *or Same Wall* is evaluated.
For Same and Plus this is correct — both need two matches. For Same Wall it is a defect
(§ 15.2).

### Same

Two neighbours `i` and `j` with `same[i].value == 0 && same[j].value == 0` → both are captured,
each one only if it is a different colour (`:271-280`). Both checks are independent, so a Same
between one enemy card and one of your own captures only the enemy card, and still counts as a
Same for combo purposes.

### Plus

Two neighbours with `plus[i].value == plus[j].value` (`:282-291`) — the sums match, the
individual powers need not. Same colour filtering as above.

### Same Wall

```actionscript
if (same[i].value == 0 && same[i].tile.card.color !== COLOR) {
    if (tile.onSameWall) { ... }                                    // :294-300
}
```

`onSameWall` is true when **any** side of the placed tile faces a wall *and* the placed card's
power on that side is 10 (`Tile.as:229-247`):

```actionscript
public function get topSameWall():Boolean {
    return (!_topTile && _topPow == 10) ? true : false;
}
```

So the wall is treated as a card showing A — the correct interpretation of the rule. Note it
reads `_topPow`, the **tile** power, so Fallen Ace and the type rules do affect whether a wall
counts: Fallen Ace turns the A into 0 and Same Wall can never trigger. That interaction looks
intentional and is worth a test.

One matching neighbour plus a qualifying wall is logically two "sames" and should fire — but the
`same.length > 1` gate blocks it. See § 15.2.

### Loop structure

The `while i / while j` nest (`:268-302`) evaluates every unordered pair, but the Same Wall check
sits in the outer loop, so it runs once per `i` — including when `same.length` is 2 and the inner
loop has already run. Cards can therefore be pushed to `cardToFlip` more than once; the
`alreadyFlipped` guard in `animate` (`:112`) absorbs it. Harmless, but do not port the duplicate
pushes: a set is the right structure.

---

## 9. Elemental, Ascension, Descension

`TYPE_RULE` is a single slot, so **these three are mutually exclusive.**

### Elemental

Tile elements are assigned at match start (`Board.as:52-65`): each of the 9 tiles independently
gets an element with probability ≈½ (`tools.rand(1)`), drawn from
`["earth","fire","holy","ice","lightning","poison","water","wind"]`. Tiles never assigned keep
the initial value `"none"` (`Tile.as:31`).

Modifier computation (`TTOCore.as:47-56`):

| Condition | `card.modifier` |
|---|---|
| `card.type == tile.element` | **+1** |
| `tile.element !== "none"` and `card.type !== tile.element` | **−1** |
| otherwise | 0 |

`card.data.type` and `card.type` are the same accessor (`Card.as:312-314`), so the two different
spellings across `:48` and `:49` are cosmetic, not a bug.

**Untyped cards take −1 on any elemental tile.** A card with `type == null` fails the first test
and passes the second, so it is penalised. **This is intended** — an element-less card is
levelled down on an elemental tile exactly like a card of the wrong element; only a match gains
+1. Confirmed 2026-07-26, see § 15.5.

The eight elements here are the FF8 element set. They share the `type` field with the four FF14
tribes (`beast`, `garlean`, `primals`, `scions`), which is why the Kotlin `CardType` enum has 12
entries covering both families. Elemental is only offered in the `ff8_` rule pool
(`tripleTriadRules.as:58`) and Ascension/Descension only in the `ff14_` pool (`:56`) — the data
model unifies them but the rules do not mix.

### Ascension and Descension

A board-wide tally per type, held on the match screen as
`ascensionByType = {beast:0, garlean:0, primals:0, scions:0}` (`BaseMatchScreen.as:350`).

After every placement, `ascensionPhase` runs (`BaseMatchScreen.as:330-356`):

- **Ascension**: if the placed card has a type, `ascensionByType[type] += 1`
- **Descension**: if the placed card has a type, `ascensionByType[type] -= 1`
- Then both players' hands are refreshed via `applyAscension`

`applyAscension` (`playerPanel.as:153-178`) recomputes from scratch for each of the 5 cards:
set `card.modifier` from the tally, then recompute the tile powers from the **raw** card powers,
re-apply Fallen Ace, and add the modifier. Because it rebuilds rather than accumulates, it is
idempotent — which is what keeps the repeated application from compounding.

**The modifier applies to cards in hand as well as on the board.** `applyAscension` walks all 5
cards and only touches tile powers `if (card.tile)`. So a card's displayed strength changes
while you are still holding it — the tally is global, not positional.

**Modifier storage is an AS3 curiosity you must not reproduce.** `Card.modifier` has no backing
field: the setter writes into a Starling `TextField` and the getter parses it back
(`Card.as:102-115`):

```actionscript
public function get modifier():int { return int(_modifier.text); }
```

The display object *is* the model. In Kotlin this is an `Int` on the domain object, and the
label is derived from it.

---

## 10. Combo is unconditional

**`RULE_COMBO` is a dead constant.** It appears exactly twice in the whole codebase: its own
declaration (`tripleTriadRules.as:23`) and a help-screen entry (`HelpScreen.as:83`). No
`_RULES.COMBO` is ever written or read — verified by enumerating every `RULES.*` access in the
codebase, which yields 12 distinct flags and no `COMBO` among them. `roulette()` cannot select
it either (`tripleTriadRules.as:63-109` has no `RULE_COMBO` case).

**Therefore: combo always fires when Same, Same Wall or Plus captures a card.** It is not
separately toggleable. Any port that models Combo as a rule flag is modelling something the
original does not have.

`comboRule` (`TTOCore.as:308-373`) recurses from each specially-captured tile, applying the
**basic** power comparison (respecting `REVERSE`) to that tile's neighbours, pushing results
into `enqueue[bounce]` and recursing with `bounce+1`. `tileComboted` prevents revisiting a tile,
which bounds the recursion at 9.

The author's own `// TODO : correct combo` sits at `:310`. The structural problem is that
`enqueue` and `tileComboted` are allocated once per `specialRule` call (`:265-266`) and shared
across every starting tile, and `enqueue` is returned **by reference** and stored as the
`waveEffect` of every pushed flip. All flips therefore share one mutating array, and `animate`
walks it repeatedly (`:138-160`) relying on `alreadyFlipped` to avoid double-flipping. The
result is that the *set* of combo captures is probably right while the *wave grouping* — which
drives the staggered 400 ms animation timing at `:152` — is not.

**Recommendation for the port:** implement combo as a breadth-first propagation from the set of
just-captured tiles, with an explicit visited set and a wave index. Test the *set* of captured
cards against the AS3 reading, and treat wave grouping as new work rather than a port.

---

## 11. Roulette

> ⚠️ **CORRECTED 2026-08-02: it augments a rule set rather than generating one.** The only live
> call is `RULES = tripleTriadRules.roulette(Game.PROFILE_DATAS.MODE, RULES)`, gated on
> `RULES.ROULETTE` (`BaseMatchScreen.as:64-66`) — so an opponent declaring `RULE_ROULETTE` plays
> with everything it already declared **plus** the draw, and the roulette can never take a rule
> away. Eleven of the 85 shipped opponents use it. The `gameRules = null` branch, which builds a
> default set and is the only place `ROULETTE:true` is assigned, is never reached.
>
> **And the two pools below are the legal rule set per collection**, not merely roulette
> candidates: no `ff14` opponent declares Elemental or Same Wall, and no `ff8` one declares
> Ascension, Descension, Reverse, Fallen Ace, Order, Chaos or Swap. All 85 agree with the pools
> exactly, which is what makes the asymmetry a rule of the game rather than an accident of two array
> literals.

`tripleTriadRules.roulette(mode, gameRules)` (`tripleTriadRules.as:36-114`) adds one to three random
rules to a rule set. Two candidate pools:

- **`ff14_`** — 13 rules (`:56`): All Open, Ascension, Chaos, Descension, Fallen Ace, Order,
  Plus, Random, Reverse, Same, Sudden Death, Swap, Three Open
- **`ff8_`** — 8 rules (`:58`): All Open, Elemental, Plus, Random, Same, Same Wall, Sudden
  Death, Three Open

Note the asymmetry: **Same Wall and Elemental are FF8-only; Ascension, Descension, Reverse,
Fallen Ace, Order, Chaos and Swap are FF14-only.** Only All Open, Three Open, Plus, Random,
Same and Sudden Death are common to both.

Iteration count is `1 + tools.rand(2)` → **1 to 3** rules (`:53`), and each is drawn
independently with replacement, so drawing the same rule twice yields fewer than 3 effective
rules. Two rules that share a slot overwrite each other — draw `RULE_ORDER` then `RULE_CHAOS`
and only Chaos survives.

**The randomness is non-uniform**, and this is a real defect rather than a nitpick.
`tools.rand(to)` is `Math.round(Math.random() * to)` (`tools.as:78-80`), which returns
`0..to` inclusive but assigns **half the probability mass** to the endpoints, because only half
an interval rounds to 0 and half to `to`. Combined with `possibleRules[tools.rand(length - 1)]`
(`:62`), the first and last rules in each pool are half as likely as the others. In the `ff14_`
pool that penalises **All Open** and **Three Open**; in `ff8_`, **All Open** and **Three Open**
again. The same skew affects Chaos card selection (`BaseMatchScreen.as:427`), random tile choice
in `autoPlay` (`:428`) and Random deck building (`:130`).

Use a uniform `Random.nextInt(size)` in the port. This is a bug fix, not a behaviour change
worth preserving — but it does mean generated rule sets will not match the original's
distribution, so do not write a test that asserts otherwise.

---

## 12. Scoring and end of match

**Score = number of cards of each colour, counted across both hands** — not across the board.

`playerPanel.getScores()` (`:239-245`) iterates that panel's cards and tallies by colour;
`BaseMatchScreen.updateScores()` (`:315-321`) sums the two panels:

```actionscript
var globalScores:Object = { BLUE:(bScores.BLUE + rScores.BLUE), RED:(bScores.RED + rScores.RED) };
```

Because each panel always holds exactly 5 cards whether played or not, **the total is always
10**, and:

- **Cards still in hand count for their owner.** The second player's unplayed fifth card scores.
- A draw is 5-5.
- Scores are recomputed at the start of every `nextTurn` (`:366`), so the display tracks
  captures as they happen.

`endGame()` fires when `turn` reaches 10 (`BaseMatchScreen.as:370`), i.e. after the ninth
placement. It is abstract in the base class; `PVEMatchScreen.endGame()` (`:45`) handles rewards,
achievements and statistics — out of scope here, but note it reads and mutates
`Game.PROFILE_DATAS`, which couples the rules engine's exit path to the save file.

### Sudden Death

On a draw with `SUDDEN_DEATH` active (`PVEMatchScreen.as:63-67`), the intent is to replay with
each player taking the cards they *ended up owning*: `suddenDeathDispatcher` collects
`getCardIdsByColor()` from both panels and concatenates by colour
(`BaseMatchScreen.as:415-420`). The rematch path then skips deck selection and the coin flip,
reusing the previous `timeline` (`:116-118`, `:238-243`).

The dispatch is an event rather than a return value, which shapes how the port should model a
rematch — see § 15.1.

---

## 13. Lifecycle: the pre-match phase chain

Six rules are consumed before the first card is placed. The chain is a `setTimeout` cascade,
each link skipping its delay when the rule is off (`BaseMatchScreen.as:113-245`):

```
deckSelectionPhase   RANDOM        :120-135   build a random 5-card hand from the collection
  → openPhase        OPEN_RULE     :156-178   reveal opponent cards
  → orderPhase       ORDER         :181-195   animation only; enforcement is at :388-393
  → reversePhase     REVERSE       :197-206   animation only
  → fallenAcePhase   FALLEN_ACE    :208-216   animation only
  → swapPhase        SWAP          :218-235   exchange one random card per side
  → pileOuFace       —             :237-245   coin flip, builds the timeline
  → letsGetStarted   —             :247-254   → nextTurn
```

**Open.** `bluePlayer.openRule` is set to `RULE_ALL_OPEN` on **both** branches
(`:172`, `:176`) — the local player always sees their own hand. Only `redPlayer.openRule`
varies. So Open is purely about revealing the *opponent's* cards, and Three Open's "reveal
three of five" logic lives in `playerPanel`, not here.

**Random.** Builds a 5-card hand by splicing from the player's whole collection
(`:124-135`). Note `if (randomizer.length == 5) randomCards = randomizer;` — with exactly five
cards owned there is no randomisation at all. With fewer than five the hand fills with
duplicates (§ 15.7).

**Swap.** One random card from each side, exchanged by id (`:224-229`). Unconditional when the
rule is on — there is no "if the cards differ" guard.

**Order and Chaos** are enforced at turn start, not here: `RULE_ORDER` forces the first
remaining card (`:390`, `:427`), `RULE_CHAOS` a random one (`:392`, `PVEMatchScreen.as:187`).
With `RULE_DEFAULT_ORDER` the player picks freely (`:388-389`).

---

## 14. AI evaluation hook

`applyRules(tile, color, checking = true)` (`TTOCore.as:58-85`) is a **dry run** that returns a
flip count instead of animating. Two things about it matter for the port:

1. **It is not side-effect free.** Steps 1-4 of § 6 have already run by the time the `checking`
   branch is reached — `tile.color` is overwritten, tile powers are recomputed, and under
   Elemental `card.modifier` is mutated. The AI cannot evaluate a candidate move without
   corrupting board state, and `PVEMatchScreen.as:212` exists to patch one instance of the
   damage (`if (RULES.TYPE_RULE == RULE_ELEMENTAL) tile.card.modifier = 0;`).
2. **It counts distinct tiles, not flip events** (`:66-80`), walking the combo waves and
   de-duplicating by `tile.id`.

In the port, make evaluation a pure function of `(board, hand, rules, candidate)` returning a
capture set. That removes the need for the reset patch entirely and makes the AI testable.

---

## 15. Defects and ambiguities

Nine items. Each says whether to reproduce or fix, because "faithful" and "correct" diverge here.

### 15.1 Sudden Death is dispatched, not resolved — **design decision for the port**

`BaseMatchScreen.suddenDeathDispatcher()` (`:415-420`) is an empty stub whose body is commented
out, marked `// need an override`. All four concrete match screens do override it —
`PVEMatchScreen.as:175`, `PVPMatchScreen.as:369`, `CCGroupMatchScreen.as:265`,
`GSGroupMatchScreen.as:266` — each dispatching a `'sudden_death'` event carrying
`BLUE_CARDS`/`RED_CARDS` rebuilt from final ownership, `SUDDEN_DEATH_NEXT:true` and the existing
`timeline`. So the rule works; only the base class is inert.

The consequence for the port is architectural rather than a bug: **a rematch is a new screen
instance driven by an event, not a loop inside the match.** State that must survive the
transition is exactly the four fields in that payload. In Kotlin this should be an explicit
`MatchOutcome.SuddenDeath(blueCards, redCards, timeline)` return value rather than an event —
the AS3 shape exists because Starling screens cannot easily return values.

### 15.2 Same Wall cannot fire with a single neighbour — **fix**

The `same.length > 1` gate (`:261`) requires two occupied neighbours before any special rule is
evaluated, including Same Wall. But Same Wall's whole purpose is to let a wall stand in for the
second match. A corner tile with an A facing each wall and exactly one adjacent enemy card
satisfies the rule and does not fire.

Reproducing this would be reproducing a bug that makes a selectable rule near-inoperative in
early game states. Gate Same Wall on `same.length >= 1` instead.

### 15.3 Combo wave grouping is unreliable — **fix, and treat as new work**

`// TODO : correct combo` at `:310`, and the shared-mutable-`enqueue` analysis in § 10. The set
of captured cards is likely correct; the wave partitioning that drives animation timing is not.
Port the capture set faithfully and rebuild the wave logic.

### 15.4 Same and Plus ignore power modifiers — **decide deliberately**

`specialRule` computes its sums and differences from raw card powers while `basicRule` uses
modified tile powers (§ 8). Under Elemental or Ascension the two disagree: a card whose
effective power is 6 is treated as its printed 5 for Same/Plus purposes.

In FF14 the modified values are used. The author's comment at `:215` reads like uncertainty
rather than a decision. **This changes outcomes in real games** — it is not cosmetic. Pick a
side, write it down, and test it.

### 15.5 Untyped cards are penalised by Elemental — ~~verify~~ **confirmed correct, reproduce**

`card.type == null` on an elemental tile yields −1 (§ 9). This was listed as an open question
because the code was unambiguous while the intent was not. **Resolved 2026-07-26 by the project
owner: it is correct.** A card with no element is levelled down on an elemental tile, same as a
card of the wrong element. Only a matching element gains +1.

So `TTOCore.as:47-56` is right as written and the port reproduces it. No decision needed and
nothing to switch.

### 15.6 `tools.rand` is non-uniform — **fix**

`Math.round(Math.random() * to)` halves the probability of both endpoints (§ 11). Affects rule
generation, Chaos card choice, random tile choice and Random deck building. Use
`Random.nextInt(size)`.

### 15.7 `Random` deck building duplicates cards on a small collection — **fix**

`BaseMatchScreen.as:129-132`. The loop fills five slots by splicing out of a copy of the
collection, but the `randomizer.length > 1` guard at `:130` falls back to
`randomizer[0]` **without splicing** once one card is left. The loop still terminates —
`randomCards.push` grows the target — but it pushes the same id repeatedly.

- collection of 1 card → a hand of the same card five times
- collection of 0 cards → `randomizer[0]` is `undefined`, pushed five times
- collection of 2-4 cards → the last remaining card fills every leftover slot

Only `randomizer.length == 5` takes the clean path (`:126-127`), and a collection of 6+ works
correctly. So the bug window is 0-4 cards — a new profile. Guard the precondition and draw
without replacement.

### 15.8 Modifier application has three independent paths — **hazard, not confirmed**

`TTOCore.applyRules:38-45`, `Card.set tile:359-368` and `playerPanel.applyAscension:172-175` all
add `card.modifier` to tile powers. `applyAscension` recomputes from raw values and is
idempotent, which probably masks the overlap. Whether `Card.set tile` can double-apply depends
on its ordering relative to `applyRules`, **which I did not trace.**

In the port, compute effective power in exactly one place — a pure function of card, tile and
rules. Then the question cannot arise.

### 15.9 Ties never capture under Reverse either — **reproduce**

Both comparisons are strict (§ 7). This is correct Triple Triad behaviour and is called out only
because the obvious refactor breaks it.

---

## 15b. Implementation status

**Everything described here is implemented**, in
[`shared/src/commonMain/kotlin/com/tripletriad/model/`](../../shared/src/commonMain/kotlin/com/tripletriad/model/)
— `GameRules`, `Board`, `Power`, `RulesEngine`, `Match`, `MatchState` from Phase 1, and `Roulette`,
`MatchSetup`, `MatchAi` from **Phase 3, 2026-08-02** — with the § 16 matrix as
[`RulesEngineTest`](../../shared/src/commonTest/kotlin/com/tripletriad/model/RulesEngineTest.kt),
`MatchSetupTest`, `MatchAiTest` and `RouletteTest`. `RulesEngineTest` alone is 37 tests; all run on
both targets (`desktopTest` and `testAndroidHostTest`), 0 failures.

How each § 15 item was resolved:

| Item | Resolution in the port |
|---|---|
| 15.1 Sudden Death dispatch | **Resolved as a return value**, not an event: `MatchState.outcome()` returns `MatchOutcome.SuddenDeath` and `MatchPreparation.prepareRematch` builds the follow-up match. § 16 item 32 covers it |
| 15.2 Same Wall one-neighbour gate | **Fixed** by default; `RulesEngineOptions.FAITHFUL` restores the AS3 behaviour |
| 15.3 Combo wave grouping | **Rewritten** as breadth-first propagation with an explicit visited set and wave index |
| 15.4 Same/Plus power basis | **Fixed** by default (effective powers); switchable via `RulesEngineOptions.specialPowerBasis`, and both behaviours are pinned by a test |
| 15.5 Untyped cards under Elemental | **Reproduced** (−1) — confirmed correct by the project owner, not a compromise |
| 15.6 Non-uniform `tools.rand` | **Fixed** everywhere randomness landed: the roulette draw and its iteration count, Three Open's three slots, the Random hand, the Swap picks and `Board.elements`. All use a uniform `Random`, so **no test asserts a distribution** — see § 11 |
| 15.7 Random deck duplicates | **Refused rather than reproduced**: `MatchPreparation.randomHand` requires at least five cards and draws without replacement. It also drops the `length == 5` special case, which left the hand in collection order and so decided which card `RULE_ORDER` forces |
| 15.8 Three modifier write paths | **Cannot arise**: effective power is one pure function |
| 15.9 Strict comparisons both ways | **Reproduced**, and it is the case the mutation test proves is covered |

All of it is now implemented. What was outstanding when this section was first written — roulette
generation, the pre-match phase chain, Order/Chaos enforcement, Sudden Death, the AI and the match
state machine — landed in Phase 1 (`MatchState`, Order, Chaos, scoring, outcome) and Phase 3
(`Roulette`, `MatchSetup`, `MatchAi`).

Three things this document says are worth correcting or extending against the source:

1. **§ 11 describes the roulette as generating a rule set. It augments one.** The only live call
   passes the opponent's own rules in and reassigns the result (`BaseMatchScreen.as:64-66`), so an
   opponent declaring `RULE_ROULETTE` plays with everything it declared *plus* one to three draws.
   The `gameRules = null` branch that builds a fresh set is never reached.
2. **The two roulette pools are the legal rule set per collection**, not merely candidate lists. No
   `ff14` opponent declares Elemental or Same Wall and no `ff8` one declares Ascension, Descension,
   Reverse, Fallen Ace, Order, Chaos or Swap — all 85 agree with the pools exactly.
3. **§ 17 says "AI strategy" is not covered, and the AI turns out to be one function.**
   `PVEMatchScreen.AI` (`:182-254`) scores every remaining card against every free cell by capture
   count and a defensive `cover` sum, then picks at random among the best. `NPC.difficulty` never
   reaches it. `BaseMatchScreen.opponentPhase()` is an empty stub, and `autoPlay()` — a uniformly
   random move — is the human's timer-expiry fallback, not the opponent. See
   [07-PHASE-3-CORE-LOGIC.md](../migration/07-PHASE-3-CORE-LOGIC.md) § What was built for the one
   place `MatchAi` departs from it.

---

## 16. Test matrix for the port

These are the cases that distinguish a correct engine from a plausible one. All are pure
functions of board state — no UI, no coroutines, and they can all be written before any board
renders.

**Basic capture**
1. Higher power captures; lower does not
2. **Equal powers do not capture** — both normally and under Reverse
3. Reverse inverts strictly (`>` not `>=`)
4. Own-colour neighbours are never captured
5. Empty neighbours and walls are skipped
6. A placement touching four occupied enemy tiles can capture all four

**Fallen Ace**
7. A 10 becomes 0 and loses to a 1
8. Fallen Ace + Ascension +1: the ace reads 1, not 0
9. Fallen Ace disables Same Wall on that side (`_topPow == 10` no longer holds)

**Same / Plus / Same Wall**
10. Same fires on two zero-differences; not on one
11. Same captures only the enemy card of a mixed pair, and still triggers combo
12. Plus fires on equal sums with unequal powers (3+7 and 5+5)
13. Same Wall: wall side with a 10, one matching neighbour — **currently fails, § 15.2**
14. Same Wall with Fallen Ace active: does not fire
15. Precedence: a card capturable by both Plus and raw power is attributed to Plus
16. Same/Plus arithmetic under Elemental — pins down § 15.4 whichever way it is decided

**Combo**
17. A Same-captured card whose powers beat its own neighbour propagates
18. Combo never revisits a tile (no infinite recursion)
19. Combo respects Reverse
20. Combo does not capture cards already owned by the placing player
21. Combo fires with no Combo flag set — it is unconditional

**Type rules**
22. Elemental: matching type +1, different type −1, `"none"` tile 0
23. Elemental: untyped card on an elemental tile — pins down § 15.5
24. Ascension: tally increments per typed card placed; all cards of that type gain, **including
    those still in hand**
25. Descension: symmetric decrement
26. Clamping: modifiers cannot push effective power below 0 or above 10
27. `TYPE_RULE` is exclusive — Ascension and Elemental cannot both apply

**Turn and scoring**
28. Nine placements fill the board and end the match
29. First player places 5, second places 4
30. Score counts unplayed cards for their owner; the total is always 10
31. A 5-5 result is a draw
32. Sudden Death rebuilds hands from final ownership: each side takes the cards it owned at
    the 5-5 draw, and the turn order carries over unchanged — `MatchSetupTest`

**Rule generation** — all three in `RouletteTest`
33. Roulette produces 1 to 3 rules from the correct per-mode pool. Note *draws*, not rules: the
    pool is sampled with replacement, so a repeat or a slot collision yields fewer
34. Roulette never produces an FF14-only rule in `ff8_` mode, or vice versa
35. Rules sharing a slot overwrite rather than accumulate

**Pre-match chain** — `MatchSetupTest`
36. A Random hand is five distinct cards from the collection, not from the deck
37. Swap exchanges exactly one card each way, and the swapped card takes its receiver's colour
38. Three Open reveals exactly three, and the same three stay revealed as the hand empties
39. The coin flip decides who moves first; a rematch has none and keeps the previous order
40. Board elements are rolled only under Elemental
41. Every applicable announcement runs, in source order — not just the first

**AI** — `MatchAiTest`
42. Cover counts open flanks only: a wall or an occupied neighbour contributes 10
43. Cover reads effective powers, and Reverse inverts what counts as safe
44. Evaluating a candidate does not mutate the state — the defect § 14 describes cannot arise
45. The placement capturing the most is chosen; a tie is broken at random
46. With nothing to capture, the safest square from the sixth placement on, a coin toss between
    safest and most exposed before that
47. Two AIs play any rule set the roulette can produce to a complete, legal match

---

## 17. What this document does not cover

- ~~**AI strategy.**~~ **Covered as of 2026-08-02**, and it is one function.
  `PVEMatchScreen.AI` (`:182-254`) scores every remaining card against every free cell by capture
  count plus a defensive `cover` sum, sorts by both, and picks at random among those matching the
  best capture count — so the cover sort has no effect except when nothing captures. It looks one
  move ahead. **`NPC.difficulty` never reaches it**: the field is read only to order the opponent
  list, so every opponent plays identically. `BaseMatchScreen.opponentPhase()` is an empty stub with
  its body commented out, and `autoPlay()` — a uniformly random move — is what a *human* gets when
  their clock runs out, not what the opponent does. The per-NPC rule sets in `NPCs.as` are extracted
  by `tools/extract_npcs.py` (Phase 2). See
  [07-PHASE-3-CORE-LOGIC.md](../migration/07-PHASE-3-CORE-LOGIC.md) § What was built.
- **Rewards, achievements, statistics.** `endGame()` mutates `Game.PROFILE_DATAS` extensively.
  Out of scope, and a coupling worth breaking in the port.
- **Three Open's selection logic**, which lives in `playerPanel` rather than the rule chain.
- **Animation timing.** The 400 ms combo stagger and the `justAsec` accumulator
  (`TTOCore.as:95`, `:152-154`) are presentation; Phase 6 concerns.
- **Tutorial and Crystal Cup variants.** `TutorialScreen` and `CCGroupMatchScreen` carry their
  own rule handling, including the only working Sudden Death implementation.
- **Execution verification.** Nothing here was confirmed by running the AS3 client (§ 1).

---

## 18. Related

- [dependency-matrix.md](./dependency-matrix.md) — where these classes sit in the coupling graph
- [api-mapping.md](./api-mapping.md) — AS3 → Kotlin API equivalences
- [testing-strategy.md](../development/testing-strategy.md) — how the § 16 matrix should be
  organised
- [07-PHASE-3-CORE-LOGIC.md](../migration/07-PHASE-3-CORE-LOGIC.md) — the phase that consumes
  this document
