"""Helpers to run the REAL film shader (dumped by the JVM RenderDumpTool) on an image via film_render."""
import json
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BIN = os.path.join(HERE, "out", "film_render")


def build():
    os.makedirs(os.path.join(HERE, "out"), exist_ok=True)
    subprocess.check_call(["gcc", "-O2", "-o", BIN, os.path.join(HERE, "film_render.c"), "-lEGL", "-lGLESv2"])


def lut_raw(strip_png):
    """1089x33 strip (pixel x=b*33+r, y=g) -> bytes with index ((b*33+g)*33+r)*3, like GlSupport/Lut."""
    a = np.asarray(Image.open(strip_png).convert("RGB"))  # [g][x][c]
    assert a.shape[:2] == (33, 1089), a.shape
    a = a.reshape(33, 33, 33, 3)  # [g][b][r][c]
    return np.ascontiguousarray(a.transpose(1, 0, 2, 3)).tobytes()  # [b][g][r][c]


def write_ppm(path, arr):
    h, w = arr.shape[:2]
    with open(path, "wb") as f:
        f.write(b"P6\n%d %d\n255\n" % (w, h))
        f.write(np.ascontiguousarray(arr, dtype=np.uint8).tobytes())


def read_ppm(path):
    with open(path, "rb") as f:
        assert f.readline().strip() == b"P6"
        w, h = map(int, f.readline().split())
        f.readline()
        return np.frombuffer(f.read(), dtype=np.uint8).reshape(h, w, 3).copy()


def dump_path(dump_dir, profile, flash, w, h):
    return os.path.join(dump_dir, "%s_f%d_%dx%d.json" % (profile, 1 if flash else 0, w, h))


def render(dump_dir, profile, flash, img, tile=1024, overrides=None, shader_dir=None):
    """img: HxWx3 uint8. The dump for exactly this size must exist. Returns HxWx3 uint8."""
    h, w = img.shape[:2]
    with open(dump_path(dump_dir, profile, flash, w, h)) as f:
        d = json.load(f)
    u = dict(d["uniforms"])
    if overrides:
        u.update(overrides)
    shader_dir = shader_dir or dump_dir
    with tempfile.TemporaryDirectory() as td:
        ppm_in, ppm_out, lut, uni = [os.path.join(td, n) for n in ("in.ppm", "out.ppm", "lut.raw", "u.txt")]
        write_ppm(ppm_in, img)
        with open(lut, "wb") as f:
            f.write(lut_raw(os.path.join(dump_dir, "luts", profile + ".png")))
        with open(uni, "w") as f:
            f.write("uSeed %d\n" % d["seed"])
            for k, v in u.items():
                f.write(k + " " + " ".join(repr(float(x)) for x in v) + "\n")
        subprocess.check_call([BIN, os.path.join(shader_dir, "film.vert"), os.path.join(shader_dir, "film_2d.frag"),
                               ppm_in, lut, uni, ppm_out, str(tile)],
                              env=dict(os.environ, EGL_PLATFORM="surfaceless", LIBGL_ALWAYS_SOFTWARE="1"))
        return read_ppm(ppm_out)


def load_img(path):
    return np.asarray(Image.open(path).convert("RGB"))


def render_film(dump_dir, profile, film, image, img, tile=1024, overrides=None):
    """Film-flash render: uniforms from <profile>_rl<0|1>_<image>.json, ambient map from <image>_illum.ppm (dumpRelight)."""
    h, w = img.shape[:2]
    with open(os.path.join(dump_dir, "%s_rl%d_%s.json" % (profile, 1 if film else 0, image))) as f:
        d = json.load(f)
    assert (d["width"], d["height"]) == (w, h), (d["width"], d["height"], w, h)
    u = dict(d["uniforms"])
    if overrides:
        u.update(overrides)
    with tempfile.TemporaryDirectory() as td:
        ppm_in, ppm_out, lut, uni = [os.path.join(td, n) for n in ("in.ppm", "out.ppm", "lut.raw", "u.txt")]
        write_ppm(ppm_in, img)
        with open(lut, "wb") as f:
            f.write(lut_raw(os.path.join(dump_dir, "luts", profile + ".png")))
        with open(uni, "w") as f:
            f.write("uSeed %d\n" % d["seed"])
            for k, v in u.items():
                f.write(k + " " + " ".join(repr(float(x)) for x in v) + "\n")
        subprocess.check_call([BIN, os.path.join(dump_dir, "film.vert"), os.path.join(dump_dir, "film_2d.frag"),
                               ppm_in, lut, uni, ppm_out, str(tile), os.path.join(dump_dir, image + "_illum.ppm")],
                              env=dict(os.environ, EGL_PLATFORM="surfaceless", LIBGL_ALWAYS_SOFTWARE="1"))
        return read_ppm(ppm_out)
