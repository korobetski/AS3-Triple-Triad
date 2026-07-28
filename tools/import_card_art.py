#!/usr/bin/env python3
"""Copy the card artwork out of the AS3 asset tree into the Compose resource bundle.

Companion to extract_cards.py: that one produces cards.json, this one produces the
images those records point at. Run it from the repository root:

    python tools/import_card_art.py

It is a copy, not a conversion — every file is byte-identical to the AS3 original. The
only transformation is the *name*, normalised to the AS3 texture id so a card's art is
addressable as ``files/art/{collection}{id}.png``, which is exactly ``Card.as:166``:

    _texId = (newID == 'back' || ...) ? newID : _collection + newID;

## Why individual files and not the sprite sheets

``sources/assets/cards/`` ships two ShoeBox atlases. Measured, for the same 8-bit RGBA
encoding:

* individual: 7.00 MB for all 263 cards, of which only the visible ones are ever decoded
  — about 1 MB for a match (19 cards at 104x128 RGBA = 52 KB each);
* atlases: 3.56 MB but covering only 190 of 263 cards (``ff14_cards.xml`` stops at id 80),
  so a complete set needs a third 1024x2048 sheet: about 4.9 MB. Each decoded sheet is
  8 MB of resident bitmap whether one card is on screen or all of them, so a complete set
  costs ~24 MB of RAM permanently.

Trading 2 MB of download for 24 MB of resident memory is the wrong way round on a phone,
and it would also mean repacking ff14 with a tool that is not in this repository. So:
individual files.

``digits.png`` is the one atlas kept, and not for size. It is the only source of the
28x28 ``cdbg`` plate, and its entries are the untrimmed 18x18 rectangles the geometry in
CardColors.kt is built on — the loose ``digits/1.png`` files are trimmed to 15x12 and
would need their offsets re-derived.
"""

from __future__ import annotations

import json
import pathlib
import shutil
import sys

# tools/import_card_art.py -> repository root
REPO = pathlib.Path(__file__).resolve().parents[1]
ASSETS = REPO / "sources" / "assets"
CARDS = ASSETS / "cards"
OUT = REPO / "shared" / "src" / "commonMain" / "composeResources" / "files" / "art"
CATALOG = OUT.parent / "cards.json"

# The four FFXIV tribes and the eight FF8 elements, as CardType serialises them.
TYPES = [
    "beast", "garlean", "primals", "scions",
    "earth", "fire", "holy", "ice", "lightning", "poison", "water", "wind",
]
RARITIES = range(1, 6)


def source_for(collection: str, card_id: int) -> pathlib.Path:
    """Where a card's art lives in the AS3 tree.

    FF8 art sits in its own subdirectory named by bare id; FF14 art sits at the top
    level, zero-padded to three digits.
    """
    if collection == "ff8_":
        return CARDS / "ff8" / f"{card_id}.png"
    return CARDS / f"{card_id:03d}.png"


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)

    copied, missing = 0, []
    for collection_key, prefix in (("ff14", "ff14_"), ("ff8", "ff8_")):
        for card in catalog[collection_key]:
            src = source_for(prefix, card["id"])
            if not src.is_file():
                missing.append(f"{prefix}{card['id']} ({card['name']}) -> {src}")
                continue
            shutil.copyfile(src, OUT / f"{prefix}{card['id']}.png")
            copied += 1

    extras = [
        (CARDS / "back.png", "back.png"),
        (ASSETS / "digits" / "digits.png", "digits.png"),
        *[(ASSETS / "card_rarities" / f"{n}stars.png", f"{n}stars.png") for n in RARITIES],
        *[(ASSETS / "card_types" / f"type-{t}.png", f"type-{t}.png") for t in TYPES],
    ]
    for src, name in extras:
        if not src.is_file():
            missing.append(f"{name} -> {src}")
            continue
        shutil.copyfile(src, OUT / name)
        copied += 1

    total = sum(p.stat().st_size for p in OUT.glob("*.png"))
    print(f"copied {copied} files, {total / 1048576:.2f} MB -> {OUT.relative_to(REPO)}")

    if missing:
        print(f"\n{len(missing)} MISSING — every card in cards.json needs art:", file=sys.stderr)
        for line in missing:
            print(f"  {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
