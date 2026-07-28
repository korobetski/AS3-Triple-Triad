# API Mapping — AS3 to Kotlin

Phase 0, Task 1.3 deliverable 3.

Scope: the **167 distinct external imports** actually used by `sources/src/tto`, ranked by
use count from [dependency-matrix.md](./dependency-matrix.md) §6. This is a
type-for-type translation table; for code *patterns* see
[15-CHEAT-SHEET.md](../migration/15-CHEAT-SHEET.md), and for widget-for-widget UI
mapping see [14-COMPONENT-MAPPING.md](../migration/14-COMPONENT-MAPPING.md).

Every row is marked with how much confidence it carries:

| Mark | Meaning |
|---|---|
| ✅ | verified in the Kotlin PoC — it compiles and is covered by a test |
| 🔶 | standard, well-documented equivalent, not yet exercised in this repo |
| ⚠️ | no direct equivalent; needs design work |
| ❌ | no equivalent at all; the feature has to be dropped or replaced wholesale |

---

## 1. The three runtimes being replaced

| Runtime | Imports | What replaces it |
|---|--:|---|
| Starling (GPU display list) | 310 | Compose Multiplatform — Skia-backed, retained-mode |
| Feathers UI (widgets) | 244 | Compose Material 3 + custom composables |
| Adobe AIR / Flash | 148 | Kotlin stdlib, coroutines, `kotlinx.*`, platform APIs |
| Adobe corelib | 14 | Kotlin stdlib (all 14 uses are `ArrayUtil`) |
| as3crypto | 2 | see §6 |

The paradigm change matters more than any single row below. Starling is a **retained**
display list: you construct objects, mutate their `x`/`y`/`alpha`, and add or remove
children. Compose is **declarative**: you describe the frame as a function of state.
`tools.purge(this)` followed by `addChild(…)` — a pattern used throughout `display/` —
has no translation; it becomes a state change.

---

## 2. Starling display

| AS3 | Uses | Kotlin / Compose | | Notes |
|---|--:|---|:-:|---|
| `starling.display.Sprite` | 35 | `Box` / `@Composable fun` | ✅ | 34 of the game's 103 classes extend `Sprite`; each becomes a composable, not a class |
| `starling.display.Image` | 52 | `Image(painter, …)` | 🔶 | textures come from atlases — see §7 |
| `starling.display.Quad` | 7 | `Box(Modifier.background(color))` | ✅ | `CardFace` in the PoC does exactly this |
| `starling.display.DisplayObject` | 22 | — | ⚠️ | the base type has no analogue; usages are mostly `parent`/`stage` walks that disappear |
| `starling.display.DisplayObjectContainer` | 5 | `Box` / `Column` / `Row` | 🔶 | |
| `starling.display.Button` | 10 | `Button` / `Modifier.clickable` | ✅ | |
| `starling.display.BlendMode` | 5 | `BlendMode` in `DrawScope` | 🔶 | Compose's set is smaller; check each use |
| `starling.display.ButtonState` | 2 | `InteractionSource` | 🔶 | |
| `sprite.pivotX` / `pivotY` | — | `graphicsLayer { transformOrigin = … }` | 🔶 | `Card.as:62-63` sets pivot to the sprite centre, which is Compose's default |
| `tools.purge(container)` | 26 (via `tools`) | delete the call | ⚠️ | manual child teardown; in Compose the tree is derived from state |

## 3. Starling text, textures, filters

| AS3 | Uses | Kotlin / Compose | | Notes |
|---|--:|---|:-:|---|
| `starling.text.TextField` | 4 | `Text(…)` | ✅ | |
| `starling.text.TextFieldAutoSize` | 2 | intrinsic sizing | 🔶 | |
| `starling.utils.HAlign` / `VAlign` | 2 | `TextAlign` / `Alignment` | ✅ | |
| `starling.textures.Texture` | 12 | `Painter` / `ImageBitmap` | 🔶 | |
| `starling.textures.TextureAtlas` | 2 | **no equivalent** | ⚠️ | see §7 — the highest-risk item in the whole mapping |
| `starling.textures.SubTexture` | 1 | manual `srcOffset`/`srcSize` crop | ⚠️ | |
| `starling.filters.BlurFilter` | 9 | `Modifier.blur` | 🔶 | Android-only below API 31 for `Modifier.blur`; **check minSdk 24** |
| `starling.filters.ColorMatrixFilter` | 2 | `ColorFilter.colorMatrix` | 🔶 | |
| `flash.filters.DropShadowFilter` | 1 | `Modifier.shadow` | 🔶 | |
| `FilterProvider.whiteBorder` | 3 | `Text` with a stroke `drawStyle` | 🔶 | `utils/FilterProvider.as` |

## 4. Animation

| AS3 | Uses | Kotlin / Compose | | Notes |
|---|--:|---|:-:|---|
| `Starling.juggler.tween(obj, 0.4, {...})` | — | `Animatable` + `animateTo` | ✅ | verified: `BoardCard` ports `Card.flip()`'s four chained tweens as four sequential `animateTo` calls |
| `starling.animation.Transitions.EASE_IN` | 17 | `FastOutLinearInEasing` | 🔶 | Starling's easing curves are not identical to Compose's; a visual diff pass is needed |
| `Transitions.EASE_OUT` | | `LinearOutSlowInEasing` | 🔶 | |
| `starling.animation.Tween` | 1 | `animate*AsState` | ✅ | |
| `onComplete` / `onCompleteArgs` | — | code after `animateTo` returns | ✅ | `animateTo` suspends; the callback chain flattens. The PoC's flip does this. |
| `Event.ENTER_FRAME` | 2 | `withFrameNanos` | 🔶 | |
| `flash.utils.setTimeout` | **30** | `delay()` in a coroutine | 🔶 | the single most-used Flash API; each one becomes a `LaunchedEffect` or a scoped `launch { delay(…) }` |
| `flash.utils.setInterval` / `clearInterval` | 2 / 1 | `while (isActive) { delay(…) }` | 🔶 | cancellation comes free with the scope, which `clearInterval` had to do by hand |
| `flash.utils.getTimer` | 2 | `TimeSource.Monotonic` | 🔶 | used for ping round-trip in `Socket.as` |

## 5. Input

| AS3 | Uses | Kotlin / Compose | | Notes |
|---|--:|---|:-:|---|
| `TouchEvent.TOUCH` + `getTouch(this)` | 10 | `Modifier.pointerInput` | ✅ | `clickable` covers the simple cases |
| `TouchPhase.BEGAN/MOVED/ENDED/HOVER` | 11 | `PointerEventType` | 🔶 | |
| `feathers.dragDrop.DragDropManager` | 3 | `detectDragGestures` | ⚠️ | **not covered by the PoC.** The card→tile drop is the core interaction and the broker pattern has no analogue; the board must own the drop as hoisted state |
| `IDragSource` / `IDropTarget` | 2 | — | ⚠️ | same |
| `feathers.events.DragDropEvent` | 1 | — | ⚠️ | same |
| `flash.ui.Keyboard` / `KeyboardEvent` | 2 | `Modifier.onKeyEvent` | 🔶 | |
| `flash.ui.Mouse` / `MouseCursor` | 2 | `PointerIcon` | 🔶 | desktop only |

## 6. Data, files, crypto, sound

| AS3 | Uses | Kotlin | | Notes |
|---|--:|---|:-:|---|
| `com.adobe.utils.ArrayUtil.arrayContainsValue` | 14 | `List.contains` | ✅ | all 14 uses are this one call; the dependency disappears |
| `JSON.stringify` / `JSON.parse` | — | `kotlinx.serialization` | ✅ | verified in the PoC's `CardCatalogParser` |
| Untyped `Object` as a record | pervasive | `data class` | ✅ | `cards.DATAS[id]` returns `Object`; the PoC replaces it with `Card` |
| `flash.filesystem.File` | 6 | `okio.Path` / platform paths | 🔶 | `File.applicationStorageDirectory` → `Context.filesDir` / `NSDocumentDirectory` |
| `flash.filesystem.FileStream` / `FileMode` | 1 | `okio.FileSystem` | 🔶 | |
| `flash.net.FileReference` | 1 | platform file picker | ⚠️ | |
| `flash.utils.ByteArray` | 2 | `ByteArray` | ✅ | |
| `com.hurlant.crypto.symmetric.AESKey` | 1 | platform crypto | ⚠️ | `utils/CryptoHelper.as` is 21 lines; check whether the save encryption is worth keeping at all |
| `com.hurlant.util.Hex` | 1 | `toHexString()` | 🔶 | |
| `flash.media.Sound` / `SoundChannel` / `SoundMixer` / `SoundTransform` | 4 | AndroidX Media3 + AVFoundation | 🔶 | `expect`/`actual`; Media3 is **not** in the verified dependency set |
| `flash.net.URLLoader` / `URLRequest` / `URLVariables` | 7 | Ktor client | 🔶 | not in the verified set either |
| `flash.net.XMLSocket` | 1 | **nothing compatible** | ❌ | see [network-protocol.md](./network-protocol.md) §1.1 |
| `flash.system.Security.loadPolicyFile` | 1 | — | ❌ | Flash-only concept |

## 7. Assets: the unvalidated risk

`utils/Assets.as` has the highest fan-in of any file in the codebase — **57 files import
it**. It wraps `starling.utils.AssetManager`, which loads Starling texture atlases: a
PNG plus an XML listing sub-rectangles.

```xml
<!-- sources/assets/digits/digits.xml -->
<SubTexture name="cd0" x="42" y="2" width="18" height="18"/>
<SubTexture name="cdbg" x="0" y="62" width="28" height="28"/>
```

Compose Multiplatform has **no atlas support**. Three options, none validated:

1. Parse the atlas XML at runtime and crop with `BitmapPainter` + `srcOffset`/`srcSize`.
   Keeps the assets untouched; costs a custom loader.
2. Slice the atlases into individual files at build time. Loses atlas batching, inflates
   the APK, and there are 263 card images plus UI sheets.
3. Re-author the assets. Only viable if BR-003 forces a reskin anyway.

The Kotlin PoC deliberately draws its card from primitives and loads **no** texture, so
it says nothing about this. Treat it as an open Phase 1 spike, not a solved problem.

## 8. Feathers → Compose

Deliberately not duplicated here — the 67 distinct Feathers imports are mapped in
[14-COMPONENT-MAPPING.md](../migration/14-COMPONENT-MAPPING.md). The four highest-use
ones, for orientation:

| AS3 | Uses | Compose |
|---|--:|---|
| `feathers.controls.Label` | 20 | `Text` |
| `feathers.controls.Header` | 20 | `TopAppBar` |
| `feathers.controls.Panel` | 18 | `Card` / `Surface` |
| `feathers.controls.Screen` | 18 | a `@Composable` route |

`feathers.controls.ScreenNavigator` (1 use, in `Game.as`) is the navigation host; see
[event-catalog.md](./event-catalog.md) §2 for why its string-keyed API should not be
reproduced.

`theme/BaseTTOTheme.as` is **2,290 lines** — the largest file in the project, 13% of the
codebase, and 70 of its imports are Feathers types. It is a Feathers style-provider and
translates to essentially nothing: Compose theming is a `MaterialTheme` plus a handful of
custom tokens. This is the one place where the migration should be *much* smaller than
the original.

## 9. Language-level differences

| AS3 | Kotlin | Notes |
|---|---|---|
| `uint` / `int` | `Int` / `UInt` | AS3 `uint` is 32-bit unsigned; most uses are card ids and indices where `Int` is fine |
| `uint("0x" + power[i])` | explicit `Int` field | `Card.as:316-330` parses power values as **hex** so `'A'` means 10. The PoC resolves this at extraction time |
| `Object` as a map | `Map` / `data class` | |
| `Array` (untyped, mixed) | `List<T>` | `cards.as` stores `power:[1,8,'A',8]` — `Int` and `String` in one array |
| `*` (any type) | `Any?` | |
| `Vector.<T>` | `List<T>` | 1 use, in dead code |
| `for each (var x:T in xml)` | — | E4X; no equivalent. Only in the dead socket handlers |
| class-as-namespace (`cards`, `tools`, `i18n`, `conf`) | `object` / top-level functions | 5 of the game's classes are all-static |
| `[Embed(source="…")]` | Compose resources | `Card.as:54` embeds a TTF; the PoC uses `composeResources` ✅ |

## 10. Related

- [dependency-matrix.md](./dependency-matrix.md) — the import counts this table is ranked by
- [event-catalog.md](./event-catalog.md) — event-by-event mapping
- [network-protocol.md](./network-protocol.md) — the socket rows in detail
- [15-CHEAT-SHEET.md](../migration/15-CHEAT-SHEET.md) — pattern-level idioms
- [README.md](../../README.md) — what the ✅ marks are based on
