"""Generate docs/analysis/dependency-matrix.md from sources/src/tto.

Run from the repository root:

    python docs/analysis/tools/analyse_as3.py

The point of generating this rather than writing it by hand is that it can be
re-run after any change to the AS3 tree, and the numbers in the migration plan can
be checked instead of trusted.
"""

from __future__ import annotations

import collections
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
TTO = REPO / "sources" / "src" / "tto"
OUT = REPO / "docs" / "analysis" / "dependency-matrix.md"

IMPORT = re.compile(r"^\s*import\s+([\w.]+);", re.M)
CLASS = re.compile(
    r"public(?:\s+final)?(?:\s+dynamic)?\s+class\s+(\w+)"
    r"(?:\s+extends\s+([\w.]+))?"
    r"(?:\s+implements\s+([\w.,\s]+?))?\s*\{"
)
# `\s*` not `\s+`: `TTONet.as` declares the default package as bare `package {`.
PACKAGE = re.compile(r"^\s*package\s*([\w.]*)\s*\{", re.M)

# Which runtime each external package belongs to, for the replacement table.
RUNTIME = [
    ("starling.", "Starling (GPU display list)"),
    ("feathers.", "Feathers UI (widget toolkit)"),
    ("flash.", "Adobe AIR / Flash runtime"),
    ("com.adobe.", "Adobe corelib (vendored)"),
    ("com.hurlant.", "as3crypto (vendored)"),
]


def runtime_of(imp: str) -> str:
    for prefix, name in RUNTIME:
        if imp.startswith(prefix):
            return name
    return "other"


def main() -> None:
    files = sorted(TTO.rglob("*.as"))
    # utf-8-sig: several of these files carry a UTF-8 BOM, which otherwise defeats any
    # regex anchored with `^` on the first line.
    src = {p: p.read_text(encoding="utf-8-sig", errors="replace") for p in files}
    bom = [p for p in files
           if p.read_bytes().startswith(b"\xef\xbb\xbf")]

    def rel(p: Path) -> str:
        return str(p.relative_to(TTO)).replace("\\", "/")

    def pkg_of(p: Path) -> str:
        r = rel(p)
        return r.rsplit("/", 1)[0] if "/" in r else "(root)"

    internal: dict[str, set[str]] = {}
    external: dict[str, list[str]] = {}
    declared_package: dict[str, str] = {}
    classes: dict[str, tuple[str, str | None, str | None]] = {}

    for p, text in src.items():
        r = rel(p)
        imps = IMPORT.findall(text)
        internal[r] = {i for i in imps if i.startswith("tto.")}
        external[r] = [i for i in imps if not i.startswith("tto.")]
        m = PACKAGE.search(text)
        declared_package[r] = (m.group(1) or "(default)") if m else "(none)"
        c = CLASS.search(text)
        if c:
            classes[r] = (
                c.group(1),
                c.group(2),
                " ".join(c.group(3).split()) if c.group(3) else None,
            )

    loc = {rel(p): len(src[p].splitlines()) for p in files}
    total_loc = sum(loc.values())

    # tto.a.B -> a/B.as, so fan-in can be counted per file.
    def import_to_rel(imp: str) -> str:
        return imp[len("tto."):].replace(".", "/") + ".as"

    fan_in: collections.Counter[str] = collections.Counter()
    for deps in internal.values():
        for d in deps:
            fan_in[import_to_rel(d)] += 1

    packages = sorted({pkg_of(p) for p in files})
    pkg_edges: collections.Counter[tuple[str, str]] = collections.Counter()
    for p in files:
        for d in internal[rel(p)]:
            target = import_to_rel(d)
            target_pkg = target.rsplit("/", 1)[0] if "/" in target else "(root)"
            pkg_edges[(pkg_of(p), target_pkg)] += 1

    ext_counter: collections.Counter[str] = collections.Counter()
    for imps in external.values():
        ext_counter.update(imps)

    runtime_counter: collections.Counter[str] = collections.Counter()
    for imp, n in ext_counter.items():
        runtime_counter[runtime_of(imp)] += n

    lines: list[str] = []
    w = lines.append

    w("# Dependency Matrix — `sources/src/tto`")
    w("")
    w("> **Generated file.** Produced by")
    w("> [`docs/analysis/tools/analyse_as3.py`](./tools/analyse_as3.py); run")
    w("> `python docs/analysis/tools/analyse_as3.py` from the repository root to")
    w("> refresh it. Do not edit by hand — edit the script.")
    w("")
    w(f"- Files analysed: **{len(files)}**")
    w(f"- Lines: **{total_loc:,}**")
    w(f"- Classes found: **{len(classes)}**")
    w(f"- Distinct external imports: **{len(ext_counter)}**"
      f" ({sum(ext_counter.values())} import statements)")
    w("")
    w("Only the game's own package is analysed. The wider `sources/src/` tree also")
    w("contains vendored Starling, Feathers UI, as3crypto and Adobe corelib, which are")
    w("dependencies to be *replaced*, not code to be migrated.")
    w("")

    w("## 1. Size by package")
    w("")
    w("| Package | Files | Lines | Share |")
    w("|---|--:|--:|--:|")
    for pkg in packages:
        ps = [p for p in files if pkg_of(p) == pkg]
        pl = sum(loc[rel(p)] for p in ps)
        w(f"| `{pkg}` | {len(ps)} | {pl:,} | {100 * pl / total_loc:.1f}% |")
    w(f"| **total** | **{len(files)}** | **{total_loc:,}** | 100% |")
    w("")

    w("## 2. Package-to-package coupling")
    w("")
    w("Rows import columns. The cell is the number of `import` statements, so a high")
    w("number means many files in that package reach into the target.")
    w("")
    header = "| from \\ to | " + " | ".join(f"`{p}`" for p in packages) + " |"
    w(header)
    w("|---|" + "--:|" * len(packages))
    for a in packages:
        cells = []
        for b in packages:
            n = pkg_edges.get((a, b), 0)
            cells.append(str(n) if n else "·")
        w(f"| `{a}` | " + " | ".join(cells) + " |")
    w("")

    w("## 3. Most-depended-on classes (migration order)")
    w("")
    w("Fan-in is the number of files that import a class. These have to be migrated")
    w("first: everything else is waiting on them.")
    w("")
    w("| Fan-in | File | Lines | Class | Extends |")
    w("|--:|---|--:|---|---|")
    for target, n in fan_in.most_common(20):
        cls = classes.get(target)
        w(
            f"| {n} | `{target}` | {loc.get(target, 0):,} | "
            f"`{cls[0] if cls else '?'}` | "
            f"{('`' + cls[1] + '`') if cls and cls[1] else '—'} |"
        )
    w("")

    w("## 4. Highest fan-out (hardest single files)")
    w("")
    w("Files that import the most other `tto` classes. High fan-out plus high line")
    w("count is where migration effort concentrates.")
    w("")
    w("| tto imports | File | Lines | External imports |")
    w("|--:|---|--:|--:|")
    ordered = sorted(internal.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    for r, deps in ordered[:20]:
        w(f"| {len(deps)} | `{r}` | {loc[r]:,} | {len(external[r])} |")
    w("")

    w("## 5. Largest files")
    w("")
    w("| Lines | File | tto imports | External imports |")
    w("|--:|---|--:|--:|")
    for r in sorted(loc, key=lambda r: -loc[r])[:20]:
        w(f"| {loc[r]:,} | `{r}` | {len(internal[r])} | {len(external[r])} |")
    w("")

    w("## 6. External runtimes to replace")
    w("")
    w("| Runtime | Import statements | Distinct imports |")
    w("|---|--:|--:|")
    for name, n in runtime_counter.most_common():
        distinct = sum(1 for i in ext_counter if runtime_of(i) == name)
        w(f"| {name} | {n} | {distinct} |")
    w("")
    w("The 20 most-used external types, which are the ones worth a documented")
    w("Kotlin equivalent in [api-mapping.md](./api-mapping.md):")
    w("")
    w("| Uses | Import | Runtime |")
    w("|--:|---|---|")
    for imp, n in ext_counter.most_common(20):
        w(f"| {n} | `{imp}` | {runtime_of(imp)} |")
    w("")

    w("## 7. Base classes")
    w("")
    w("What the game's classes extend, which decides what each one becomes in Compose.")
    w("")
    bases: collections.Counter[str] = collections.Counter()
    for cls in classes.values():
        bases[cls[1] or "(nothing)"] += 1
    w("| Count | Extends |")
    w("|--:|---|")
    for base, n in bases.most_common():
        w(f"| {n} | `{base}` |")
    w("")

    w("## 8. Full file list")
    w("")
    w("| File | Lines | Class | Extends | Implements | tto deps | ext deps |")
    w("|---|--:|---|---|---|--:|--:|")
    for p in files:
        r = rel(p)
        cls = classes.get(r)
        w(
            f"| `{r}` | {loc[r]:,} | `{cls[0] if cls else '—'}` | "
            f"{('`' + cls[1] + '`') if cls and cls[1] else '—'} | "
            f"{('`' + cls[2] + '`') if cls and cls[2] else '—'} | "
            f"{len(internal[r])} | {len(external[r])} |"
        )
    w("")

    anomalies = [r for r, pk in declared_package.items()
                 if not pk.startswith("tto") and pk != "(none)"]
    w("## 9. Anomalies")
    w("")
    if anomalies:
        w("Files under `tto/` that do not declare a `tto.*` package. They do not belong")
        w("to the package tree they are filed under, so nothing can import them by a")
        w("`tto.*` name — check whether they are dead before budgeting for them.")
        w("")
        w("| File | Declared package | Referenced elsewhere? |")
        w("|---|---|---|")
        for r in sorted(anomalies):
            name = classes[r][0] if r in classes else r
            refs = sum(
                1 for other, text in src.items()
                if rel(other) != r and re.search(rf"\b{re.escape(name)}\b", text)
            )
            w(f"| `{r}` | `{declared_package[r]}` | "
              f"{'yes, ' + str(refs) + ' file(s)' if refs else '**no — dead code**'} |")
        w("")
    if bom:
        w(f"{len(bom)} of {len(files)} files carry a UTF-8 BOM:")
        w("")
        for p in sorted(bom):
            w(f"- `{rel(p)}`")
        w("")
        w("Worth knowing before any bulk transliteration: a BOM defeats regexes anchored")
        w("with `^` on the first line, and `java.util.Properties` folds it into the first")
        w("key name.")
        w("")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO)} ({len(lines)} lines)")
    print(f"  {len(files)} files, {total_loc:,} lines, {len(classes)} classes")
    if anomalies:
        print(f"  anomalies: {anomalies}")


if __name__ == "__main__":
    main()
