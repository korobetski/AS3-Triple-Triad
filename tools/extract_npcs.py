"""Extract the two NPC tables out of sources/src/tto/datas/NPCs.as into JSON.

Usage, from the repository root:

    python tools/extract_npcs.py \
        shared/src/commonMain/composeResources/files/npcs.json


The AS3 source builds each opponent with a constructor call taking an object
literal:

    new NPC( {
        id:2,
        name:'STR_NPC_Jonas',
        iconID:'jonas',
        rules:[tripleTriadRules.RULE_ALL_OPEN, tripleTriadRules.RULE_SAME],
        fetishesCards:[23, 3, 4],
        cards:[11, 15, 20, 26],
        level:NPC.LEVEL_NOVICE,
        matchFee:10,
        MGPReward: { w:22, d:8, l:3 },
        difficulty:2,
        itemRewards:[{type:"card", card:15, rate:0.25}, ...]
    }),

Three things need resolving rather than copying:

* `tripleTriadRules.RULE_*` and `NPC.LEVEL_*` are constant *references*. Their
  values are read out of tripleTriadRules.as and NPC.as, so a renamed constant
  fails loudly here instead of producing a rule string nothing matches.
* two entries call `cards.getCardsByRarities([...], 'ff8_')` instead of listing
  ids. That function returns the *indices* of every card of those rarities
  (cards.as:308-317), so it is evaluated here against cards.as.
* `availability` is in **hours of the day**, not a date range -- the AS3 comments
  say so and NPCs.toListCollection compares it against `getHours()`. It is copied
  through as hours; see Availability.kt for why the original's filter could not
  work.

Note ff8 ids are **not unique** (id 2 and id 13 appear twice). That is checked
for and reported, not repaired: nothing in the game keys anything by NPC id --
wins are recorded under iconID -- and inventing ids here would make this file
disagree with the source it came from.
"""

import json
import re
import sys
from pathlib import Path

# tools/extract_npcs.py -> repository root
REPO = Path(__file__).resolve().parents[1]
NPCS_AS = REPO / "sources/src/tto/datas/NPCs.as"
CARDS_AS = REPO / "sources/src/tto/datas/cards.as"
RULES_AS = REPO / "sources/src/tto/datas/tripleTriadRules.as"
NPC_AS = REPO / "sources/src/tto/datas/NPC.as"

# Counts read straight off the source, asserted after parsing.
EXPECTED = {"ff14": 60, "ff8": 25}

CONST = re.compile(
    r"public static const (?P<name>\w+):String\s*=\s*'(?P<value>[^']*)'"
)
CARD_ENTRY = re.compile(r"\{\s*name\s*:\s*\"[^\"]*\"\s*,\s*power\s*:\s*\[[^\]]*\]\s*,"
                        r"\s*rarity\s*:\s*(?P<rarity>\d+)\s*,")
BY_RARITIES = re.compile(
    r"cards\.getCardsByRarities\(\s*\[(?P<rarities>[\d,\s]*)\]\s*"
    r"(?:,\s*'(?P<collection>[^']*)')?\s*\)"
)


def constants(source: Path, class_name: str) -> dict:
    """Map `Class.CONST_NAME` to its string value, for reference resolution."""
    text = source.read_text(encoding="utf-8", errors="replace")
    return {
        f"{class_name}.{m.group('name')}": m.group("value")
        for m in CONST.finditer(text)
    }


def card_rarities(source: str, const_name: str) -> list:
    """Rarity per card index for one table, index 0 being the "Back" sentinel."""
    start = source.index(f"{const_name}:Array = new Array(")
    depth = 0
    for i in range(start, len(source)):
        if source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
            if depth == 0:
                text = source[start : i + 1]
                break
    else:
        raise ValueError(f"unterminated {const_name}")
    return [int(m.group("rarity")) for m in CARD_ENTRY.finditer(text)]


def by_rarities(rarities: list, collection: str, tables: dict) -> list:
    """Reproduce cards.getCardsByRarities: the indices of matching cards.

    Index 0 is the "Back" sentinel with rarity 0, so it is never selected --
    which is why the ids this returns line up with Card.id.
    """
    table = tables[collection]
    return [i for i, rarity in enumerate(table) if rarity in rarities]


def entries(text: str) -> list:
    """Split one `[ new NPC({...}), ... ]` literal into its object literals."""
    out = []
    marker = "new NPC( {"
    index = text.find(marker)
    while index != -1:
        start = text.index("{", index + len("new NPC("))
        depth = 0
        for i in range(start, len(text)):
            if text[i] in "{[":
                depth += 1
            elif text[i] in "}]":
                depth -= 1
                if depth == 0:
                    out.append(text[start : i + 1])
                    break
        else:
            raise ValueError("unterminated NPC literal")
        index = text.find(marker, i)
    return out


def field(literal: str, name: str) -> str | None:
    """The raw text of one top-level `name: value` field, or None if absent."""
    match = re.search(rf"(?<![\w']){re.escape(name)}\s*:", literal)
    if not match:
        return None
    start = match.end()
    depth = 0
    for i in range(start, len(literal)):
        char = literal[i]
        # Parentheses count too: `cards:cards.getCardsByRarities([1,2], 'ff8_')`
        # has a top-level-looking comma inside a call.
        if char in "{[(":
            depth += 1
        elif char in "}])":
            if depth == 0:
                return literal[start:i].strip()
            depth -= 1
        elif char == "," and depth == 0:
            return literal[start:i].strip()
    raise ValueError(f"unterminated field {name}")


def int_list(raw: str, tables: dict) -> list:
    """A `[1, 2, 3]` literal, or a getCardsByRarities call, as a list of ints."""
    if raw is None:
        return []
    call = BY_RARITIES.search(raw)
    if call:
        rarities = [int(r) for r in call.group("rarities").split(",") if r.strip()]
        collection = call.group("collection") or "ff14_"
        return by_rarities(rarities, collection, tables)
    inner = raw.strip().lstrip("[").rstrip("]")
    return [int(token) for token in inner.split(",") if token.strip()]


def rules(raw: str, rule_constants: dict) -> list:
    """`[tripleTriadRules.RULE_SAME, ...]` as the constants' string values."""
    if raw is None:
        return []
    out = []
    for token in re.findall(r"[\w.]+", raw):
        if token in rule_constants:
            out.append(rule_constants[token])
        elif token:
            raise ValueError(f"unknown rule constant {token!r}")
    return out


def reward(raw: str) -> dict:
    """A `{ w:22, d:8, l:3 }` literal."""
    if raw is None:
        return {"w": 0, "d": 0, "l": 0}
    values = dict(re.findall(r"(\w+)\s*:\s*(-?\d+)", raw))
    return {key: int(values.get(key, 0)) for key in ("w", "d", "l")}


def item_rewards(raw: str) -> list:
    """A `[{type:"card", card:15, rate:0.25}, ...]` literal."""
    if raw is None:
        return []
    out = []
    for obj in re.findall(r"\{[^{}]*\}", raw):
        entry = {}
        type_match = re.search(r"type\s*:\s*[\"'](\w+)[\"']", obj)
        # `getRewardItem` branches on the payload key, not on `type`, so an entry
        # with no `type` is still meaningful. Infer it from the payload.
        for key in ("card", "potion", "booster"):
            value = re.search(rf"\b{key}\s*:\s*(?:(\d+)|[\"']([^\"']+)[\"'])", obj)
            if value:
                entry[key] = int(value.group(1)) if value.group(1) else value.group(2)
        rate = re.search(r"rate\s*:\s*([\d.]+)", obj)
        if not entry:
            raise ValueError(f"item reward with no payload: {obj}")
        inferred = next(k for k in ("card", "potion", "booster") if k in entry)
        entry["type"] = type_match.group(1) if type_match else inferred
        entry["rate"] = float(rate.group(1)) if rate else 0.0
        out.append(entry)
    return out


def availability(raw: str) -> dict | None:
    """A `{ begins:20, ends:8 }` literal, in hours of the day."""
    if raw is None:
        return None
    values = dict(re.findall(r"(\w+)\s*:\s*(\d+)", raw))
    begins, ends = int(values.get("begins", 0)), int(values.get("ends", 0))
    for label, hour in (("begins", begins), ("ends", ends)):
        if not 0 <= hour <= 24:
            raise ValueError(f"availability.{label} is not an hour: {hour}")
    return {"begins": begins, "ends": ends}


def parse(text: str, rule_constants: dict, levels: dict, tables: dict) -> list:
    out = []
    for literal in entries(text):
        level_ref = field(literal, "level")
        if level_ref is not None and level_ref not in levels:
            raise ValueError(f"unknown level constant {level_ref!r}")
        npc = {
            "id": int(field(literal, "id")),
            "name": field(literal, "name").strip("'\""),
            "iconID": field(literal, "iconID").strip("'\""),
            "rules": rules(field(literal, "rules"), rule_constants),
            "fetishesCards": int_list(field(literal, "fetishesCards"), tables),
            "cards": int_list(field(literal, "cards"), tables),
            "level": levels[level_ref] if level_ref else "STR_NPC_LEVEL_NONE",
            "matchFee": int(field(literal, "matchFee") or 0),
            "MGPReward": reward(field(literal, "MGPReward")),
            "itemRewards": item_rewards(field(literal, "itemRewards")),
            "difficulty": int(field(literal, "difficulty") or 0),
        }
        window = availability(field(literal, "availability"))
        if window:
            npc["availability"] = window
        out.append(npc)
    return out


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    source = NPCS_AS.read_text(encoding="utf-8", errors="replace")
    cards_source = CARDS_AS.read_text(encoding="utf-8", errors="replace")
    tables = {
        "ff14_": card_rarities(cards_source, "FF14_DATAS"),
        "ff8_": card_rarities(cards_source, "FF8_DATAS"),
    }
    # 153 cards + the Back sentinel, 110 + the sentinel. Same facts
    # extract_cards.py asserts, restated so a change to cards.as fails in both.
    assert len(tables["ff14_"]) == 154, len(tables["ff14_"])
    assert len(tables["ff8_"]) == 111, len(tables["ff8_"])

    rule_constants = constants(RULES_AS, "tripleTriadRules")
    levels = constants(NPC_AS, "NPC")

    split = source.index("FF8_NPCS:Array")
    ff14 = parse(source[source.index("FF14_NPCS:Array"):split], rule_constants, levels, tables)
    ff8 = parse(source[split:], rule_constants, levels, tables)

    for name, npcs in (("ff14", ff14), ("ff8", ff8)):
        assert len(npcs) == EXPECTED[name], f"{name}: {len(npcs)} != {EXPECTED[name]}"
        icons = [n["iconID"] for n in npcs]
        assert len(set(icons)) == len(icons), "duplicate iconID -- NPC_W keys collide"
        ids = [n["id"] for n in npcs]
        duplicates = sorted({i for i in ids if ids.count(i) > 1})
        note = f", duplicate ids {duplicates}" if duplicates else ""
        windows = sum(1 for n in npcs if "availability" in n)
        print(f"{name}: {len(npcs)} npcs, {windows} with an availability window{note}")

    # Across the two tables an iconID *may* repeat, and one does: the Queen of Cards
    # is declared in both with the same id (99), name and icon, differing only in her
    # card pool. Since NPC_W is one flat map keyed by iconID, her wins pool across
    # collections -- which is right, being one character. Reported rather than
    # asserted either way, so a second, accidental collision is visible.
    shared = sorted({n["iconID"] for n in ff14} & {n["iconID"] for n in ff8})
    if shared:
        print(f"iconID present in both collections: {shared}")

    dest = Path(sys.argv[1])
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(
        json.dumps({"ff14": ff14, "ff8": ff8}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
