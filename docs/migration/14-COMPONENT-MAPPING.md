# Component Mapping - Feathers UI to Compose Multiplatform

## 📋 Document Information

- **Purpose**: Map all Feathers UI and Starling components to Compose Multiplatform equivalents
- **Status**: PLANNING COMPLETE
- **Last Updated**: 2026-07-21
- **Related**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md), [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

## 🗃️ Component Categories

---

## 1. Framework Components

### Feathers UI Framework → Compose Multiplatform

| Feathers UI | Compose Multiplatform | Notes | Migration Priority |
|-------------|---------------------|-------|-------------------|
| `Screen` | `@Composable` function | Root UI component | HIGH |
| `ScreenNavigator` | `NavHost` + `NavController` | Navigation container | HIGH |
| `LayoutGroup` | `Column` / `Row` / `Box` | Container layout | HIGH |
| `HorizontalLayout` | `Row` | Horizontal arrangement | HIGH |
| `VerticalLayout` | `Column` | Vertical arrangement | HIGH |
| `AnchorLayout` | `Box` with `Modifier.align()` | Absolute positioning | MEDIUM |
| `RelativeLayout` | `ConstraintLayout` (Compose) | Relative positioning | LOW |
| `ScrollContainer` | `LazyColumn` / `LazyRow` | Scrollable content | HIGH |
| `Scroller` | `ScrollState` + `Modifier.verticalScroll()` | Scroll control | MEDIUM |

---

## 2. UI Controls (from `controls/`)

### Buttons

| Feathers | Compose | Migration Notes |
|----------|---------|----------------|
| `Button` | `Button` | Material 3 by default |
| `MainButton` | Custom `MainButton` composable | Use Material Button with custom styling |
| `ImageButton` | `IconButton` | For icon-only buttons |
| `ToggleButton` | `IconToggleButton` or `Checkbox` | Depending on use case |

**Example - MainButton**:

> ⚠️ **The AS3 excerpt previously shown here was fabricated.** `MainButton` does
> not extend `feathers.controls.Button` and has no `skin` or `labelFactory`.
> Verified signature:
>
> ```actionscript
> // controls/MainButton.as — 402 lines, a hand-rolled Starling button
> [Event(name="triggered", type="starling.events.Event")]
> public class MainButton extends starling.display.DisplayObjectContainer {
>     private static const MAX_DRAG_DIST:Number = 50;
>     private var mUpState:Texture;
>     private var mDownState:Texture;
>     private var mOverState:Texture;
>     private var mDisabledState:Texture;
>     // ... manual TouchEvent handling, ButtonState machine, MouseCursor,
>     //     FilterProvider effects, TextField label
> }
> ```
>
> This matters for estimation: 402 lines of manual state/texture/touch handling
> collapse into a single Material `Button`. It is one of the largest *reductions* in
> the whole migration, not a like-for-like port — the mapping table's "LOW"
> complexity rating is correct, but the reason is that most of the file disappears.

```kotlin

// Kotlin MainButton.kt
@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(0.8f),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
```

---

### Labels and Text

| Feathers | Compose | Migration Notes |
|----------|---------|----------------|
| `Label` | `Text` | Basic text display |
| `TextField` | `TextField` / `OutlinedTextField` | Input fields |
| `TextArea` | `TextField` with `maxLines` | Multi-line input |
| `MGPLabel` | Custom `MGPLabel` composable | Currency display |
| `XPLabel` | Custom `XPLabel` composable | XP display |
| `TouchLabel` | `Text` with `Modifier.clickable()` | Clickable text |

**Example - MGPLabel**:

> ⚠️ **Corrected.** `MGPLabel` extends `feathers.controls.LayoutGroup`, not `Label`.
> It is a *composite*: a `HorizontalLayout` (padding 8, gap 4) containing a
> Starling `Image` currency icon plus a Feathers `Label`, with `touchable = false`
> and a `BlurFilter`. The Kotlin equivalent therefore needs a `Row` with an icon,
> not a bare `Text`:
>
> ```actionscript
> // controls/MGPLabel.as
> public class MGPLabel extends LayoutGroup {
>     private var label:Label;
>     private var PGSIcon:Image;
>     private var _value:uint;
>     public function MGPLabel(MGP_value:uint) { super(); _value = MGP_value; }
>     override protected function initialize():void {
>         super.initialize();
>         this.touchable = false;
>         var HL:HorizontalLayout = new HorizontalLayout();
>         HL.padding = 8; HL.gap = 4;
>         // ...
>     }
> }
> ```

```kotlin
// Kotlin MGPLabel.kt — composite Row, mirroring the AS3 HorizontalLayout
@Composable
fun MGPLabel(
    value: UInt,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Res.drawable.mgp_icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

<details><summary>Previous (incorrect) single-Text version, kept for reference</summary>

```kotlin
@Composable
fun MGPLabel(
    value: UInt,
    modifier: Modifier = Modifier
) {
    Text(
        text = "MGP: $value",
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}
```
</details>

---

### Progress and Charts

| Feathers | Compose | Migration Notes |
|----------|---------|----------------|
| `ProgressBar` | `LinearProgressIndicator` | Horizontal progress |
| `RoundChart` | `CircularProgressIndicator` | Circular progress |

**Example - RoundChart**:

> ⚠️ **Corrected.** `RoundChart` extends `starling.display.Sprite` — it is custom
> drawing, not a `feathers.controls.ProgressBar` subclass (Feathers' `ProgressBar`
> is linear and has no `layout` property). Read `controls/RoundChart.as` before
> assuming `CircularProgressIndicator` is a visual match; if the original draws
> arcs or segments, a `Canvas` with `drawArc` will be closer.

```kotlin
// Kotlin RoundChart.kt
@Composable
fun RoundChart(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 4.dp
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
```

---

## 3. Display Components (from `display/`)

### Cards

| Feathers/Starling | Compose | Migration Notes |
|-------------------|---------|----------------|
| `Card` (display) | `CardComponent` | Main card display |
| `CardThumb` | `CardThumb` | Thumbnail version |
| `CardListThumb` | `CardListThumb` | List thumbnail |
| `CardDigits` | `CardDigits` | Power digits overlay |

**Example - CardComponent**:
```kotlin
// AS3 Card.as (display)
public class Card extends Sprite implements IDragSource {
    private var _id:uint;
    private var _collection:String;
    private var _texId:String;
    private var _data:Object;
    private var _color:String;
    // ... 423 lines
}

// Kotlin CardComponent.kt
@Composable
fun CardComponent(
    card: Card,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isDraggable: Boolean = false,
    onClick: () -> Unit = {}
) {
    // See the dimensions note below.
    val cardWidth = 88.dp
    val cardHeight = 118.dp
    
    Box(
        modifier = modifier
            .size(cardWidth, cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(when (card.color) {
                CardColor.BLUE -> BlueColor
                CardColor.RED -> RedColor
                CardColor.GREY -> GreyColor
            })
            .clickable(enabled = !isDraggable) { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = Color.White
            ),
        contentAlignment = Alignment.Center
    ) {
        // Card image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("file:///android_asset/cards/${card.collection.name.lowercase()}/${card.id}.png")
                .placeholder(R.drawable.card_back)
                .crossfade(true)
                .build(),
            contentDescription = card.nameKey,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        
        // Card power digits
        CardDigits(
            top = card.topPow,
            right = card.rightPow,
            bottom = card.bottomPow,
            left = card.leftPow,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
```

> **Dimensions**: verified against the AS3 source — card `88 x 118`
> (`display/Card.as:73`, `new Quad(88, 118, 0x5a595a)`), tile `136 x 136`
> (`display/Tile.as:51`). Earlier revisions used `104 x 128` for cards, which
> appears nowhere in the source. Note that AS3 values are **pixels at a fixed
> 1280x720 landscape stage**, not density-independent units — treat them as design
> ratios (card ≈ 0.65 x tile) and scale to the viewport rather than hardcoding dp.

---

### Card Digits

```kotlin
// AS3 CardDigits.as
public class CardDigits extends Sprite {
    private var _top:uint, _right:uint, _bottom:uint, _left:uint;
    // Displays power values in corners
}

// Kotlin CardDigits.kt
//
// ⚠️ The previous version placed the four digits in the four CORNERS
// (top→TopStart, right→TopEnd, bottom→BottomStart, left→BottomEnd). That is
// wrong twice over: the values are EDGE powers, not corner values, and the
// bottom/left assignments were swapped relative to any sensible reading.
//
// The AS3 original is a compact DIAMOND badge, ~36x24 px, overlaid on a
// semi-transparent 'cdbg' background image — not spread across the whole card:
//
//   // display/CardDigits.as:14
//   private static const positions:Array =
//       [{x:14, y:0}, {x:26, y:6}, {x:14, y:12}, {x:2, y:6}];
//   //  top          right         bottom        left
//   // plus: cdbg background image at (8, 1), alpha 0.5
//   // digit textures are named 'cd' + value, e.g. 'cd7', 'cdA'
//
// Reproduce it as a fixed-size diamond, then position that badge on the card.
@Composable
fun CardDigits(
    top: UInt,
    right: UInt,
    bottom: UInt,
    left: UInt,
    modifier: Modifier = Modifier
) {
    // 36x24 dp badge, matching the AS3 position extents (2..26 x, 0..12 y,
    // plus one digit's width/height).
    Box(modifier = modifier.size(36.dp, 24.dp)) {
        Image(
            painter = painterResource(Res.drawable.cdbg),
            contentDescription = null,
            alpha = 0.5f,
            modifier = Modifier.offset(8.dp, 1.dp)
        )
        PowerDigit(top,    Modifier.offset(14.dp, 0.dp))
        PowerDigit(right,  Modifier.offset(26.dp, 6.dp))
        PowerDigit(bottom, Modifier.offset(14.dp, 12.dp))
        PowerDigit(left,   Modifier.offset(2.dp,  6.dp))
    }
}

@Composable
fun PowerDigit(value: UInt, modifier: Modifier = Modifier) {
    val hexValue = when (value) {
        10u -> "A"
        11u -> "B"
        12u -> "C"
        13u -> "D"
        14u -> "E"
        15u -> "F"
        else -> value.toString()
    }
    
    Text(
        text = hexValue,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
            .padding(2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White
    )
}
```

---

### Other Display Components

| Feathers/Starling | Compose | Migration Notes |
|-------------------|---------|----------------|
| `Tile` | `TileComponent` | Board tile |
| `ImageExtended` | `AsyncImage` with custom loader | Extended image handling |
| `InventoryItem` | `InventoryItemComponent` | Inventory display |
| `ItemIcon` | `Icon` or `AsyncImage` | Item icon |
| `UserBar` | `UserBarComponent` | User info bar |

---

## 4. Screen Components

### Screen Base Class

| Feathers | Compose | Migration Notes |
|----------|---------|----------------|
| `Screen` | `@Composable` function | Root screen component |
| `ScreenNavigator` | `NavHost` | Navigation host |

**Example - Base Screen Structure**:
```kotlin
// AS3 BaseMatchScreen.as extends Screen
public class BaseMatchScreen extends Screen {
    // ... 448 lines
}

// Kotlin BaseMatchScreen.kt
@Composable
fun BaseMatchScreen(
    viewModel: GameViewModel,
    navController: NavController
) {
    TripleTriadTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background
            Background()
            
            // Board
            BoardComponent(
                board = viewModel.state.collectAsState().value.board,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // Player panels
            PlayerPanel(
                player = viewModel.state.collectAsState().value.bluePlayer,
                color = CardColor.BLUE,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // etc...
        }
    }
}
```

---

## 5. Layout Patterns

### Common Layout Patterns

| Feathers Layout | Compose Equivalent | Notes |
|----------------|-------------------|-------|
| `HorizontalLayout` | `Row` | Default horizontal arrangement |
| `VerticalLayout` | `Column` | Default vertical arrangement |
| `AnchorLayout` | `Box` with alignment | Anchor children to edges |
| `GridLayout` | `LazyVerticalGrid` | Grid of items |

**Example - Complex Layout**:
```actionscript
// AS3 Layout
var layout:LayoutGroup = new LayoutGroup();
layout.layout = new HorizontalLayout();
layout.gap = 10;
layout.padding = 20;
layout.addChild(button1);
layout.addChild(button2);
layout.addChild(button3);
```

```kotlin
// Kotlin Compose
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    MainButton(text = "Button 1", onClick = {})
    MainButton(text = "Button 2", onClick = {})
    MainButton(text = "Button 3", onClick = {})
}
```

---

## 6. Styling and Theming

### Feathers Theme → Compose Theme

| Feathers Theme | Compose Theme | Notes |
|---------------|---------------|-------|
| `TTOTheme` | `TripleTriadTheme` | Custom theme wrapper |
| `BaseTTOTheme` | `MaterialTheme` with custom colors | Base theme |
| Color constants | `Color` values | Define in Colors.kt |
| Font styles | `TextStyle` | Define in Typography.kt |

**Example - Theme Migration**:

> ⚠️ **Corrected.** `TTOTheme` extends `tto.theme.BaseTTOTheme` (declared
> `package tto.theme`), **not** `feathers.themes.BaseTTOTheme` — no such class
> exists. And the two colours shown were misattributed: `0x43a7c8` / `0xbb594f` are
> *text* element-format colours in `BaseTTOTheme.as:1537-1544`, not card colours.
> Card colours live in `display/Card.as:29-31` and are `0x2d4660` (blue),
> `0x602d2d` (red), `0x5a595a` (grey). `0xFF1a1a1a` was invented — the real
> background is `PRIMARY_BACKGROUND_COLOR = 0x202020`.
>
> See the corrected colour table and theme code in
> [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) Task 4.1; it is not duplicated
> here to avoid the two documents drifting apart again.

```actionscript
// AS3 — actual declarations
// theme/TTOTheme.as
package tto.theme {
    public class TTOTheme extends BaseTTOTheme { ... }   // tto.theme.BaseTTOTheme
}
// theme/BaseTTOTheme.as:124-137
protected static const PRIMARY_BACKGROUND_COLOR:uint = 0x202020;
protected static const LIGHT_TEXT_COLOR:uint         = 0xe5e5e5;
protected static const SELECTED_TEXT_COLOR:uint      = 0xff9900;
// display/Card.as:29-31
public static const GREY_COLOR:uint = 0x5a595a;
public static const BLUE_COLOR:uint = 0x2d4660;
public static const RED_COLOR:uint  = 0x602d2d;
```

---

## 7. Event Handling

### Feathers Events → Compose Events

| Feathers | Compose | Notes |
|----------|---------|-------|
| `addEventListener` | `Modifier.clickable` | Click events |
| `TouchEvent.TOUCH` | `Modifier.pointerInput` | Touch/gesture events |
| `Event.COMPLETE` | `LaunchedEffect` + `SharedFlow` | Completion events |
| Custom events | `SharedFlow` / `StateFlow` | Custom event streams |

**Example - Event Migration**:
```actionscript
// AS3
button.addEventListener(TouchEvent.TOUCH, onTouch);

private function onTouch(event:TouchEvent):void {
    if (event.touchPhase == TouchPhase.TAP) {
        // Handle tap
    }
}
```

```kotlin
// Kotlin Compose
Button(
    onClick = { /* Handle click */ }
)

// For custom gesture detection
Box(
    modifier = Modifier
        .clickable { /* Handle click */ }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { /* Handle tap */ },
                onDoubleTap = { /* Handle double tap */ },
                onLongPress = { /* Handle long press */ }
            )
        }
) {
    // Content
}
```

---

## 8. Drag and Drop

### Feathers Drag & Drop → Compose Drag & Drop

| Feathers | Compose | Notes |
|----------|---------|-------|
| `IDragSource` | `Modifier.pointerInput` + `detectDragGestures` | Draggable source |
| `IDropTarget` | `Modifier.pointerInput` + `detectDragGestures` | Drop target |
| `DragDropManager` | Custom `DragManager` class | Drag state management |
| `DragData` | `StateFlow<Card?>` | Dragged data |

**Example - Drag and Drop Migration**:
See [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md) for complete implementation.

---

## 9. Animations

### Starling Animations → Compose Animations

| Starling | Compose | Notes |
|----------|---------|-------|
| `Starling.juggler.tween()` | `animate*AsState` | Property animations |
| `Transitions` | `AnimationSpec` | Easing functions |
| `onComplete` | `finishedListener` | Animation callbacks |
| Custom animation classes | Custom `@Composable` functions | Special animations |

**Example - Animation Migration**:
```actionscript
// AS3 Starling animation
Starling.juggler.add(flippable);
Starling.juggler.tween(card, 0.4, {
    transition: Transitions.EASE_IN,
    y: card.y - 100,
    alpha: 0,
    onComplete: afterFly,
    onCompleteArgs: [x, y]
});
```

```kotlin
// Kotlin Compose animation
val offsetY by animateDpAsState(
    targetValue = if (shouldAnimate) (-100).dp else 0.dp,
    animationSpec = tween(
        durationMillis = 400,
        easing = LinearEasing
    ),
    finishedListener = { afterFly(x, y) }
)

Box(modifier = Modifier.offset(y = offsetY)) {
    // Content
}
```

---

## 10. Image Handling

### Starling Images → Compose Images

| Starling | Compose | Notes |
|----------|---------|-------|
| `Image` | `AsyncImage` (Coil) | Async image loading |
| `Sprite` | `Box` + `Canvas` | Custom drawing |
| `Texture` | `Painter` | Image painter |
| `Quad` | `Box` with background | Solid color rectangle |
| `[Embed]` | Resource assets | Compile-time embedding not needed |

**Example - Image Migration**:
```actionscript
// AS3 Embed
[Embed(source = "../../assets/cards/1.png")]
private static const CARD_1:Class;

var image:Image = new Image(CARD_1);
addChild(image);
```

```kotlin
// Kotlin Compose
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data("file:///android_asset/cards/ff14/1.png")
        .placeholder(R.drawable.card_back)
        .crossfade(true)
        .build(),
    contentDescription = "Card 1",
    modifier = Modifier.size(88.dp, 118.dp)
)
```

---

## 🎯 Migration Priority

### High Priority (Must be migrated first)
1. **Screen** → `@Composable` functions
2. **ScreenNavigator** → `NavHost` + `NavController`
3. **Button** → `Button` / `MainButton`
4. **Label** → `Text`
5. **LayoutGroup** → `Column` / `Row` / `Box`
6. **Card** → `CardComponent`
7. **Tile** → `TileComponent`
8. **CardDigits** → `CardDigits`

### Medium Priority
1. **ImageButton** → `IconButton`
2. **ProgressBar** → `ProgressIndicator`
3. **ScrollContainer** → `LazyColumn`
4. **CardThumb** → `CardThumb`
5. **InventoryItem** → `InventoryItemComponent`
6. **UserBar** → `UserBarComponent`

### Low Priority
1. **TextField** → `TextField`
2. **RoundChart** → `CircularProgressIndicator`
3. **ImageExtended** → Custom image component
4. **AvatarChooser** → `AvatarChooser`

---

## ✅ Migration Checklist

### Framework Components
- [ ] Screen → Composable
- [ ] ScreenNavigator → NavHost
- [ ] LayoutGroup → Column/Row/Box
- [ ] HorizontalLayout → Row
- [ ] VerticalLayout → Column

### UI Controls
- [ ] Button → Button
- [ ] MainButton → MainButton
- [ ] Label → Text
- [ ] MGPLabel → MGPLabel
- [ ] XPLabel → XPLabel
- [ ] TouchLabel → Text with clickable
- [ ] ProgressBar → ProgressIndicator
- [ ] RoundChart → CircularProgressIndicator

### Display Components
- [ ] Card → CardComponent
- [ ] CardThumb → CardThumb
- [ ] CardListThumb → CardListThumb
- [ ] CardDigits → CardDigits
- [ ] Tile → TileComponent
- [ ] ImageExtended → AsyncImage wrapper
- [ ] InventoryItem → InventoryItemComponent
- [ ] ItemIcon → Icon/AsyncImage
- [ ] UserBar → UserBarComponent

### Animations
- [ ] All 24 animation classes → Compose animations

---

## 📞 Related Documents

- **Current System Analysis**: [02-CURRENT-SYSTEM-ANALYSIS.md](./02-CURRENT-SYSTEM-ANALYSIS.md)
- **Phase 4 (UI Layer)**: [08-PHASE-4-UI-LAYER.md](./08-PHASE-4-UI-LAYER.md)
- **Cheat Sheet**: [15-CHEAT-SHEET.md](./15-CHEAT-SHEET.md)

---

*Generated: 2026-07-21*  
*Status: PLANNING COMPLETE*
