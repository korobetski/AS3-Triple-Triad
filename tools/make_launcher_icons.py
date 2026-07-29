#!/usr/bin/env python3
"""Generate the Android launcher icons from the AS3 original.

    python tools/make_launcher_icons.py

Reads `sources/assets/appIcons/icon_128.png` — the icon the AIR build shipped, and the only
size of it that exists — and writes `androidApp/src/main/res/`. Run it again after changing
the source art; nothing else in the build depends on it.

**How the source is decomposed.** The original is a teal plate with a white wing on it, and
the two are separated rather than resized together:

* The plate is a *linear gradient at 45°*. Measured, not assumed: fitting a plane per channel
  over the 9 338 opaque background pixels gives `kx == ky` to three decimals and a median
  residual of 4/255. The only pixels that miss the plane are the ~470 hugging the wing, which
  is its drop shadow. So the plate is reproduced as a vector gradient and is exact at every
  density, instead of being an upscaled 128 px bitmap. The drop shadow is *not* reproduced:
  adaptive icons must not carry a baked shadow, since the launcher casts its own from the
  layer geometry, and carrying one only on the legacy bitmaps would make the two disagree.
* The wing is *pure white*, so the red channel is its coverage mask: the plate's red sits at
  0–12 and the wing's at 255. That is what `WING_FLOOR` keys on. Verified on a blend pixel —
  (190, 233, 242) at the wing edge is white at α=0.74 over the fitted plate to within 4/255.

Only the wing is ever resampled, and its mask is re-sharpened afterwards ([`_wing`]), so the
one lossy step is confined to a smooth silhouette rather than to the whole icon.

**Why the adaptive foreground is smaller than 72dp.** The wing reaches a radius of 66.7 px in
the 128 px source — past the plate's own corners. Mapping the plate to the usual 72dp would put
the wing at radius 37.5dp, which a circular mask (r=36) clips. So the plate maps to 63.4dp
instead, keeping every wing pixel inside the 66dp safe circle under any mask shape. The icon
reads slightly smaller than a stock Android Studio import and is never cut.
"""

import math
import pathlib
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - a developer tool, not part of the build
    sys.exit("Pillow is required: python -m pip install pillow")

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "sources/assets/appIcons/icon_128.png"
RES = ROOT / "androidApp/src/main/res"

#: Density buckets, as multiples of the mdpi baseline.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

#: Adaptive-icon canvas, of which the central 72dp is what a mask can show.
ADAPTIVE_DP = 108
#: Legacy launcher icon, for API 24–25, which predate adaptive icons.
LEGACY_DP = 48

#: Red below this is plate, not wing. The plate's red never exceeds 12.
WING_FLOOR = 12
#: The wing's own radius in the source, in pixels from the centre. Measured.
WING_RADIUS = 66.7
#: Radius of the guaranteed-visible circle inside the adaptive canvas.
SAFE_RADIUS = 33.0
#: Corner radius of the source plate, as a fraction of its side. Recovered from the
#: 2 994 transparent corner pixels: r*r*(1 - pi/4) per corner, so r ~= 59 of 128.
PLATE_CORNER = 59 / 128
#: Anti-aliasing factor for the synthesised legacy plates.
SUPERSAMPLE = 4


def load_source():
    """The source split into (plate colour planes, wing coverage mask)."""
    image = Image.open(SOURCE).convert("RGBA")
    width, height = image.size
    pixels = image.load()

    mask = Image.new("L", (width, height))
    mask_pixels = mask.load()
    samples = []
    for y in range(height):
        for x in range(width):
            red, green, blue, alpha = pixels[x, y]
            if alpha < 250:
                # A corner outside the rounded plate. Neither wing nor a usable plate sample:
                # the PNG stores unpremultiplied junk under a zero alpha.
                continue
            coverage = max(0.0, (red - WING_FLOOR) / (255.0 - WING_FLOOR))
            mask_pixels[x, y] = round(255 * min(1.0, coverage))
            if coverage < 0.02:
                samples.append((x, y, red, green, blue))

    planes = [_fit_plane(samples, channel) for channel in range(3)]
    return image.size, planes, mask


def _fit_plane(samples, channel):
    """Least-squares `c = k0 + kx*x + ky*y` for one channel of the plate."""
    n = len(samples)
    sx = sy = sxx = syy = sxy = sc = scx = scy = 0.0
    for x, y, *colour in samples:
        value = colour[channel]
        sx += x
        sy += y
        sxx += x * x
        syy += y * y
        sxy += x * y
        sc += value
        scx += value * x
        scy += value * y
    rows = [[n, sx, sy, sc], [sx, sxx, sxy, scx], [sy, sxy, syy, scy]]
    for i in range(3):
        pivot = max(range(i, 3), key=lambda r: abs(rows[r][i]))
        rows[i], rows[pivot] = rows[pivot], rows[i]
        for r in range(3):
            if r != i:
                factor = rows[r][i] / rows[i][i]
                for k in range(i, 4):
                    rows[r][k] -= factor * rows[i][k]
    return [rows[i][3] / rows[i][i] for i in range(3)]


def plate_colour(planes, fx, fy, side):
    """The fitted plate colour at a fraction of the source square."""
    x, y = fx * (side - 1), fy * (side - 1)
    return tuple(_byte(k[0] + k[1] * x + k[2] * y) for k in planes)


def _byte(value):
    return max(0, min(255, round(value)))


def _hex(colour):
    return "#{:02X}{:02X}{:02X}".format(*colour)


def _wing(mask, size):
    """The wing mask at `size` px, with its edge sharpness restored after resampling.

    A plain LANCZOS upscale softens the silhouette by the scale factor; pushing the mask's
    contrast around 0.5 by that same factor puts the transition back to about one output
    pixel wide. Below 1:1 the resample is already a downscale and needs no help.
    """
    scaled = mask.resize((size, size), Image.LANCZOS)
    factor = size / mask.size[0]
    if factor <= 1.0:
        return scaled
    lut = [_byte(128 + (value - 128) * factor) for value in range(256)]
    return scaled.point(lut)


def adaptive_foreground(mask, size):
    """The wing, white on transparent, on an `ADAPTIVE_DP` canvas."""
    # Scale the plate so the wing's own radius lands on the safe circle rather than on the
    # mask edge -- see the module docstring.
    plate = round(size * (SAFE_RADIUS / WING_RADIUS) * 128 / ADAPTIVE_DP)
    wing = _wing(mask, plate)
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white = Image.new("RGBA", (plate, plate), (255, 255, 255, 255))
    offset = (size - plate) // 2
    canvas.paste(white, (offset, offset), wing)
    return canvas


def legacy_icon(planes, mask, size, side):
    """A pre-adaptive launcher icon: the plate, shaped, with the wing filling it.

    No `android:roundIcon` counterpart is generated. That attribute exists so a launcher on
    API 25 can ask for a circular treatment of a square icon, and this plate is already a
    circle to within a 10 px straight segment per side — the round variant came out visually
    indistinguishable and cost 59 KB of near-duplicate bitmaps.
    """
    scale = SUPERSAMPLE
    big = size * scale

    gradient = Image.new("RGBA", (big, big))
    pixels = gradient.load()
    for y in range(big):
        for x in range(big):
            pixels[x, y] = plate_colour(planes, x / (big - 1), y / (big - 1), side) + (255,)

    shape = Image.new("L", (big, big), 0)
    ImageDraw.Draw(shape).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=round(big * PLATE_CORNER), fill=255
    )
    gradient.putalpha(shape)

    plate = gradient.resize((size, size), Image.LANCZOS)
    wing = _wing(mask, size)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    plate.paste(white, (0, 0), wing)
    # The wing overhangs the plate's corners, so re-apply the silhouette.
    plate.putalpha(
        Image.composite(
            plate.getchannel("A"),
            Image.new("L", (size, size), 0),
            shape.resize((size, size), Image.LANCZOS),
        )
    )
    return plate


def background_vector(planes, side):
    """The plate as a vector gradient, so it stays exact at every density.

    The gradient runs corner to corner of the 108dp canvas, with the colours *extrapolated*
    outward: the source plate covers the 72dp a mask can show, i.e. `t` from 1/6 to 5/6, so
    the endpoints sit a quarter of that span beyond it. The start's blue clamps at 255 —
    the source already sat at 254 there, so the original was clipped in the same place.
    """
    start_src = plate_colour(planes, 0, 0, side)
    end_src = plate_colour(planes, 1, 1, side)
    overshoot = 0.25
    start = tuple(_byte(a - overshoot * (b - a)) for a, b in zip(start_src, end_src))
    end = tuple(_byte(b + overshoot * (b - a)) for a, b in zip(start_src, end_src))
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_launcher_icons.py from sources/assets/appIcons/icon_128.png.
     The AS3 plate is a linear gradient at 45 degrees. Fitting a plane per channel over its
     background pixels gives equal x and y slopes, with a median residual of 4/255. Kept as a
     vector so it is exact at any density; only the wing is ever resampled.
     Visible-region corners are {_hex(start_src)} to {_hex(end_src)}; these endpoints extend
     that past the 72dp mask into the 108dp canvas. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{ADAPTIVE_DP}dp"
    android:height="{ADAPTIVE_DP}dp"
    android:viewportWidth="{ADAPTIVE_DP}"
    android:viewportHeight="{ADAPTIVE_DP}">
    <path android:pathData="M0,0h{ADAPTIVE_DP}v{ADAPTIVE_DP}h-{ADAPTIVE_DP}z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="{ADAPTIVE_DP}"
                android:endY="{ADAPTIVE_DP}"
                android:startColor="{_hex(start)}"
                android:endColor="{_hex(end)}" />
        </aapt:attr>
    </path>
</vector>
"""


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/make_launcher_icons.py.
     `monochrome` is the same white-on-transparent wing, which is exactly what a themed icon
     wants; API 33+ tints it and older releases ignore the tag. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print(f"  {path.relative_to(ROOT).as_posix()}")


def main():
    if not SOURCE.exists():
        sys.exit(f"missing source art: {SOURCE}")
    size, planes, mask = load_source()
    side = size[0]
    print(f"source {SOURCE.relative_to(ROOT).as_posix()} {size[0]}x{size[1]}")
    print(f"plate {_hex(plate_colour(planes, 0, 0, side))} -> "
          f"{_hex(plate_colour(planes, 1, 1, side))}")

    write(RES / "drawable/ic_launcher_background.xml", background_vector(planes, side))
    write(RES / "mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE_XML)

    for bucket, factor in DENSITIES.items():
        out = RES / f"mipmap-{bucket}"
        out.mkdir(parents=True, exist_ok=True)

        foreground = out / "ic_launcher_foreground.png"
        # The wing is white throughout, so grey+alpha carries it exactly and in a third of
        # the bytes of RGBA. Android's PNG decoder reads LA natively.
        adaptive_foreground(mask, round(ADAPTIVE_DP * factor)).convert("LA").save(
            foreground, optimize=True
        )
        print(f"  {foreground.relative_to(ROOT).as_posix()}")

        legacy = out / "ic_launcher.png"
        legacy_icon(planes, mask, round(LEGACY_DP * factor), side).save(legacy, optimize=True)
        print(f"  {legacy.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
