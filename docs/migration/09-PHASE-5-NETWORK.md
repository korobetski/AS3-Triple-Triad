# Phase 5: Network Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 5 - Network Layer
- **Duration**: 3 weeks (Weeks 21-23) — **not a usable estimate**; see § Read this before estimating
- **Status**: re-scoped 2026-07-25, shape sketched 2026-08-06, **sequencing steps 1-2 done, step 3
  half done, and the local server verifying real transcripts 2026-08-07**
- **Version**: 1.3
- **Last Updated**: 2026-08-07
- **Prerequisites**: Phases 1-4

> ⚠️ **§ The shape of the network layer is a design in progress, not a plan.** Three product
> decisions are settled and recorded there; the architecture that follows from them is a proposal
> that has not been prototyped, costed or reviewed. It is written down at this stage so the
> reasoning survives the conversation, and so the prerequisites it identifies — which are useful
> whatever is decided next — can start.

---

## 🎯 Phase Overview

### 🔴 Read this before estimating the phase

**This phase is not a migration.** `net/Socket.as` declares 29 `Socket_On_*`
handlers, but `dataHandler()` — the only inbound entry point — dispatches to
exactly **two** of them (`pong`, `clients`). The remaining 27 have a single
reference each: their own declaration. They are orphaned remnants of an abandoned
XML→JSON protocol refactor.

**Multiplayer does not work in the AS3 source.** No card synchronisation, no match
start, no swap, no elements, no trade. There is nothing to reach parity with.

Two further blockers:
1. **Transport incompatibility.** `flash.net.XMLSocket` is a raw TCP socket. Ktor
   WebSocket cannot connect to it — no HTTP upgrade, no frame protocol. Either the
   server gains a WebSocket endpoint or a TCP↔WebSocket proxy is introduced. Both
   are **backend work**, contradicting the "server remains as-is" scope in
   [01-EXECUTIVE-SUMMARY.md](./01-EXECUTIVE-SUMMARY.md).
2. **Unknown server availability.** The production endpoint is commented out in
   `PVPScreen.as:315` (`triple-triad-online.com:2468`); the live code points at
   `localhost:3000`. Confirm a server exists before planning against it.

**Recommended re-scope**: drop PvP from v1 (removes 3 weeks, loses no working
functionality) and treat multiplayer as a post-launch greenfield feature with its
own design, client **and server** budget — realistically 8-12 weeks, not 3.

If PvP is retained, read the rest of this document as a **design proposal for new
work**, not a port. See **TR-007** in [16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md).

### ✅ Decision, 2026-07-25: the socket server is abandoned

TR-007 is resolved. **The original socket architecture is dropped entirely** — no XMLSocket,
no TCP↔WebSocket proxy, no `triple-triad-online.com:2468`, no backend work to keep an
existing server alive. Multiplayer, if built, is a new design.

Everything in **§ Purpose onwards** that describes Ktor WebSockets talking to the legacy server is
therefore **obsolete**, not merely optimistic. Keep it only as a record of what the AS3
client attempted. (§ The shape of the network layer, which follows immediately below, is *not*
obsolete — it is the design that replaced it.)

**Transport is undecided.** Bluetooth is under consideration. The constraint to weigh before
committing: Kotlin Multiplatform has **no common Bluetooth API**, and peer-to-peer needs one
device to act as peripheral/advertiser, which the cross-platform BLE libraries do not
support. That makes Bluetooth the *most* platform-specific option available — roughly two
independent native implementations — rather than the simplest. This is a design discussion
still to be had, not a decision.

The two facts above that survive the re-scope: **multiplayer never worked in the AS3
source** (no card synchronisation, no match start, no swap, no trade), so there is nothing to
reach parity with; and the phase is greenfield, so its cost bears no relation to the 3 weeks
budgeted here.

---

## 🧭 The shape of the network layer (2026-08-06)

> **Status of this section: a design in progress.** Nothing below has been prototyped. The three
> decisions in § What is settled are product decisions and are firm; everything after them is what
> appears to follow from them, and is open to being wrong. § What is not decided lists the gaps
> honestly rather than filling them with a guess.

### What is settled

| # | Decision |
|---|---|
| 1 | **Local play must work, and be verifiable by cryptography** — not refereed by a trusted host |
| 2 | **Progression must be trustworthy and stored on a remote server** — profiles are server-held |
| 3 | **No server code inside the client APKs** |
| 4 | **Desktop and Android are the targets for now.** The coroutines version may move if Ktor needs it |
| 5 | **During development the server is hosted locally**, in Docker, against Postgres (2026-08-07) |
| 6 | **Client and server carry the same version, and a major mismatch forces the client to update** (2026-08-07) |

Decision 3 is what rules out the host-as-referee model for local play, and decision 1 is what
replaces it. Decision 2 is the expensive one: it is what turns this phase from "a transport" into "a
service with accounts".

Decision 5 defers the hosting question rather than answering it. The comparison of hosting shapes
below concluded that the sharpest constraint is a **JVM runtime** — without it the rules engine has
to be reimplemented in a second language and the whole verify-by-replay design collapses. A local
container satisfies that constraint trivially and commits to no provider.

### One version, shared — decision 6

The client and the server are **one build, deployed in two places**. They carry the same version and
are tagged together; a client whose major version is behind the server's is refused and told to
update, rather than allowed to connect and fail obscurely.

This is what closes the problem `TranscriptVerifier` and `Catalogs` both have, and which nothing
else could close. The server deals hands from its **own** copy of `cards.json` and `npcs.json` — it
has to, since asking the claimant for the card table would be letting the claimant choose the rules.
But two copies drift, and the day one is regenerated without the other, **every transcript from the
updated client is rejected by a server dealing from the old table, and the rejection looks exactly
like cheating**. A version gate turns that silent, misdiagnosed failure into a loud one with the
right message.

Note what the gate is really protecting, because it is wider than the wire format: a **major** bump
means the *replay* can reach a different answer. That covers the card and opponent tables, and it
covers the engine itself — `RulesEngine`, `MatchState`, `Roulette`, `MatchAi`. It is the same event
that bumps `TRANSCRIPT_VERSION`, and the same event that breaks the goldens in
`ReplayDeterminismTest`. Those three are one decision wearing three hats, and they should move
together.

Two consequences worth stating now, while the client's half is still unwritten:

- **The version has to travel on the wire**, on the first exchange, before a transcript is worth
  parsing. Rejecting a stale client is a *protocol* answer, not a verdict.
- **Minor is not free either.** A minor bump must mean the replay is unchanged, or the distinction
  is decoration. That is a discipline, and `ReplayDeterminismTest` is what enforces it.

Still open: whether the catalogs move into the `:core` artifact so the two sides share the bytes and
not merely the version. The gate makes drift *visible*; publishing the catalogs makes it
*impossible*, and is the better fix once there is somewhere to publish to.

### The mechanism: a replayable, signed transcript

Because progression is server-held, every match has to end in a verdict the server accepts. The
engine is **pure and deterministic** — `MatchPreparation.prepare`, `Roulette.augment`,
`Npc.randomHand`, `MatchAi.play` and `MatchState.play` all take an injected `Random` and touch
nothing else — so the server does not need to have *watched* a match. It can **replay** it:

```
transcript = agreed seed
           + the ordered moves, each signed by its author
           + the end-of-match reveals
       → the server replays it against :core
       → it reaches the same score, or the transcript is rejected
```

One mechanism covers all three modes:

| Mode | What the transcript holds | What the server checks |
|---|---|---|
| **Solo (PvE)** | seed + the player's own moves | the deal and every AI move follow from the seed, so the score is forced |
| **Local PvP** | both commitments, both sides' signed moves, the reveals | legality, signatures, commitments consistent with reveals |
| **Online PvP** | — | the server refereed it live |

The solo case is worth dwelling on: the opponent's hand comes from `Npc.randomHand(random)` and
**every one of its moves from `MatchAi.play(state, random)`**. An offline player can therefore invent
nothing — at best they play well. So **offline play still counts**, which a conventional
authoritative server would have cost.

### What local play needs on top

Three pieces, all small:

1. **A joint seed.** Each side commits to a nonce (sends its hash), then both reveal; the seed
   combines the two. Neither can reroll until the coin flip, the roulette or Three Open favours
   them.
2. **A hand commitment.** Five hashes, one per card, each salted. A card is revealed as it is
   played, and its hash proves it was in the announced hand. Nothing leaks during the match and
   everything is checkable after it. Five items do not need a Merkle tree.
3. **Signed moves.** Without signatures a transcript submitted by one player alone is worth nothing.

Server-held progression buys a fourth check for free: the server **knows the profile's collection**,
so it can also verify that the committed hand held only cards the player owns. That check is
impossible in pure peer-to-peer.

### What the cryptography does not solve

Named rather than hoped away:

- **Collusion.** Two accounts held by one person play a perfectly valid match in which one loses on
  purpose. No signature scheme touches this; it is an economics and detection problem — cap the
  gain between repeat opponents, diminishing returns, anomaly detection.
- **Selective submission.** The loser simply does not submit. Credit on receipt, and let the
  opponent submit a partial transcript amounting to a forfeit — `GameSave.forfeits` already models
  the outcome.
- **Seed grinding, in solo.** Replay locally until the deal is favourable, then submit. Either the
  server issues the seed (which needs connectivity at match start) or this is accepted; the reward
  difference is small.

One reassurance about keys: a player who compromises their own device can sign whatever they like,
but **still cannot produce an illegal transcript**. A key protects against impersonation, not
against its owner — the rules do that.

### Determinism stops being elegant and becomes load-bearing

If the server and a client diverge by one bit, every verification fails. The places that could
diverge were checked on 2026-08-06:

| Site | Verdict |
|---|---|
| `Roulette.augment` | indexes a `List` by `random.nextInt` — stable |
| `RulesEngine.plusCaptures` | iterates `groupBy{}.values`; `groupBy` returns a `LinkedHashMap` filled in `Side.entries` order — stable |
| `HandVisibility` | holds a `Set` but only ever tests membership — order-irrelevant |
| Everything else in `model/` | iterates `enum.entries` or lists |

The diagnosis is favourable — Kotlin's default `Set` and `Map` are insertion-ordered — but it must
become **a test, not an observation**: a fixed set of seeds replayed in `:core`, run on every
target, failing the day the standard library's generator or an iteration order changes underneath.
This is the one prerequisite that depends on no remaining decision and can start immediately.

### What the server becomes

Five responsibilities: accounts and public keys, profiles, a transcript verifier, a live referee for
online matches, and player rendezvous. The **verifier and the referee share one engine**, so the
rules cannot drift between them.

Costs that decision 2 changes, relative to the 2026-07-25 note:

- Matches may die on deploy — profiles may not. That means **a real datastore, with backups and a
  tested restore**.
- Account recovery, therefore email or an equivalent.
- Personal-data obligations: export and deletion.
- Key lifecycle: generation, storage, device loss, device change.

Hosting stays a few euros a month and capacity is not a concern — the game is turn-based, roughly
twenty tiny messages per match. What grows is **responsibility**: the players' progression is now
yours to hold, and losing it is not recoverable. That, and not the hosting bill, is the argument to
weigh — the game being ported died exactly this way, and `PVPScreen.as:315` still points at
`localhost:3000` with the production address commented out above it.

### Where it runs: development

Decision 5. The environment is declared in [`compose.yaml`](../../compose.yaml) at the repository
root, with [`.env.sample`](../../.env.sample) as the template for the git-ignored `.env`:

```
cp .env.sample .env
docker compose up -d
```

Today it starts **only Postgres** — there is no `:server` module yet, and the `server` service in
the file is left commented rather than stubbed so that `docker compose up` always describes
something that runs. Three choices in it are deliberate and worth not undoing:

- **The image is pinned to a major version.** Postgres does not upgrade its data directory in
  place, so a float to `latest` would one day leave the volume unreadable and look like data loss.
- **The port is bound to `127.0.0.1`, not `0.0.0.0`.** The bare `5432:5432` most examples use
  publishes the database to the whole local network, with the development password.
- **The data lives in a named volume, not a bind mount.** Postgres requires POSIX ownership on its
  data directory, which a bind mount from a Windows host cannot provide.

`docker/postgres/init/` is mounted for first-boot schema bootstrap and is intentionally empty:
decision 2 settles that profiles are server-held, but not yet what a profile or a transcript looks
like, and a schema written before those exist would be a guess. Note that the directory runs
**once**, on an empty volume — it is a bootstrap, not a migration system, and a real migration tool
is a decision still open.

### Where it runs: production — the shapes, compared

Recorded 2026-08-07. **Not a recommendation of a provider**, and every cost is an order of
magnitude to re-verify: this market's prices and free tiers change every quarter.

The filter that eliminates most options is not price, it is the **runtime**. The design's whole
value is that the server replays matches with the *real* engine (`:core`) rather than a
reimplementation, so client and server cannot drift apart on the rules. Any platform whose runtime
is not the JVM costs a second implementation of `RulesEngine`, `MatchState`, `Roulette` and
`MatchAi`, maintained in lockstep forever.

| Shape | JVM | Long-lived connections | Always on | Ops | Order of magnitude | Verdict |
|---|---|---|---|---|---|---|
| Dedicated server | ✅ | ✅ | ✅ | heavy | ~30-80 €/mo | oversized |
| VPS | ✅ | ✅ | ✅ | medium | ~4-10 €/mo | ✅ candidate |
| Managed container / PaaS | ✅ | ✅ | ✅ (paid tier) | light | ~7-25 €/mo | ✅ candidate |
| Managed Kubernetes | ✅ | ✅ | ✅ | heavy | ~50 €/mo+ | disproportionate |
| Serverless containers | ✅ | ⚠️ capped | ❌ scales to zero | light | usage | fights the requirement |
| FaaS | ✅ | ❌ | ❌ | light | usage | incompatible model |
| Edge / durable objects | ❌ | ✅ excellent | ✅ | light | low | **non-JVM runtime** |
| BaaS (auth + Postgres) | ❌ for logic | ✅ | ✅ | light | low | hybrid only |
| Game backends (Nakama…) | ❌ for logic | ✅ | ✅ | medium | varies | hybrid only |
| Self-hosting | ✅ | ✅ | ⚠️ home uplink | medium | ~0 € | honest but fragile |

Two notes worth keeping:

- **Edge runtimes are the best-designed tool here and still unusable.** A single addressable object
  holding one match's state and its sockets is exactly the abstraction this needs — and its runtime
  is JavaScript/Wasm. It would cost the rules engine twice.
- **The one hybrid that pays.** The expensive part of decision 2 is not storage, it is
  authentication, account recovery, and the export/deletion obligations. Those are generic and can
  be bought; the referee and the verifier stay on the JVM and reuse `:core`. That splits the
  problem along the right seam.

On the database: backups are the argument, not capacity. A backup that has to be remembered
eventually is not taken, and the progression being held is unrecoverable — so managed backups are
probably the best-spent euro in the whole arrangement, on the condition that the **restore** is
tested and not merely the backup.

### Two prerequisites in the existing code

- **Extract `:core`.** The server must run the real rules without dragging in Compose. `model/`
  imports only `kotlin` and `kotlinx` — nothing to do. `data/` is clean too **except two one-line
  functions**, `loadCardCatalog()` and `loadNpcCatalog()`, which call `Res.readBytes`; the parsers
  (`CardCatalogParser.parse(text)`) are already separate. The whole cost is moving those two calls.
- **Introduce a `MatchView`.** Today the client holds the truth and hides part of it:
  `HandVisibility.isVisible()` filters at render time while `MatchState` carries both hands in
  memory. An authority must **redact before sending**, so the complete `MatchState` needs a
  per-player projection. In solo the projection is the identity, so nothing there gets harder — and
  it removes a leak that exists today, where a modified build can read the opponent's hidden hand.

### Cryptography under Kotlin Multiplatform

Decision 4 simplifies this considerably: **desktop and Android are both JVM targets**, so hashing
and signatures go through the platform's own APIs behind the pattern this project already uses four
times — the common code declares the interface, the host supplies the implementation, an in-memory
double serves the tests (`DocumentStore`, `SettingsStore`, `AudioPlayer`, `Clock`). No exotic
dependency.

iOS becomes a known, bounded cost when it arrives: one more native implementation, exactly like
`AndroidClock` / `JvmClock`.

### Transport

Undecided, and deliberately deferred. If the protocol is separated from the transport — a
serialisable `MatchMessage` and a `MatchSession` state machine in `commonMain`, with the wire behind
an interface — then the whole of PvP can be built and tested against **an in-memory pair of
endpoints**, with no Ktor and no network at all. The transport becomes an implementation detail
chosen later.

What is known about the candidates:

| | |
|---|---|
| **Ktor Client** (WebSocket) | genuinely multiplatform; the natural choice for talking to the server |
| **Ktor Server** | JVM-first. Fine for the server itself. Excluded from clients by decision 3 |
| **ktor-network** (raw TCP) | JVM + Native, so *more* portable than Ktor Server — the candidate for LAN play |
| **Peer discovery** | no common KMP API. Avoidable with a short code or a typed address |

### What is not decided

- The transport, above.
- Whether local play needs peer discovery at all, or whether a code typed by one player is enough.
- Authentication: password, email link, or something else.
- Whether wagers (`Game.MATCHES[id].gils`) and card trades (`Socket_On_tradeCards`, entirely
  commented out in the AS3) are in scope. They are what would make cheating pay, and they should be
  decided before the anti-cheat design is called finished.
- ~~Ktor's minimum coroutines version against the `1.8.1` pinned in `gradle/libs.versions.toml`.~~
  **Measured 2026-08-07, and it is not the obstacle it looked like.** Ktor 3.5.2 brings the
  coroutines **BOM**, whose constraint wins over a plain `version.ref`, so adding Ktor Client to the
  client does not *risk* moving the pin — it moves it, to `1.11.0`. That is already what the server
  resolves today (`:core`'s 1.8.1 → 1.11.0), so `:core` is running on 1.11.0 in production already.
  Bumping the client catalog to `1.11.0` was tried: `:core:desktopTest`, `:shared:desktopTest`,
  `:shared:testAndroidHostTest` and `:androidApp:assembleDebug` all pass. Compose Multiplatform
  1.9.3 asks for 1.8.0 transitively, not 1.8.1, so nothing there is pinning it either. The decision
  left is whether to bump the catalog **deliberately, in its own commit**, or let Ktor drag it —
  and the first is obviously right, since the catalog comment's real warning was against a silent
  upgrade arriving from a *test* dependency.
- Every cost figure in this section. They are orders of magnitude, not quotes.

### Sequencing

1. ✅ **Pin determinism** with replay tests — done 2026-08-06,
   `shared/src/commonTest/kotlin/com/tripletriad/model/ReplayDeterminismTest.kt`. Eight tests in
   `commonTest` so the suite runs on the desktop JVM *and* Android's: self-consistency across six
   seeds, plus golden values recorded from a real run. A golden breaking is a data-version bump,
   not a test to edit — see the class's own note.
2. ✅ **Extract `:core`** — done 2026-08-07. The module is `core/`, the two resource-reading
   functions stayed behind in `shared/.../data/CatalogLoaders.kt`, and `:core` publishes as
   `com.tripletriad:core:0.1.0-SNAPSHOT` so the server can consume it.
3. **Define the transcript, the protocol and `MatchView`.** Half done, 2026-08-07:
   - ✅ **The solo transcript** — `core/.../protocol/MatchTranscript.kt` and `TranscriptVerifier.kt`.
     The server replays a submitted match with the real engine and reaches its own score; a
     truncated, padded or forged transcript is rejected with a machine-readable reason.
   - ❌ **`MatchView`** — not started. Only needed once a match has two live sides, so it follows
     local PvP rather than blocking it.
   - ❌ **The local-PvP protocol over an in-memory loopback** — not started. Needs the joint seed,
     the hand commitment and signed moves from § What local play needs on top.
4. **The client's half.** Nothing in the client talks to the server yet: there is no Ktor Client and
   no `ktor` entry in `gradle/libs.versions.toml`. ← next, and it is where the open question about
   Ktor's minimum coroutines version against the pinned `1.8.1` finally has to be answered.
5. Only then: accounts, signatures, and crediting a verdict to a profile.

**The local server exists**, and was verified end to end on 2026-08-07: `docker compose up -d
--build` in [`tto-server`](../../../tto-server), then a real transcript posted to
`/matches/verify` came back `{"type":"accepted","blue":3,"red":7,"winner":"RED"}` — a score the
server computed rather than copied. See that repository's README.

One wrinkle worth knowing about: `:core` is published nowhere, so the server's **image build** could
not resolve it. It now mounts the developer's `~/.m2/repository` read-only through a named build
context (`MAVEN_LOCAL_REPO` in the server's `.env`). That is the only part of the server image that
is not self-contained, and it disappears the day `:core` is published somewhere real.

Steps 2-3 are useful under every option still open and commit to no infrastructure. Decision 5's
container is the exception, and a cheap one: it is local, and it is deleted with `docker compose
down -v`.

---

> ## ⛔ Everything from here to the end of the document is superseded
>
> What follows was written against the legacy socket server, which the 2026-07-25 decision
> abandoned. It survives as a record of what the AS3 client attempted and of what was originally
> budgeted — **not** as instructions. The live design is § The shape of the network layer, above.
>
> In particular: the three-week timeline, Tasks 5.1-5.6, and the deliverables and completion
> criteria at the end all describe porting a protocol that does not work and reaching parity with a
> feature that never ran.

### Purpose
Replace the AS3 XMLSocket transport with a WebSocket implementation and **specify**
the game protocol, since the existing one is incomplete and largely unreachable.

### Key Objectives
1. Port the *working* parts of `Socket.as` — connect, ping/pong, user list (~120 of
   its 649 lines are live)
2. **Specify** the server protocol (the dead handlers are design input, not a spec)
3. Coordinate the required server-side changes
4. Implement Ktor WebSocket client
5. Design and implement the ~15 game message types that were never wired up
6. Connection management (ping/pong, reconnection)
7. Integrate with game state
8. Test network communication

---

## 📅 Timeline

| Week | Focus | Owner |
|------|-------|-------|
| Week 21 | Protocol analysis, Socket migration | Tech Lead + Network Team |
| Week 22 | Message handling, Connection management | Network Team |
| Week 23 | Integration, Testing | Network Team + QA |

---

## 📝 Tasks

### Week 21: Protocol Analysis & Socket Migration

#### Task 5.1: Reverse-Engineer Server Protocol
**Owner**: Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**Socket.as Analysis** (from 02-CURRENT-SYSTEM-ANALYSIS.md):
- XMLSocket protocol (old Flash technology)
- Many message types to handle (20+)
- Connection state management
- Ping/pong for keepalive (1000 ms - `Socket.as:31 pingDelay`)
- Room-based system; default room value is `'Gold Saucer'` (`Socket.as:30`)

**Message Types Identified** - WARNING: only `pong` and `clients` are actually
dispatched today; the rest are declared but unreachable (see the warning at the top
of this document). The full set of 29 declared handlers is listed in
[02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md) section 8; the
subset below is what a v2 protocol would need:
- `pong` - Ping response
- `clients` - User list update
- `new_game` - New game created
- `actu_game` - Game state update
- `start_game` - Game start
- `ready` - Opponent ready
- `setCards` - Cards set
- `initiative` - Initiative result
- `swap` - Card swap
- `elements` - Element setup
- `cardMove` - Card movement
- `tradeCards` - Card trade
- `chat` - Chat message
- `error` - Error message
- `connection` - Connection event

**Protocol Documentation to Create**:
```
# Server Protocol Specification

## Connection
- Protocol: WebSocket (replaces raw-TCP XMLSocket) - REQUIRES SERVER CHANGES
- Port: 3000 in the AS3 dev config; 2468 for the commented-out production host.
  (An earlier revision guessed "1935 or 8080" - neither appears in the source.)
- Format: JSON. Note the AS3 client already sends JSON for most outbound messages
  and parses JSON inbound; the XML paths are the dead ones.

## Message Format
All messages are JSON objects with a "type" field:
{
  "type": "message_type",
  "data": { ... }
}

## Message Types

### Connection Messages
- **connect**: Client connects to server
  - Request: {"type": "connect", "data": {"username": "...", "room": "main_room"}}
  - Response: {"type": "connected", "data": {"users": [...], "sessionId": "..."}}

- **pong**: Response to ping
  - Request: {"type": "ping"}
  - Response: {"type": "pong"}

### Game Messages
- **new_game**: New game created
  - Data: {"gameId": "...", "creator": "...", "rules": {...}}

- **actu_game**: Game state update
  - Data: {"gameId": "...", "state": {...}}

- **start_game**: Game start
  - Data: {"gameId": "...", "players": [...], "rules": {...}}

- **setCards**: Cards set
  - Data: {"gameId": "...", "player": "...", "cards": [...]}

- **cardMove**: Card played
  - Data: {"gameId": "...", "player": "...", "cardId": 1, "position": 0}

- **game_over**: Game ended
  - Data: {"gameId": "...", "winner": "...", "scores": {...}}

### Chat Messages
- **chat**: Chat message
  - Data: {"from": "...", "message": "...", "timestamp": 1234567890}

### Lobby Messages
- **clients**: User list update
  - Data: {"users": [{"username": "...", "status": "..."}, ...]}
```

**Deliverables**:
- `docs/protocol/server-protocol.md`
- Protocol test suite
- Example message captures

**Acceptance Criteria**:
- [ ] All message types documented
- [ ] Protocol validated with server
- [ ] Message formats defined

---

#### Task 5.2: Migrate Socket.as to SocketManager
**Owner**: Network Team | **Duration**: 4 days | **Priority**: CRITICAL

**AS3 Socket.as Key Properties**:
- `_socket: XMLSocket` - Flash XMLSocket
- `main_room: String` - Default room
- `pingDelay: int` - Ping interval (1000ms)
- `_isInit: Boolean` - Initialization state
- `_connected: Boolean` - Connection state
- `_usersList: Array` - User list

**AS3 Socket.as Key Methods**:
- `connect(param: Object): void` - Connect to server
- `send(value: Object): void` - Send message
- `close(): void` - Close connection
- `sendPing(): void` - Send ping
- `dataHandler(e: DataEvent): void` - Handle incoming data
- Multiple `Socket_On_*` methods for message types

**Kotlin Implementation**:
```kotlin
// SocketManager.kt
class SocketManager(
    private val client: HttpClient,
    private val configuration: SocketConfiguration
) {
    private var session: DefaultClientWebSocketSession? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Messages
    private val _incomingMessages = MutableSharedFlow<SocketMessage>()
    val incomingMessages: SharedFlow<SocketMessage> = _incomingMessages.asSharedFlow()
    
    // Users
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    
    // Current game
    private val _currentGame = MutableStateFlow<GameState?>(null)
    val currentGame: StateFlow<GameState?> = _currentGame.asStateFlow()
    
    suspend fun connect(room: String = configuration.defaultRoom) {
        close()
        
        _connectionState.value = ConnectionState.Connecting
        
        try {
            // `client.webSocket(...) { }` returns Unit and tears the session down
            // when the block exits — it cannot be assigned to a field, and there is
            // no `room` property on the session. Use webSocketSession() instead.
            session = client.webSocketSession {
                url {
                    protocol = if (configuration.useSSL) URLProtocol.WSS else URLProtocol.WS
                    host = configuration.host
                    port = configuration.port
                    path(configuration.path)
                }
            }

            send(SocketMessage.Connect(room))   // room is a message, not a session field

            _connectionState.value = ConnectionState.Connected(room)
            startPing()
            startMessageHandler()

        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e)
            scheduleReconnect()
        }
    }
    
    suspend fun send(message: SocketMessage) {
        try {
            session?.send(Frame.Text(Json.encodeToString(message)))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e)
        }
    }
    
    // WebSocketSession.close() is a suspend function, so this must suspend too.
    suspend fun close() {
        pingJob?.cancelAndJoin()
        reconnectJob?.cancelAndJoin()
        session?.close()
        session = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    private fun startPing() {
        pingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(configuration.pingInterval)
                try {
                    send(SocketMessage.Ping)
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error(e)
                    close()
                    break
                }
            }
        }
    }
    
    private fun startMessageHandler() {
        CoroutineScope(Dispatchers.IO).launch {
            session?.incoming?.consumeAsFlow()?.collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val message = Json.decodeFromString<SocketMessage>(frame.readText())
                        handleMessage(message)
                    }
                    else -> {
                        AppLogger.w("Unknown frame type: ${frame::class.simpleName}")
                    }
                }
            }
        }
    }
    
    private suspend fun handleMessage(message: SocketMessage) {
        when (message) {
            is SocketMessage.Pong -> handlePong()
            is SocketMessage.Clients -> handleClients(message.users)
            is SocketMessage.NewGame -> handleNewGame(message.game)
            is SocketMessage.GameUpdate -> handleGameUpdate(message.state)
            is SocketMessage.GameStart -> handleGameStart(message.game)
            is SocketMessage.OpponentReady -> handleOpponentReady(message.player)
            is SocketMessage.CardsSet -> handleCardsSet(message)
            is SocketMessage.Initiative -> handleInitiative(message)
            is SocketMessage.CardSwap -> handleCardSwap(message)
            is SocketMessage.Elements -> handleElements(message)
            is SocketMessage.CardMove -> handleCardMove(message)
            is SocketMessage.CardTrade -> handleCardTrade(message)
            is SocketMessage.Chat -> handleChat(message)
            is SocketMessage.Error -> handleError(message.error)
            else -> AppLogger.w("Unknown message type: ${message::class.simpleName}")
        }
        
        _incomingMessages.emit(message)
    }
    
    private fun scheduleReconnect() {
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            while (attempts < configuration.maxReconnectAttempts && isActive) {
                attempts++
                delay(configuration.reconnectDelay)
                try {
                    connect()
                    return@launch // Success
                } catch (e: Exception) {
                    AppLogger.e("Reconnect attempt $attempts failed", e)
                }
            }
            _connectionState.value = ConnectionState.Disconnected
        }
    }
}
```

**SocketMessage Sealed Class**:
```kotlin
// SocketMessage.kt
@Serializable
sealed class SocketMessage {
    // Connection
    @Serializable object Ping : SocketMessage()
    @Serializable object Pong : SocketMessage()
    @Serializable data class Connect(val room: String) : SocketMessage()
    @Serializable data class Connected(val users: List<User>) : SocketMessage()
    
    // Lobby
    @Serializable data class Clients(val users: List<User>) : SocketMessage()
    
    // Game
    @Serializable data class NewGame(val game: GameInfo) : SocketMessage()
    @Serializable data class GameUpdate(val state: GameState) : SocketMessage()
    @Serializable data class GameStart(val game: GameStartInfo) : SocketMessage()
    @Serializable data class OpponentReady(val player: String) : SocketMessage()
    @Serializable data class CardsSet(
        val player: String,
        val cards: List<Card>
    ) : SocketMessage()
    @Serializable data class Initiative(val result: String) : SocketMessage()
    @Serializable data class CardSwap(
        val player: String,
        val cardIndex: Int,
        val newCard: Card
    ) : SocketMessage()
    @Serializable data class Elements(val elements: List<Element>) : SocketMessage()
    @Serializable data class CardMove(
        val gameId: String,
        val player: String,
        val cardIndex: Int,
        val position: Int
    ) : SocketMessage()
    @Serializable data class CardTrade(
        val player: String,
        val cardIndex: Int
    ) : SocketMessage()
    
    // Chat
    @Serializable data class Chat(
        val from: String,
        val message: String,
        val timestamp: Long
    ) : SocketMessage()
    
    // Errors
    @Serializable data class Error(val error: String) : SocketMessage()
}

// WARNING: these were declared `@JvmInline value class` with 2-3 constructor
// parameters. A `value class` may have EXACTLY ONE parameter, and `@JvmInline`
// is a JVM-only annotation with no place in commonMain. Plain data classes:
@Serializable
data class User(val username: String, val status: String)

@Serializable
data class GameInfo(val id: String, val creator: String, val rules: GameRules)

@Serializable
data class GameStartInfo(val id: String, val players: List<User>, val rules: GameRules)
```

**Connection State**:
```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val room: String) : ConnectionState()
    data class Error(val exception: Throwable) : ConnectionState()
}
```

**Acceptance Criteria**:
- [ ] SocketManager compiles
- [ ] All message types defined
- [ ] Connection handling works
- [ ] Message routing works

---

### Week 22: Message Handling & Connection Management

#### Task 5.3: Implement Message Handlers
**Owner**: Network Team | **Duration**: 3 days | **Priority**: CRITICAL

**Message Handlers to Implement** (from Socket.as):
- `Socket_On_pong()` - Pong response
- `Socket_On_clients(clients: Array)` - User list update
- `Socket_On_new_game(node: XML)` - New game created
- `Socket_On_actu_game(node: XML)` - Game state update
- `Socket_On_start_game(node: XML)` - Game start
- `Socket_On_ready(node: XML)` - Opponent ready
- `Socket_On_setCards(node: XML)` - Cards set
- `Socket_On_initiative(node: XML)` - Initiative result
- `Socket_On_swap(node: XML)` - Card swap
- `Socket_On_elements(node: XML)` - Element setup
- `Socket_On_cardMove(node: XML)` - Card movement
- `Socket_On_tradeCards(node: XML)` - Card trade

**Handler Implementation**:
```kotlin
// In SocketManager.kt
private fun handlePong() {
    // Update last ping time
    // Reset connection timeout
}

private fun handleClients(users: List<User>) {
    _users.value = users
}

private fun handleNewGame(game: GameInfo) {
    // Notify that new game is available
    // Can join or ignore
}

private fun handleGameUpdate(state: GameState) {
    _currentGame.value = state
}

private fun handleGameStart(game: GameStartInfo) {
    // Start the game
    // Set up board, players, etc.
}

private fun handleOpponentReady(player: String) {
    // Opponent is ready
    // Can start game if both ready
}

private fun handleCardsSet(message: SocketMessage.CardsSet) {
    // Opponent has set their cards
}

private fun handleInitiative(message: SocketMessage.Initiative) {
    // Handle initiative result (who goes first)
}

private fun handleCardSwap(message: SocketMessage.CardSwap) {
    // Handle card swap for Swap rule
}

private fun handleElements(message: SocketMessage.Elements) {
    // Set element types on board
}

private fun handleCardMove(message: SocketMessage.CardMove) {
    // Opponent played a card
    // Update board state
}

private fun handleCardTrade(message: SocketMessage.CardTrade) {
    // Handle card trade
}

private fun handleChat(message: SocketMessage.Chat) {
    // Display chat message
}

private fun handleError(error: String) {
    // Display error to user
    AppLogger.e("Server error: $error")
}
```

**Acceptance Criteria**:
- [ ] All message handlers implemented
- [ ] Handlers update state correctly
- [ ] Error handling works

---

#### Task 5.4: Connection Management
**Owner**: Network Team | **Duration**: 2 days | **Priority**: HIGH

**Features to Implement**:
- Automatic reconnection
- Connection timeout
- Network status monitoring
- Room management
- Multiple connection handling

**Connection Configuration**:
```kotlin
@Serializable
data class SocketConfiguration(
    // AS3 dev target was PVPScreen.as:315 -> {ip:"localhost", port:"3000"};
    // the commented-out production target was triple-triad-online.com:2468.
    // 8080 was a guess in an earlier revision and matches nothing in the source.
    val host: String = "localhost",
    val port: Int = 3000,
    val path: String = "/socket",
    // Socket.as:30 -> main_room = 'Gold Saucer'. "main_room" is the VARIABLE
    // name, not the room name - an earlier revision used it as the value.
    val defaultRoom: String = "Gold Saucer",
    val pingInterval: Long = 1000,   // Socket.as:31 pingDelay
    val connectionTimeout: Long = 5000,
    val maxReconnectAttempts: Int = 5,
    val reconnectDelay: Long = 3000,
    val useSSL: Boolean = false
)
```

**Connection Monitoring**:
```kotlin
// NetworkMonitor.kt
interface NetworkMonitor {
    val isConnected: Flow<Boolean>
    val connectionType: Flow<ConnectionType>
}

enum class ConnectionType {
    NONE, WIFI, CELLULAR, ETHERNET, UNKNOWN
}

// WARNING: the previous version wrote:
//     expect class AndroidNetworkMonitor() : NetworkMonitor
//     actual class IosNetworkMonitor()   : NetworkMonitor
// That is not how expect/actual works: an `actual` must have the SAME name as its
// `expect`, and platform-prefixed names defeat the point. Declare one expected
// factory in commonMain and provide platform actuals:

// commonMain
expect fun createNetworkMonitor(): NetworkMonitor

// androidMain - needs a Context, so inject it (e.g. via Koin)
actual fun createNetworkMonitor(): NetworkMonitor = AndroidNetworkMonitor(appContext)

// iosMain - backed by NWPathMonitor
actual fun createNetworkMonitor(): NetworkMonitor = IosNetworkMonitor()
```

**Acceptance Criteria**:
- [ ] Reconnection works
- [ ] Timeout handling works
- [ ] Network status updates
- [ ] Room management works

---

### Week 23: Integration & Testing

#### Task 5.5: Network Integration with Game
**Owner**: Network Team + Tech Lead | **Duration**: 3 days | **Priority**: CRITICAL

**Integration Points**:
1. **PVPMatchScreen** - Network game screen
2. **PVPScreen** - Lobby screen
3. **GameViewModel** - Game state updates from network
4. **SocketManager** - Connection to game

**Integration Implementation**:
```kotlin
// PVPMatchScreen.kt
@Composable
fun PVPMatchScreen(
    navController: NavController,
    socketManager: SocketManager = koinInject()
) {
    val connectionState by socketManager.connectionState.collectAsState()
    val currentGame by socketManager.currentGame.collectAsState()
    
    LaunchedEffect(Unit) {
        socketManager.connect()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            socketManager.close()
        }
    }
    
    // Collect network messages
    LaunchedEffect(Unit) {
        socketManager.incomingMessages.collect { message ->
            when (message) {
                is SocketMessage.GameStart -> {
                    // Initialize game
                }
                is SocketMessage.GameUpdate -> {
                    // Update game state
                }
                is SocketMessage.CardMove -> {
                    // Process opponent's move
                }
                // ... etc
            }
        }
    }
    
    // Display based on state
    when (connectionState) {
        ConnectionState.Disconnected -> DisconnectedScreen()
        ConnectionState.Connecting -> LoadingScreen()
        is ConnectionState.Connected -> {
            if (currentGame != null) {
                ActiveGameScreen(game = currentGame!!)
            } else {
                LobbyScreen()
            }
        }
        is ConnectionState.Error -> ErrorScreen(error = (connectionState as ConnectionState.Error).exception)
    }
}
```

**Acceptance Criteria**:
- [ ] Network messages update game state
- [ ] Game state syncs between players
- [ ] All game actions send network messages

---

#### Task 5.6: Network Testing
**Owner**: QA Engineer | **Duration**: 3 days | **Priority**: CRITICAL

**Testing Strategy**:
1. **Unit Tests**: Test SocketManager in isolation
2. **Integration Tests**: Test with mock server
3. **End-to-End Tests**: Test with real server
4. **Stress Tests**: Test with many connections
5. **Edge Cases**: Test error conditions

**Test Types**:
```kotlin
// SocketManagerTest.kt
class SocketManagerTest : BaseTest() {
    private lateinit var socketManager: SocketManager
    // MockK is JVM-only - these tests can only live in jvmTest/androidUnitTest,
    // not commonTest. Also the helper is `mockk<T>()`; `mockkClass` takes a KClass.
    private val mockClient = mockk<HttpClient>()
    private val mockWebSocket = mockk<DefaultClientWebSocketSession>()
    
    @BeforeTest
    fun setup() {
        socketManager = SocketManager(mockClient, SocketConfiguration())
    }
    
    @Test
    fun `connect emits Connected state on success`() = runTest {
        coEvery { mockClient.webSocket(any(), any(), any()) } returns mockWebSocket
        coEvery { mockWebSocket.send(any()) } just Runs
        coEvery { mockWebSocket.incoming } returns emptyFlow()
        
        val states = mutableListOf<ConnectionState>()
        socketManager.connectionState.onEach { states.add(it) }.launchIn(this)
        
        socketManager.connect()
        
        // Wait for state changes
        delay(100)
        
        states shouldContain ConnectionState.Connecting
        states.last() shouldBe ConnectionState.Connected("main_room")
    }
    
    @Test
    fun `reconnect attempts on connection failure`() = runTest {
        coEvery { mockClient.webSocket(any(), any(), any()) } throws Exception("Connection failed")
        
        val states = mutableListOf<ConnectionState>()
        socketManager.connectionState.onEach { states.add(it) }.launchIn(this)
        
        socketManager.connect()
        delay(100)
        
        // `ConnectionState.Error` is a data class, not an object - you cannot
        // compare a value to the class itself. Match on the type instead:
        states.any { it is ConnectionState.Error } shouldBe true
        // Reconnect should be attempted
    }
    
    @Test
    fun `ping pong maintains connection`() = runTest {
        // Test ping/pong flow
    }
    
    @Test
    fun `message routing works correctly`() = runTest {
        // Test message handling
    }
}

// Mock WebSocket for testing
class MockWebSocket : WebSocketSession {
    override val call: HttpClientCall get() = error("Not implemented")
    override val closeReason: Throwable? get() = null
    override val customAttributes: Attributes get() = Attributes()
    override val extensions: List<WebSocketExtension<*>> get() = emptyList()
    // `Channel.Factory.receiveChannel {}` / `sendChannel {}` do not exist.
    // Use a real Channel and expose it as both ends.
    private val frames = Channel<Frame>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<Frame> get() = frames
    override val outgoing: SendChannel<Frame> get() = frames
    override val isActive: Boolean get() = !frames.isClosedForSend
    override val maxFrameSize: Long get() = Long.MAX_VALUE
    
    override suspend fun close(cause: Throwable?) {}
    override suspend fun flush() {}
    override suspend fun send(frame: Frame) {}
}
```

**Acceptance Criteria**:
- [ ] All network tests pass
- [ ] Test coverage >90% for network layer
- [ ] Tested with real server
- [ ] Error conditions handled

---

## 📊 Phase 5 Deliverables

### Code Deliverables
- [ ] Protocol specification document
- [ ] SocketManager implementation
- [ ] All message types and handlers
- [ ] Connection management
- [ ] Network integration
- [ ] Network tests

### Documentation Deliverables
- [ ] `docs/protocol/server-protocol.md`
- [ ] Network layer documentation
- [ ] API documentation

---

## ✅ Phase 5 Completion Criteria

### Technical
- [ ] All network functionality migrated
- [ ] Protocol fully documented
- [ ] Connection management works
- [ ] Message handling works
- [ ] Integration complete

### Testing
- [ ] All network tests pass
- [ ] Test coverage >90%
- [ ] Tested with real server

### Approvals
- [ ] Tech Lead approval
- [ ] QA Engineer approval

---

## 🎯 Next Phase: Phase 6 - Animations

**Phase 6 Focus** (Weeks 24-26):
- Complete any remaining animations
- Polish existing animations
- Performance optimization
- Testing

**Prerequisites**: All Phase 5 deliverables complete

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 6**: [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
