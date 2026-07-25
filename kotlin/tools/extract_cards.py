"""Extract the two card tables out of sources/src/tto/datas/cards.as into JSON.

Usage, from the repository root:

    python kotlin/tools/extract_cards.py \
        kotlin/shared/src/commonMain/composeResources/files/cards.json


The AS3 source stores each card as an object literal:

    {name:"STR_FF14_CARD_62", power:[1,8,'A',8], rarity:5, type:null},

`power` is [top, right, bottom, left] (stated in CardDigits.display) and each
element is read back with `uint("0x" + power[i])` in Card.as -- a *hex* parse, so
the literal 'A' means 10. Index 0 of each table is a "Back" sentinel with no
powers; it is not a card and is dropped.

Display names are i18n keys resolved against sources/bin/datas/locales/en_US.json.
"""

import json
import re
import sys
from pathlib import Path

# kotlin/tools/extract_cards.py -> repository root
REPO = Path(__file__).resolve().parents[2]
CARDS_AS = REPO / "sources/src/tto/datas/cards.as"
LOCALE = REPO / "sources/bin/datas/locales/en_US.json"

ENTRY = re.compile(
    r"\{\s*name\s*:\s*\"(?P<name>[^\"]*)\"\s*,"
    r"\s*power\s*:\s*\[(?P<power>[^\]]*)\]\s*,"
    r"\s*rarity\s*:\s*(?P<rarity>\d+)\s*,"
    r"\s*type\s*:\s*(?P<type>null|\"[^\"]*\"|'[^']*')\s*\}"
)


def dataset(src: str, const_name: str) -> str:
    """Return the text of one `private static const X:Array = new Array(...);`."""
    start = src.index(f"{const_name}:Array = new Array(")
    depth = 0
    for i in range(start, len(src)):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return src[start : i + 1]
    raise ValueError(f"unterminated {const_name}")


def power_value(token: str) -> int:
    """Mirror Card.as: uint("0x" + token). 'A' -> 10."""
    t = token.strip().strip("'\"")
    return int(t, 16)


def parse(src: str, const_name: str, collection: str, locale: dict) -> list:
    text = dataset(src, const_name)
    out = []
    for index, m in enumerate(ENTRY.finditer(text)):
        if index == 0:
            # The "Back" sentinel: power:[] , rarity 0. Not a playable card.
            assert m.group("name") == "Back", m.group("name")
            assert m.group("power").strip() == "", m.group("power")
            continue
        powers = [power_value(p) for p in m.group("power").split(",")]
        if len(powers) != 4:
            raise ValueError(f"{const_name}[{index}] has {len(powers)} powers")
        raw_type = m.group("type")
        card_type = None if raw_type == "null" else raw_type.strip("'\"")
        key = m.group("name")
        out.append(
            {
                # `id` is the index into the AS3 array, which is what Card.draw(),
                # CardItem and every save file use as the card's identity. Keep it.
                "id": index,
                "collection": collection,
                "nameKey": key,
                "name": locale.get(key, key),
                "top": powers[0],
                "right": powers[1],
                "bottom": powers[2],
                "left": powers[3],
                "rarity": int(m.group("rarity")),
                "type": card_type,
            }
        )
    return out


def main() -> int:
    src = CARDS_AS.read_text(encoding="utf-8")
    locale = json.loads(LOCALE.read_text(encoding="utf-8"))

    ff14 = parse(src, "FF14_DATAS", "ff14_", locale)
    ff8 = parse(src, "FF8_DATAS", "ff8_", locale)

    # Sanity checks against facts read straight out of the AS3 source.
    assert len(ff14) == 153, len(ff14)
    assert len(ff8) == 110, len(ff8)
    assert [c["id"] for c in ff14] == list(range(1, 154))
    assert [c["id"] for c in ff8] == list(range(1, 111))
    for c in ff14 + ff8:
        for side in ("top", "right", "bottom", "left"):
            assert 1 <= c[side] <= 10, c
        assert 1 <= c["rarity"] <= 5, c
    # cards.as line 82: STR_FF14_CARD_62 power:[1,8,'A',8] -- the hex parse.
    c62 = next(c for c in ff14 if c["id"] == 62)
    assert (c62["top"], c62["right"], c62["bottom"], c62["left"]) == (1, 8, 10, 8), c62
    # cards.as line 182: STR_FF8_CARD_1 power:[1,4,1,5] -- Geezard.
    g = next(c for c in ff8 if c["id"] == 1)
    assert (g["top"], g["right"], g["bottom"], g["left"]) == (1, 4, 1, 5), g

    for name, cards in (("ff14", ff14), ("ff8", ff8)):
        types = sorted({c["type"] for c in cards if c["type"]})
        print(f"{name}: {len(cards)} cards, rarities "
              f"{sorted({c['rarity'] for c in cards})}, types {types}")
        untranslated = [c["nameKey"] for c in cards if c["name"] == c["nameKey"]]
        if untranslated:
            print(f"  {len(untranslated)} names missing from en_US.json: "
                  f"{untranslated[:5]}")

    dest = Path(sys.argv[1])
    dest.parent.mkdir(parents=True, exist_ok=True)
    payload = {"ff14": ff14, "ff8": ff8}
    dest.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
