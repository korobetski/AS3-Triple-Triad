# Testing Strategy

Phase 0, Task 1.6 deliverable.

*What* is tested and *why*. The procedures — running, filtering, writing, and breaking the code to
prove a test can fail — are in [testing-guide.md](./testing-guide.md).

---

## 1. What exists today

Measured, not projected. `./gradlew build` at the repository root:

Refreshed 2026-08-02, with the playable loop of Phase 4.

| Source set | Package | Suite | Tests |
|---|---|---|--:|
| `commonTest` | `audio` | `SoundTest` | 5 |
| `commonTest` | `data` | `AchievementRepositoryTest` | 12 |
| `commonTest` | `data` | `CardCatalogTest` | 8 |
| `commonTest` | `data` | `CardRepositoryTest` | 13 |
| `commonTest` | `data` | `InventoryTest` | 19 |
| `commonTest` | `data` | `MatchHistoryRepositoryTest` | 18 |
| `commonTest` | `data` | `MatchRewardsTest` | 22 |
| `commonTest` | `data` | `NpcCatalogTest` | 9 |
| `commonTest` | `data` | `PveMatchTest` | 20 |
| `commonTest` | `data` | `SaveRepositoryTest` | 19 |
| `commonTest` | `i18n` | `StringsTest` | 9 |
| `commonTest` | `log` | `LogTest` | 7 |
| `commonTest` | `model` | `AchievementTest` | 13 |
| `commonTest` | `model` | `CardTest` | 5 |
| `commonTest` | `model` | `GameSaveTest` | 20 |
| `commonTest` | `model` | `ItemTest` | 16 |
| `commonTest` | `model` | `MatchAiTest` | 26 |
| `commonTest` | `model` | `MatchRecordTest` | 8 |
| `commonTest` | `model` | `MatchSetupTest` | 40 |
| `commonTest` | `model` | `MatchStateTest` | 27 |
| `commonTest` | `model` | `NpcTest` | 22 |
| `commonTest` | `model` | `RouletteTest` | 14 |
| `commonTest` | `model` | `RulesEngineTest` | 37 |
| `commonTest` | `model` | `XpTableTest` | 10 |
| `commonTest` | `settings` | `UserSettingsTest` | 12 |
| `commonTest` | `storage` | `DocumentStoreTest` | 9 |
| `commonTest` | `storage` | `SaveCodecTest` | 12 |
| `desktopTest` | `data` | `CardBundleTest` | 4 |
| `desktopTest` | `data` | `NpcBundleTest` | 12 |
| `desktopTest` | `i18n` | `StringsBundleTest` | 8 |
| `desktopTest` | `model` | `EnginePerformanceTest` | 4 |
| `desktopTest` | `ui` | `CardFaceTest` | 2 |
| `desktopTest` | `ui` | `MatchAudioTest` | 10 |
| `desktopTest` | `ui` | `MatchLayoutTest` | 6 |
| `desktopTest` | `ui` | `MatchUiTest` | 14 |
| `desktopTest` | `ui` | `NavigationTest` | 11 |
| `desktopTest` | `ui` | `OpponentUiTest` | 8 |
| `desktopTest` | `ui` | `OptionsUiTest` | 7 |
| `desktopTest` | `ui` | `ProfileUiTest` | 11 |
| | | **total** | **529 distinct / 961 executions**, 0 failures |
| | | coverage | **96.8% line / 86.7% branch**, gated at 90 / 75 in `check` |

`commonTest` runs on every target, which is the point of putting it there — **432** of the 529
execute twice, as `:shared:desktopTest` and `:shared:testAndroidHostTest`. The 97 in `desktopTest`
are there because they need something the JVM has and the Android host source set does not: a
Compose test harness, the packaged resource bundle, or a nanosecond clock. It was three times over
under AGP 8: AGP 9 dropped the release unit-test variant for library modules, and the module has
since moved to `com.android.kotlin.multiplatform.library`, which runs the Android unit tests once,
from an `androidHostTest` source set. They would also run on iOS via
`:shared:iosSimulatorArm64Test`, which the CI workflow invokes but which has **never been
executed**, because Kotlin/Native cannot target Apple platforms from a Windows host.

## 2. The test pyramid, and where this project's risk actually sits

The conventional pyramid is wrong for this migration. The risk is not in the units; it is
in the **rules engine's combinatorics** and in the **board interaction**.

```
        ┌─────────────────────┐
        │  manual / on-device │  the flip, the drag, the feel
        ├─────────────────────┤
        │   Compose UI tests  │  ← board interaction lives here
        ├─────────────────────┤
        │  rules engine tests │  ← THE priority: 20 rules, combinatorial
        ├─────────────────────┤
        │     unit tests      │  models, parsing
        └─────────────────────┘
```

`datas/tripleTriadRules.as:9-30` declares **20 rules**. `utils/TTOCore.as` resolves them in
four methods, one of which (`comboRule`) recurses. Rules interact: Same and Plus both feed
Combo; Reverse inverts comparison; Fallen Ace changes what a 10 means; Elemental applies a
±1 modifier before any comparison. This is where a migration silently changes behaviour,
and where property-based testing earns its keep:

```kotlin
// A move never changes the total number of cards on the board.
// A capture never flips a card to its own colour.
// Reverse applied twice is the identity.
```

**This exists as of 2026-08-02** — `RulesEngineTest`, `MatchStateTest`, `MatchSetupTest`,
`MatchAiTest` and `RouletteTest`, structured as the
[game-rules.md](../analysis/game-rules.md) § 16 matrix.

Two things this section got wrong, worth correcting rather than deleting:

- **No property-based library was added, and none is planned.** The three invariants sketched above
  are all pinned, but by direct assertion: the card total and the never-flip-to-own-colour rule fall
  out of the § 16 matrix, and "Reverse applied twice is the identity" is *false* here — both
  comparisons are strict, so a tie captures under neither, and § 15.9 explains why the obvious
  double-negation refactor is a bug. A generator would have explored board states that cannot occur
  while adding a dependency and a shrinking algorithm to debug. Where behaviour is randomised — the
  roulette draw, Chaos, the coin flip, the AI's tie-break — the tests sweep seeds instead.
- **"Run both against the same cases" is not possible.** The AIR client is unrunnable, so there is
  no oracle to diff against. What replaces it is the line-referenced reading of the source in
  [game-rules.md](../analysis/game-rules.md) and tests that cite the line they encode. Where
  faithful and correct diverge, `RulesEngineOptions` and `MatchAiOptions` hold **both** behaviours
  and a test pins each.

## 3. Rules by layer

| Layer | Framework | Requirement |
|---|---|---|
| `model/` | `kotlin.test` in `commonTest` | every invariant in an `init` block has a test that trips it |
| `data/` | `kotlin.test` in `commonTest` | parse a known-good payload, a payload with unknown fields, and an invalid one |
| `model/` (rules) | `kotlin.test` in `commonTest` | every rule in isolation, plus every documented interaction. **No property-based framework** — see §2 |
| `ui/` | `compose.uiTest` in `desktopTest` | behaviour, not pixels — see §4 |
| platform | instrumented / manual | only what cannot run on the JVM |

**`commonTest` by default.** Put a test in a platform source set only if it needs that
platform. The 432 common tests here run twice for free; the same tests in
`desktopTest` would run once. `StringsTest` is the pattern: the lookup and fallback *logic* needs
no resource bundle, so it lives in `commonTest`; what the shipped bundles *contain* is
`StringsBundleTest`, in `desktopTest`, for the same reason `CardBundleTest` is.

**Desktop is the fast host for Compose UI tests.** `runComposeUiTest` on the JVM needs no
emulator and no device, so it runs in CI in seconds. Reserve instrumented Android tests
for genuinely platform-specific behaviour.

## 4. Compose UI tests: assert behaviour, and check the test can fail

Three rules learned from the PoC.

### Test through the real tree

[`MatchUiTest`](../../shared/src/desktopTest/kotlin/com/tripletriad/ui/MatchUiTest.kt)
drives the real `App()`, which reads `cards.json` out of the actual Compose resource bundle. It
therefore fails if the resource is dropped from packaging, if the generated `Res` accessor moves,
or if the JSON schema drifts from the model — none of which a mocked loader would catch. The
*parser* is tested purely in `commonTest`; the *bundle's contents* in
[`CardBundleTest`](../../shared/src/desktopTest/kotlin/com/tripletriad/data/CardBundleTest.kt).

That last split is worth naming. The bundle's card counts used to be asserted through the UI,
off a debug line the app printed above the board. When the line was removed the assertion had
nowhere to live — a sign it had been attached to the wrong thing all along. Nothing about "the
resource is packaged and parses" needs a composition.

### Extract what can be tested without a screen

[`matchLayout`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/MatchScreen.kt) is a
pure function from a measured width and height to an arrangement, and
[`MatchLayoutTest`](../../shared/src/desktopTest/kotlin/com/tripletriad/ui/MatchLayoutTest.kt)
checks across nine viewports that the arrangement fits inside the bounds it was given.

It was extracted *because* the alternative had already failed three times: each earlier revision
estimated the leftover space as a screen size minus a constant, and each was wrong on some
device. Compose does not complain when a column is over-subscribed — `Modifier.size` coerces into
the constraints it is given, so children collapse to zero height while still drawing at full
size. The only symptom is cards drawn on top of each other, which no assertion in the tree was
looking for. **When a layout bug can only be seen in a screenshot, extract the arithmetic.**

### Verify the test is not vacuous

A UI test that passes for the wrong reason is worse than none. During the PoC, the flip
assertion was validated by mutation: changing `value >= 90f` to `value >= 9000f` made exactly
the flip tests fail, and reverting made them pass again. The same was done for the rules engine:
mutating `RulesEngine.beats` from `defence < attack` to `defence <= attack` fails
`equalPowersNeverCapture` and nothing else.

**Do this at least once per non-trivial UI test.** If breaking the feature does not break
the test, the test is decoration.

### Assert on identity, not on pixels

`CardFaceTest` had to prove a card is drawn with its own picture — a screenshot question. It
does it by bitmap identity instead: `CardArt` caches one `ImageBitmap` per texture id, so "the
right artwork" reduces to "the instance the cache hands out for this card". Cheap, exact, and
it runs on the JVM.

That bug — `produceState` keeping a previous card's value because its state is remembered
unkeyed — was live for a whole feature and the suite could not see it. It needs a *reused*
composable slot, and nothing had asserted on what a reused slot contains. **When a bug needs
a slot to be reused, drive the reuse in the test**: the failing case here is one
`mutableStateOf` swapped from one card to another with the composition kept.

### Say what is not covered, in the file that is not covering it

`UserSettingsTest` drives the settings layer through an in-memory store. The two **platform** stores
— `AndroidSettingsStore`, `DesktopSettingsStore` — have no tests at all, and that is a decision, not
an omission: they are twenty lines of `java.io.File` each, they live in the host modules, and a test
would be testing the JDK. What is worth pinning is the *on-disk shape*, because a `UserSettings.json`
written by the AS3 build has to keep parsing — so that is what the tests assert, including a
verbatim AS3-written payload as a fixture.

The temp-file-and-rename in the Android store is likewise unasserted; it was verified by reading the
file off a device with `adb shell run-as`. **When coverage stops, say where** — the alternative is a
reader assuming the green suite means the file handling was exercised.

### A test that cannot fail is worse than no test

`MatchAudioTest` first asserted that each placement played *exactly one* of the two placement
sounds. It passed. Then the mutation check — swap `CARD_PLACED` and `CARD_CAPTURED` in the source —
**also passed**, because "one of the two" is true either way round.

That is the whole value of running the mutation. The test looked thorough, it exercised the real UI
through nine placements, and it could not detect the one bug it existed to detect. The fix was to
make the test find out what the placement actually *did*: the score line gives it away, since the
side that played gains one for its own card plus one per capture, so the other side's score falling
is proof of a capture. With that, the swap fails.

Mutating the source to check the test notices is the standard here — see
[§ Verify the test is not vacuous](#verify-the-test-is-not-vacuous) — precisely because
plausible-looking assertions like that first one are easy to write and impossible to spot by
reading.

### Do not let the harness decide what you are testing

The first version of `theSplashHoldsWhileStartupIsUnfinishedAndNamesItsPhase` asserted on the
first frame of a *normal* start: set the content, then check the menu is not up yet. It failed,
and the failure was the useful part — `runComposeUiTest` drains coroutines around every
interaction, so by the time the first assertion ran a healthy startup had already finished.

Had the timing gone the other way the test would have **passed while proving nothing**: it would
have been measuring how fast the machine reads a 60 KB JSON file. The fix is to remove the timing
from the question — a settings store whose `read` never returns holds the splash in
`StartupPhase.SETTINGS` indefinitely, and the assertion becomes about the state rather than about
the clock.

Note this is deliberately *not* the same fixture as
`aStoreThatThrowsDoesNotStrandTheSplash`. A store that throws must be recovered from; a store that
never answers is the only way to observe a fixed phase. Two different claims, two different doubles.

### Screenshots still catch what assertions cannot

The three new screens were rendered to PNG through `captureToImage()` at phone dimensions and
looked at, in French and in Japanese, before being called done. Two things came out of it that no
assertion in this suite would have found:

* the Japanese options screen shows its two app-owned strings **in English**, because `app-ja_JA.json`
  is empty — correct, documented fallback behaviour, and quite different to read about than to see;
* a suspected rendering defect — a stray vertical tick beside each volume slider — turned out to be
  the Material 3 slider **thumb**, which in this version is a thin detached bar. Rendering the same
  screen at 40% instead of 100% moved the tick with the value and settled it. The "fix" was reverted,
  along with the experimental opt-in it had needed.

The second one is the more useful lesson: a screenshot is evidence, not a verdict. It showed
something worth investigating and the investigation said the code was already right.

### Some bugs only a screenshot can see, and it is worth saying which

Localising the board surfaced a defect no assertion could: the status bar was sized for English,
and French pushed one control onto a second line. A Compose test reads the semantics text, which
is identical whether the text wrapped, elided or fitted — so the suite was green and the screen
was wrong.

That is the opposite lesson to `matchLayout`'s. There, the arithmetic could be extracted and
tested; here the thing that is wrong is the *typography of a real string in a real font*, and
there is nothing to extract. **When a class of bug is unreachable from the suite, name it** — this
one is checked by running the app in each locale, and the README says so rather than implying the
tests cover it.

### Known gap: no layout assertions

`MatchLayoutTest` covers the *arrangement* — which hand goes where, at what scale, and that it
fits. It does not cover *card-internal* geometry: a regression that moved the digit badge inside
the face would still pass. Since the whole point of `CardColors.kt` is reproducing exact AS3
coordinates, that gap wants closing:

```kotlin
onNodeWithTag(DIGITS_TEST_TAG).assertLeftPositionInRootIsEqualTo(expectedX)
```

## 5. Coverage

Targets, once there is enough code for the number to mean anything:

| Layer | Target | Rationale |
|---|--:|---|
| `model/`, `data/` | 90% | small, pure, no excuse |
| `domain/` (rules) | 95% | the correctness core |
| `ui/` | not measured | line coverage of composables measures nothing useful; measure *behaviours covered* instead |

This section asked for **Kover** on the grounds that JaCoCo does not cover Kotlin/Native.
**Kover turned out to be unusable here** — it aborts during plugin application under
`com.android.kotlin.multiplatform.library`, in all three versions that exist. Coverage is
measured with JaCoCo on the desktop target instead, and gated in `check` at 90% line / 75% branch
against 97.8% / 85.9% measured. The reasoning, and why measuring one target is not a shortcut:
[README § Coverage](../../README.md#coverage). How to run it and how to read it:
[testing-guide.md § 5](./testing-guide.md#5-coverage).

The advice not to gate before the rules engine existed has been overtaken — it exists, and the
gates are floors set well below the measured figures precisely so they do not encourage tests of
getters.

## 6. What running the tests looks like

```bash
./gradlew build                      # everything, including ktlint + detekt
./gradlew :shared:desktopTest        # fast loop: all 529 tests, ~60 s forced from scratch
./gradlew :shared:allTests           # every target the host can build
./gradlew :androidApp:installDebug   # then drive it by hand on a device
```

Filtering, report locations, and what to do when a UI test hangs:
[testing-guide.md § 1](./testing-guide.md#1-running-them).

`build` includes `check`, which includes `ktlintCheck` and `detekt` — a formatting failure
fails the build, deliberately.

## 7. What is not tested, and is known not to be

Stated so nobody mistakes green CI for coverage:

| Area | Status |
|---|---|
| iOS, at all | never compiled |
| Texture atlas loading | not implemented; the highest unvalidated risk — [docs/analysis/api-mapping.md](../analysis/api-mapping.md) §7 |
| Drag and drop onto the board | not implemented |
| The rules engine | not migrated |
| Networking | not migrated; and see [docs/analysis/network-protocol.md](../analysis/network-protocol.md) |
| Frame timing / jank | not measured — [docs/analysis/performance-baseline.md](../analysis/performance-baseline.md) §2 |
| Layout geometry | no assertions — §4 above |

## 8. Related

- [coding-standards.md](./coding-standards.md)
- [architecture-guidelines.md](./architecture-guidelines.md) §8 — making the rules engine testable
- [performance-guidelines.md](./performance-guidelines.md)
- [docs/migration/17-TESTING-GUIDE.md](../migration/17-TESTING-GUIDE.md) — framework examples
- [docs/migration/11-PHASE-7-TESTING.md](../migration/11-PHASE-7-TESTING.md) — the QA phase
