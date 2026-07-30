# Development Standards

Phase 0, Task 1.6 deliverables. How this project is written, tested, reviewed and measured.

| Document | Covers |
|---|---|
| [project-setup.md](./project-setup.md) | the toolchain, `local.properties`, the IDE, what your host can build, and the first-run failures |
| [build-guide.md](./build-guide.md) | what each Gradle task builds, where the output lands, and how to reproduce a CI failure locally |
| [testing-guide.md](./testing-guide.md) | running, filtering and writing tests — and the mutation check |
| [coding-standards.md](./coding-standards.md) | formatting, naming, imports, documentation, complexity — and which config file enforces each |
| [architecture-guidelines.md](./architecture-guidelines.md) | layering, state, navigation, DI, error handling, concurrency, the rules engine |
| [git-workflow.md](./git-workflow.md) | branches, commits, PRs, merge strategy, branch protection |
| [testing-strategy.md](./testing-strategy.md) | what is tested today, what is not, and how to know a test can fail |
| [performance-guidelines.md](./performance-guidelines.md) | targets, how to measure them, Compose specifics, the app-size decision |

The first three are the Phase 1 Task 1.13 deliverables, together with
[CONTRIBUTING.md](../../CONTRIBUTING.md) at the repository root, which is the front door for
someone arriving with no context.

## Two things to know before you start

**1. Open the repository root in the IDE.** The Gradle build *is* the root —
`settings.gradle.kts`, `gradlew` and the three modules are all there. It used to live in a
`kotlin/` subdirectory that had to be opened instead; that is no longer the case. See
[README.md](../../README.md).

**2. The standards are enforced.** `./gradlew build` runs ktlint and detekt and fails on any
finding. The configuration files are the authority; the documents explain the reasoning.

```bash
./gradlew build          # compile + test + ktlint + detekt
./gradlew ktlintFormat   # fix formatting
```

## Related

- [docs/analysis/](../analysis/) — analysis of the existing AS3 codebase
- [docs/migration/](../migration/) — the migration plan
- [README.md](../../README.md) — the Proof of Concept
