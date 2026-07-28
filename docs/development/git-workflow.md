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
| Gradle root | **the repository root** |
| CI | [`.github/workflows/build.yml`](../../.github/workflows/build.yml), `paths-ignore` on `docs/`, `sources/` and `*.md` |

The Gradle build used to live in a `kotlin/` subdirectory, which meant Android Studio had
to be pointed at it and CI needed `working-directory: kotlin`. It was promoted to the root,
so both are now the obvious thing. CI switched from a `kotlin/**` allow-list to
`paths-ignore` at the same time: an allow-list at the root would have to name every module
and would silently stop building one that was added and not listed.

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

- `local.properties` (machine-specific SDK path; it is in `.gitignore`)
- anything under `build/`, `.gradle/`, `.kotlin/`
- generated files that live in `build/` — but **do** commit
  [`cards.json`](../../shared/src/commonMain/composeResources/files/cards.json) and
  [`dependency-matrix.md`](../analysis/dependency-matrix.md), which are checked-in
  generator output; regenerate rather than edit them

### File modes on Windows

This project is developed on Windows, where `git config core.filemode` is `false`. Git
therefore never notices the executable bit, and a script committed from Windows lands in
the index as `100644` — **not executable**. On a Linux CI runner that fails immediately:

```
./gradlew: Permission denied
Error: Process completed with exit code 126.
```

That is exactly how the first CI run failed. The fix is to set the mode in the index
explicitly, then commit:

```bash
git update-index --chmod=+x gradlew
```

Check with `git ls-files -s <path>`: it must read `100755`. Any new shell script, hook or
wrapper added from Windows needs the same treatment. `gradlew.bat` correctly stays
`100644` — it is only ever run on Windows.

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
[docs/migration/04-PHASE-0-PREPARATION.md](../migration/04-PHASE-0-PREPARATION.md).

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

`v<major>.<minor>.<patch>` on `master` only. No release workflow exists yet, but it is now on
the critical path rather than deferred: **updates are delivered through GitHub Releases** with
an in-app version check, so a tag is what ships. That needs a signing key with a stable
signature, a monotonic `versionCode`, and a workflow that attaches a signed APK to the release.
See [docs/migration/12-PHASE-8-RELEASE.md](../migration/12-PHASE-8-RELEASE.md).

Read the certificate note below before generating that key.

### ⚠️ A private key is publicly downloadable

`sources/air/TripleTriadOnlineReborn.p12` is tracked in git, **and this repository is public**,
so the file is served to anyone who asks:

```
GET https://raw.githubusercontent.com/korobetski/AS3-Triple-Triad/master/sources/air/TripleTriadOnlineReborn.p12
→ HTTP 200, 2434 bytes
```

Verified 2026-07-26. A `.p12` holds a **private key**. This is past tense, not a risk to
prevent: the key has been publicly downloadable and deleting the file now changes nothing,
because it may already be cached, cloned or indexed. Rewriting history does not help either.

Treat it as compromised:

1. Establish whether the key is still valid. It is the AIR signing key and AIR is abandoned,
   so it probably signs nothing that matters — confirm rather than assume.
2. If it was issued by a CA and is still valid, revoke it.
3. **Do not reuse it, or its passphrase, for the Android signing key** that the update
   mechanism now requires.
4. Add `*.p12`, `*.pfx`, `*.jks`, `*.keystore` to `.gitignore` and keep new keys in GitHub
   Secrets.

This is out of scope for the migration itself, but it is the one item in this document worth
acting on before writing any more code.

## 8. Related

- [coding-standards.md](./coding-standards.md)
- [testing-strategy.md](./testing-strategy.md)
- [`.github/workflows/build.yml`](../../.github/workflows/build.yml)
