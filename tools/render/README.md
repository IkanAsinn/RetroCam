# Software render harness (real shader, no GPU)

Renders the REAL film fragment shader (2D/capture variant) on sample photos with Mesa llvmpipe (GLES 3.2 via EGL
pbuffer), so the look can be judged without a device.

```
FilmParams path (exact):  ProfileRepository -> ParamsMapper -> FilmParams -> FilmUniforms.compute  (JVM test RenderDumpTool)
                          -> JSON uniforms + shader sources + LUT strips in tools/render/out/<tag>/
Renderer:                 film_render.c (EGL pbuffer + GLES 3.0): scene as a 2D texture (row 0 = image top), 33^3 RGB8 3D LUT
                          built like GlSupport (strip pixel x=b*33+r, y=g), topDown=1, identity tex matrix, 1024 px tiles + uQuadRect
```

## Setup (Debian/Ubuntu)
```
apt-get install -y libegl1 libgles2 libegl-mesa0 libegl1-mesa-dev libgles2-mesa-dev libgl1-mesa-dri
pip install numpy pillow scikit-image
python3 tools/render/make_samples.py     # regenerates tools/render/samples/*.jpg (already committed, ~0.4 MB total)
```
`filmlib.build()` (or `gcc -O2 -o out/film_render film_render.c -lEGL -lGLESv2`) builds the renderer; it is run with
`EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1`.

## Run
```
export ANDROID_HOME=/opt/android-sdk
python3 -c "import sys; sys.path.insert(0,'tools/render'); import filmlib; filmlib.build()"
tools/render/dump.sh after 512x512,600x400,451x300,640x427,4000x3000        # uniforms for every profile x flash x size
python3 tools/render/sheets.py contact out/after out/contact_sheet.png      # (run from tools/render) rows = photos, cols = profiles
python3 tools/render/sheets.py flash out/after out/flash_on_off.png         # flash off vs on on flash-lit photos
python3 tools/render/sheets.py compare out/before out/after gold_snap out/before_after_gold_snap.png   # incl. 1:1 crops of a 4000x3000 render
python3 tools/render/sheets.py flashcmp out/before out/after out/flash_before_after.png
tools/render/dump.sh gold 256x192 both gold_snap,instant_mini,mono_snap,slide_100,expired_90s && python3 tools/render/check_filmmath.py out/gold
```
`dump.sh` takes extra env: `RENDER_GRAIN`, `RENDER_FLARE`, `RENDER_LEAKS`, `RENDER_STRENGTH`, `RENDER_SEED` (see `RenderDumpTool.kt`).
A "before" dump of an older revision: check out that revision's `filmengine/` + `profiles/` into a scratch worktree (keeping `FilmUniforms.kt` + `RenderDumpTool.kt`) and run `dump.sh before ...`.
Seeds must stay below 2^24 (passed through a float text file). Everything under `out/` is gitignored.

`check_filmmath.py` renders flat colours through the shader (grain/CA/blur/halation off) and compares against `FilmMath.apply`:
480 cases (5 profiles x flash on/off x 8 colours x 6 positions), max error 1.4/255.

## Sample photos (all freely licensed, bundled with scikit-image 0.26; no network needed)
`astronaut` (NASA, public domain), `coffee` (Rachel Michetti, CC0), `chelsea` the cat (Stefan van der Walt, CC0), `rocket`
(SpaceX, CC0). The data are small (<= 640 px) and JPEG-compressed, so sky gradients show banding in the *originals*;
the 1:1 crops use a bicubic 4000x3000 upscale, i.e. a soft 12 MP source. `*_flash.jpg` are SYNTHETIC flash-lit variants
(`make_samples.py`: dim ambient + inverse-square-like centre light). Do not use film scans as inputs.

`retune_profiles.py` records the v0.3 one-off magnitude retune applied to the profile JSON.
