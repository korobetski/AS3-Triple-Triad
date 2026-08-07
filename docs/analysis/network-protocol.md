# Network Protocol Specification

Phase 0, Task 1.3 deliverable 4.

> **Read this before costing Phase 5.** This document is a **specification exercise**,
> not reverse engineering. There is almost no live protocol to observe: of the 29
> `Socket_On_*` handlers in `net/Socket.as`, exactly **2** are reachable. The rest is a
> vestige of a chat server the client no longer speaks to. See TR-007 in
> [16-RISK-ASSESSMENT.md](../migration/16-RISK-ASSESSMENT.md).

> ⚠️ **The protocol described here is not being ported.** The socket architecture was abandoned on
> 2026-07-25 and the replacement is a different design entirely — a replayable signed transcript
> verified server-side, sketched in
> [09-PHASE-5-NETWORK.md](../migration/09-PHASE-5-NETWORK.md) § The shape of the network layer.
> What follows keeps its value as the record of what the AS3 client attempted, and the 27 dead
> handlers remain useful as **design input** for the message set a new protocol will need.

---

## 1. What actually works today

### 1.1 Transport

```as3
// net/Socket.as:57-58
Security.loadPolicyFile("xmlsocket://" + param.ip + ":" + param.port);
socket = new XMLSocket(param.ip, param.port);
```

`flash.net.XMLSocket` — a raw TCP socket that frames messages with a **null byte**
(`\0`) terminator. It is not HTTP, not WebSocket, and **not wire-compatible with
either**. A WebSocket client cannot talk to an XMLSocket server and vice versa: there is
no HTTP upgrade handshake and no frame header.

This is the fact that makes "the server remains as-is" impossible. Either the server
gains a WebSocket endpoint, or the Kotlin client speaks raw TCP with null framing
(possible with Ktor's raw sockets on JVM/Android, but **not available on Kotlin/Native
for iOS** without platform code).

The `loadPolicyFile` call has no equivalent and no successor: Flash socket policy files
are a Flash-only security mechanism.

### 1.2 Connection lifecycle

| Step | Code | Notes |
|---|---|---|
| connect | `Socket.connect({ip, port})` | `net/Socket.as:49` |
| on connect | sends `incoming` | `net/Socket.as:94-111` |
| keep-alive | `setInterval(sendPing, pingDelay)` | JSON `{"action":"ping"}` |
| on close | sends `exit`, then `socket.close()` | `net/Socket.as:76-92` |

If `Game.PROFILE_DATAS.MODE == 'ff8_'` the main room name changes to `Salon`
(`Socket.as:54-55`) — the two card collections use different lobbies.

### 1.3 Outbound messages — the live set

Three, all JSON, all built by hand with `JSON.stringify`.

```jsonc
// on connect — net/Socket.as:101-107
{
  "action":   "incoming",
  "nickname": "<Game.PROFILE_DATAS.USERNAME>",
  "params":   { "iconID": "<Game.PROFILE_DATAS.AVATAR_ID>" },
  "isAdmin":  true            // present only when PROFILE_DATAS.ADMIN
}

// keep-alive — net/Socket.as:127-132
{ "action": "ping" }

// on disconnect — net/Socket.as:76-81
{ "action": "exit" }
```

Note `Socket.send` silently drops everything while `_connected` is false
(`Socket.as:69-74`) — no queue, no error, no retry.

### 1.4 Inbound messages — the live set

`dataHandler` (`net/Socket.as:134-151`) is the **only** dispatch point in the file:

```as3
private static function dataHandler(e:DataEvent):void {
    if (e.data == 'pong') {
        Socket_On_pong();
    }
    var sj:Object
    try { sj = JSON.parse(e.data); } catch (e:Error) { sj = null; }
    if (sj) {
        if (sj.users) {
            Socket_On_clients(sj.users as Array);
        }
    }
}
```

So the client understands exactly two things:

| Inbound | Shape | Handler | Effect |
|---|---|---|---|
| `pong` | the bare string `pong`, **not** JSON | `Socket_On_pong` | writes round-trip time into `PVPScreen.pingLabel` |
| user list | `{"users": [ … ]}` | `Socket_On_clients` | stores `_usersList`; see §1.5 |

Anything else that arrives is parsed and discarded.

### 1.5 Known defects in the live path

These are bugs in the current client, not migration concerns — but they tell you the
live path was barely exercised:

1. **`pong` is handled then re-parsed.** `e.data == 'pong'` is checked, then the same
   payload is fed to `JSON.parse`, which throws and is swallowed. Harmless, but it means
   the "error" branch is hit on every keep-alive.
2. **`Socket_On_clients` only `trace`s.** `net/Socket.as:264-269` prints the array and
   assigns `_usersList`; the commented-out `PVPScreen.refreshUsersList()` next to it
   (`Socket.as:163-164`) shows the wiring was removed.
3. **The socket layer writes UI directly.** `Socket.close()` sets
   `PVPScreen.pingLabel.text`, `PVPScreen.connectBtn.label` and
   `PVPScreen.registerBtn.isEnabled` (`Socket.as:86-90`). The network layer depends on a
   screen's static fields. This must be inverted before either side can be migrated.

---

## 2. The dead protocol — 27 of 29 handlers

Every handler below takes `node:XML` and **is never called**. `Socket_On_` appears in no
file other than `net/Socket.as`, and within it only `Socket_On_pong` and
`Socket_On_clients` have call sites.

The comment above them reads `// PALABRE INSTRUCTIONS` (`Socket.as:158`) — Palabre was
an AS3 XMLSocket chat/lobby server. The client evidently once spoke Palabre's XML
dialect and was partially converted to a JSON server, leaving the XML half stranded.

| Handler | Line | Intended meaning |
|---|--:|---|
| `Socket_On_error` | 168 | server error |
| `Socket_On_joined` | 172 | we joined a room |
| `Socket_On_leaved` | 182 | we left a room |
| `Socket_On_childrooms` | 188 | list of game rooms under the lobby |
| `Socket_On_room` | 222 | one room's state |
| `Socket_On_rooms` | 240 | room list |
| `Socket_On_client` | 271 | one user appeared |
| `Socket_On_clientparam` | 298 | a user's property changed |
| `Socket_On_m` | 311 | chat message |
| `Socket_On_invitation` | 341 | game invite |
| `Socket_On_new_game` | 353 | a game was created |
| `Socket_On_get_game_infos` | 366 | game metadata request |
| `Socket_On_decline_game` | 381 | invite declined |
| `Socket_On_actu_game` | 404 | game state refresh |
| `Socket_On_cancel` | 429 | game cancelled |
| `Socket_On_can_join` | 444 | join permitted |
| `Socket_On_cannot_join` | 465 | join refused |
| `Socket_On_plz_join` | 469 | host asks us to join |
| `Socket_On_start_game` | 526 | match starts |
| `Socket_On_ready` | 558 | opponent ready |
| `Socket_On_setCards` | 568 | opponent's hand |
| `Socket_On_initiative` | 574 | who plays first |
| `Socket_On_swap` | 584 | the Swap rule's card exchange |
| `Socket_On_elements` | 597 | elemental tile assignment |
| `Socket_On_cardMove` | 611 | **a move** |
| `Socket_On_tradeCards` | 623 | post-match card trade |
| `Socket_On_libActualise` | 645 | collection refresh |

The outbound XML counterparts appear inside those same dead handlers, most of them
commented out. The surviving literals are worth keeping as a design sketch:

```xml
<join room="Salon" />
<leave room="game_1270307634746" />
<clientparam name="guild" value="…" />
<clientparam name="iconID" value="…" />
<get_game_infos toroom="Salon"><id>game_…</id></get_game_infos>
<plz_join toclient="…"><id>game_…</id></plz_join>
<cannot_join toclient="…"><id>game_…</id></cannot_join>
<decline_game toclient="…"><id>game_…</id></decline_game>
<cancel toroom="Salon"><game_id>game_…</game_id></cancel>
<start_game toroom="game_…" />
<ready toroom="game_…" />
<cardMove toroom="game_…"><c>ply_0_c_1</c><p>p3</p></cardMove>
<swap toroom="game_…">
  <blue><index>2</index><id>91</id></blue>
  <red><index>0</index><id>17</id></red>
</swap>
```

Room names are `game_` + `new Date().getTime()`. Card references in `cardMove` use
`ply_{player}_c_{slot}` and board positions use `p{0..8}`.

---

## 3. What this means for Phase 5

The plan's three-week Phase 5 assumes a protocol to port. There is not one. The
realistic work is:

| Work item | Present in AS3? |
|---|---|
| Design a match protocol (moves, rule negotiation, reconnection, authority) | no |
| Implement a server that speaks it | no — server is out of scope in the current plan |
| Add a WebSocket endpoint (XMLSocket is not upgradeable) | no |
| Server-side rules validation | no — `TTOCore` is client-side only, so today's client is authoritative and trivially cheatable |
| Client transport + serialisation | partially: 3 outbound actions, 2 inbound |
| Lobby, invitations, chat | handler stubs only, never reachable |

There is also **no authentication** anywhere in the socket path: `incoming` carries a
nickname and an avatar id, nothing more. Any client can claim any nickname.

**Recommendation** (unchanged from TR-007): drop PvP from v1. If it stays, budget it as
greenfield client **and** server work — 8-12 weeks with a backend developer who is not
currently on the roster in
[00-INDEX.md](../migration/00-INDEX.md#-team-structure) — and re-scope Phase 5
accordingly.

---

## 4. Proposed Kotlin shape (for when the protocol is designed)

Not a port; a starting point. Given a server that speaks JSON over WebSocket:

```kotlin
@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("incoming")
    data class Incoming(val nickname: String, val avatarId: String) : ClientMessage

    @Serializable @SerialName("ping")
    data object Ping : ClientMessage

    @Serializable @SerialName("exit")
    data object Exit : ClientMessage

    @Serializable @SerialName("cardMove")
    data class CardMove(val gameId: String, val handSlot: Int, val boardPosition: Int) : ClientMessage
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("pong") data object Pong : ServerMessage
    @Serializable @SerialName("users") data class Users(val users: List<UserSummary>) : ServerMessage
    // …
}
```

`kotlinx.serialization`'s sealed-class polymorphism replaces the string switch, and the
`action` discriminator maps onto `@SerialName`. Note this is **untested**: the serialization
plugin is verified in the Kotlin PoC for card data
([README.md](../../README.md)), but no networking library is in the
verified dependency set — see Set C in
[03-TECHNICAL-STACK.md](../migration/03-TECHNICAL-STACK.md).

---

## 5. Related

- [event-catalog.md](./event-catalog.md) §4 — the socket's Flash events
- [dependency-matrix.md](./dependency-matrix.md) §9 — `net/TTONet.as` is dead code
- [16-RISK-ASSESSMENT.md](../migration/16-RISK-ASSESSMENT.md) — TR-007
- [09-PHASE-5-NETWORK.md](../migration/09-PHASE-5-NETWORK.md) — the phase this invalidates
