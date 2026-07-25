# Testing Strategy

Phase 0, Task 1.6 deliverable.

---

## 1. What exists today

Measured, not projected. `./gradlew build` in `kotlin/`:

| Source set | Suite | Tests | Runs on |
|---|---|--:|---|
| `commonTest` | `CardTest` | 5 | desktop, androidDebug, androidRelease |
| `commonTest` | `CardCatalogTest` | 8 | desktop, androidDebug, androidRelease |
| `desktopTest` | `FlipUiTest` | 3 | desktop |
| `desktopTest` | `CatalogUiTest` | 5 | desktop |
| | **total** | **21 distinct / 47 executions** | 0 failures |

`commonTest` runs on every target, which is the point of putting it there — the same 13
tests execute three times. They would also run on iOS via
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
platform. The 13 common tests here run three times for free; the same tests in
`desktopTest` would run once.

**Desktop is the fast host for Compose UI tests.** `runComposeUiTest` on the JVM needs no
emulator and no device, so it runs in CI in seconds. Reserve instrumented Android tests
for genuinely platform-specific behaviour.

## 4. Compose UI tests: assert behaviour, and check the test can fail

Two rules learned from the PoC.

### Test through the real tree

[`CatalogUiTest`](../../kotlin/shared/src/desktopTest/kotlin/com/tripletriad/ui/CatalogUiTest.kt)
reads `cards.json` out of the actual Compose resource bundle and asserts
`catalog: 263 cards`. It therefore fails if the resource is dropped from packaging, if the
generated `Res` accessor moves, or if the JSON schema drifts from the model — none of which
a mocked loader would catch. The *parser* is tested separately and purely in `commonTest`.

### Verify the test is not vacuous

A UI test that passes for the wrong reason is worse than none. During the PoC, the flip
assertion was validated by mutation: changing `value >= 90f` to `value >= 9000f` in
`CardView.kt` made exactly the two flip tests fail ("8 tests completed, 2 failed"), and
reverting made them pass again.

**Do this at least once per non-trivial UI test.** If breaking the feature does not break
the test, the test is decoration.

### Known gap: no layout assertions

The current UI tests assert text content and state changes, not geometry. A regression that
moved the digit badge would not be caught. Since the whole point of `CardColors.kt` is
reproducing exact AS3 coordinates, add layout assertions when the board arrives:

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
cd kotlin
./gradlew build                      # everything, including ktlint + detekt
./gradlew :shared:desktopTest        # fast loop: all 21 tests, ~4 s warm
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
| Texture atlas loading | not implemented; the highest unvalidated risk — [../analysis/api-mapping.md](../analysis/api-mapping.md) §7 |
| Drag and drop onto the board | not implemented |
| The rules engine | not migrated |
| Networking | not migrated; and see [../analysis/network-protocol.md](../analysis/network-protocol.md) |
| Frame timing / jank | not measured — [../analysis/performance-baseline.md](../analysis/performance-baseline.md) §2 |
| Layout geometry | no assertions — §4 above |

## 8. Related

- [coding-standards.md](./coding-standards.md)
- [architecture-guidelines.md](./architecture-guidelines.md) §8 — making the rules engine testable
- [performance-guidelines.md](./performance-guidelines.md)
- [../migration/17-TESTING-GUIDE.md](../migration/17-TESTING-GUIDE.md) — framework examples
- [../migration/11-PHASE-7-TESTING.md](../migration/11-PHASE-7-TESTING.md) — the QA phase
