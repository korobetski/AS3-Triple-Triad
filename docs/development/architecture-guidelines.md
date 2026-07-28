# Architecture Guidelines

Phase 0, Task 1.6 deliverable.

The AS3 original has one architectural rule: none. Understanding what it does wrong is the
fastest way to state what the Kotlin version must do, so each section below starts from a
real, cited defect in `sources/src/tto`.

---

## 1. Layering

```
ui/          composables. Reads state, emits events. No I/O, no rules.
domain/      rules engine, match state. Pure Kotlin. No Compose, no I/O.
data/        parsing, persistence, network. No Compose.
model/       data classes + invariants. Depends on nothing.
```

Dependencies point inwards only: `ui → domain → data → model`. No arrow ever points back.

**Why, concretely.** In the original, `net/Socket.as:86-90` does this:

```as3
PVPScreen.pingLabel.text = '';
PVPScreen.connectBtn.label = i18n.gettext("STR_CONNECT");
PVPScreen.registerBtn.isEnabled = false;
```

The network layer writes into a screen's static widgets. Neither layer can be tested,
replaced or migrated without the other. That single pattern is why Phase 5 cannot be
started independently of Phase 4 in the current codebase.

Enforce the boundary mechanically once modules are split (Phase 1): `:domain` must not
declare a Compose dependency, so a violation is a compile error rather than a review
comment. Until then, `model/` already has this property — keep it.

## 2. State

**One source of truth, hoisted as high as it needs to be and no higher.**

- Composables take state as parameters and emit events as lambdas.
- Match state lives in a ViewModel-equivalent (`StateFlow`), not in the composable tree.
- `remember` is for state that is genuinely local and can be discarded with the widget.

The PoC's [`BoardCard`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/MatchScreen.kt)
owns its flip animation with `remember` and re-triggers it from a `LaunchedEffect` keyed on the
card's owner — correct, because the animation is presentation-only. *Who* owns the card is
`MatchState`'s business: the flip is a view concern, the capture is a domain event, and
`MatchScreen` never decides one. It holds a single `var state` and calls
`state.play(card, position)`.

The screen's own arrangement follows the same split. `matchLayout` is a pure function of a
measured width and height returning a data class, not logic buried in a composable — which is
what makes it testable without a screen (`MatchLayoutTest`).

**Anti-pattern to avoid.** `Game.PROFILE_DATAS` is read from 34 files. Global mutable state
read from a third of the codebase makes every function's behaviour depend on invisible
inputs — which is why `Game.prepareMatch` has to re-derive which of three events called it.
Pass state; do not reach for it.

## 3. Navigation

Typed, not string-keyed. See [docs/analysis/event-catalog.md](../analysis/event-catalog.md) §2
for what the original does (31 `dispatchEventWith('gotoScreen', …)` calls resolved by name)
and why a typo there is a silent no-op.

```kotlin
sealed interface Destination {
    data object Menu : Destination
    data class PveMatch(val config: MatchConfig) : Destination
}
```

Arguments travel in the destination, not in globals.

## 4. Dependency injection

Constructor injection by default. A DI container (Koin is the plan's choice) only where
constructor wiring becomes unwieldy — and note that **no DI library is in the verified
dependency set** yet ([docs/migration/03-TECHNICAL-STACK.md](../migration/03-TECHNICAL-STACK.md),
Set C). Do not assume Koin works with this Kotlin/Compose combination until someone has
compiled it.

Never a static singleton holding mutable state. `utils/Assets.as` (imported by **57 files**,
the highest fan-in in the codebase) is the counter-example: a static registry that
everything reaches into, which makes the asset strategy impossible to change in one place.

## 5. `expect`/`actual`

Use it for platform capabilities, not for convenience. Current legitimate candidates:

| Need | Why platform-specific |
|---|---|
| Audio playback | Media3 on Android, AVFoundation on iOS |
| File paths for saves | `filesDir` vs `NSDocumentDirectory` |
| Crypto for the save file | if `utils/CryptoHelper.as`'s AES is kept at all |

Everything else — including resource loading — belongs in `commonMain`. The PoC loads
`cards.json` through Compose resources precisely so it does not need an `expect`/`actual`
pair, and that is the pattern to follow for the 263 card images too.

## 6. Error handling

| Situation | Approach |
|---|---|
| Invalid data at a boundary | throw in `init` — see `Card`'s `require` block; fail at parse time, not three screens later |
| Recoverable operation | `Result<T>` or a sealed result type |
| Cancellation | let `CancellationException` propagate; never `catch (e: Exception)` around a coroutine |
| Unexpected state | fail loudly in debug |

detekt has `TooGenericExceptionCaught` and `SwallowedException` **on**. The original
swallows silently in at least one place that matters:
`net/Socket.as:140-144` wraps `JSON.parse` in `try/catch` and sets `sj = null`, so a
malformed server message is indistinguishable from a `pong`. Do not reproduce that.

## 7. Concurrency

- Structured concurrency only: every coroutine has a scope with a lifecycle.
- `Dispatchers.Default` for parsing and rules, `Dispatchers.Main` for UI. Do not use
  `Dispatchers.IO` in `commonMain` — it does not exist on Kotlin/Native.
- No `GlobalScope`.
- Timers are `delay()` in a scoped coroutine, replacing the **30** `flash.utils.setTimeout`
  calls; cancellation then comes free, which `clearInterval` had to do by hand.

## 8. The rules engine

`utils/TTOCore.as` (396 lines, 4 public methods: `applyRules`, `basicRule`, `specialRule`,
`comboRule`) is the heart of the game and the best migration candidate in the codebase: it
is nearly pure already. Two changes when porting:

1. **Make it actually pure.** `applyRules(tile, color, checking)` mutates tiles and cards
   in place and reaches into `SCREEN` (a `BaseMatchScreen`) for animation. Split the
   decision from the mutation and from the presentation: `fun resolve(move, board, rules):
   MoveOutcome`, where `MoveOutcome` lists the captures, and let the UI animate that.
2. **Move it server-side eventually.** Today the client is authoritative and trivially
   cheatable — see [docs/analysis/network-protocol.md](../analysis/network-protocol.md) §3.
   A pure function is portable to a server; a function that touches `SCREEN` is not.

This is also where test coverage pays for itself: 20 rules
(`datas/tripleTriadRules.as:9-30`) interacting combinatorially, with a `comboRule` that
recurses. See [testing-strategy.md](./testing-strategy.md).

## 9. Related

- [coding-standards.md](./coding-standards.md)
- [testing-strategy.md](./testing-strategy.md)
- [docs/analysis/dependency-matrix.md](../analysis/dependency-matrix.md) — the coupling this is reacting to
- [docs/analysis/api-mapping.md](../analysis/api-mapping.md) — type-level translations
