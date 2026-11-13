#!/usr/bin/env python3
"""Builds the 33x33x33 colour LUT of every RetroCam camera type.

With no reference photo pairs yet, every LUT comes from a parametric "look"
description kept in this file (see LOOKS): per-channel tone curves, channel
crosstalk, white-balance gains, split toning, saturation / hue rotation by hue
band, a B&W channel mix for mono types, hue swaps (Purple Haze) and cross-process
curves. Everything is deterministic (no random numbers), so reruns are byte-stable.

Outputs (run from the repo root:  python3 tools/lut/generate_luts.py):
  tools/lut/out/<id>.cube                       (gitignored)
  tools/lut/out/contact_sheet.png               (gitignored)
  profiles/src/main/assets/luts/<id>.png        1089x33 strip, pixel (x=b*33+r, y=g)
  docs/lut-contact-sheet.png                    downscaled contact sheet (committed)

Requires numpy and Pillow. Colour maths is plain sRGB-encoded 0..1 values: the LUT
is applied to encoded pixels by the shader, so that is the space we author in.
"""
import colorsys
import math
import os
import sys
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np
from PIL import Image, ImageDraw

SIZE = 33
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "tools", "lut", "out")
LUT_DIR = os.path.join(ROOT, "profiles", "src", "main", "assets", "luts")
DOCS_SHEET = os.path.join(ROOT, "docs", "lut-contact-sheet.png")

LUMA = np.array([0.2126, 0.7152, 0.0722])


def hexrgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


# --------------------------------------------------------------------------- #
# Look description
# --------------------------------------------------------------------------- #
@dataclass
class Curve:
    """Single-channel tone curve: gamma -> logistic S-curve -> black/white level."""
    k: float = 0.0       # S-curve strength (0 = none, ~3 gentle, ~6 hard, negative = flattening)
    pivot: float = 0.5   # where the S-curve crosses the diagonal
    gamma: float = 1.0   # >1 darkens mid-tones, <1 brightens
    lift: float = 0.0    # output black level (fade / lifted shadows)
    white: float = 1.0   # output white level (shoulder / soft highlights)


@dataclass
class Look:
    gains: Tuple[float, float, float] = (1.0, 1.0, 1.0)       # white balance, multiplicative
    mix: Optional[Tuple[float, ...]] = None                    # 3x3 channel crosstalk, rows are normalised
    curve: Curve = field(default_factory=Curve)               # applied to all channels
    rc: Optional[Curve] = None                                 # extra per-channel curves
    gc: Optional[Curve] = None
    bc: Optional[Curve] = None
    sat: float = 1.0                                           # global saturation (luma-preserving)
    sat_bands: Dict[float, float] = field(default_factory=dict)   # hue centre deg -> saturation multiplier
    hue_shifts: List[Tuple[float, float, float]] = field(default_factory=list)  # (centre, half-width, delta deg)
    shadow: Optional[Tuple[str, float]] = None                 # split toning (colour, amount)
    highlight: Optional[Tuple[str, float]] = None
    mono: Optional[Tuple[float, float, float]] = None          # B&W channel mix (weights sum ~1)
    band_width: float = 45.0


def C(**kw) -> Curve:
    return Curve(**kw)


# Order = PRD table order. Hue centres: red 0, orange 30, yellow 60, green 120, cyan 180, blue 240, magenta 300.
LOOKS: Dict[str, Look] = {
    # ---------------- Disposables ----------------
    "gold_snap": Look(  # Kodak single-use scan: cream-yellow neutrals, olive-brown lifted shadows, punchy reds
        gains=(0.99, 1.0, 0.95), curve=C(k=2.3, lift=0.04, white=0.98), sat=1.04,
        sat_bands={0: 1.15, 30: 1.0, 60: 1.05, 120: 0.8, 180: 1.05, 240: 1.1}, hue_shifts=[(120, 40, -16)],
        shadow=("#787646", 0.22), highlight=("#FFF1C8", 0.12)),
    "power_800": Look(  # gold_snap family at 800: harder, muddier shadows, a touch more yellow-brown
        gains=(0.99, 0.995, 0.94), curve=C(k=3.1, gamma=1.07, lift=0.04, white=0.98), sat=1.04,
        sat_bands={0: 1.15, 30: 1.0, 60: 1.05, 120: 0.8, 180: 1.0, 240: 1.05}, hue_shifts=[(120, 40, -16)],
        shadow=("#6E6234", 0.30), highlight=("#FFF1C8", 0.12)),
    "green_snap": Look(  # Fuji single-use scan: olive-green blacks and mid greys, warm-pink highlights and skin
        gains=(0.925, 1.0, 0.95), curve=C(k=2.4, lift=0.03, white=0.985), sat=0.97,
        sat_bands={0: 1.15, 30: 1.08, 60: 1.0, 120: 0.95, 240: 0.85}, hue_shifts=[(240, 35, -14)],
        shadow=("#648540", 0.23), highlight=("#FFE1EB", 0.55)),
    "mono_snap": Look(  # B&W, punchy mids
        mono=(0.34, 0.50, 0.16), curve=C(k=3.4, lift=0.03, white=0.985),
        shadow=("#7A6040", 0.08), highlight=("#FFF4E0", 0.05)),
    "pop_simple": Look(  # saturated, warm, high contrast
        gains=(1.04, 1.0, 0.93), curve=C(k=4.2, gamma=1.03), sat=1.28,
        sat_bands={0: 1.2, 60: 1.15, 240: 1.25, 300: 1.25},
        shadow=("#5B3CC0", 0.30), highlight=("#FFC0A0", 0.12)),
    "lebox_red": Look(  # red-leaning, warm, saturated reds
        gains=(1.09, 0.96, 0.86), mix=(1, 0.05, 0, 0.04, 1, 0, 0.0, 0.1, 1),
        curve=C(k=3.0, gamma=1.03, lift=0.03), sat=1.1,
        sat_bands={0: 1.35, 30: 1.2, 300: 1.2, 120: 0.85}, hue_shifts=[(35, 30, -10), (80, 35, -18)],
        shadow=("#9A2A22", 0.40), highlight=("#FFC890", 0.14)),
    "aqua": Look(  # blue-green cast, low contrast, soft, waterline-blue tint
        gains=(0.80, 0.99, 1.08), curve=C(k=1.0, lift=0.075, white=0.97), sat=0.92,
        sat_bands={0: 0.8, 30: 0.8},
        shadow=("#00909E", 0.55), highlight=("#8FDCFF", 0.22)),
    # ---------------- Point-and-shoot compacts ----------------
    "classic_200": Look(  # warm golden, slightly yellow highlights
        gains=(1.02, 1.01, 0.91), curve=C(k=2.0, lift=0.02, white=0.985), sat=1.04,
        sat_bands={0: 1.05, 30: 1.0},
        shadow=("#A07A40", 0.12), highlight=("#FFE040", 0.32)),
    "studio_400": Look(  # soft pastel, accurate warm skin, low contrast
        gains=(1.02, 1.0, 0.975), curve=C(k=0.9, lift=0.045, white=0.985), sat=0.88,
        sat_bands={120: 0.8, 240: 0.85}, hue_shifts=[(120, 40, -8)],
        shadow=("#78A898", 0.20), highlight=("#FFD2B0", 0.14)),
    "vivid_100": Look(  # high saturation, deep blues, crisp
        gains=(1.0, 1.0, 1.025), curve=C(k=3.4, pivot=0.48, gamma=1.04), sat=1.42,
        sat_bands={240: 1.5, 180: 1.3, 0: 1.25, 120: 1.1},
        shadow=("#1E3C8C", 0.18)),
    "street_400": Look(  # greenish, crisp, cool shadows
        gains=(0.965, 1.015, 0.985), curve=C(k=3.0, lift=0.025), sat=1.04,
        sat_bands={120: 1.2, 60: 1.1}, hue_shifts=[(60, 30, 10)],
        shadow=("#468C7D", 0.16), highlight=("#FFF0E6", 0.22)),
    "airy_400": Look(  # bright, mint greens, pastel blues, lifted blacks
        gains=(1.0, 1.015, 1.01), curve=C(k=0.8, gamma=0.9, lift=0.055, white=1.0), sat=0.84,
        sat_bands={120: 1.0, 240: 0.8, 30: 0.9}, hue_shifts=[(120, 40, 20), (240, 35, -14)],
        shadow=("#9ADCC8", 0.20), highlight=("#FFF0F0", 0.08)),
    "budget_200": Look(  # slightly cool, green shadows, modest saturation
        gains=(0.97, 1.0, 1.03), curve=C(k=2.2, lift=0.03, white=0.98), sat=0.92,
        sat_bands={0: 0.9},
        shadow=("#46A860", 0.35), highlight=("#EEF4FF", 0.08)),
    "luxe_compact": Look(  # neutral-warm, contrasty, rich
        gains=(1.025, 1.0, 0.955), curve=C(k=4.2, pivot=0.46, gamma=1.08, white=0.99), sat=1.04,
        sat_bands={0: 1.1, 30: 1.05, 240: 0.95},
        shadow=("#1E4C6A", 0.18), highlight=("#FFD9A0", 0.12)),
    # ---------------- Instant ----------------
    "instant_square": Look(  # Instax Square: same film as the Mini, a touch softer in tone
        gains=(0.995, 1.0, 1.012), curve=C(k=3.0, gamma=0.94, lift=0.006, white=1.0), sat=1.1,
        sat_bands={0: 1.1, 120: 0.95, 180: 1.1, 240: 1.05, 300: 1.1}, hue_shifts=[(28, 20, -6)],
        shadow=("#3A3A5A", 0.08), highlight=("#F4F8FF", 0.05)),
    "instant_mini": Look(  # Instax Mini: vivid, high-key, punchy, dense shadows, highlights blow early
        gains=(0.995, 1.0, 1.012), curve=C(k=3.4, gamma=0.92, lift=0.005, white=1.0), sat=1.14,
        sat_bands={0: 1.1, 120: 0.95, 180: 1.15, 240: 1.05, 300: 1.1}, hue_shifts=[(28, 20, -6)],
        shadow=("#3A3A5A", 0.08), highlight=("#F4F8FF", 0.05)),
    # ---------------- Black and white ----------------
    "grit_400": Look(  # high contrast, deep blacks, dramatic skies
        mono=(0.46, 0.42, 0.12), curve=C(k=6.2, gamma=1.14, white=1.0),
        shadow=("#404050", 0.05), highlight=("#FFFFFF", 0.0)),
    "silver_100": Look(  # smooth tones, gentle contrast, cool silver
        mono=(0.27, 0.61, 0.12), curve=C(k=1.5, lift=0.015, white=0.985),
        shadow=("#7C94C0", 0.12), highlight=("#FFFFFF", 0.0)),
    # ---------------- Slide and cinema ----------------
    "slide_100": Look(  # very saturated, deep blacks, punchy greens
        gains=(1.01, 1.0, 1.0), curve=C(k=4.6, gamma=1.08, white=1.0), sat=1.55,
        sat_bands={120: 1.55, 90: 1.3, 240: 1.35, 0: 1.25, 60: 1.1}, hue_shifts=[(100, 30, 8)],
        shadow=("#0F2A55", 0.12)),
    "night_800t": Look(  # tungsten-balanced cool, teal shadows, warm light sources
        gains=(0.88, 1.0, 1.12), mix=(1, 0, 0, 0, 1, 0.06, 0, 0.08, 1),
        curve=C(k=3.1, gamma=1.04, lift=0.035, white=0.985), sat=1.05,
        sat_bands={30: 1.2, 0: 1.15}, hue_shifts=[(120, 40, 14)],
        shadow=("#00A6A0", 0.55), highlight=("#FFAA66", 0.18)),
    "cross_process": Look(  # slide film in C-41: yellow-green highlights, cyan-blue shadows
        gains=(1.0, 1.0, 1.0), curve=C(k=2.0),
        rc=C(k=6.0, pivot=0.52, gamma=1.06), gc=C(k=3.5, pivot=0.5, gamma=0.95, white=1.0),
        bc=C(k=-2.0, pivot=0.5, lift=0.11, white=0.80), sat=1.15,
        sat_bands={60: 1.3, 180: 1.3},
        shadow=("#0A8CB4", 0.25), highlight=("#E8F040", 0.30)),
    # ---------------- Toy and special ----------------
    "plastic_120": Look(  # saturated, warm, toy-lens charm
        gains=(1.05, 1.0, 0.94), curve=C(k=2.6, lift=0.05, white=0.98), sat=1.3,
        sat_bands={0: 1.1, 60: 1.2, 240: 1.2}, hue_shifts=[(110, 40, -18), (0, 20, -8)],
        shadow=("#2E8A9A", 0.35), highlight=("#FFC8A0", 0.16)),
    "expired_90s": Look(  # magenta shadows, cyan highlights, fogged blacks, faded
        gains=(1.02, 0.95, 1.0), curve=C(k=1.8, gamma=0.97, lift=0.11, white=0.9), sat=0.8,
        sat_bands={120: 0.8},
        shadow=("#C03AA8", 0.55), highlight=("#5CD8E0", 0.26)),
    "half_frame": Look(  # warm vintage, muted, slightly olive shadows
        gains=(1.04, 1.0, 0.94), curve=C(k=2.0, lift=0.05, white=0.96), sat=0.88,
        sat_bands={0: 1.05, 30: 1.05, 240: 0.85},
        shadow=("#8A7A44", 0.30), highlight=("#FFE2AA", 0.24)),
    "purple_haze": Look(  # foliage greens -> purple, blue skies -> teal
        gains=(1.02, 1.0, 1.0), curve=C(k=2.4, lift=0.02), sat=1.2,
        sat_bands={240: 1.2},
        hue_shifts=[(105, 70, 178), (225, 32, -48), (255, 25, -50)],
        shadow=("#4A2C7A", 0.22), highlight=("#FFE6F2", 0.10)),
}

NAMES = {
    "gold_snap": "Gold Snap", "power_800": "Power 800", "green_snap": "Green Snap", "mono_snap": "Mono Snap",
    "pop_simple": "Pop Simple", "lebox_red": "Lebox Red", "aqua": "Aqua", "classic_200": "Classic 200",
    "studio_400": "Studio 400", "vivid_100": "Vivid 100", "street_400": "Street 400", "airy_400": "Airy 400",
    "budget_200": "Budget 200", "luxe_compact": "Luxe Compact", "instant_square": "Instant Square",
    "instant_mini": "Instant Mini", "grit_400": "Grit 400", "silver_100": "Silver 100",
    "slide_100": "Slide 100", "night_800t": "Night 800T", "cross_process": "Cross Process",
    "plastic_120": "Plastic 120", "expired_90s": "Expired 90s", "half_frame": "Half Frame",
    "purple_haze": "Purple Haze",
}


# --------------------------------------------------------------------------- #
# Colour maths (vectorised over (N,3) arrays of encoded sRGB 0..1)
# --------------------------------------------------------------------------- #
def _sig(t):
    return 1.0 / (1.0 + np.exp(-t))


def sigmoid_curve(x, k, p):
    if abs(k) < 1e-6:
        return x
    kk = abs(k)
    s0, s1 = _sig(-kk * p), _sig(kk * (1 - p))
    if k > 0:
        return (_sig(kk * (x - p)) - s0) / (s1 - s0)
    sv = np.clip(x * (s1 - s0) + s0, 1e-9, 1 - 1e-9)       # inverse logistic: flattens the middle
    return p + np.log(sv / (1 - sv)) / kk


def apply_curve(x, c: Curve):
    x = np.clip(x, 0.0, 1.0)
    if c.gamma != 1.0:
        x = x ** c.gamma
    x = sigmoid_curve(x, c.k, c.pivot)
    return c.lift + x * (c.white - c.lift)


def hsv_hue(rgb):
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    mx, mn = rgb.max(1), rgb.min(1)
    d = np.where(mx - mn < 1e-9, 1.0, mx - mn)
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4))
    return (h * 60.0) % 360.0, mx - mn


def band_weight(h, centre, half_width):
    d = np.abs((h - centre + 180.0) % 360.0 - 180.0)
    return np.cos(0.5 * np.pi * np.minimum(1.0, d / half_width)) ** 2


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def chroma_ops(rgb, look: Look):
    """Saturation by hue band and hue rotation, both luma-preserving (rotate the Cb/Cr plane)."""
    y = rgb @ LUMA
    cb, cr = (rgb[:, 2] - y) / 1.8556, (rgb[:, 0] - y) / 1.5748
    h, chroma = hsv_hue(rgb)
    mult = np.full(len(rgb), look.sat)
    for centre, m in look.sat_bands.items():
        mult *= 1.0 + (m - 1.0) * band_weight(h, centre, look.band_width)
    theta = np.zeros(len(rgb))
    for centre, hw, delta in look.hue_shifts:
        theta += delta * band_weight(h, centre, hw)
    theta = np.radians(theta) * smoothstep(0.02, 0.15, chroma)    # leave neutrals alone
    c, s = np.cos(theta), np.sin(theta)
    cb, cr = (cb * c - cr * s) * mult, (cb * s + cr * c) * mult
    r = y + 1.5748 * cr
    b = y + 1.8556 * cb
    g = (y - LUMA[0] * r - LUMA[2] * b) / LUMA[1]
    return np.stack([r, g, b], 1)


def split_tone(rgb, look: Look):
    y = np.clip(rgb @ LUMA, 0, 1)[:, None]
    out = rgb
    if look.shadow and look.shadow[1] > 0:
        t = hexrgb(look.shadow[0])
        out = out + look.shadow[1] * (1 - y) ** 2.2 * (t - t @ LUMA)
    if look.highlight and look.highlight[1] > 0:
        t = hexrgb(look.highlight[0])
        out = out + look.highlight[1] * y ** 2.2 * (t - t @ LUMA)
    return out


def apply_look(rgb: np.ndarray, look: Look) -> np.ndarray:
    x = np.clip(rgb, 0, 1)
    if look.mono is not None:
        g = np.clip(x @ np.array(look.mono), 0, 1)
        g = apply_curve(g, look.curve)
        x = np.repeat(g[:, None], 3, 1)
        return np.clip(split_tone(x, look), 0, 1)
    if look.mix is not None:
        m = np.array(look.mix, dtype=float).reshape(3, 3)
        m = m / m.sum(1, keepdims=True)
        x = x @ m.T
    x = np.clip(x * np.array(look.gains), 0, 1)
    x = apply_curve(x, look.curve)
    for i, c in enumerate((look.rc, look.gc, look.bc)):
        if c is not None:
            x[:, i] = apply_curve(x[:, i], c)
    x = chroma_ops(x, look)
    x = split_tone(np.clip(x, 0, 1), look)
    return np.clip(x, 0, 1)


# --------------------------------------------------------------------------- #
# LUT layout and IO
# --------------------------------------------------------------------------- #
def grid() -> np.ndarray:
    """(33^3, 3) input colours, red fastest then green then blue (the .cube / contract order)."""
    t = np.linspace(0, 1, SIZE)
    b, g, r = np.meshgrid(t, t, t, indexing="ij")
    return np.stack([r.ravel(), g.ravel(), b.ravel()], 1)


def lut_to_u8(lut: np.ndarray) -> np.ndarray:
    return np.clip(np.rint(lut * 255.0), 0, 255).astype(np.uint8)


def lut_to_strip(lut_u8: np.ndarray) -> np.ndarray:
    """(33^3,3) in [b][g][r] order -> (33, 1089, 3) with pixel (x = b*33 + r, y = g)."""
    return lut_u8.reshape(SIZE, SIZE, SIZE, 3).transpose(1, 0, 2, 3).reshape(SIZE, SIZE * SIZE, 3)


def strip_to_lut(strip: np.ndarray) -> np.ndarray:
    """Inverse of lut_to_strip, returns float (33^3, 3) in 0..1."""
    return strip.reshape(SIZE, SIZE, SIZE, 3).transpose(1, 0, 2, 3).reshape(-1, 3) / 255.0


def write_cube(path: str, title: str, lut: np.ndarray):
    with open(path, "w") as f:
        f.write(f'TITLE "{title}"\nLUT_3D_SIZE {SIZE}\nDOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n')
        for r, g, b in lut:
            f.write(f"{r:.6f} {g:.6f} {b:.6f}\n")


def sample_lut(lut: np.ndarray, rgb: np.ndarray) -> np.ndarray:
    """Trilinear lookup of (M,3) colours in a (33^3,3) LUT."""
    n = SIZE - 1
    p = np.clip(rgb, 0, 1) * n
    i0 = np.minimum(p.astype(int), n - 1)
    f = p - i0
    out = np.zeros_like(rgb, dtype=float)
    for db in (0, 1):
        for dg in (0, 1):
            for dr in (0, 1):
                w = (f[:, 0] if dr else 1 - f[:, 0]) * (f[:, 1] if dg else 1 - f[:, 1]) * (f[:, 2] if db else 1 - f[:, 2])
                idx = ((i0[:, 2] + db) * SIZE + (i0[:, 1] + dg)) * SIZE + (i0[:, 0] + dr)
                out += w[:, None] * lut[idx]
    return out


# --------------------------------------------------------------------------- #
# Contact sheet
# --------------------------------------------------------------------------- #
def test_chart(w: int, h: int) -> np.ndarray:
    """Synthetic chart: grey ramp, hue sweep, skin/sky/foliage patches and a tiny scene. Float RGB (h,w,3)."""
    img = np.zeros((h, w, 3))
    ramp_h, hue_h, patch_h = h // 10, h // 10, h // 6
    x = np.linspace(0, 1, w)
    steps = np.floor(x * 15.999) / 15.0
    img[0:ramp_h] = steps[None, :, None]
    hh = np.floor(x * 23.999) / 24.0 * 360.0
    hue_rgb = np.array([colorsys.hsv_to_rgb((v % 360) / 360.0, 0.75, 0.85) for v in hh])
    img[ramp_h:ramp_h + hue_h] = hue_rgb[None]
    patches = [(0.96, 0.80, 0.69), (0.80, 0.58, 0.45), (0.45, 0.30, 0.22), (0.35, 0.58, 0.88),
               (0.22, 0.45, 0.15), (0.60, 0.55, 0.25), (0.80, 0.15, 0.12), (0.10, 0.15, 0.50)]
    y0 = ramp_h + hue_h
    pw = w / len(patches)
    for i, c in enumerate(patches):
        img[y0:y0 + patch_h, int(i * pw):int((i + 1) * pw)] = c
    y1 = y0 + patch_h
    sh = h - y1
    yy, xx = np.mgrid[0:sh, 0:w]
    v = yy / max(1, sh - 1)
    sky = np.array([0.30, 0.52, 0.90])[None, None] * (1 - v[..., None] * 0.6) \
        + np.array([0.85, 0.90, 0.95])[None, None] * (v[..., None] * 0.6)
    scene = sky.copy()
    hill = yy > (0.55 + 0.12 * np.sin(xx / w * 6.0)) * sh
    scene[hill] = np.array([0.25, 0.50, 0.17]) * (0.8 + 0.4 * (yy[hill] / sh))[:, None]
    house = (xx > 0.08 * w) & (xx < 0.30 * w) & (yy > 0.40 * sh) & (yy < 0.80 * sh)
    scene[house] = (0.78, 0.18, 0.14)
    roof = (xx > 0.06 * w) & (xx < 0.32 * w) & (yy > 0.30 * sh) & (yy <= 0.40 * sh)
    scene[roof] = (0.30, 0.20, 0.18)
    wall = (xx > 0.40 * w) & (xx < 0.46 * w) & (yy < 0.55 * sh)
    scene[wall] = (0.92, 0.92, 0.88)
    face = (xx - 0.62 * w) ** 2 + (yy - 0.55 * sh) ** 2 < (0.20 * sh) ** 2
    scene[face] = (0.86, 0.64, 0.52)
    sun = (xx - 0.90 * w) ** 2 + (yy - 0.18 * sh) ** 2 < (0.10 * sh) ** 2
    scene[sun] = (1.0, 0.97, 0.80)
    img[y1:] = scene
    return img


def contact_sheet(ids: List[str], cols: int = 5, cell_w: int = 464, cell_h: int = 252) -> Image.Image:
    label_h, gap = 26, 12
    chart = test_chart(cell_w, cell_h)
    rows = math.ceil(len(ids) / cols)
    sheet = Image.new("RGB", (cols * (cell_w + gap) + gap, rows * (cell_h + label_h + gap) + gap), (24, 22, 28))
    d = ImageDraw.Draw(sheet)
    flat = chart.reshape(-1, 3)
    for n, pid in enumerate(ids):
        strip = np.array(Image.open(os.path.join(LUT_DIR, pid + ".png")).convert("RGB"))
        lut = strip_to_lut(strip)       # decoded from the written PNG: also verifies the layout
        out = np.clip(sample_lut(lut, flat).reshape(chart.shape), 0, 1)
        tile = Image.fromarray((out * 255 + 0.5).astype(np.uint8))
        cx, cy = gap + (n % cols) * (cell_w + gap), gap + (n // cols) * (cell_h + label_h + gap)
        d.text((cx + 2, cy + 6), f"{NAMES[pid]}  ({pid})", fill=(235, 228, 214))
        sheet.paste(tile, (cx, cy + label_h))
    return sheet


# --------------------------------------------------------------------------- #
# Bonus: fit a LUT from reference photo pairs (replaces the parametric looks)
# --------------------------------------------------------------------------- #
def fit_lut_from_pairs(src: np.ndarray, dst: np.ndarray, smooth: float = 0.05, prior: float = 0.01,
                       iters: int = 200) -> np.ndarray:
    """Least-squares 33^3 LUT from paired pixels.

    src, dst: (N,3) float arrays in 0..1, the same scene before (phone photo, encoded sRGB) and
    after (scan of the real film look), spatially aligned. Solves, per output channel,
        min_x |A x - dst|^2 + smooth |Laplacian x|^2 + prior |x - identity|^2
    where A is the trilinear interpolation operator, by matrix-free conjugate gradients, so
    lattice nodes without data fall back to a smooth extrapolation of their neighbours.
    Returns an (33^3, 3) LUT in the same order as grid(); write_cube / lut_to_strip work unchanged.
    """
    n = SIZE
    p = np.clip(src, 0, 1) * (n - 1)
    i0 = np.minimum(p.astype(int), n - 2)
    f = p - i0
    idx, wts = [], []
    for db in (0, 1):
        for dg in (0, 1):
            for dr in (0, 1):
                w = (f[:, 0] if dr else 1 - f[:, 0]) * (f[:, 1] if dg else 1 - f[:, 1]) * (f[:, 2] if db else 1 - f[:, 2])
                idx.append(((i0[:, 2] + db) * n + (i0[:, 1] + dg)) * n + (i0[:, 0] + dr))
                wts.append(w)
    total = n ** 3

    def A(x):
        return sum(w * x[i] for i, w in zip(idx, wts))

    def At(v):
        return sum(np.bincount(i, weights=w * v, minlength=total) for i, w in zip(idx, wts))

    def lap(x):
        v = x.reshape(n, n, n)
        out = np.zeros_like(v)
        for ax in range(3):
            out += 2 * v - np.roll(v, 1, ax) - np.roll(v, -1, ax)
        return out.ravel()

    ident = grid()
    result = np.zeros((total, 3))
    for ch in range(3):
        rhs = At(dst[:, ch]) + prior * ident[:, ch]

        def op(x):
            return At(A(x)) + smooth * lap(lap(x)) + prior * x

        x = ident[:, ch].copy()
        r = rhs - op(x)
        pdir = r.copy()
        rs = r @ r
        for _ in range(iters):
            ap = op(pdir)
            alpha = rs / max(pdir @ ap, 1e-20)
            x += alpha * pdir
            r -= alpha * ap
            rs_new = r @ r
            if rs_new < 1e-14:
                break
            pdir = r + (rs_new / rs) * pdir
            rs = rs_new
        result[:, ch] = x
    return np.clip(result, 0, 1)


# --------------------------------------------------------------------------- #
def build_all(ids: Optional[List[str]] = None, sheet: bool = True):
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(LUT_DIR, exist_ok=True)
    ids = ids or list(LOOKS)
    g = grid()
    for pid in ids:
        lut = apply_look(g, LOOKS[pid])
        u8 = lut_to_u8(lut)
        write_cube(os.path.join(OUT, pid + ".cube"), NAMES[pid], u8 / 255.0)
        Image.fromarray(lut_to_strip(u8), "RGB").save(os.path.join(LUT_DIR, pid + ".png"), optimize=True)
    if sheet:
        img = contact_sheet(list(LOOKS))
        img.save(os.path.join(OUT, "contact_sheet.png"), optimize=True)
        small = img.resize((img.width // 2, img.height // 2), Image.LANCZOS)
        small.save(DOCS_SHEET, optimize=True)
    print(f"wrote {len(ids)} LUTs to {LUT_DIR}")


if __name__ == "__main__":
    only = sys.argv[1:]
    build_all(only or None, sheet=not only)   # the contact sheet needs all 25 PNGs
