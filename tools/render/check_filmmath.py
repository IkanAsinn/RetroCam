#!/usr/bin/env python3
"""Harness-vs-FilmMath golden check: flat colours through the real shader (llvmpipe) vs FilmMath.apply.
Needs: dump.sh <tag> 256x192 both gold_snap,instant_mini,mono_snap,slide_100,expired_90s
(writes filmmath_reference.json too).   usage: check_filmmath.py out/<tag>
Tolerance: 2/255 per channel (8-bit output quantisation + GPU float / trilinear differences)."""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import filmlib as L  # noqa: E402

d = sys.argv[1]
ref = json.load(open(os.path.join(d, "filmmath_reference.json")))
ZERO = {"uGrain": [0, 1], "uCaPx": [0], "uEdgeSoft": [0], "uHalStrength": [0]}
worst = 0.0
cache = {}
for r in ref:
    key = (r["profile"], r["flash"], tuple(r["rgb"]))
    if key not in cache:
        img = np.empty((192, 256, 3), np.uint8)
        img[:] = r["rgb"]
        cache[key] = L.render(d, r["profile"], r["flash"], img, overrides=ZERO).astype(float) / 255
    got = cache[key][r["py"], r["px"]]
    err = np.abs(got - np.array(r["expected"])).max() * 255
    worst = max(worst, err)
    if err > 2.0:
        print("MISMATCH", r["profile"], r["flash"], r["rgb"], r["px"], r["py"], "got", got * 255, "exp", np.array(r["expected"]) * 255)
print("cases=%d  max abs error = %.2f / 255  -> %s" % (len(ref), worst, "PASS" if worst <= 2.0 else "FAIL"))
sys.exit(0 if worst <= 2.0 else 1)
