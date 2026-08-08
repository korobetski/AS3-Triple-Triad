"""Extract the two tournament ladders out of the AS3 screens that declare them.

Usage, from the repository root:

    python tools/extract_campaigns.py \
        shared/src/commonMain/composeResources/files/campaigns.json


## Why this exists

`CCGroupMatchScreen.as:30-108` and `GSGroupMatchScreen.as:31-110` each open with a
literal array of seven and six `new NPC({...})` calls -- rules, card pools, reward
tables and drop rates, written into a *screen*. That is the shape `shopScreen` had
before Phase 2 pulled its price tables into ShopCatalog, and it is why the migration
notes call these six screens "data, not UI".

The opponent records use the same object literal as NPCs.as, so this script reuses
extract_npcs.py's parser wholesale rather than writing a second one. What it adds is
the ladder around them: the entry fee, the order, and the per-opponent dialogue.

## The two ladders

* **cc** -- the Card Club, `ff8_` only (`PVEScreen.as:84`). Seven opponents, from
  Jack to the King. Every one of its `messages` is the empty string, so the Card
  Club never speaks; the `TalkAnim` import in its screen is dead.
* **gs** -- the Gold Saucer tournament, `ff14_` only (`PVEScreen.as:91`). Six
  opponents, and this one does talk.

Both charge 500 MGP up front (`CCGroupScreen.as:88`, `GSGroupScreen.as:89`), which
is why their opponents' own `matchFee` is almost always 0 -- see the report this
prints, which names the one that is not.

## The dialogue is untranslated in the original

`messages` are Flash string literals with no `i18n.gettext` around them and no key
in any of the four bundles, exactly like `TutorialScreen.helpTexts`. They are copied
through as literal English text rather than invented keys: a key that no bundle
defines would render as the key itself.

## These are not the catalogue's opponents

Every one of the thirteen also exists in npcs.json under the same iconID, with
**different rules, fees and card pools** -- the ladder versions almost all add
`RULE_SUDDEN_DEATH`, and the Gold Saucer's Triple Triad Master pays more than twice
what the catalogue's does. So a ladder entry is a whole opponent record and not an
override of one; the original constructs them inline and never consults `NPCs.LIST`.
The differences are reported on every run so a future re-import cannot silently
flatten them.
"""

import json
import re
import sys
from pathlib import Path

from extract_npcs import (
    CARDS_AS,
    NPC_AS,
    RULES_AS,
    card_rarities,
    constants,
    entries,
    field,
    parse,
)

# tools/extract_campaigns.py -> repository root
REPO = Path(__file__).resolve().parents[1]
SCREENS = REPO / "sources/src/tto/screens"
NPCS_JSON = REPO / "shared/src/commonMain/composeResources/files/npcs.json"

# `panel.title = i18n.gettext("STR_CCGROUP")` and the 500 in both entry screens.
LADDERS = [
    {
        "key": "cc",
        "collection": "ff8_",
        "nameKey": "STR_CCGROUP",
        "match": "CCGroupMatchScreen.as",
        "entry": "CCGroupScreen.as",
        "expected": 7,
    },
    {
        "key": "gs",
        "collection": "ff14_",
        "nameKey": "STR_GSGROUP",
        "match": "GSGroupMatchScreen.as",
        "entry": "GSGroupScreen.as",
        "expected": 6,
    },
]

FEE = re.compile(r"MGP\s*-=\s*(?P<fee>\d+)")
MESSAGES = re.compile(r"messages\s*:\s*(?P<body>\{[^{}]*\})")


def fee_of(entry_screen: Path) -> int:
    """`Game.PROFILE_DATAS.MGP -= 500` in the entry screen's Start handler."""
    match = FEE.search(entry_screen.read_text(encoding="utf-8", errors="replace"))
    if not match:
        raise ValueError(f"no entry fee in {entry_screen.name}")
    return int(match.group("fee"))


def messages(text: str) -> list:
    """Each `messages:{start:"", draw:"", win:"", lose:""}`, in declaration order.

    Kept even when every field is empty -- that a ladder is silent is a fact about
    it, and an absent list would read as "not extracted yet".
    """
    out = []
    for match in MESSAGES.finditer(text):
        body = match.group("body")
        spoken = {
            key: (found.group(1) if (found := re.search(
                rf'{key}\s*:\s*"((?:[^"\\]|\\.)*)"', body)) else "")
            for key in ("start", "win", "lose", "draw")
        }
        out.append({k: v for k, v in spoken.items() if v})
    return out


def ladder(spec: dict, rule_constants: dict, levels: dict, tables: dict) -> dict:
    text = (SCREENS / spec["match"]).read_text(encoding="utf-8", errors="replace")
    # The array is the constructor's only statement, but `initialize()` below it
    # mentions `matches[STEP]`, so the slice stops at the first method after it.
    body = text[text.index("this.matches = ["):text.index("override protected")]

    opponents = parse(body, rule_constants, levels, tables, require_id=False)
    lines = messages(body)
    if len(lines) != len(opponents):
        raise ValueError(
            f"{spec['key']}: {len(opponents)} opponents but {len(lines)} message sets"
        )
    steps = []
    for step, (opponent, spoken) in enumerate(zip(opponents, lines)):
        # Every Card Club record omits `id`, and the Gold Saucer's repeat the
        # catalogue's -- so neither identifies a *rung*. The step does, and it is
        # what `NEXT_STEP` counts, so that is what the id becomes here.
        opponent["id"] = step + 1
        # `{npc:..., messages:...}`, which is the AS3's own shape for a rung.
        steps.append({"npc": opponent, "messages": spoken})

    return {
        "key": spec["key"],
        "nameKey": spec["nameKey"],
        "collection": spec["collection"],
        "fee": fee_of(SCREENS / spec["entry"]),
        "steps": steps,
    }


def report_against_catalog(ladders: list) -> None:
    """Name every field a ladder opponent disagrees with its catalogue twin on."""
    if not NPCS_JSON.is_file():
        print("npcs.json not built yet; skipping the catalogue comparison")
        return
    catalog = json.loads(NPCS_JSON.read_text(encoding="utf-8"))
    by_icon = {
        collection: {n["iconID"]: n for n in table}
        for collection, table in (("ff8_", catalog["ff8"]), ("ff14_", catalog["ff14"]))
    }
    for entry in ladders:
        table = by_icon[entry["collection"]]
        for step in entry["steps"]:
            opponent = step["npc"]
            twin = table.get(opponent["iconID"])
            if twin is None:
                print(f"  {entry['key']}/{opponent['iconID']}: not in npcs.json")
                continue
            differs = [
                key for key in ("rules", "cards", "matchFee", "MGPReward", "level")
                if opponent[key] != twin[key]
            ]
            if differs:
                print(f"  {entry['key']}/{opponent['iconID']}: differs on {differs}")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2

    cards_source = CARDS_AS.read_text(encoding="utf-8", errors="replace")
    tables = {
        "ff14_": card_rarities(cards_source, "FF14_DATAS"),
        "ff8_": card_rarities(cards_source, "FF8_DATAS"),
    }
    rule_constants = constants(RULES_AS, "tripleTriadRules")
    levels = constants(NPC_AS, "NPC")

    ladders = [ladder(spec, rule_constants, levels, tables) for spec in LADDERS]

    for spec, entry in zip(LADDERS, ladders):
        count = len(entry["steps"])
        assert count == spec["expected"], f"{spec['key']}: {count} != {spec['expected']}"
        speaking = sum(1 for s in entry["steps"] if s["messages"])
        paying = [s["npc"]["iconID"] for s in entry["steps"] if s["npc"]["matchFee"]]
        note = f", and {paying} still charge a fee of their own" if paying else ""
        print(
            f"{entry['key']}: {count} opponents, {entry['fee']} MGP to enter, "
            f"{speaking} of them speak{note}"
        )

    print("against npcs.json:")
    report_against_catalog(ladders)

    dest = Path(sys.argv[1])
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(
        json.dumps({"campaigns": ladders}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
