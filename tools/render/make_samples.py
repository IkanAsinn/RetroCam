#!/usr/bin/env python3
"""Writes the small sample photos into tools/render/samples/ (scikit-image bundled data, see README).

The *_flash.jpg files are SYNTHETIC "phone flash" photos: the clean photo multiplied by a flash-light model
(inverse-square-like fall-off from a near, centred subject over a dim ambient base) -- a lit subject on a
darker background, which is what the flash-simulation tuning needs.
"""
import os

import numpy as np
import skimage.data as d
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "samples")
os.makedirs(OUT, exist_ok=True)


def flash_variant(a, centre=(0.5, 0.45), sigma=0.34, ambient=0.18, gain=1.05):
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    r2 = ((xx / w - centre[0]) * (w / h)) ** 2 + (yy / h - centre[1]) ** 2
    light = ambient + gain / (1.0 + r2 / (sigma * sigma)) ** 1.5
    lin = (a / 255.0) ** 2.2
    lin = lin * light[..., None] * np.array([1.0, 0.97, 0.93])  # flash is slightly warm-white
    return (np.clip(lin, 0, 1) ** (1 / 2.2) * 255 + 0.5).astype(np.uint8)


def dim_variant(a, gain=0.11, lamp=(0.15, 0.2)):
    """Dim, warm indoor ambient (tungsten-ish cast, one lamp off to a side so the light is uneven): the film-flash test input."""
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    r2 = ((xx / w - lamp[0]) * (w / h)) ** 2 + (yy / h - lamp[1]) ** 2
    light = 0.45 + 0.9 / (1.0 + r2 / 0.35)
    lin = (a / 255.0) ** 2.2 * light[..., None] * gain * np.array([1.0, 0.66, 0.36])
    return (np.clip(lin, 0, 1) ** (1 / 2.2) * 255 + 0.5).astype(np.uint8)


if __name__ == "__main__":
    for n in ["astronaut", "coffee", "chelsea", "rocket"]:
        a = getattr(d, n)()
        Image.fromarray(a).save(os.path.join(OUT, n + ".jpg"), quality=95)
    Image.fromarray(flash_variant(d.astronaut())).save(os.path.join(OUT, "astronaut_flash.jpg"), quality=95)
    Image.fromarray(flash_variant(d.chelsea(), centre=(0.45, 0.5), sigma=0.3)).save(os.path.join(OUT, "chelsea_flash.jpg"), quality=95)
    for n in ["astronaut", "chelsea", "coffee"]:
        Image.fromarray(dim_variant(getattr(d, n)())).save(os.path.join(OUT, n + "_dim.jpg"), quality=95)
