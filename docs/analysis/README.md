# Source Code Analysis

Phase 0, Task 1.3 deliverables. These describe the **existing ActionScript 3 codebase**,
as read from `sources/`. The migration *plan* lives in [../migration/](../migration/).

| Document | What it answers | How it was produced |
|---|---|---|
| [dependency-matrix.md](./dependency-matrix.md) | What imports what; migration order; where the effort is | **generated** — `python docs/analysis/tools/analyse_as3.py` |
| [event-catalog.md](./event-catalog.md) | Every event dispatched or listened for, and its Kotlin replacement | hand-written from greps, each entry cited to `file:line` |
| [api-mapping.md](./api-mapping.md) | AS3/Starling/Feathers/Flash type → Kotlin equivalent, ranked by real use count | hand-written, ranked from the generated matrix |
| [network-protocol.md](./network-protocol.md) | What the socket actually speaks (very little) and what Phase 5 really costs | hand-written from `net/Socket.as` |
| [performance-baseline.md](./performance-baseline.md) | Measured sizes and runtime figures; what could not be measured and why | measured on a Pixel 6a; gaps stated explicitly |

## Ground rules used

1. **Cite or omit.** Every claim points at a file and line, or at a command that
   reproduces it. Where a number could not be obtained, the document says so instead of
   estimating.
2. **Generate what can be generated.** The dependency matrix is a script output so it
   can be refreshed and checked, not trusted.
3. **Say what is not covered.** The `kotlin/` PoC validates a narrow slice; every
   document marks which of its claims rest on it and which do not.

## Headline findings

These change scope or cost and are worth reading even if you read nothing else:

- **27 of the 29 `Socket_On_*` handlers are unreachable.** The live protocol is 3 outbound
  JSON actions and 2 inbound messages. Multiplayer is greenfield, not a migration —
  [network-protocol.md](./network-protocol.md) §2.
- **`flash.net.XMLSocket` is not wire-compatible with WebSocket.** Server work is
  unavoidable, contradicting the "server remains as-is" scope —
  [network-protocol.md](./network-protocol.md) §1.1.
- **The shipped `tto.apk` contains no card artwork.** It is a 9.67 MB downloader shell, so
  the plan's app-size criterion was compared against the wrong artifact —
  [performance-baseline.md](./performance-baseline.md) §1.
- **There is no AS3 performance baseline and there probably never will be.** AIR is
  end-of-life; decide now whether a before/after comparison is required —
  [performance-baseline.md](./performance-baseline.md) §4.
- **Starling texture atlases have no Compose equivalent.** `utils/Assets.as` has the
  highest fan-in in the codebase (57 files) and the replacement is unvalidated —
  [api-mapping.md](./api-mapping.md) §7.
- **`theme/BaseTTOTheme.as` is 2,290 lines (13% of the codebase) that largely
  disappears.** Feathers style providers have no Compose counterpart; this is where the
  migration shrinks — [api-mapping.md](./api-mapping.md) §8.
- **`net/TTONet.as` is dead code** in the default package, and 5 files carry a UTF-8 BOM —
  [dependency-matrix.md](./dependency-matrix.md) §9.

## Related

- [../migration/00-INDEX.md](../migration/00-INDEX.md) — the migration plan
- [../migration/04-PHASE-0-PREPARATION.md](../migration/04-PHASE-0-PREPARATION.md) — the phase these belong to
- [../../kotlin/README.md](../../kotlin/README.md) — the Proof of Concept
