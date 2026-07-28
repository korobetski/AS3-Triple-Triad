"""Import the AS3 string bundles into the Compose resource bundle.

Usage (from the repository root):

    python tools/import_locales.py

Reads `sources/bin/datas/locales/{de_DE,en_US,fr_FR,ja_JA}.json` and writes normalised copies
to `shared/src/commonMain/composeResources/files/locales/tto-<tag>.json`. Exits non-zero if a
bundle is unreadable or if `en_US` — the fallback every other locale leans on — is missing a key
the code needs.

## Where the strings actually were

`docs/migration/05-PHASE-1-INFRASTRUCTURE.md` (Task 1.10) says the translated bundles are under
`sources/bin/assets/{de_DE,en_US,fr_FR,ja_JA}/`. They are not. Those four directories hold
`rules.png` + `rulesAtlas.xml`, which is a *texture* atlas of rule-name images. The strings are
JSON already, in `sources/bin/datas/locales/`, which is why this script is 80 lines and not a
converter.

## Why the output is normalised rather than copied

Unlike `import_card_art.py`, which copies bytes so the artwork can be proved identical, these
files are rewritten. Three defects in the source make a byte copy the wrong choice:

1. **Duplicate keys with different values.** `STR_REGISTER_MATCH` appears twice in both `en_US`
   ("Create Match", then "Defy") and `fr_FR` ("Créer la partie", then "Défier"), and
   `STR_GSGROUP` twice in `fr_FR` with the same value. JSON has no duplicate keys, so a parser
   picks one silently — and which one it picks is a *visible product decision* made by an
   implementation detail. This script resolves them the way AS3's `JSON.parse` did, keeping the
   **last** occurrence, so the port shows what the original showed. It prints every one it
   resolved; do not let those lines go unread.

2. **Malformed keys.** `ja_JA` carries `STR_SAVES_LISTは` (a stray kana welded onto the key) and
   `STR_NPC_MA_DINCHT` followed by two zero-width spaces. Both are typos, both are unreachable —
   no lookup can name them — and both are kept verbatim anyway. Deleting source data quietly is
   worse than shipping four dead bytes; they are reported instead.

3. **Inconsistent formatting.** `de_DE` is indented with four spaces and has spaces after its
   colons; the other three do not. Normalising to one shape makes a future diff of a
   *translation* change readable instead of drowning in whitespace.

Sorted keys, UTF-8, no BOM, `ensure_ascii=False` so the Japanese and the accented French stay
legible in a diff.

## What this script deliberately does NOT fix

`fr_FR` puts `<i>FF14 uniquement</i>` at the front of 18 `RULE_*_HELP` values — Feathers
rendered HTML in a text field, Compose's `Text` does not. Nothing displays rule help yet, so the
markup is left in the data rather than stripped from it: when the rules screen is built it needs
an `AnnotatedString` converter, and silently deleting the emphasis now would hide that. The same
18 values are the only place any markup appears; `en_US` has no equivalent prefix at all.

`de_DE` is also 44 keys short of the others and still carries a pre-rename `RULE_OPEN` that no
other locale has. That is not repaired here — the fallback chain covers it at runtime.
"""
import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]
SRC = REPO / "sources" / "bin" / "datas" / "locales"
OUT = REPO / "shared" / "src" / "commonMain" / "composeResources" / "files" / "locales"

# `utils/conf.as:11` — `supportedLanguages`. The order is that file's order.
LOCALES = ["en_US", "fr_FR", "de_DE", "ja_JA"]
FALLBACK = "en_US"

# Keys the Kotlin UI looks up out of the imported bundle. Anything here that is missing from
# en_US would fall through to being rendered as its own key, so it fails the import instead.
# App-owned strings (the `APP_*` keys) are not imported and are not listed here — they live in
# `files/locales/app-<tag>.json`, which is authored, not generated.
REQUIRED = ["STR_NEXT_MATCH", "STR_YOU_WIN", "STR_YOU_LOSE", "STR_DRAW", "RULE_SUDDEN_DEATH"]

PAIR = re.compile(r'"((?:[^"\\]|\\.)*)"\s*:\s*"((?:[^"\\]|\\.)*)"')


def read_bundle(path):
    """Parse `path`, resolving duplicate keys last-wins and reporting each one."""
    text = path.read_text(encoding="utf-8-sig")
    values, duplicates = {}, {}
    for raw_key, raw_value in PAIR.findall(text):
        key = json.loads('"%s"' % raw_key)
        value = json.loads('"%s"' % raw_value)
        if key in values and values[key] != value:
            duplicates.setdefault(key, []).append((values[key], value))
        values[key] = value

    # The regex must agree with a real parser, or a value containing an escaped quote could
    # have been split in two without anyone noticing.
    parsed = json.loads(text)
    if set(parsed) != set(values):
        raise SystemExit(
            "%s: scanner and json.loads disagree on the key set (%d vs %d)"
            % (path.name, len(values), len(parsed))
        )
    return values, duplicates


def main():
    if not SRC.is_dir():
        raise SystemExit("no locale directory at %s" % SRC)
    OUT.mkdir(parents=True, exist_ok=True)

    bundles = {}
    for tag in LOCALES:
        path = SRC / (tag + ".json")
        if not path.is_file():
            raise SystemExit("missing bundle: %s" % path)
        values, duplicates = read_bundle(path)
        bundles[tag] = values
        for key, pairs in sorted(duplicates.items()):
            for kept_over, winner in pairs:
                print("  %s: %s duplicated, kept %r over %r" % (tag, key, winner, kept_over))

    missing = [k for k in REQUIRED if k not in bundles[FALLBACK]]
    if missing:
        raise SystemExit(
            "%s is the fallback and is missing %d key(s) the UI needs: %s"
            % (FALLBACK, len(missing), ", ".join(missing))
        )

    every = set().union(*[set(v) for v in bundles.values()])
    orphans = sorted(every - set(bundles[FALLBACK]))
    for key in orphans:
        holders = [t for t in LOCALES if key in bundles[t]]
        print("  not in %s, so unreachable: %r (only in %s)" % (FALLBACK, key, ", ".join(holders)))

    for tag, values in bundles.items():
        target = OUT / ("tto-%s.json" % tag)
        body = json.dumps(values, ensure_ascii=False, indent=2, sort_keys=True)
        target.write_text(body + "\n", encoding="utf-8")
        gap = len(every) - len(values)
        print(
            "%s -> %s  %d keys%s"
            % (tag, target.relative_to(REPO), len(values), "" if not gap else ", %d absent" % gap)
        )

    print("%d keys across %d locales, union %d" % (len(bundles[FALLBACK]), len(LOCALES), len(every)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
