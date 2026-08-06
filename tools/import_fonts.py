#!/usr/bin/env python3
"""Copy the AS3 theme's typeface into `composeResources/font/`.

    python tools/import_fonts.py

A copy, not a conversion — both files are byte-identical to the AS3 originals.

## Which font, and why not the one the migration plan named

`docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1 says the theme font is **Eurostile**, and warns
that redistributing it may need a licence. Both halves are about the wrong font.

`theme/BaseTTOTheme.as:118` declares `FONT_NAME = "Raleway"` and `:115-116` embed exactly two
weights of it — Regular as `normal` and Medium as `bold`. That is the typeface every Feathers
control in the game draws with.

Eurostile appears once, at `display/Card.as:54` and `:81`, and it renders one thing: the `±N`
modifier text on a card during a match. This port does not draw that field, so Eurostile is not
imported. Which is just as well: `sources/assets/fonts/eurostile.TTF` ships with no licence beside
it, where Raleway ships with its own `OFL.txt`.

## The licence travels with the files

Raleway is under the **SIL Open Font License 1.1**, which permits redistribution — bundled in a
larger work, and this is one — provided the licence goes with it. So `OFL.txt` is copied into the
bundle too. Copyright (c) 2010-2012 Matt McInerney, Pablo Impallari, Rodrigo Fuenzalida.

It goes to `files/fonts/` rather than next to the two faces: Compose Resources treats everything in
`composeResources/font/` as a typeface and generates a `Res.font.*` accessor for it, so a `.txt`
there would be a font that cannot be loaded. `files/` is where this project already puts assets it
reads itself.

## Two weights, not twelve

The AS3 tree holds twelve Raleway faces and the theme embeds two of them. Only those two are
copied: the other ten are 1.2 MB of weights nothing asks for, and Compose synthesises the
intermediate ones it needs from the two that are here.
"""

from __future__ import annotations

import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "sources" / "assets" / "fonts" / "Raleway"
RESOURCES = ROOT / "shared" / "src" / "commonMain" / "composeResources"
DESTINATION = RESOURCES / "font"
LICENCE_DESTINATION = RESOURCES / "files" / "fonts"

# AS3 name -> Compose resource file name. Compose Resources derives the accessor from the file
# name, so these must stay lowercase with underscores: `Res.font.raleway_regular`.
FACES = {
    "Raleway-Regular.ttf": "raleway_regular.ttf",
    "Raleway-Medium.ttf": "raleway_medium.ttf",
}

LICENCE = "OFL.txt"


def main() -> int:
    if not SOURCE.is_dir():
        print(f"no AS3 font directory at {SOURCE}", file=sys.stderr)
        return 1

    DESTINATION.mkdir(parents=True, exist_ok=True)
    LICENCE_DESTINATION.mkdir(parents=True, exist_ok=True)

    targets = {origin: DESTINATION / name for origin, name in FACES.items()}
    targets[LICENCE] = LICENCE_DESTINATION / LICENCE

    for origin, target in targets.items():
        source = SOURCE / origin
        if not source.is_file():
            print(f"missing {source}", file=sys.stderr)
            return 1
        shutil.copyfile(source, target)
        where = target.relative_to(RESOURCES)
        print(f"{origin} -> {where.as_posix()} ({source.stat().st_size:,} bytes)")

    print(f"\n{len(targets)} files into {RESOURCES.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
