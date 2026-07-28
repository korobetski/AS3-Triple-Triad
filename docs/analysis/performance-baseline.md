# Performance Baseline

Phase 0, Task 1.3 deliverable 5.

> **Honest summary up front.** The task asked for "current AS3 performance metrics" to
> compare the migration against. **That baseline does not exist and cannot be produced on
> this machine.** Adobe AIR and Flash Player are end-of-life (Adobe ended Flash Player
> support in December 2020) and no AIR runtime is installed here, so the existing client
> cannot be run, let alone profiled.
>
> What follows is therefore split into: what was **measured** (§1-§3), what **could not
> be** and why (§4), and the **protocol** to fill the gap if someone with a working AIR
> environment is available (§5). No number in this document is estimated.

---

## 1. Package size — measured

Both stacks, same machine, same day.

| Artifact | Size | Contents |
|---|--:|---|
| `sources/air/tto.apk` (existing AS3/AIR build) | **9.67 MB** | 178 entries; see below |
| Kotlin PoC, debug APK | **17.27 MB** | playable board + 64 KB of card JSON + 7.00 MB of card artwork |
| Kotlin PoC, release APK (unsigned, no shrinking) | **14.46 MB** | same |
| — of which the 263 card faces | 7.00 MB | added after the figures above were first taken (10.11 / 7.43 MB, no artwork) |
| `sources/bin/ttoclient.swf` (unpackaged) | 0.75 MB | code only |

### The existing APK is a downloader, not the game

This is the finding that invalidates the app-size acceptance criterion as written.
Unzipping `tto.apk`:

| Entry | Files | Uncompressed |
|---|--:|--:|
| `assets/ttoclient.swf` | 1 | 6.68 MB |
| `assets/sounds/` | — | 1.3 MB |
| `assets/assets/` (mogu animation, updater background, rules pages) | — | ~0.9 MB |
| `assets/datas/locales/` (4 languages) | 4 | 0.13 MB |
| `classes.dex` | 1 | 0.03 MB |
| `lib/armeabi-v7a/libNativeABI.so` | 1 | 0.01 MB |

**Zero card images.** Searching the archive for `cards`, `card_thumbs`, `tto_mobile`,
`digits`, `npcs` and `avatars` returns nothing. The artwork is fetched at runtime — that
is what `utils/TTOFiles.as` and the updater screen are for.

So "9.67 MB" is the size of a shell that downloads the game, and the plan's
"< 20 MB for PoC" criterion was being compared against the wrong number.

### What the assets actually weigh

On disk, `sources/assets/` is **193 MB**. Not all of it ships — a good deal is source
material — but the runtime payload is substantial:

| Directory | Size | Ships at runtime? |
|---|--:|---|
| `fonts/` | 114 MB | partially — a subset is embedded in the SWF (`Card.as:54` embeds Eurostile); full CJK families are not all needed |
| `screens/` | 38 MB | no — appears to be design/reference material |
| `cards/` | 18 MB | **yes**, 167 files |
| `card_thumbs/` | 8.3 MB | **yes**, 164 files |
| `npcs/` | 6.4 MB | yes |
| `help/`, `images/`, `triad_rules/`, `avatars/`, `ui/` | ~7 MB | yes |

A conservative floor for the artwork the game must have access to is **~40 MB**, on top of
a 7.4 MB Compose release baseline. Any plan that promises a sub-20 MB installed app has
to either keep the download-on-demand model or re-encode the assets (WebP/AVIF), and
should say which.

**Correction needed in [04-PHASE-0-PREPARATION.md](../migration/04-PHASE-0-PREPARATION.md):**
the criterion should compare like with like, and the release figure should be re-measured
with R8 enabled — the PoC currently sets `isMinifyEnabled = false`, so 7.43 MB is an
un-shrunk upper bound, not the real floor.

---

## 2. Runtime — measured on a physical device

Device: **Pixel 6a** (`bluejay`), Android 17, API 37, arm64-v8a, 1080×2400 at 420 dpi,
connected over adb-tcpip.

Subject: Kotlin PoC, **debug** build (no R8, no baseline profile, no
`profileinstaller` warm-up — every figure below is therefore a pessimistic bound).

### Cold start

`adb shell am start-activity -W -n com.tripletriad.android/.MainActivity`, process killed
before each run:

| Run | LaunchState | TotalTime | WaitTime |
|--:|---|--:|--:|
| 1 | COLD | 658 ms | 662 ms |
| 2 | COLD | 752 ms | 755 ms |

Two samples is not a distribution. Recorded as an order of magnitude, nothing more:
**sub-second cold start including a 64 KB JSON parse.**

### Memory

`adb shell dumpsys meminfo com.tripletriad.android`, app idle on the card screen:

| Metric | Value |
|---|--:|
| TOTAL PSS | **72.4 MB** |
| TOTAL RSS | 169.4 MB |
| Java heap | 12.7 MB (24.0 MB allocated) |
| Native heap | 6.5 MB |
| Code | 29.2 MB |
| Graphics | 3.0 MB |
| TOTAL SWAP PSS | 0.18 MB |

29 MB of that is code — the Compose runtime — and it will not grow much with the real
game. Graphics is 3 MB for one card; this is the number to watch once texture atlases
arrive.

### Frame timing — **not measured**

`dumpsys gfxinfo` reported `Total frames rendered: 0`. The device's screen locked
partway through the session, and I did not attempt to get past the lock screen. The app
launched and drew (1 frame recorded above the keyguard) but no usable histogram exists.

To fill this in, on an unlocked device:

```bash
adb shell dumpsys gfxinfo com.tripletriad.android reset
# drive the flip animation ~30 times, then:
adb shell dumpsys gfxinfo com.tripletriad.android | grep -E "Total frames|Janky|percentile"
```

Until that is run, **the plan's "60+ FPS" and "animations perform smoothly" criteria are
unverified.** The flip looks smooth to the eye on the device, which is not a measurement.

---

## 3. Code-size baseline — measured

From [dependency-matrix.md](./dependency-matrix.md), regenerable with
`python docs/analysis/tools/analyse_as3.py`:

| Metric | Value |
|---|--:|
| Files to migrate (`sources/src/tto`) | 103 |
| Lines to migrate | 17,066 |
| Classes | 103 |
| Distinct external imports to replace | 167 |
| Largest single file | `theme/BaseTTOTheme.as`, 2,290 lines |
| Dead code identified so far | `net/TTONet.as` (49 lines, unreferenced), 27 of 29 `Socket_On_*` handlers |

The Kotlin PoC is a few thousand lines of Kotlin. It is not a basis for extrapolating
a line-count ratio and should not be used as one.

---

## 4. What could not be measured, and why

| Wanted | Status | Reason |
|---|---|---|
| AS3 frame rate | **impossible here** | AIR/Flash runtime is EOL and not installed; `tto.apk` targets `armeabi-v7a` only and needs the AIR runtime |
| AS3 memory use | **impossible here** | same |
| AS3 load time | **impossible here** | same; also the app downloads its assets, so any figure depends on the asset server, which is not reachable |
| AS3 asset-decode cost | **impossible here** | Starling `AssetManager` timings need the runtime |
| Kotlin frame timing | **not done** | device locked mid-session; see §2 |
| Kotlin release-build size with R8 | **not done** | `isMinifyEnabled = false` in the PoC |
| iOS anything | **not done** | Kotlin/Native cannot target Apple platforms from a Windows host, and `iosApp/` has no `.xcodeproj`. The framework has never been compiled. |
| Startup with a baseline profile | **not done** | no baseline profile generated |

The honest consequence: **there is no before/after comparison available, and there may
never be one.** If the project needs to prove the migration did not regress performance,
that has to be decided now, while someone can still stand up an AIR environment. If it
is accepted that the old client cannot be benchmarked, then the Kotlin targets have to be
set as absolutes rather than as "no worse than today", and
[11-PHASE-7-TESTING.md](../migration/11-PHASE-7-TESTING.md) should say so.

---

## 5. Measurement protocol

For whoever runs this properly. All of it is cheap; none of it has been done beyond §1-§3.

### 5.1 Absolute targets (proposed, since a comparison baseline is unavailable)

| Metric | Target | How to measure |
|---|---|---|
| Cold start | < 1.5 s on a Pixel 6a-class device | `am start-activity -W`, 10 runs, report median |
| Jank | < 5% janky frames during a full match | `dumpsys gfxinfo … reset` then play |
| 99th-percentile frame | < 16.7 ms | same |
| PSS, idle on board | < 200 MB | `dumpsys meminfo` |
| Installed size | to be set once the asset strategy is decided | `bundletool get-size total` on the AAB |
| Board interaction latency | < 100 ms drag-to-drop feedback | Macrobenchmark trace |

### 5.2 Tooling to add in Phase 1

None of these are in the verified dependency set yet
([03-TECHNICAL-STACK.md](../migration/03-TECHNICAL-STACK.md), Set C):

- **androidx.benchmark Macrobenchmark** — startup and frame metrics in CI
- **Baseline profiles** — typically the single largest cold-start win for Compose
- **`bundletool get-size`** — real download size, per device configuration
- **R8** — enable `isMinifyEnabled` and re-measure; `7.43 MB` is an un-shrunk figure

### 5.3 If an AIR environment can be found

Order of value:

1. Frame rate during a match with animations (the Same/Plus/Combo chains are the
   worst case — 24 animation classes in `tto/anims/`)
2. Cold start to menu
3. Peak memory during a match
4. Asset load time from a warm cache

Record device, OS, and AIR runtime version alongside every figure, and put the raw output
in this file rather than a summary.

---

## 6. Related

- [dependency-matrix.md](./dependency-matrix.md) — the code-size figures in §3
- [api-mapping.md](./api-mapping.md) §7 — texture atlases, the main unknown for graphics cost
- [README.md](../../README.md) — how the PoC figures were produced
- [16-RISK-ASSESSMENT.md](../migration/16-RISK-ASSESSMENT.md) — performance risks
