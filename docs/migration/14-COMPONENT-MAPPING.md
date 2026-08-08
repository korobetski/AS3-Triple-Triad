# Component Mapping - Feathers UI to Compose Multiplatform

## 📋 Document Information

- **Purpose**: what is left of the Feathers/Starling → Compose mapping once the UI is ported
- **Status**: reduced 2026-08-08. The UI is built (Phase 4), so the control-by-control mappings
  this document used to carry are superseded by the real composables — read those instead. What
  survives is the layout table, which still explains the shape of the original, and the animation
  mapping, which Phase 6 has yet to use.
- **Last Updated**: 2026-08-08
- **Related**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md),
  [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

## Framework and layout

| Feathers UI | Compose Multiplatform | Notes |
|-------------|----------------------|-------|
| `Screen` | `@Composable` function | Root UI component |
| `ScreenNavigator` | a `remember`ed `Screen` enum | **Not** `NavHost` — see Task 4.3 in [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) for why a navigation library earns nothing at this size |
| `LayoutGroup` | `Column` / `Row` / `Box` | Container layout |
| `HorizontalLayout` | `Row` | Horizontal arrangement |
| `VerticalLayout` | `Column` | Vertical arrangement |
| `AnchorLayout` | `Box` with `Modifier.align()` | Absolute positioning |
| `RelativeLayout` | `ConstraintLayout` | Relative positioning |
| `ScrollContainer` | `LazyColumn` / `LazyRow` | Scrollable content |
| `Scroller` | `ScrollState` + `Modifier.verticalScroll()` | Scroll control |

---

## Animations — still to port, Phase 6

| Starling | Compose | Notes |
|----------|---------|-------|
| `Starling.juggler.tween()` | `animate*AsState` | Property animations |
| `Transitions` | `AnimationSpec` | Easing functions |
| `onComplete` | `finishedListener` | Animation callbacks |
| Custom animation classes | Custom `@Composable` functions | Special animations |

The one example worth keeping, because it shows the shape of the translation — a juggler tween is
imperative and fire-and-forget, while the Compose version is a value derived from state:

```actionscript
Starling.juggler.tween(card, 0.4, {
    transition: Transitions.EASE_IN,
    y: card.y - 100,
    alpha: 0,
    onComplete: afterFly,
    onCompleteArgs: [x, y]
});
```

```kotlin
val offsetY by animateDpAsState(
    targetValue = if (shouldAnimate) (-100).dp else 0.dp,
    animationSpec = tween(durationMillis = 400, easing = LinearEasing),
    finishedListener = { afterFly(x, y) },
)
```

---

## 📞 Related Documents

- **Current System Analysis**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 4 (UI Layer)**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Phase 6 (Animations)**: [10-PHASE-6-ANIMATIONS.md](./10-PHASE-6-ANIMATIONS.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)
