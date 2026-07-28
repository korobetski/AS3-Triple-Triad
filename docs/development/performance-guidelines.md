# Performance Guidelines

Phase 0, Task 1.6 deliverable.

> **There is no comparison baseline.** The AS3 client cannot be benchmarked — Adobe AIR is
> end-of-life and no runtime is available. See
> [docs/analysis/performance-baseline.md](../analysis/performance-baseline.md) §4. Every
> target below is therefore an **absolute**, not a "no worse than today". Someone needs to
> accept that explicitly, because it means a regression against the original is
> undetectable by construction.

---

## 1. Targets

| Metric | Target | Measured today |
|---|---|---|
| Cold start | < 1.5 s | **658 ms / 752 ms** (2 samples, Pixel 6a, debug build) |
| Janky frames during a match | < 5% | **not measured** |
| 99th-percentile frame | < 16.7 ms (60 fps) | **not measured** |
| PSS, idle on the board | < 200 MB | **72.4 MB** (one card, debug) |
| Installed size | undecided — see §4 | 7.43 MB release, un-shrunk, no assets |
| Drag-to-drop feedback | < 100 ms | not implemented |

The two frame metrics are the ones the plan's "60+ FPS" criterion rests on, and they are the
two that have never been measured. Until they are,
[docs/migration/04-PHASE-0-PREPARATION.md](../migration/04-PHASE-0-PREPARATION.md)'s
performance criteria are unverified.

## 2. Measuring, not guessing

```bash
# Cold start. Ten runs, report the median; a single sample is worthless.
adb shell am force-stop com.tripletriad.android
adb shell am start-activity -W -n com.tripletriad.android/.MainActivity

# Frame timing. The device must be UNLOCKED or this records zero frames.
adb shell dumpsys gfxinfo com.tripletriad.android reset
#   ... drive the interaction ...
adb shell dumpsys gfxinfo com.tripletriad.android | grep -E "Total frames|Janky|percentile"

# Memory, app idle on the screen under test.
adb shell dumpsys meminfo com.tripletriad.android

# Real download size, per device configuration.
bundletool get-size total --apks=app.apks
```

Record the device, OS version and build type with every figure. A debug-build number is a
pessimistic bound, not a result: no R8, no baseline profile, no `profileinstaller` warm-up.

## 3. Compose rules that matter here

Ordered by how much they will matter to *this* app.

### 3.1 Do not allocate in the composition or draw phase

The board redraws on every capture in a combo chain. Hoist `Painter`s, `Brush`es and
`TextStyle`s out with `remember`. A `Color(0xFF…)` per frame per card, times nine tiles,
times a recursive combo, is measurable.

### 3.2 Defer state reads to the phase that needs them

```kotlin
// Bad: any offset change recomposes.
Box(Modifier.offset(x = animatedX))

// Good: layout only; skips recomposition entirely.
Box(Modifier.offset { IntOffset(animatedX.roundToPx(), 0) })
```

Card animations are pure layout and draw changes. They should never recompose.

### 3.3 `graphicsLayer` for anything animated

The PoC's flip animates `rotationY` inside `graphicsLayer`, which keeps it off the
recomposition path. Same for alpha and scale — `Modifier.alpha` on an animated value is a
draw-phase read; setting `alpha` inside `graphicsLayer` is cheaper still.

### 3.4 Keys on lists

The card collection screen shows 263 cards. `LazyVerticalGrid` with a stable
`key = { it.id }` — without it, every insert re-composes the tail.

### 3.5 Stability

`data class Card` with `val` fields and primitives is stable, so Compose can skip
recomposition when it has not changed. Keep it that way: no `List<T>` fields on state
classes passed to composables (use `ImmutableList` or a wrapper), no `var`.

## 4. App size: an open decision

The existing `tto.apk` is **9.67 MB and contains no card artwork** — it downloads its assets
at runtime. The runtime asset payload is roughly **40 MB** (18 MB `cards/`, 8.3 MB
`card_thumbs/`, 6.4 MB `npcs/`, plus UI, rules pages and avatars). The Compose release
baseline alone is 7.43 MB before any asset ships.

So there are three coherent strategies and the project must pick one:

| Strategy | Installed size | Cost |
|---|---|---|
| Ship everything | ~45-50 MB | simple; well past the plan's 20 MB figure |
| Download on demand (what AIR does today) | ~10 MB | needs an asset server and an updater |
| Play Asset Delivery / on-demand resources | ~10 MB install | store-specific, and no iOS equivalent that matches |

Do not carry the "< 20 MB" criterion forward without saying which of these it assumes.

Before measuring release size at all, enable R8: the PoC has `isMinifyEnabled = false`, so
7.43 MB is an upper bound.

## 5. Assets are the performance unknown

Starling batched draw calls through texture atlases. Compose has no atlas support
([docs/analysis/api-mapping.md](../analysis/api-mapping.md) §7). Whichever replacement is
chosen, it has to be measured, not assumed:

- decode time for 263 card images
- memory held by decoded bitmaps while the collection screen scrolls
- draw-call count with nine cards plus effects on screen

This is a Phase 1 spike with a performance acceptance criterion, not an implementation
detail.

## 6. Tooling to add (none of it is in the build yet)

| Tool | For | Status |
|---|---|---|
| androidx.benchmark Macrobenchmark | startup + frame metrics in CI | not added |
| Baseline profiles | usually the biggest Compose cold-start win | not added |
| Compose compiler metrics (`reportsDestination`) | finding unstable parameters and unskippable composables | not added |
| R8 | release size and startup | present but **disabled** |
| Kover | coverage incl. Native targets | not added |
| `bundletool` | real download size | not added |

None of these are in the verified dependency set
([docs/migration/03-TECHNICAL-STACK.md](../migration/03-TECHNICAL-STACK.md), Set C). Adding
them is Phase 1 work, and each one should be verified by actually running it — the PoC
history in this repository is a lesson in what "configured" versus "verified" means.

## 7. Profiling checklist before any performance claim

1. Release build, R8 on, baseline profile installed.
2. Physical device, unlocked, screen on, not thermally throttled.
3. Ten runs minimum; report the median and the 99th percentile, not the best case.
4. Paste the raw tool output into
   [docs/analysis/performance-baseline.md](../analysis/performance-baseline.md), not a summary.

## 8. Related

- [docs/analysis/performance-baseline.md](../analysis/performance-baseline.md) — the measurements
- [testing-strategy.md](./testing-strategy.md)
- [architecture-guidelines.md](./architecture-guidelines.md)
- [docs/migration/16-RISK-ASSESSMENT.md](../migration/16-RISK-ASSESSMENT.md)
