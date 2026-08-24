#!/usr/bin/env python3
"""gen_gate_sources.py — generate R.kt, BuildConfig.kt and *Binding.kt stubs
for the kotlinc type-check gate (no Gradle/Android SDK in the sandbox).

Usage:  gen_gate_sources.py <res_dir> <out_dir>
Writes: <out_dir>/R.kt <out_dir>/BuildConfig.kt <out_dir>/Bindings.kt
"""
import os
import re
import sys

RES = sys.argv[1]
OUT = sys.argv[2]
PKG = "com.guardian.shield"

# ------------------------------------------------------------------ helpers
def read(p):
    with open(p, "r", encoding="utf-8", errors="ignore") as f:
        return f.read()

def listdir(d):
    return sorted(os.listdir(d)) if os.path.isdir(d) else []

def pascal(name):
    return "".join(w[:1].upper() + w[1:] for w in name.split("_") if w)

def camel(name):
    parts = name.split("_")
    return parts[0] + "".join(w[:1].upper() + w[1:] for w in parts[1:])

# ------------------------------------------------------------- R.kt kinds
kinds = {}  # kind -> set of names

def add(kind, name):
    kinds.setdefault(kind, set()).add(name)

# values*/ resources
VAL_RE = re.compile(r'<(string|color|dimen|bool|integer)\s+name="([^"]+)"', re.M)
STYLE_RE = re.compile(r'<style\s+name="([^"]+)"', re.M)
for d in listdir(RES):
    if d.startswith("values"):
        for f in listdir(os.path.join(RES, d)):
            if f.endswith(".xml"):
                text = read(os.path.join(RES, d, f))
                for tag, name in VAL_RE.findall(text):
                    add("integer" if tag == "integer" else tag, name)
                for name in STYLE_RE.findall(text):
                    add("style", name.replace(".", "_"))

# file-based kinds: (dir prefix, r kind)
for d in listdir(RES):
    base = d.split("-")[0]
    full = os.path.join(RES, d)
    if base == "layout" and os.path.isdir(full):
        for f in listdir(full):
            if f.endswith(".xml"):
                add("layout", f[:-4])
    elif base in ("anim", "animator", "drawable", "mipmap", "xml", "color"):
        for f in listdir(full):
            if "." in f:
                add(base if base != "color" else "color", f.rsplit(".", 1)[0])

# ids: layouts + menus (+ any other xml that defines @+id/)
ID_RE = re.compile(r'@\+id/([A-Za-z0-9_]+)')
for d in listdir(RES):
    base = d.split("-")[0]
    if base in ("layout", "menu"):
        for f in listdir(os.path.join(RES, d)):
            if f.endswith(".xml"):
                for name in ID_RE.findall(read(os.path.join(RES, d, f))):
                    add("id", name)

# menu file names -> R.menu, as well as ids already collected
for f in listdir(os.path.join(RES, "menu")):
    if f.endswith(".xml"):
        add("menu", f[:-4])

os.makedirs(OUT, exist_ok=True)
lines = ["package %s" % PKG, "", "object R {"]
for kind in sorted(kinds):
    lines.append("    object %s {" % kind)
    for i, name in enumerate(sorted(kinds[kind]), 1):
        lines.append("        const val %s: Int = %d" % (name, i))
    lines.append("    }")
lines.append("}")
with open(os.path.join(OUT, "R.kt"), "w") as f:
    f.write("\n".join(lines) + "\n")

# ------------------------------------------------------------- BuildConfig
with open(os.path.join(OUT, "BuildConfig.kt"), "w") as f:
    f.write(
        "package %s\n\n"
        "object BuildConfig {\n"
        "    const val DEBUG: Boolean = true\n"
        '    const val APPLICATION_ID: String = "com.guardian.shield.debug"\n'
        '    const val BUILD_TYPE: String = "debug"\n'
        "    const val VERSION_CODE: Int = 35\n"
        '    const val VERSION_NAME: String = "3.6.5"\n'
        "    const val CRASHLYTICS_CONFIGURED: Boolean = false\n"
        "}\n" % PKG
    )

# ------------------------------------------------------------- Bindings.kt
BUILTIN = {
    "View": "android.view.View",
    "merge": "android.view.View",
    "include": "android.view.View",
    "TextView": "android.widget.TextView",
    "EditText": "android.widget.EditText",
    "Button": "android.widget.Button",
    "ImageView": "android.widget.ImageView",
    "ImageButton": "android.widget.ImageButton",
    "CheckBox": "android.widget.CheckBox",
    "RadioButton": "android.widget.RadioButton",
    "RadioGroup": "android.widget.RadioGroup",
    "Switch": "android.widget.Switch",
    "ToggleButton": "android.widget.ToggleButton",
    "ProgressBar": "android.widget.ProgressBar",
    "SeekBar": "android.widget.SeekBar",
    "Spinner": "android.widget.Spinner",
    "ListView": "android.widget.ListView",
    "ScrollView": "android.widget.ScrollView",
    "HorizontalScrollView": "android.widget.HorizontalScrollView",
    "LinearLayout": "android.widget.LinearLayout",
    "FrameLayout": "android.widget.FrameLayout",
    "RelativeLayout": "android.widget.RelativeLayout",
    "GridLayout": "android.widget.GridLayout",
    "TableLayout": "android.widget.TableLayout",
    "TableRow": "android.widget.TableRow",
    "Space": "android.widget.Space",
    "WebView": "android.webkit.WebView",
    "SurfaceView": "android.view.SurfaceView",
    "ViewStub": "android.view.ViewStub",
}

def fq(tag):
    if tag in BUILTIN:
        return BUILTIN[tag]
    if "." in tag:
        return tag
    return "android.widget." + tag

# element + id pairs, in document order (comments stripped so commented-out
# views do not leak fake ids/fields into the bindings)
TAG_ID_SCAN = re.compile(
    r'<(merge|include|fragment|[A-Za-z0-9_.]+)\b[^>]*?'
    r'android:id="@\+id/([A-Za-z0-9_]+)"', re.S)
ROOT_TAG = re.compile(r'<(merge|include|fragment|[A-Za-z0-9_.]+)\b')
COMMENT_RE = re.compile(r'<!--.*?-->', re.S)

b_lines = ["package %s.databinding" % PKG, ""]
layout_dir = os.path.join(RES, "layout")
for f in listdir(layout_dir):
    if not f.endswith(".xml"):
        continue
    name = f[:-4]
    cls = pascal(name) + "Binding"
    text = COMMENT_RE.sub("", read(os.path.join(layout_dir, f)))
    root_m = TAG_ID_SCAN.search(text) if False else ROOT_TAG.search(text)
    root_tag = root_m.group(1) if root_m else "View"
    root_type = fq(root_tag)
    fields = {}
    order = []
    for tag, vid in TAG_ID_SCAN.findall(text):
        if tag == "fragment":
            ftype = "android.view.View"
        else:
            ftype = fq(tag)
        if vid not in fields:
            fields[vid] = ftype
            order.append(vid)
    b_lines.append(
        "class %s private constructor(val root: %s) {" % (cls, root_type))
    for vid in order:
        b_lines.append(
            "    val %s: %s = root.findViewById(%s.R.id.%s)"
            % (camel(vid), fields[vid], PKG, vid))
    b_lines.append("    companion object {")
    b_lines.append(
        "        @JvmStatic fun inflate(inflater: android.view.LayoutInflater): "
        "%s = throw RuntimeException(\"stub\")" % cls)
    b_lines.append(
        "        @JvmStatic fun inflate(inflater: android.view.LayoutInflater, "
        "parent: android.view.ViewGroup?, attachToParent: Boolean): "
        "%s = throw RuntimeException(\"stub\")" % cls)
    b_lines.append(
        "        @JvmStatic fun bind(root: android.view.View): "
        "%s = throw RuntimeException(\"stub\")" % cls)
    b_lines.append("    }")
    b_lines.append("}")
    b_lines.append("")
with open(os.path.join(OUT, "Bindings.kt"), "w") as f:
    f.write("\n".join(b_lines) + "\n")

stats = {k: len(v) for k, v in sorted(kinds.items())}
print("R kinds:", stats)
print("layouts -> bindings:", sum(1 for f in listdir(layout_dir) if f.endswith(".xml")))
print("generated in", OUT)
