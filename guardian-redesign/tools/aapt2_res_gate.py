#!/usr/bin/env python3
"""Real resource gate: aapt2 COMPILE every app resource + compat overlay for
library (androidx/material) symbols, then aapt2 LINK against android-30.jar.
This is the same machinery Gradle runs — a green link locally means the res
tree is reference-complete.

Needs (ephemeral, auto-downloadable): /home/user/gate/bin/aapt2 (pip wheel
'aapt2' 0.2.1 -> aapt2/bin/Linux/aapt2) and /home/user/gate/android-30.jar
(Sable/android-platforms). See bootstrap_gate.sh.

Legacy notes:
- aapt2 2.19 cannot parse android-35 arsc -> we link against android-30.
- aapt2 2.19 knows no API 31-34 manifest attrs -> stripped in the GATE COPY.
- aapt2 2.19 rejects dp translate deltas (modern AGP accepts; app ships them
  on green CI) -> sanitized THROWAWAY copy for the link step only.
Run:  python3 guardian-redesign/tools/aapt2_res_gate.py"""
import os, re, subprocess, sys, glob, shutil

REPO = os.environ.get("GATE_REPO", os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
RES = f"{REPO}/app/src/main/res"
AAPT2 = os.environ.get("GATE_AAPT2", "/home/user/gate/bin/aapt2")
ANDROID_JAR = os.environ.get("GATE_ANDROID_JAR", "/home/user/gate/android-30.jar")
OUT = "/home/user/gate/res-out"
OVERLAY = "/home/user/gate/res-overlay"

ALL_XML = []

def sh(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)

def collect_symbols():
    syms = {}
    def add(t, n): syms.setdefault(t, set()).add(n)
    for d in os.listdir(RES):
        p = os.path.join(RES, d)
        if not os.path.isdir(p) or d.startswith("values"):
            continue
        t = d.split("-")[0]
        for f in os.listdir(p):
            if f.endswith((".xml", ".png", ".webp")):
                add(t, os.path.splitext(f)[0])
    val_item = re.compile(r'<(string|color|dimen|style|attr|bool|integer|plurals|array|string-array|integer-array|declare-styleable)\s+name="([^"]+)"')
    for vf in glob.glob(f"{RES}/values*/**/*.xml", recursive=True) + glob.glob(f"{RES}/values*/*.xml"):
        txt = open(vf, encoding="utf-8").read()
        for t, n in val_item.findall(txt):
            t = {"string-array": "array", "integer-array": "array", "declare-styleable": "attr"}.get(t, t)
            add(t, n)
        for m in re.finditer(r'<item\s+name="([^":]+)"[^>]*type="([^"]+)"', txt):
            add(m.group(2), m.group(1))
    return syms

def gen_overlay(syms):
    os.makedirs(f"{OVERLAY}/values", exist_ok=True)
    styles_defined = syms.get("style", set())
    attrs_declared = syms.get("attr", set())
    need_styles, need_attrs = set(), set()

    # app-namespace attributes used anywhere in res (aapt2 link-validates
    # them). Scan per-ATTRIBUTE: one tag can carry many app: attrs.
    appns = re.compile(r'\sapp:([A-Za-z0-9_]+)=')
    for vf in ALL_XML:
        txt = open(vf, encoding="utf-8").read()
        if "res-auto" in txt:
            for a in appns.findall(txt):
                need_attrs.add(a)
        for r in re.findall(r'\?attr/([A-Za-z0-9_]+)', txt) + re.findall(r'\?([A-Za-z][A-Za-z0-9_]+)', txt):
            if not r.startswith("android"):
                need_attrs.add(r)

    for vf in glob.glob(f"{RES}/values*/**/*.xml", recursive=True) + glob.glob(f"{RES}/values*/*.xml"):
        txt = open(vf, encoding="utf-8").read()
        for p in re.findall(r'parent="([^"]+)"', txt):
            if not p.startswith("@android:") and not p.startswith("android:") and p not in styles_defined:
                need_styles.add(p)
        for a in re.findall(r'<item\s+name="([^"]+)"', txt):
            if not a.startswith("android:"):
                need_attrs.add(a)

    # unresolved @type/name VALUE refs (library strings/dimens/styles/colors)
    ref_pat = re.compile(r'@([a-z_][a-z_0-9]*)/([A-Za-z0-9_.]+)')
    need_res = {"string": set(), "dimen": set(), "color": set(), "bool": set(), "integer": set()}
    unresolved_other = set()
    for vf in ALL_XML:
        txt = open(vf, encoding="utf-8").read()
        txt = re.sub(r'@(android:[a-z_]+/|\+|id/|null)', "", txt)
        for t, n in ref_pat.findall(txt):
            if t == "attr" or n in syms.get(t, set()):
                continue
            if t == "style":
                need_styles.add(n)
            elif t in need_res:
                need_res[t].add(n)
            else:
                unresolved_other.add(f"@{t}/{n} (in {vf.replace(RES, 'res')})")
    if unresolved_other:
        print("UNRESOLVABLE refs (cannot stub type):\n  " + "\n  ".join(sorted(unresolved_other)))
        sys.exit(2)

    need_attrs -= attrs_declared
    need_attrs = {a for a in need_attrs if not a.startswith("android:")}
    with open(f"{OVERLAY}/values/gate_compat.xml", "w", encoding="utf-8") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for a in sorted(need_attrs):
            fh.write(f'    <attr name="{a}" format="color|dimension|reference|boolean|integer|float|string|fraction"/>\n')
        for n in sorted(need_res["string"]):
            fh.write(f'    <string name="{n}">gate_stub</string>\n')
        for n in sorted(need_res["dimen"]):
            fh.write(f'    <dimen name="{n}">1dp</dimen>\n')
        for n in sorted(need_res["color"]):
            fh.write(f'    <color name="{n}">#00000000</color>\n')
        for n in sorted(need_res["bool"]):
            fh.write(f'    <bool name="{n}">false</bool>\n')
        for n in sorted(need_res["integer"]):
            fh.write(f'    <integer name="{n}">0</integer>\n')
        for s in sorted(need_styles):
            fh.write(f'    <style name="{s}" parent=""/>\n')
        fh.write('</resources>\n')
    return len(need_attrs), len(need_styles), sum(len(v) for v in need_res.values())

def compile_dir(src, out_zip):
    return sh([AAPT2, "compile", "--dir", src, "-o", out_zip, "-v"])

LEGACY_FP = re.compile(
    r"error: '\d+(\.\d+)?dp' is incompatible with attribute "
    r"(fromYDelta|toYDelta|fromXDelta|toXDelta) \(attr\) float\|fraction\.")

def filter_link_errors(err: str) -> str:
    kept = [ln for ln in err.splitlines() if ln.strip() and not LEGACY_FP.search(ln)]
    return "\n".join(kept)

def main():
    if not (os.path.exists(AAPT2) and os.path.exists(ANDROID_JAR)):
        print("gate binaries missing — run guardian-redesign/tools/bootstrap_gate.sh first")
        sys.exit(3)
    shutil.rmtree(OUT, ignore_errors=True)
    shutil.rmtree(OVERLAY, ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    ALL_XML.extend(glob.glob(f"{RES}/**/*.xml", recursive=True))
    syms = collect_symbols()
    na, ns, nr = gen_overlay(syms)
    print(f"compat overlay: {na} library attrs, {ns} styles, {nr} value stubs")

    r1 = compile_dir(RES, f"{OUT}/app.zip")
    if r1.returncode != 0:
        print("COMPILE FAIL (app res):\n" + r1.stderr[-4000:]); sys.exit(1)
    r2 = compile_dir(OVERLAY, f"{OUT}/overlay.zip")
    if r2.returncode != 0:
        print("COMPILE FAIL (overlay):\n" + r2.stderr[-4000:]); sys.exit(1)
    print("aapt2 compile: PASS (untouched app res)")

    link_res = f"{OUT}/res_link"
    shutil.rmtree(link_res, ignore_errors=True)
    shutil.copytree(RES, link_res)
    for af in glob.glob(f"{link_res}/anim*/*.xml"):
        txt = open(af, encoding="utf-8").read()
        txt = re.sub(r'((?:from|to)[XY]Delta=")(\d+(?:\.\d+)?)dp"', r'\g<1>\g<2>"', txt)
        open(af, "w", encoding="utf-8").write(txt)
    r1b = compile_dir(link_res, f"{OUT}/app_link.zip")
    if r1b.returncode != 0:
        print("COMPILE FAIL (link copy):\n" + r1b.stderr[-4000:]); sys.exit(1)

    man = open(f"{REPO}/app/src/main/AndroidManifest.xml", encoding="utf-8").read()
    man = re.sub(r'\s+tools:[A-Za-z]+="[^"]*"', "", man)
    man = man.replace("<manifest ", '<manifest package="com.guardian.shield" ', 1)
    man = re.sub(r'\s+android:(dataExtractionRules|enableOnBackInvokedCallback|appCategory|localeConfig)="[^"]*"', "", man)
    man = re.sub(r'android:foregroundServiceType="specialUse"', 'android:foregroundServiceType="dataSync"', man)
    open(f"{OUT}/Manifest.xml", "w", encoding="utf-8").write(man)

    link = sh([AAPT2, "link", "-o", f"{OUT}/gate.apk",
               "-I", ANDROID_JAR,
               "--manifest", f"{OUT}/Manifest.xml",
               "--java", f"{OUT}/gen",
               "--min-sdk-version", "26", "--target-sdk-version", "35",
               "--version-code", "34", "--version-name", "3.6.4",
               f"{OUT}/overlay.zip", f"{OUT}/app_link.zip"])
    combined = (link.stderr or "") + "\n" + (link.stdout or "")
    real_errors = filter_link_errors(combined).replace("error: failed linking file resources.", "").strip()
    if link.returncode != 0 and real_errors:
        print("LINK FAIL: returncode=%d" % link.returncode)
        print(real_errors[:6000]); sys.exit(1)

    rjava = glob.glob(f"{OUT}/gen/**/R.java", recursive=True)
    counts = {}
    if rjava:
        txt = open(rjava[0], encoding="utf-8").read()
        counts = {m.group(1): len(re.findall(r"public static final int", m.group(0)))
                  for m in re.finditer(r"class (\w+) \{[^}]*\}", txt, re.S)}
    print("aapt2 link: PASS (R.java types: " +
          ", ".join(f"{k}={v}" for k, v in sorted(counts.items())) + ")")
    print("RES GATE: PASS")

if __name__ == "__main__":
    main()
