# Phase 6: Animations - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 6 - Animations
- **Duration**: the original 3-week budget assumed 24 pieces of work. It is not 24 — see below
- **Status**: re-scoped 2026-08-08. **All twenty-four classes are done or accounted
  for.** The match is animated end to end; the inventory reveals what it unlocks; the
  tutorial speaks. One class, `Mogu`, turned out to be dead code — see below
- **Version**: 2.0
- **Last Updated**: 2026-08-08
- **Prerequisites**: Phases 1-5

---

## 🎯 The finding that re-scoped this phase

**`sources/src/tto/anims/` holds 24 classes. Nineteen of them are the same animation.**

They average 56 lines and each one is: load a texture, tween it in, hold, tween it out, dispose.
What differs between `SameAnim` and `ReverseAnim` is a six-digit texture id and a duration. The
earlier version of this document read that directory as 24 pieces of work and budgeted three
weeks for it. It is **four motion shapes and a table**, plus four animations that genuinely differ.

That table is [`MatchBanner`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/MatchBanner.kt)
— one enum entry per caption, carrying its texture id, its motion and its three durations,
transcribed from the AS3 tween it came from.

### Reading an AS3 tween pair

Worth recording once, because getting it wrong changes every duration in the table. The exit tween
is created inside `predispose()`, which is the **entry's** `onComplete` — so the exit's `delay` is
measured from the moment the entry finishes and is therefore the *hold*. The entry's own `delay`,
where it has one, is a wait before anything appears at all.

Read as if both delays ran from one clock, `ComboAnim` appears to fade out before it fades in. Read
correctly, it waits 0.8s for the Same or Plus caption that caused it to clear. Two banners were
initially transcribed with the wrong hold because of this.

---

## ✅ What shipped

### The twenty captions

| Group | Banners |
|---|---|
| Flow | Start, Blue Turn, Red Turn, Blue Win, Red Win, Draw |
| Rules, announced before the match | Random, All Open, Three Open, Order, Chaos, Reverse, Fallen Ace, Swap |
| Rules, announced when they fire | Same, Plus, Combo, Ascension, Descension, Sudden Death |

Four motion shapes cover all twenty — zoom, zoom-and-fade, slide, and slide-off-an-edge — plus
Sudden Death's bounced tilt, which is the one variant that earns its own case.

### The captions are localised text images

Not a detail that was anticipated. Each banner is a **picture of a word**, 600×90, and the asset
tree carries a full set per language: 20 ids × 4 locales = 80 files, 1.4 MB, imported by
[`tools/import_rule_banners.py`](../../tools/import_rule_banners.py). That is what makes
`BannerArt` a per-locale cache rather than a single map — sharing one would show the previous
language's captions until the app restarted.

Eleven of the twenty Japanese captions are byte-identical to the English ones: the original's
Japanese UI keeps START, PLUS, SAME, COMBO and the turn and outcome words in Latin script. French
is the only set that differs on all twenty, which is why the per-locale test asserts against it.

### The unlock reveal

`UnlockCardAnim` plays in exactly one branch of `InventoryScreen.useBtnHandler` (`:236-245`): a
**card item** being used, which is the moment a card enters the collection. Opening a pack does
not play it, because a pack yields another bag item rather than a card. That distinction is easy
to lose — `PackOpened` carries a card id too, so "play it whenever Use produces one" would show
off a card the player does not own yet — and it is asserted.

The card is drawn half again as large as a card ever is elsewhere (`scaleX: 1.5`), which is the
original saying that this one is a prize rather than a game piece.

### The coin flip

`PileOuFace` is the one pre-match animation that is not a picture of a word: three card backs,
blue or red, and the majority takes the first move. It **shows the player a result** rather than
announcing a rule, so its rolls come from the `CoinFlip` the model already drew — drawing a fresh
one in the view would show a result contradicting whose turn it then is.

It plays through the same queue as the captions, between Swap and Start, because that position is
load-bearing: Start announcing a match whose first player has not been drawn yet is the wrong
order, and appending the flip to the end is exactly the shortcut that produces it.

### The sequencing, which is the part with decisions in it

The motion is transcription and can be checked by eye. *When* a caption plays is a decision, and it
is the one that fails invisibly — a missing Same reads as a dropped frame, and a Same played three
times for three flipped cards reads as a stutter.

- **The pre-match chain** comes from `MatchSetup.intro`, which Phase 3 already built: Random → Open
  → Order/Chaos → Reverse → Fallen Ace → Swap → coin flip → Start. The first implementation
  re-derived this list from `GameRules` and was wrong in one case — a sudden-death rematch is
  played under the same Random rule but its hand was *not* re-dealt, so the rules say announce it
  and the setup says do not. Reading the setup is both less code and the only correct source.
- **Per placement**: the capture captions, then Ascension or Descension, then the turn change or
  the result. One caption per *rule*, not per flipped card.
- **A sudden-death draw says both** — the draw, then that it is not over — and its rematch
  re-announces the rules but not the deal, since the hands were not re-dealt.
- **Elemental has no caption at all.** `openPhase` paints the board and announces nothing. Neither
  do Same, Same Wall and Plus up front; they announce themselves when they fire.

Everything above is derived from `MatchState` rather than fired from the placement handler, because
the handler is not the only thing that plays a card — the turn timer auto-plays and the opponent
plays on its own, and a caption wired to one call site would be missing from the other two.

### A defect the animations exposed: the turn clock was running under the intro

The original arms both players' clocks in `nextTurn`, which `letsGetStarted` calls **after** the
whole phase cascade (`BaseMatchScreen.as:250`, `:377-387`). This port started the clock when the
match screen composed, which nothing revealed while there was no intro to run against it. With the
announcements in place, the player's thirty seconds began behind the Start banner — a few seconds
of every first turn silently spent, and, in a test with a deliberately short limit, a match that
played itself out before the intro had finished.

The clock now waits for the intro. **Input does not**: the captions are drawn over the board
without consuming touches, deliberately, so a player who already knows the rules can open with a
card while Reverse is still on screen. What they must not do is lose part of their first turn to
an announcement.

### Pacing replaced a guess with a sum

`PVEMatchScreen` waits `1000 + rand(4) * 1000` before the AI moves. That range was never thinking
time; it was cover for the `setTimeout` cascade announcing the turn and the captures. Now that each
caption states its own duration, the cover is **added up** rather than guessed at: the opponent
waits for what the last placement earned, plus a short fixed pause.

---

## 📝 What remains

### `Mogu` is dead code

**Nothing constructs it.** `Mogu.as` is the only file in the source tree that names it, and its
asset — `-mogu_anime_en.xml`, `-mogu_anime_en_01.psd` — carries the leading hyphen this tree uses
to mark disabled files. It is also the only animation in the directory that extends
`flash.display.MovieClip` rather than a Starling display object, so it could not have been added
to the stage everything else lives on even if something had tried.

It is a leftover from a pre-Starling iteration, and it is the third such find in this port after
`RULE_COMBO` and the `ElementalAnim` that never existed. **Do not plan work for it.**

### `TalkBubble` now has its caller

It was built a day ahead of one, as the dependency `TutorialScreen` and `TutorialRematchPanel` were
listed in Phase 4 as blocked on. **Both of those shipped on 2026-08-08**, and the tutorial is what
the bubble was for: nine lines over a scripted match, spaced 6.1s apart exactly as
`setTimeout(talk, 6100, n)` spaced them. See
[08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) § The tutorial.

Its remaining two callers are the group ladders, which are Phase 4 work still to come.

Unlike the rule captions it holds **live text over one language-independent frame**, which is why
there is one `talk.png` rather than four sets of twenty. The tutorial's own nine sentences turned
out to be untranslated in the original too — Flash literals with no `gettext` and no bundle key —
so they enter through this port's `app-*` bundle instead.

### The scoreboard

| Class | State |
|---|---|
| The 19 caption classes | Done — one table, four motion shapes |
| `PileOuFace` | Done, driven by the model's own coin flip |
| `UnlockCardAnim` | Done, wired to the inventory's Use |
| `TalkAnim` | Done as `TalkBubble`, speaking the tutorial's nine lines |
| `Mogu` | **Dead code.** Nothing to port |

Card motion is not in `tto/anims/` at all — the original keeps it on `Card` — and is now done in
both directions:

- **The capture flip** (`Card.flip`, `:249-291`) shipped with Phase 4: four 0.1 s tweens
  squashing the card to an edge and back, twice, with the colour switching at the first pinch.
  Worth recording that an earlier revision used a `rotationY` half-turn instead, which *mirrored
  the card's contents* between 90° and 180° — every glyph drawn backwards for a fifth of a second.
  A squash cannot do that, because the scale never goes negative. The original's choice was right.
- **The landing** (`Card.afterFly`, `:199-207`): the card drops into its cell from up and to the
  right, turning three quarters anticlockwise, from 1.2× and transparent to at rest.

`Card.fly`'s **first** half — raising the card 100px out of the hand and fading it — is
deliberately not ported. This port removes the card from the hand the instant it is played, so
there is nothing left there to raise; and under a drag it would be wrong as well as absent, since
the player's own finger has already carried the card across and replaying that journey would show
it twice.

---

## ✅ Completion criteria

- [x] Every caption implemented, in four locales
- [x] `PileOuFace`, sequenced in position and driven by the model's own coin flip
- [x] Sequencing derived from state and setup, covering all three ways a card gets played
- [x] Pacing computed from the animations rather than a fixed guess, and the turn clock waiting
      for the intro
- [x] Tests: the caption table, the intro assembly, the sequencing, the artwork bundle, and the
      overlay playing through a real composition
- [x] Card motion on placement: the capture flip (Phase 4) and the landing
- [x] `UnlockCardAnim`, wired to the inventory
- [x] `TalkBubble` built and tested; `Mogu` established as dead code
- [x] A tutorial screen for `TalkBubble` to live on — delivered under Phase 4

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 7**: [11-PHASE-7-TESTING.md](./11-PHASE-7-TESTING.md)
