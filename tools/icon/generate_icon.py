#!/usr/bin/env python3
"""Generates the RetroCam launcher icon from one shape description.

Outputs:
  app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
  docs/icon-512.png   (needs Pillow: pip install pillow)

Run from the repo root:  python3 tools/icon/generate_icon.py
Canvas is the 108dp adaptive-icon canvas; the foreground stays inside the 66dp
safe zone (21..87). Original artwork, no brand logos.
"""
import os
from PIL import Image, ImageDraw

CREAM, AMBER, ORANGE, CHARCOAL = "#F4E8D2", "#F2B544", "#E2683A", "#2B2733"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app/src/main/res/drawable")


def rr(x1, y1, x2, y2, r):
    return (f"M{x1 + r},{y1}H{x2 - r}A{r},{r} 0 0 1 {x2},{y1 + r}V{y2 - r}A{r},{r} 0 0 1 {x2 - r},{y2}"
            f"H{x1 + r}A{r},{r} 0 0 1 {x1},{y2 - r}V{y1 + r}A{r},{r} 0 0 1 {x1 + r},{y1}Z")


def circle(cx, cy, r):
    return f"M{cx - r},{cy}A{r},{r} 0 1 0 {cx + r},{cy}A{r},{r} 0 1 0 {cx - r},{cy}Z"


# Foreground shapes: (kind, args, colour). kind rr = (x1,y1,x2,y2,r), circle = (cx,cy,r)
FG = [
    ("rr", (35, 32.5, 53, 40, 2), CHARCOAL),         # viewfinder hump
    ("rr", (24, 38, 84, 74, 5), CHARCOAL),           # body
    ("rr", (28, 66.5, 80, 69.5, 0.1), AMBER),        # stripes
    ("rr", (28, 69.5, 80, 72.5, 0.1), ORANGE),
    ("rr", (65, 33.5, 73, 38.5, 1.5), AMBER),        # shutter button
    ("rr", (29, 42, 40, 48, 1.5), CREAM),            # viewfinder window
    ("rr", (66, 42, 78, 48, 1.5), CREAM),            # flash window
    ("circle", (54, 55, 11), CREAM),                 # lens ring
    ("circle", (54, 55, 8.5), CHARCOAL),
    ("circle", (54, 55, 4.5), AMBER),
]
SUN = ("circle", (54, 54, 36), AMBER)


def path_of(shape):
    kind, a = shape[0], shape[1]
    return rr(*a) if kind == "rr" else circle(*a)


def vector(paths, extra=""):
    body = "\n".join(
        f'    <path android:fillColor="{c}"{"" if not e else " android:fillType=\"evenOdd\""} android:pathData="{d}" />'
        for d, c, e in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n' + body + "\n</vector>\n")


def write(name, content):
    with open(os.path.join(RES, name), "w") as f:
        f.write(content)


write("ic_launcher_background.xml",
      vector([(rr(0, 0, 108, 108, 0.01), CREAM, False), (path_of(SUN), AMBER, False)]))
write("ic_launcher_foreground.xml", vector([(path_of(s), s[2], False) for s in FG]))
# Monochrome: body with windows and lens ring punched out (even-odd); hump, lens core and button separate.
write("ic_launcher_monochrome.xml", vector([
    (path_of(FG[0]), "#000000", False),
    (path_of(FG[1]) + path_of(FG[5]) + path_of(FG[6]) + circle(54, 55, 11), "#000000", True),
    (circle(54, 55, 8.5), "#000000", False),
    (path_of(FG[4]), "#000000", False),
]))

# 512px preview: full-bleed cream square with the same shapes (adaptive masks crop at runtime).
S = 512 / 108.0
SS = 4  # supersample
img = Image.new("RGB", (512 * SS, 512 * SS), CREAM)
d = ImageDraw.Draw(img)
k = S * SS


def draw(shape, colour):
    kind, a = shape[0], shape[1]
    if kind == "circle":
        cx, cy, r = a
        d.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=colour)
    else:
        x1, y1, x2, y2, r = a
        d.rounded_rectangle([x1 * k, y1 * k, x2 * k, y2 * k], radius=r * k, fill=colour)


draw(SUN, AMBER)
for s in FG:
    draw(s, s[2])
# Scale up the artwork a little for the README PNG? Keep identical to the launcher layout.
img = img.resize((512, 512), Image.LANCZOS)
os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
img.save(os.path.join(ROOT, "docs/icon-512.png"))
print("ok")
