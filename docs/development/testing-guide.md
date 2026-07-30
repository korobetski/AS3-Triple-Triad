# Testing Guide

Phase 1, Task 1.13. How to run the tests, and how to write one that belongs here.

**Two neighbours, and the difference matters:**

- [testing-strategy.md](./testing-strategy.md) is *what* is tested, *what is not*, and the
  reasoning. Read it before arguing with a rule below.
- [docs/migration/17-TESTING-GUIDE.md](../migration/17-TESTING-GUIDE.md) is a **planning-era**
  document from Phase 0: framework examples and a target pyramid, written before any of this code
  existed. Where the two disagree, this one describes the repository.

## 1. Running them

```bash
./gradlew :shared:desktopTest            # the loop: all 165 tests, 22 s from cold caches
```

Measured with `--rerun-tasks`, so 22 s is the pessimistic figure; unchanged code is up-to-date and
returns immediately, and a single filtered class is ~8 s.

That single task is the whole suite as one developer experiences it: `desktopTest` compiles
`commonTest` **and** `desktopTest`, so it runs the 110 common tests plus the 55 that need a Compose
tree.

| Task | Runs | Why it exists |
|---|---|---|
| `:shared:desktopTest` | `commonTest` + `desktopTest`, on the JVM | the fast loop, and the only place UI tests run |
| `:shared:testAndroidHostTest` | `commonTest` again, on the Android host JVM | proves the common code is not accidentally JVM-desktop-specific |
| `:shared:allTests` | every target this host can build, aggregated report | |
| `:shared:iosSimulatorArm64Test` | `commonTest` on Kotlin/Native | **macOS only** — silently skipped elsewhere |
| `./gradlew build` | all of the above plus coverage, lint, ktlint, detekt | what CI runs |

**165 distinct tests, 275 executions** — the 110 in `commonTest` run once per target. That is not
double-counting for its own sake: `commonTest` is where the model, rules, i18n, settings, logging
and audio mapping live, and running it on two runtimes is how a Kotlin stdlib difference gets
caught.

### Filtering

`--tests` works on any of these tasks, with wildcards:

```bash
./gradlew :shared:desktopTest --tests "com.tripletriad.ui.MatchAudioTest"
```

```bash
./gradlew :shared:desktopTest --tests "*RulesEngine*" --tests "*MatchState*"
```

```bash
./gradlew :shared:desktopTest --tests "*.MatchUiTest.pickingACardThenACellPlacesItAndPassesTheTurn"
```

Reports land in `shared/build/reports/tests/desktopTest/index.html`, and the raw XML — which is what
CI uploads as `shared-test-results` — in `shared/build/test-results/`.

## 2. Where a new test goes

| It needs | Source set | Runs on |
|---|---|---|
| nothing but Kotlin | `shared/src/commonTest/` | desktop **and** Android host |
| a Compose tree (`runComposeUiTest`) | `shared/src/desktopTest/` | desktop only |
| the resource bundle read as a file | `shared/src/desktopTest/` | desktop only |
| a real device | **nowhere** — see §7 | |

Default to `commonTest`. Putting a test in `desktopTest` halves the runtimes it covers, so it wants
a reason: a Compose tree, or a JVM-only file read.

The existing packages mirror production — `model/`, `data/`, `i18n/`, `log/`, `settings/`, `audio/`,
`ui/` — and a test file is named after the thing it covers, not after its layer.

**Test names are sentences.** `equalPowersNeverCapture`,
`aRepairedFileSaysSoInTheLog`, `eachPlacementPlaysTheSoundThatMatchesWhatItDid`. A name that reads
as a claim makes a vacuous test obvious; `testPlay3` hides one.

## 3. Writing a Compose UI test here

Everything drives the **real `App()`**, from the splash down. There is no shortcut composable and no
test-only entry point, on purpose: a test that skipped straight to `MatchScreen` would stop noticing
if Play ever led nowhere.

[`ComposeTestSupport.kt`](../../shared/src/desktopTest/kotlin/com/tripletriad/ui/ComposeTestSupport.kt)
holds the vocabulary. Use it rather than reinventing a wait:

| Helper | Does |
|---|---|
| `settingsFor(locale)` | an in-memory store whose JSON pins the language |
| `awaitMenu()` | blocks until the splash finishes and the menu is up |
| `startMatch()` | `awaitMenu()`, press Play, wait for the board |
| `score()` | the score as `(blue, red)`, read off the status bar |
| `sideToPlay()` | which side is to play, read off the turn line |
| `playOut()` | plays a whole match, leftmost card onto the lowest free cell |
| `isVisible(text)` / `assertVisible(text, message)` | substring match anywhere on screen |
| `UI_TIMEOUT_MS` | 10 s — the budget for an animation or a resource load |

Three conventions those encode:

**Pin the locale, always.** `settingsFor(AppLocale.EN_US)` — otherwise the test inherits the
machine's language and the assertions that read text off the screen fail on a French laptop and pass
on CI. Going through the settings *file* rather than a parameter is deliberate: it means every UI
test also covers `UserSettingsRepository` reading a language.

**Address controls by test tag, not by label.** Tags are declared next to the composable as
`const val …_TEST_TAG` in `commonMain` — `MENU_PLAY_TEST_TAG = "menu-play"`, `BOARD_TEST_TAG`,
`SCORE_TEST_TAG`, `OPTIONS_NOISE_VOLUME_TEST_TAG` — so a wording change does not break a test and a
tag rename does not compile.

**Read state off the screen, not out of the model.** `sideToPlay()` parses the turn line because
that is the only way a test can catch the turn line and the turn disagreeing. The cost is coupling
to `app-en_US.json` wording, which is why the previous rule exists.

Assert **invariants**, not a particular board: the turn passes, the score always totals 10, an
illegal placement is swallowed. The deal is not seeded, so a test that expected a specific card
would be flaky by construction.

## 4. The mutation check — the standard for a non-trivial test

A test that passes proves nothing about its ability to fail. Before calling a test done, **break the
code it covers and watch it fail**:

1. Make the smallest plausible wrong change in the production source — the one a careless port would
   actually make.
2. Run the suite.
3. It must fail **that test**, and ideally only that test.
4. Revert, and confirm green.

Three real ones from this repository, each of which paid for itself:

| Mutation | Result |
|---|---|
| `RulesEngine.beats`: `defence < attack` → `<=` | fails `equalPowersNeverCapture`, and nothing else. Ties are exactly what a plausible-looking port gets wrong, because `reverse` *looks* like a negation and is not |
| `rememberCardFace` back to its `produceState` version | fails `theFaceFollowsTheCardWhenASlotIsReused`, and nothing else |
| swap `CARD_PLACED` and `CARD_CAPTURED` in `MatchScreen` | **failed nothing**, because the test asserted only "exactly one of the two played". That is what a vacuous test looks like from the inside: it drove the real UI over nine placements and could not detect the one bug it existed for. It now reads the score to establish what the placement actually did |

That third row is the reason this section is a procedure and not a suggestion. See
[testing-strategy.md § 4](./testing-strategy.md#4-compose-ui-tests-assert-behaviour-and-check-the-test-can-fail).

## 5. Coverage

```bash
./gradlew :shared:coverageReport
```

Then open `shared/build/reports/jacoco/coverageReport/html/index.html`. `./gradlew build` runs
`coverageVerify` on its own, so you find out about a collapse without asking.

Read it as a **smoke detector, not a score**. An uncovered branch is a question ("is this
reachable?"), and a covered line is not a tested one. The gates are 90% line / 75% branch against
97.8% / 85.9% measured — deliberately slack, so the number never becomes something to negotiate
with. Details and the Kover story: [build-guide.md § 5](./build-guide.md#5-coverage).

If your new code drops coverage, the useful question is which branch is unreachable from any test,
not how to get the percentage back.

## 6. When a test hangs, or lies

**It hangs at `awaitMenu()` / `startMatch()`.** Almost always resource packaging, not the UI: `App()`
sits on the splash until `cards.json` and the nineteen shared textures load, so a resource dropped
from the bundle presents as a 10-second timeout. Check the bundle before you check the composable —
`CardBundleTest` is the test that names that failure properly.

**`runComposeUiTest` drains coroutines around every interaction.** So any assertion about "the first
frame" or "before loading finishes" is really an assertion about machine speed, and will pass on
your laptop and fail on a loaded runner. The fix is to make the transient state *permanent* for the
duration of the test — `NavigationTest` uses a store whose `read()` never returns
(`awaitCancellation()`), which makes "the splash is still up" a fact rather than a race.

**A slider will not move with `performClick()`.** Drive it through semantics:

```kotlin
onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG)
    .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
```

**`waitUntil` over `waitForIdle`** when you are waiting for something asynchronous to *arrive*;
`waitForIdle` only settles what has already started.

## 7. What no test here can do

Stated so green CI is not mistaken for verification. Each of these is checked by hand, and the
result is written down where it can be audited:

| Not testable | How it is verified instead |
|---|---|
| whether a sound is **audible**, or the loop point clicks | ears. The tests pin the *mapping* — which moment asks for which sound — and `dumpsys audio` proves the track is decoding. [README § Audio](../../README.md#audio) |
| text **wrapping** and layout on a real screen | screenshots. A French label pushed the status bar onto two lines and no assertion could see it — semantics text does not change when text wraps |
| where a layer sits **inside** a card | nothing yet. A known gap: `assertLeftPositionInRootIsEqualTo` would close it |
| frame timing / jank | `dumpsys gfxinfo`, not yet successfully — the device screen locked mid-session |
| the two host modules and the audio player | nothing. `AndroidSettingsStore`, `DesktopSettingsStore`, `AndroidAudioPlayer` and `MainActivity` have no tests, and each file says so in its own header. A mock of `SoundPool` would assert that the mock was called |
| iOS, at all | the `ios-framework` CI job compiles and tests the shared framework. No app has ever run |

Driving a device by hand:

```bash
./gradlew :androidApp:installDebug
```

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

Both have Windows traps — see
[project-setup.md § 6](./project-setup.md#6-first-run-failures) before blaming the app.

**Say so in the file.** When something is not covered, the header of the file that is not covering it
is where that belongs — not only here. That convention is why the list above can be trusted.

## 8. Related

- [testing-strategy.md](./testing-strategy.md) — what is tested and why, and the rules by layer
- [build-guide.md](./build-guide.md) — the task graph and the coverage gate
- [coding-standards.md](./coding-standards.md) — naming and documentation, which applies to tests
- [README § Test breakdown](../../README.md#test-breakdown) — the per-class counts and what the
  load-bearing tests exist to catch
