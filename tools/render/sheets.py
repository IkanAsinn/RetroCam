#!/usr/bin/env python3
"""Review images rendered with the REAL shader (llvmpipe). Run dump.sh first (see README).

  sheets.py contact <dump_dir> <out.png> [profiles...]      rows = photos, cols = original + profiles
  sheets.py flash   <dump_dir> <out.png> [profiles...]      flash-lit photos: original | per profile: flash off | flash on
  sheets.py compare <before_dump> <after_dump> <profile> <out.png> [--flash] [--photo NAME]
                                                            original | before | after, plus 1:1 crops of a 4000x3000 render
  sheets.py flashcmp <before_dump> <after_dump> <out.png>   old vs new flash result for several profiles on the flash photos
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import filmlib as L  # noqa: E402

SAMPLES = os.path.join(L.HERE, "samples")
DEFAULT_PROFILES = ["gold_snap", "green_snap", "power_800", "instant_mini", "classic_200", "slide_100", "mono_snap", "expired_90s"]
PHOTOS = ["astronaut", "coffee", "chelsea", "rocket"]
FLASH_PHOTOS = ["astronaut_flash", "chelsea_flash"]
BIG = (4000, 3000)
CELL_H = 300


def font(sz=14):
    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", sz)
    except Exception:
        return ImageFont.load_default()


def photo(name):
    return L.load_img(os.path.join(SAMPLES, name + ".jpg"))


def fit(a, h=CELL_H):
    im = Image.fromarray(a)
    w = round(im.width * h / im.height)
    return im.resize((w, h), Image.LANCZOS)


def label(im, text):
    d = ImageDraw.Draw(im)
    f = font(13)
    tw = d.textlength(text, font=f)
    d.rectangle([0, 0, tw + 8, 20], fill=(0, 0, 0))
    d.text((4, 3), text, fill=(255, 255, 255), font=f)
    return im


def grid(rows, pad=4, bg=(24, 24, 24)):
    W = max(sum(i.width for i in r) + pad * (len(r) + 1) for r in rows)
    H = sum(max(i.height for i in r) + pad for r in rows) + pad
    out = Image.new("RGB", (W, H), bg)
    y = pad
    for r in rows:
        x = pad
        for i in r:
            out.paste(i, (x, y))
            x += i.width + pad
        y += max(i.height for i in r) + pad
    return out


def contact(dump, out, profiles):
    rows = []
    for p in PHOTOS:
        a = photo(p)
        row = [label(fit(a), p)]
        for prof in profiles:
            row.append(label(fit(L.render(dump, prof, False, a)), prof))
        rows.append(row)
    grid(rows).save(out)


def flash(dump, out, profiles):
    rows = []
    for p in FLASH_PHOTOS:
        a = photo(p)
        row = [label(fit(a), p)]
        for prof in profiles:
            row.append(label(fit(L.render(dump, prof, False, a)), prof + " no flash"))
            row.append(label(fit(L.render(dump, prof, True, a)), prof + " FLASH"))
        rows.append(row)
    grid(rows).save(out)


def big_source(name):
    a = Image.fromarray(photo(name))
    return np.asarray(a.resize(BIG, Image.BICUBIC))


def crop_1to1(a, cx=0.5, cy=0.45, w=560, h=420):
    H, W = a.shape[:2]
    x0 = int(cx * W - w / 2)
    y0 = int(cy * H - h / 2)
    return Image.fromarray(a[y0:y0 + h, x0:x0 + w])


def compare(before, after, prof, out, fl=False, name="coffee"):
    a = photo(name)
    big = big_source(name)
    top = [label(fit(a, 330), "original"),
           label(fit(L.render(before, prof, fl, a), 330), "before (v0.2.0)"),
           label(fit(L.render(after, prof, fl, a), 330), "after")]
    cb = L.render(before, prof, fl, big)
    ca = L.render(after, prof, fl, big)
    # two crops: centre, and a corner (CA / edge softness / vignette)
    bot, bot2 = [], []
    for t, arr in (("original 1:1", big), ("before 1:1", cb), ("after 1:1", ca)):
        bot.append(label(crop_1to1(arr), t))
        bot2.append(label(crop_1to1(arr, 0.09, 0.12, 560, 420), t + " corner"))
    grid([top, bot, bot2]).save(out)


def flashcmp(before, after, out):
    rows = []
    for p in FLASH_PHOTOS:
        a = photo(p)
        rows.append([label(fit(a), p)])
        for prof in ("gold_snap", "power_800", "instant_mini", "classic_200"):
            rows[-1].append(label(fit(L.render(before, prof, True, a)), prof + " old flash"))
            rows[-1].append(label(fit(L.render(after, prof, True, a)), prof + " new flash"))
    # split into two rows per photo to keep the sheet a sane width
    out_rows = []
    for r in rows:
        out_rows.append(r[:5])
        out_rows.append(r[5:])
    grid(out_rows).save(out)


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "contact":
        contact(args[0], args[1], args[2:] or DEFAULT_PROFILES)
    elif cmd == "flash":
        flash(args[0], args[1], args[2:] or DEFAULT_PROFILES[:4])
    elif cmd == "compare":
        fl = "--flash" in args
        nm = args[args.index("--photo") + 1] if "--photo" in args else "coffee"
        compare(args[0], args[1], args[2], args[3], fl, nm)
    elif cmd == "flashcmp":
        flashcmp(args[0], args[1], args[2])
    else:
        sys.exit(__doc__)
