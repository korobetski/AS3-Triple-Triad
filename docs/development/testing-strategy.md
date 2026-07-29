# Testing Strategy

Phase 0, Task 1.6 deliverable.

---

## 1. What exists today

Measured, not projected. `./gradlew build` at the repository root:

| Source set | Suite | Tests | Runs on |
|---|---|--:|---|
| `commonTest` | `CardTest` | 5 | desktop, androidHostTest |
| `commonTest` | `CardCatalogTest` | 8 | desktop, androidHostTest |
| `commonTest` | `RulesEngineTest` | 37 | desktop, androidHostTest |
| `commonTest` | `MatchStateTest` | 27 | desktop, androidHostTest |
| `commonTest` | `StringsTest` | 9 | desktop, androidHostTest |
| `commonTest` | `UserSettingsTest` | 12 | desktop, androidHostTest |
| `commonTest` | `LogTest` | 7 | desktop, androidHostTest |
| `desktopTest` | `MatchUiTest` | 10 | desktop |
| `desktopTest` | `NavigationTest` | 9 | desktop |
| `desktopTest` | `OptionsUiTest` | 7 | desktop |
| `desktopTest` | `StringsBundleTest` | 8 | desktop |
| `desktopTest` | `MatchLayoutTest` | 6 | desktop |
| `desktopTest` | `CardBundleTest` | 4 | desktop |
| `desktopTest` | `CardFaceTest` | 2 | desktop |
| | **total** | **151 distinct / 256 executions** | 0 failures |
| | coverage | **97.7% line / 86.2% branch** | gated at 90 / 75 in `check` |

`commonTest` runs on every target, which is the point of putting it there — the same 96
tests execute twice, as `:shared:desktopTest` and `:shared:testAndroidHostTest`. It was three
times under AGP 8: AGP 9 dropped the release unit-test variant for library modules, and the
module has since moved to `com.android.kotlin.multiplatform.library`, which runs the Android
unit tests once, from an `androidHostTest` source set. They would also run on iOS via
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

None of this exists yet. It is Phase 3 work and it should be planned as
test-first — the AS3 is the specification, and the only way to know the port matches is to
run both against the same cases.

## 3. Rules by layer

| Layer | Framework | Requirement |
|---|---|---|
| `model/` | `kotlin.test` in `commonTest` | every invariant in an `init` block has a test that trips it |
| `data/` | `kotlin.test` in `commonTest` | parse a known-good payload, a payload with unknown fields, and an invalid one |
| `domain/` (rules) | `kotlin.test` + property tests | every rule in isolation, plus every documented interaction |
| `ui/` | `compose.uiTest` in `desktopTest` | behaviour, not pixels — see §4 |
| platform | instrumented / manual | only what cannot run on the JVM |

**`commonTest` by default.** Put a test in a platform source set only if it needs that
platform. The 96 common tests here run twice for free; the same tests in
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

Use **Kover**, not JaCoCo: JaCoCo does not cover Kotlin/Native targets. Kover is **not yet
in the build** — the CI workflow does not run it. Adding it is Phase 1 work.

Do not gate on a coverage percentage before the rules engine exists; it will only encourage
tests of getters.

## 6. What running the tests looks like

```bash
./gradlew build                      # everything, including ktlint + detekt
./gradlew :shared:desktopTest        # fast loop: all 95 tests, ~10 s warm
./gradlew :shared:allTests           # every target the host can build
./gradlew :androidApp:installDebug   # then drive it by hand on a device
```

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
