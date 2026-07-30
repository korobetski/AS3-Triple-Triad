#!/usr/bin/env python3
"""Copy the sounds this port plays out of the AS3 asset tree into the Android host.

    python tools/import_sounds.py

A copy, not a conversion — every file is byte-identical to the AS3 original. **No format change
is needed**, and this docstring records the measurement rather than the assumption:

| | |
|---|---|
| container / codec | MPEG-1 and MPEG-2 Layer III |
| sample rates | 22 050, 44 100 and 48 000 Hz |
| channels | 21 of 22 mono; only the music is stereo |
| bitrate | 17 of 22 are VBR, all with a Xing header |

Every one of those combinations is in Android's **mandatory** decoder set, at every API level this
build supports, so `SoundPool` and `MediaPlayer` take them as they are. The Xing headers matter for
the one file that gets seeked (the music loops back to 16.374 s): without one, a VBR seek would be
a bitrate guess.

## Why `androidApp/src/main/res/raw/` and not `composeResources`

Every other asset in this project lives in `shared/src/commonMain/composeResources/` — 283 images
and four locale bundles — so this is the exception and needs its reason. Compose resources hand out
a `ByteArray`. `SoundPool.load` and `MediaPlayer.setDataSource` want a resource id, a path or a file
descriptor; bridging the two means writing ten files into the cache directory on every cold start
and managing them. `res/raw` gives the host a resource id directly, AAPT stores `.mp3` uncompressed
so it is memory-mapped rather than inflated, and the mapping to `Sound` stays compile-checked in
`AndroidAudioPlayer`.

The cost is that a second host would need its own copy. That cost is currently zero: the desktop
host plays nothing (the JVM cannot decode MP3 without a third-party library) and iOS is descoped.
When either changes, add a destination here.

## What is not copied, and why

`Sound` names ten of the AS3's twenty-two files. The other twelve are listed in `UNUSED` below with
the reason each is left out. Copying them would add 0.36 MB to the APK for sounds nothing can play.
"""

from __future__ import annotations

import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
SOUNDS = REPO / "sources" / "bin" / "sounds"
OUT = REPO / "androidApp" / "src" / "main" / "res" / "raw"
AUDIO_KT = REPO / "shared/src/commonMain/kotlin/com/tripletriad/audio/AudioPlayer.kt"

#: Not copied. The reason is the point of the table.
UNUSED = {
    "flip": "its only two call sites are commented out, in favour of se_ttriad.scd_157",
    "card": "the coin flip (PileOuFace.as), which this port has not implemented",
    "se_ttriad.scd_156": "the coin flip's result, same reason",
    "menu_cancel": "referenced by no call site in the AS3 source",
    "mog": "referenced by no call site",
    "special": "referenced by no call site",
    "win": "referenced by no call site (the win sounds are se_ttriad.scd_7 / _8)",
    "se_ttriad.scd_0": "referenced by no call site",
    "se_ttriad.scd_3": "referenced by no call site",
    "se_ttriad.scd_63": "referenced by no call site",
    "se_ttriad.scd_71": "referenced by no call site",
    "se_ttriad.scd_77": "referenced by no call site",
}


def wanted() -> list[str]:
    """The file stems `Sound` names, read out of the enum so the two cannot drift."""
    source = AUDIO_KT.read_text(encoding="utf-8")
    body = source.split("enum class Sound(", 1)[1].split("\n    ;", 1)[0]
    return re.findall(r'^\s*[A-Z_]+\("([^"]+)"', body, re.M)


def resource_name(stem: str) -> str:
    """`se_ttriad.scd_2` -> `se_ttriad_scd_2`.

    Android resource names take `[a-z0-9_]` only, so the dots have to go. Nothing else about the
    name changes, which keeps the AS3 id readable in `R.raw.*`.
    """
    name = stem.lower().replace(".", "_").replace("-", "_")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
        raise SystemExit(f"{stem!r} does not map to a legal resource name (got {name!r})")
    return name


def main() -> int:
    stems = wanted()
    if not stems:
        return int(bool(print("could not read Sound's file stems from AudioPlayer.kt", file=sys.stderr)))

    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.mp3"):
        stale.unlink()

    copied, missing, total = [], [], 0
    for stem in stems:
        src = SOUNDS / f"{stem}.mp3"
        if not src.is_file():
            missing.append(f"{stem} -> {src}")
            continue
        target = OUT / f"{resource_name(stem)}.mp3"
        target.write_bytes(src.read_bytes())
        total += target.stat().st_size
        copied.append((stem, target.name, target.stat().st_size))

    width = max(len(s) for s, _, _ in copied) if copied else 0
    for stem, name, size in copied:
        print(f"  {stem:{width}s} -> R.raw.{name[:-4]:22s} {size / 1024:8.1f} KB")
    print(f"\ncopied {len(copied)} files, {total / 1024:.1f} KB -> {OUT.relative_to(REPO)}")

    skipped = sum((SOUNDS / f"{s}.mp3").stat().st_size for s in UNUSED if (SOUNDS / f"{s}.mp3").is_file())
    print(f"skipped {len(UNUSED)} files, {skipped / 1024:.1f} KB:")
    for stem, reason in sorted(UNUSED.items()):
        print(f"  {stem:20s} {reason}")

    on_disk = {p.stem for p in SOUNDS.glob("*.mp3")}
    unaccounted = on_disk - set(stems) - set(UNUSED)
    if unaccounted:
        print(f"\n{len(unaccounted)} file(s) neither played nor explained:", file=sys.stderr)
        for stem in sorted(unaccounted):
            print(f"  {stem}", file=sys.stderr)
        return 1

    if missing:
        print(f"\n{len(missing)} MISSING — Sound names a file that is not there:", file=sys.stderr)
        for line in missing:
            print(f"  {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
