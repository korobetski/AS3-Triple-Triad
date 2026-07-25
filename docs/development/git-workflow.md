# Git Workflow

Phase 0, Task 1.6 deliverable.

---

## 1. Repository facts

Check these before writing any automation — the earlier draft of the CI pipeline keyed on
`main` and would never have fired.

| Fact | Value |
|---|---|
| Default branch | **`master`** (not `main`) |
| Current migration branch | `migration/kotlin-multiplatform` |
| Gradle root | **`kotlin/`**, not the repository root |
| CI | [`.github/workflows/build.yml`](../../.github/workflows/build.yml), paths-filtered to `kotlin/**` |

The Gradle root matters for more than CI: **Android Studio must open `kotlin/`**, not the
repository root, because the root has no `settings.gradle.kts`.

## 2. Branches

| Pattern | Purpose |
|---|---|
| `master` | released state; protected |
| `migration/<topic>` | long-lived migration workstreams, e.g. `migration/kotlin-multiplatform` |
| `feature/<phase>-<slug>` | one deliverable, e.g. `feature/phase1-atlas-loader` |
| `fix/<slug>` | a defect |
| `spike/<slug>` | throwaway investigation; may be deleted unmerged |

Branch off the migration branch, not `master`, while the migration is in flight.

Keep branches short-lived. The `theme/BaseTTOTheme.as` rewrite (2,290 lines) is the one
place where a longer branch is justified; split anything else.

## 3. Commits

Conventional-commit style, with a scope that names the layer or module:

```
<type>(<scope>): <imperative summary under 72 chars>

<body: what changed and why. The why is the part that is not in the diff.>

<footer: refs, co-authors>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.

Scopes in this project: `shared`, `android`, `desktop`, `ios`, `model`, `data`, `ui`,
`build`, `docs`, `analysis`.

**Migration-specific rule.** A commit that ports AS3 code names the source:

```
feat(model): port Card from tto.display.Card

Fields and power ordering taken from Card.as:316-330, where each power is
read with uint("0x" + power[i]) -- a hex parse, which is how the literal
'A' in cards.as means 10.

Geometry constants cite Card.as and CardDigits.as line by line so the port
can be checked against the original.
```

This is not ceremony. Six months in, "why is the badge at (28, 88)?" has to be answerable
without reading ActionScript.

Do not commit:

- `kotlin/local.properties` (machine-specific SDK path; it is in `.gitignore`)
- anything under `build/`, `.gradle/`, `.kotlin/`
- generated files that live in `build/` — but **do** commit
  [`cards.json`](../../kotlin/shared/src/commonMain/composeResources/files/cards.json) and
  [`dependency-matrix.md`](../analysis/dependency-matrix.md), which are checked-in
  generator output; regenerate rather than edit them

## 4. Pull requests

| Requirement | Rule |
|---|---|
| Size | under ~400 changed lines where possible; a mechanical rename may be larger if it is *only* a rename |
| CI | all five jobs green — `quality`, `shared`, `android`, `desktop`, `ios-framework` |
| Reviewers | 1 for a port of existing behaviour, 2 for anything touching the rules engine or the protocol |
| Description | what changed, what was verified **and how**, what was deliberately not done |

On that last point: state the verification, not the intent. "Tests pass" is not a claim
anyone can check. "`./gradlew build` green; 47 test executions, 0 failures; installed on a
Pixel 6a and the flip works under touch" is.

The PoC history in this repository is the cautionary tale — a first attempt was reported
COMPLETE and "technology stack validated" while never having been compiled, and had 12
build-blocking defects. See
[../migration/04-PHASE-0-PREPARATION.md](../migration/04-PHASE-0-PREPARATION.md).

## 5. Merge strategy

- **Squash** for feature and fix branches: one logical change, one commit on the target.
- **Merge commit** for `migration/*` into `master`: the individual commits are the record
  of how the migration was done.
- **Never rebase** a branch someone else has pulled.

## 6. Branch protection on `master`

To configure (not yet in place — this repository has no protection rules):

- require the `quality`, `shared` and `android` checks
- require at least one approving review
- no force pushes, no deletions
- require branches to be up to date before merging

`ios-framework` runs on a macOS runner and costs 10× the minutes of a Linux one. Run it on
PRs but consider not making it a blocking check until the iOS app actually exists.

## 7. Tags and releases

`v<major>.<minor>.<patch>` on `master` only. No release workflow exists yet — signing keys
and store credentials are Phase 8 concerns, and a release pipeline that cannot sign
anything would be theatre. See
[../migration/12-PHASE-8-RELEASE.md](../migration/12-PHASE-8-RELEASE.md).

### ⚠️ A signing certificate is committed to this repository

`sources/air/TripleTriadOnlineReborn.p12` is tracked in git — confirmed with
`git ls-files --error-unmatch sources/air/TripleTriadOnlineReborn.p12`. A `.p12` holds a
**private key**. If that key is still valid, anyone with repository access can sign
artifacts as this publisher, and rewriting history does not help once it has been cloned or
if the repository has ever been public.

Before building any release process:

1. Establish whether the key is still in use.
2. If it is, treat it as compromised: revoke and re-issue.
3. Add `*.p12`, `*.jks`, `*.keystore` to `.gitignore` and keep new keys in CI secrets.

This is out of scope for the migration itself but should not be discovered during Phase 8.

## 8. Related

- [coding-standards.md](./coding-standards.md)
- [testing-strategy.md](./testing-strategy.md)
- [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
