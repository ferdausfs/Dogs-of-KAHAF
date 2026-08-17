#!/usr/bin/env python3
"""Static resource-reference verifier for the Guardian Shield res/ tree.
Checks every @color/@drawable/@style reference in res XML (layouts, drawables,
menus, color selectors) resolves to a defined resource. Run before every commit.
"""
import re, glob, os, sys

RES = "app/src/main/res"
problems = []

# collect defined color names
colors = set(re.findall(r'<color name="([\w]+)"', open(f"{RES}/values/colors.xml").read()))
for f in glob.glob(f"{RES}/color/*.xml"):
    colors.add(os.path.basename(f)[:-4])

# collect defined drawables (xml now; png would also count)
drawables = {os.path.basename(f)[:-4] for f in glob.glob(f"{RES}/drawable/*")}

# collect defined styles
styles = set()
for f in glob.glob(f"{RES}/values/*.xml"):
    styles |= set(re.findall(r'<style name="([\w.]+)"', open(f).read()))

# android: namespaced styles that are always valid parents (library)
LIB_OK = re.compile(r'^(Widget|TextAppearance|ShapeAppearance|Theme|ThemeOverlay|Base)\.(Material3|MaterialComponents|android|AppCompat)')

for f in glob.glob(f"{RES}/**/*.xml", recursive=True):
    if "/values" in f or "/xml/" in f:  # values + manifest-y xml dirs have no @res refs of these kinds
        continue
    doc = open(f).read()
    for m in re.findall(r'@color/([\w]+)', doc):
        if m not in colors: problems.append((f, "color", m))
    for m in re.findall(r'@drawable/([\w]+)', doc):
        if m not in drawables: problems.append((f, "drawable", m))
    for m in re.findall(r'@style/([\w.]+)', doc):
        if m not in styles and not LIB_OK.match(m): problems.append((f, "style", m))

if problems:
    for p in problems: print("MISSING:", p)
    sys.exit(1)
print(f"OK — all references resolve. ({len(colors)} colors, {len(drawables)} drawables, {len(styles)} styles)")
