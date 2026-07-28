# Coding Standards

Phase 0, Task 1.6 deliverable.

**These standards are enforced, not advisory.** Everything below is encoded in a
configuration file that runs in CI; the file is the authority and this document explains
the reasoning. If you disagree with a rule, change the config and the reason here — do
not add a local `@Suppress`.

| Concern | Enforced by | Runs in |
|---|---|---|
| Formatting, naming, imports | [`.editorconfig`](../../.editorconfig) via ktlint | `./gradlew ktlintCheck` |
| Complexity, code smells | [`detekt/detekt.yml`](../../detekt/detekt.yml) | `./gradlew detekt` |
| Both, on every push | [`.github/workflows/build.yml`](../../.github/workflows/build.yml) | the `quality` job |

Both are wired into `check`, so `./gradlew build` runs them too.

---

## 1. Formatting

Set in `.editorconfig`, which IntelliJ and Android Studio read directly — your IDE and CI
agree without a second configuration.

| Setting | Value | Why |
|---|---|---|
| `indent_size` | 4 | Kotlin convention |
| `max_line_length` | 100 | fits side-by-side diffs on a laptop; also set in `detekt.yml` so the two cannot disagree |
| `end_of_line` | `lf` | except `gradlew.bat`. A stray CRLF in `local.properties` cost half a day during the PoC — see [README.md](../../README.md) |
| `insert_final_newline` | true | |
| `ktlint_code_style` | `intellij_idea` | the `ktlint_official` style mandates argument-per-line signatures that make Compose code longer, not clearer |

To fix formatting: `./gradlew ktlintFormat`.

## 2. Naming

Kotlin's official conventions, with two documented exceptions.

| Kind | Convention | Example |
|---|---|---|
| Classes, objects, enums | `PascalCase` | `CardCatalog` |
| Functions, properties, parameters | `camelCase` | `powerLabel`, `cardCatalogPath` |
| `const val` and top-level compile-time constants | `SCREAMING_SNAKE_CASE` | `ACE_POWER`, `CARD_TEST_TAG` |
| Non-const design tokens | `PascalCase` | `val CardWidth = 88.dp` |
| Test functions | `camelCase`, a full sentence | `fun hexPowerAIsTen()` |

**Exception 1 — `@Composable` functions are `PascalCase`.** Universal across the Compose
ecosystem. Configured with `ktlint_function_naming_ignore_when_annotated_with = Composable`
and detekt's `FunctionNaming.ignoreAnnotated`.

**Exception 2 — `PascalCase` for non-const design tokens.** `val CardWidth = 88.dp` is the
Compose convention; `SCREAMING_SNAKE_CASE` is reserved for values the compiler can inline.
The distinction is real: `88.dp` is a function call, `10` is not.

Anything else needing a suppression must carry the reason inline, as in
[`MainViewController.kt`](../../shared/src/iosMain/kotlin/com/tripletriad/ui/MainViewController.kt),
where the name must stay `PascalCase` because Swift call sites read it as a constructor.

## 3. Imports

**No wildcards.** Enforced. This migration has an AS3 `Card`, a model `Card`, a Compose
`Card` and a `CardFace` in play simultaneously; `import androidx.compose.material3.*`
makes it impossible to see which one a file means.

Import order follows ktlint's default (lexicographic, no blank-line grouping) so the
formatter is deterministic.

## 4. Documentation

KDoc is required on:

- every public declaration in `shared/`,
- **every type or value ported from AS3 — with the source file and line it came from.**

The second rule is the one that matters here. A migration is worthless if a reader cannot
check it against the original. Compare:

```kotlin
// Bad — the reader has to take this on faith.
internal val CardWidth = 88.dp

// Good — checkable in ten seconds.
/** `new Quad(88, 118, 0x5a595a)` — `Card.as:73`. The coloured face, not the sprite. */
internal val CardWidth = 88.dp
```

Where the port deliberately differs from the original, say so and say why. See the KDoc on
[`BoardCard`](../../shared/src/commonMain/kotlin/com/tripletriad/ui/MatchScreen.kt),
which records that the AS3 flip is a `scaleX` yoyo and the Kotlin one is a `rotationY`
rotation, so nobody later mistakes it for a faithful port. `CardFace` does the same for a
subtler one: it scales by multiplying its geometry rather than by scaling its render layer,
because the layer version reports a size it does not draw at and gets clipped by any parent
that applies `alpha`.

`ForbiddenComment` is switched **off** in detekt: a `TODO` naming an owner and a reason is
useful during a migration. A bare `TODO` is not — write the reason.

## 5. Complexity

detekt's defaults apply, with these deliberate relaxations (all recorded with reasons in
`detekt.yml`):

| Rule | Relaxation | Reason |
|---|---|---|
| `LongParameterList` | ignored on `@Composable` and `@Serializable`, and on data classes | `Card` has 11 parameters because the AS3 record it mirrors has 11 fields |
| `LongMethod` | ignored on `@Composable` | splitting a layout tree at 60 lines to satisfy a counter usually hurts |
| `MagicNumber` | ignored in property declarations and named arguments in Composables | the UI layer's whole job is reproducing literal coordinates from the AS3 source, each one commented with its origin |
| `MaxLineLength` | 100, matching `.editorconfig` | one source of truth |

`build.maxIssues` is **0**. A warning threshold nobody watches is the same as no gate.

## 6. File organisation

```
shared/src/
  commonMain/kotlin/com/tripletriad/
    model/        pure data + invariants, no Compose, no I/O
    data/         parsing and loading
    ui/           composables and design tokens
  commonTest/     tests that run on every target
  desktopTest/    Compose UI tests (the desktop target is the fastest host for them)
  iosMain/        Apple entry points only
  commonMain/composeResources/
    files/        non-image resources, e.g. cards.json
```

One top-level declaration per concept per file; a file may hold several small related
declarations (`Card.kt` holds `Card`, `CardColor`, `CardType` and `powerLabel`) when they
are read together.

`model/` must not import Compose. It is the layer that will be reused by any future server
or tooling, and the compiler should enforce that.

## 7. Generated code

Never edited by hand, never formatted, never committed if it lands in `build/`:

- Compose resource accessors (`Res`) — excluded from ktlint in
  [`build.gradle.kts`](../../build.gradle.kts)
- `kotlinx.serialization` serialisers
- [`cards.json`](../../shared/src/commonMain/composeResources/files/cards.json) —
  **is** committed, because it is a build input, but it is produced by
  [`tools/extract_cards.py`](../../tools/extract_cards.py) and must be
  regenerated rather than edited
- [`docs/analysis/dependency-matrix.md`](../analysis/dependency-matrix.md) — same, from
  [`analyse_as3.py`](../analysis/tools/analyse_as3.py)

If you find yourself editing one of these, edit the generator.

## 8. Related

- [architecture-guidelines.md](./architecture-guidelines.md)
- [testing-strategy.md](./testing-strategy.md)
- [git-workflow.md](./git-workflow.md)
- [docs/migration/15-CHEAT-SHEET.md](../migration/15-CHEAT-SHEET.md) — AS3 → Kotlin idioms
- [docs/analysis/api-mapping.md](../analysis/api-mapping.md) — type-level translations
