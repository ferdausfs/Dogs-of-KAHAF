#!/usr/bin/env python3
"""Binding-contract gate: prove every layout's @+id set and per-id view TYPE
are identical between git HEAD and the working tree (binding compatibility).
Run from anywhere:  python3 guardian-redesign/tools/id_contract_gate.py"""
import re, subprocess, sys, os

REPO = os.environ.get("GATE_REPO", os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
LAYOUT_DIR = "app/src/main/res/layout"

ID_RE = re.compile(r'<([A-Za-z0-9_.]+)[^>]*?android:id="@\+id/([A-Za-z0-9_]+)"')

def idmap(xml: str):
    m = {}
    for tag, name in ID_RE.findall(xml):
        short = tag.split(".")[-1]
        if name in m and m[name] != short:
            m[name] += "!!CONFLICT:" + short
        else:
            m[name] = short
    return m

files = sorted(os.listdir(os.path.join(REPO, LAYOUT_DIR)))
fail = False
total_ids = 0
for f in files:
    if not f.endswith(".xml"):
        continue
    cur = open(os.path.join(REPO, LAYOUT_DIR, f), encoding="utf-8").read()
    try:
        head = subprocess.run(["git", "-C", REPO, "show", f"HEAD:{LAYOUT_DIR}/{f}"],
                              capture_output=True, text=True, check=True).stdout
    except subprocess.CalledProcessError:
        print(f"NEW FILE (no HEAD version): {f}"); continue
    a, b = idmap(head), idmap(cur)
    total_ids += len(b)
    removed = {k: v for k, v in a.items() if k not in b}
    changed = {k: (a[k], b[k]) for k in a if k in b and a[k] != b[k]}
    added = {k: v for k, v in b.items() if k not in a}
    if removed or changed:
        fail = True
        print(f"FAIL {f}: removed={removed} type-changed={changed}")
    if added:
        print(f"note {f}: new ids added {sorted(added)} (allowed, but flagged)")
print(f"\nChecked {len(files)} layouts, {total_ids} ids in working tree.")
print("ID/TYPE CONTRACT: PASS" if not fail else "ID/TYPE CONTRACT: FAIL")
sys.exit(1 if fail else 0)
