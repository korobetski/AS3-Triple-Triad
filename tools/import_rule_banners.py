#!/usr/bin/env python3
"""Copy the rule-banner artwork out of the AS3 asset tree into the Compose bundle.

Run from the repository root:

    python tools/import_rule_banners.py

These are the twenty 600x90 images the `tto/anims/*Anim.as` classes display: SAME, PLUS,
COMBO, the turn indicators, the win/draw cards, and each special rule's name. A copy, not
a conversion — every file is byte-identical to the AS3 original, renamed from
``121620.tex.png`` to ``121620.png`` so the id in the Kotlin enum is the whole file name.

## Why all four languages ship

**The banners are text.** Each one is the rule's name rendered into a picture, so unlike
the card art there is one per locale and picking the wrong one shows a German player the
English word. The full set is ~1.2 MB for four languages — small enough that shipping all
of it costs less than any mechanism for fetching the right one later would.

This is also why they are *images* and not text drawn at runtime: they carry the game's
own lettering and glow, which the bundled Raleway cannot reproduce. `TalkAnim`'s bubble
is the counter-example and is not imported here — it is a nine-slice frame with real text
composited on top, so it becomes a composable rather than twenty pictures.

## What is deliberately not imported

``121607`` through ``121610`` do not exist in the source tree, and nothing references
them; the AS3 ids jump from 121606 to 121611. The gap is recorded here so the next person
to count twenty files against twenty-four animation classes does not go looking for them.
"""

from __future__ import annotations

import pathlib
import shutil
import sys

# tools/import_rule_banners.py -> repository root
REPO = pathlib.Path(__file__).resolve().parents[1]
SOURCE = REPO / "sources" / "assets" / "triad_rules"
TARGET = REPO / "shared" / "src" / "commonMain" / "composeResources" / "files" / "banners"

# The four the app speaks, spelled as the AS3 spells them — `ja_JA` is not a real region
# code, and the typo is load-bearing because it is also the directory name.
LOCALES = ("en_US", "fr_FR", "de_DE", "ja_JA")

# Every texture the anims reference, checked against `tto/anims/*.as`.
BANNERS = (
    "121601", "121602", "121603", "121604", "121605", "121606",
    "121611", "121612", "121613", "121614", "121615", "121616",
    "121617", "121618", "121619", "121620", "121621", "121622",
    "121623", "121624",
)


def main() -> int:
    if not SOURCE.is_dir():
        print(f"no AS3 asset tree at {SOURCE}", file=sys.stderr)
        return 1

    missing: list[str] = []
    copied = 0

    for locale in LOCALES:
        source = SOURCE / locale
        target = TARGET / locale
        target.mkdir(parents=True, exist_ok=True)

        for banner in BANNERS:
            origin = source / f"{banner}.tex.png"
            if not origin.is_file():
                missing.append(f"{locale}/{banner}")
                continue
            shutil.copyfile(origin, target / f"{banner}.png")
            copied += 1

    print(f"copied {copied} banners into {TARGET.relative_to(REPO)}")

    # A missing banner is a blank flash mid-match rather than a crash, which is exactly
    # the sort of failure that ships. Fail the import instead.
    if missing:
        print(f"missing {len(missing)}: {', '.join(missing)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
