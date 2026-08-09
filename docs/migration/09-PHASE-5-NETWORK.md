# Phase 5: Network Layer - Triple Triad Online Migration

## 📋 Document Information

- **Phase**: 5 - Network Layer
- **Duration**: the original 3-week budget was for a port that does not exist; this phase is
  greenfield and its cost bears no relation to it
- **Status**: re-scoped 2026-07-25. **Sequencing steps 1, 2, 4, 5 and 6 done; step 3 half done.**
  Accounts, server-held progression, several servers and update notices all shipped and verified
  against the local container 2026-08-08. The peer handshake's first two pieces — the joint seed
  and the hand commitment — landed 2026-08-08. What remains is signed moves, a transport, and
  `MatchView`. See § What is left in this phase.
- **Version**: 1.6
- **Last Updated**: 2026-08-08
- **Prerequisites**: Phases 1-4

---

## 🎯 Phase Overview

### ✅ Decision, 2026-07-25: the socket server is abandoned

**The original socket architecture is dropped entirely** — no XMLSocket, no TCP↔WebSocket proxy,
no `triple-triad-online.com:2468`, no backend work to keep an existing server alive. TR-007 in
[16-RISK-ASSESSMENT.md](./16-RISK-ASSESSMENT.md) is resolved by this.

The two facts that drove it, and that are worth keeping because they explain why this phase has no
parity target: **multiplayer never worked in the AS3 source** — no card synchronisation, no match
start, no swap, no elements, no trade — and `net/Socket.as` declared 29 handlers of which its only
inbound entry point dispatched to two. There was nothing to port.

**Transport for PvP is undecided.** Bluetooth is under consideration. The constraint to weigh before
committing: Kotlin Multiplatform has **no common Bluetooth API**, and peer-to-peer needs one
device to act as peripheral/advertiser, which the cross-platform BLE libraries do not
support. That makes Bluetooth the *most* platform-specific option available — roughly two
independent native implementations — rather than the simplest. This is a design discussion
still to be had, not a decision.

---

## 🧭 The shape of the network layer (2026-08-06)

> **Status of this section:** the parts that concern solo play against the server are **built and
> running** — see § Sequencing for what shipped when. The parts that concern local PvP are still a
> design that has not been prototyped. § What is not decided lists the remaining gaps honestly
> rather than filling them with a guess.

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

**Implemented 2026-08-07.** `AppVersion`, `CURRENT_VERSION` and `VERSION_HEADER` are in `:core`, so
the number is a property of the shared artifact rather than something either side is told. The
client sends `X-TTO-Version` on every request; the server answers **426 Upgrade Required** with its
own version in the header and the body, and does so *before* reading the body. An absent or
unparseable header is refused rather than assumed current — a client old enough to predate the
header is exactly the one whose replay cannot be trusted. Verified against the running container.

Still open: whether the catalogs move into the `:core` artifact so the two sides share the bytes and
not merely the version. The gate makes drift *visible*; publishing the catalogs makes it
*impossible*, and is the better fix once there is somewhere to publish to.

### Offline play may not survive — noted 2026-08-07

Not a decision, a flagged possibility: **matches played with no connection may stop being allowed
altogether.** It is worth writing down because it moves the value of several things built around it.

What it would cost: the transcript's proudest property — that a match played on a plane still counts
— stops being exercised. The offline queue becomes dead weight rather than the point.

What it would *buy*, and this is the part not to overlook: **the server could issue the seed at
match start.** That closes seed grinding, which § What the cryptography does not solve currently
lists as accepted-and-unfixed, because fixing it "needs connectivity at match start". If
connectivity at match start is mandatory anyway, the weakness disappears for free.

The practical consequence for what is being built now: keep the reversal cheap. Submission is a
callback (`onTranscript`) and the queue sits behind a small interface over `DocumentStore`, so
removing offline tolerance means deleting a queue and inlining a call — not unpicking a design. That
is the reason to prefer those two shapes even while offline play is still supported.

### The mechanism: a replayable, signed transcript

Because progression is server-held, every match has to end in a verdict the server accepts. The
engine is **pure and deterministic** — `MatchPreparation.prepare`, `Roulette.augment`,
`Npc.randomHand`, `MatchAi.play` and `MatchState.play` all take an injected `Random` and touch
nothing else — so the server does not need to have *watched* a match. It can **replay** it.
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

### A sudden-death match cannot be transcribed — known gap, 2026-08-07

`MatchTranscript` describes **one** nine-placement match: one seed, one deck, one list of moves.
A draw in PvE does not end the match — `PVEMatchScreen.as:63-68` regroups both sides' cards from the
board and plays it again, possibly more than once — and there is no field in which to say so.

The client therefore **does not submit a match that went to sudden death** (`MatchScreen`'s
`suddenDeath` flag). That is the right failure of the two available: submitting the first nine moves
would be worse than submitting nothing, because the server would replay them happily, score the
draw, and return a verdict contradicting the reward already credited for the sudden-death result. A
transcript that is *wrong* is more dangerous than one that is *absent* — the whole design rests on a
rejection meaning something.

The cost is a small number of honest matches going unverified, and it stays small: a draw needs the
board to split 5–5. When it is fixed, the shape is a list of rounds — the seed still drives
everything, since `MatchPreparation.prepareRematch` draws from the same generator. That is a
`TRANSCRIPT_VERSION` bump and therefore a major version bump (see § One version, shared), which is
why it is written down rather than done in passing.

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
4. **The client's half.** Half done, 2026-08-07:
   - ✅ **Submission** — `MatchSubmitter` in `:core` (the contract, no transport in sight) and
     `KtorMatchSubmitter` in `shared/.../net/`. `SubmissionResult` is the part worth reading: an
     unreachable server is `Offline`, **not** an exception, because "an honest match played offline
     still counts" is only true if the caller can hold the transcript and send it later.
   - ✅ **The version gate** — decision 6, implemented on both sides. `AppVersion` and
     `VERSION_HEADER` live in `:core`, so the two cannot disagree unless they link different builds
     of it, which is exactly the case being detected. The server refuses with **426** before
     parsing the body; a client on a newer major is let through.
   - ✅ **The coroutines pin** — answered, and it was a smaller question than it looked. See
     § What is not decided.
   - ✅ **The game calls it** — done 2026-08-07. `MatchScreen` emits a transcript through
     `onTranscript` **after** the reward is credited and never instead of it, and both hosts build a
     reporter. The engine is chosen per platform behind an `expect fun defaultHttpEngineFactory()`,
     so neither app module names Ktor: they supply an address and a place to write, which is all
     they can know. Superseded in step 6 by a *list* of addresses rather than one — see
     `serverEntries` there for how both hosts now read their configuration.
   - ✅ **The offline queue** — done 2026-08-07. `TranscriptQueue` over `DocumentStore`, its own
     collection, one document per profile, bounded at 200 with the **oldest** dropped. It drains
     when a profile is opened, stops at the first `Offline` result, and consumes everything else —
     a verdict, accepted or rejected, is an answer and resubmitting it forever is the bug the
     distinction exists to prevent. No `NetworkMonitor` and no platform code.
   - ✅ **The invariant is now tested end to end** — `shared/src/desktopTest/.../MatchTranscriptTest`
     plays a whole match through the real screen and replays the emitted transcript with
     `TranscriptVerifier`. Worth its cost: the invariant it guards is that **nothing may draw from
     the match generator on the player's turn**, and three call sites already violated it (the deck
     selector's Random button, the turn timer's auto-play, and a seed that was derived twice rather
     than kept). A fourth would surface in production as honest players' matches being rejected.
   - ✅ **The verdict is applied** — done in step 5 below. `QueuedMatchReporter.drain` no longer
     discards what came back; a credited match reaches `AccountSession.adopt`.
5. ✅ **Accounts, and crediting a verdict to a profile** — done 2026-08-08.
   - The **account replaces the local profile**. With a server configured, the character comes from
     `GET /me` and the local `.sav` list is not reachable; without one, nothing changes and the game
     plays exactly as it did before. Both are supported configurations — see `ProfileGate`.
   - Storage is **aggregates plus history**: one row per verified match, and the counters derived
     from it. `AccountRoutes` registers, signs in and out, and reads and writes the server-held
     profile; `POST /matches/submit` replays the transcript, credits the reward and answers with the
     profile it wrote, which the client **adopts rather than merges** — reconciling two profiles
     field by field is how a duplicated reward gets introduced.
   - The bearer token is stored **per server** and in the clear, which `SessionStore`'s own KDoc
     argues for and bounds.
   - `QueuedMatchReporter.drain` is no longer the function that discards the verdict: a credited
     match reaches `AccountSession.adopt`, so the dashboard a player lands on after signing in
     already shows what their offline matches paid.
6. ✅ **Several servers, and update notices** — done 2026-08-08.
   - **`GET /server` is the one route that is never behind the version gate**, and that is the whole
     design. An endpoint that refuses incompatible clients cannot be the endpoint that tells them
     they are incompatible — a 426 has no way to say "the remedy is to update". `ServerRoutesTest`
     pins it with a test whose KDoc notes that a gate added there would still pass every other test
     in the suite.
   - The client keeps a **`ServerDirectory`**: a configured list, one selection, observable. The
     address and the token are both read *per request* from it, so a runtime switch cannot leave a
     request going to server A with server B's token. The desktop reads `-Dtto.servers` / `TTO_SERVERS`,
     Android a string resource; both parse with the same `serverEntries`, so the two hosts derive the
     same ids from the same text.
   - The session **and** the transcript queue are keyed by server id. Draining one server's matches
     into another would submit transcripts whose decks it never issued, which is indistinguishable
     from cheating.
   - Connectivity is **five states and not a boolean** (`ServerStatus`): reachable-but-degraded,
     too-old, not-a-game-server, unreachable and online each call for different advice, and a single
     "offline" would tell somebody three majors behind to check their wifi. Probes are explicit —
     startup, opening the list, refresh, after a switch — not a timer draining a battery to keep a
     dot green.
   - **There is no auto-updater, and there should not be one.** On Android and iOS the store owns
     updates and working around that gets a build removed; on the desktop, fetching and running a
     binary means owning signed artifacts, a verified release channel and an installer handoff, and
     getting any part of that wrong turns the update path into the attack. What is implemented
     instead: the deployment announces a `ClientRelease` (version, per-platform download, notes) via
     `TTO_CLIENT_*`, and the app shows a notice with one button that opens the right link for *this*
     platform. A **required** update replaces the sign-in form, because that form cannot work; a
     suggested one is a line above it.

**The local server exists**, and was verified end to end on 2026-08-07: `docker compose up -d
--build` in [`tto-server`](../../../tto-server), then a real transcript posted to
`/matches/verify` came back `{"type":"accepted","blue":3,"red":7,"winner":"RED"}` — a score the
server computed rather than copied. See that repository's README.

One wrinkle worth knowing about: `:core` is published nowhere, so the server's **image build** could
not resolve it. It now mounts the developer's `~/.m2/repository` read-only through a named build
context (`MAVEN_LOCAL_REPO` in the server's `.env`). That is the only part of the server image that
is not self-contained, and it disappears the day `:core` is published somewhere real.

### The handshake, two thirds of it (2026-08-08)

`PeerHandshake` in `:core` is the first two of the three pieces from § What local play needs on
top, as pure logic with no transport in it — which is what lets it be written and tested while the
transport is still undecided.

- **The joint seed.** `SeedExchange`: each side commits to a 16-byte nonce by sending its SHA-256,
  and both reveal only once both commitments are in. `jointSeed` **sorts the two nonces by content**
  before hashing them, so the two devices agree without having to agree on which of them is
  "first" — there is no first, and a seed that depended on who dialled would be two different
  matches. A reveal that does not match its commitment returns null rather than a seed: there is
  nothing both sides agree on, so there is no match to play.
- **The hand commitment.** `commitHand` returns the five hashes to send *and* the five reveals to
  keep, so no caller has to store a salt next to its card. Each slot is salted separately, which is
  not decoration: a card id is a small number, and unsalted hashes would be a lookup table away
  from public — the opponent would read the whole hand off the wire before a card was played.

Tested by playing the cheats rather than the protocol: a nonce changed after committing, a card
never in the hand, a card moved to another slot, a salt swapped between slots. A commitment scheme
exercised only with honest inputs has not been exercised.

**Two dependencies, both deliberate.** Kotlin Multiplatform has no common SHA-256 and no common
secure random, and both are load-bearing here — `kotlin.random.Random` is a seeded PRNG, and a
guessable nonce makes a commitment worthless, since the other side can try the values a weak
generator could have produced and know the seed before revealing its own. KotlinCrypto's `sha2` and
`crypto-rand` publish for every target this project declares, iOS included.

### What is left in this phase

- **Signed moves.** Without them the handshake is fair but anonymous: two honest strangers get a
  fair seed, and so does an impostor playing under someone else's name. This needs a key story —
  generation, storage on the device, registration with the server — and it spans both repositories,
  so it is its own step rather than a third of this one.
- **A transport.** Still undecided; see the Bluetooth constraint above. Nothing written so far
  depends on the answer.
- **`MatchView`**, the screen itself.

---

## 📞 Related Documents

- **Phase Overview**: [00-INDEX.md](./00-INDEX.md)
- **Current System**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 4**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 6**: [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Status: solo-vs-server built and running; local PvP still to design.*
