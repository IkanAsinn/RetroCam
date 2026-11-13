# LUT authoring

`generate_luts.py` builds the 33x33x33 colour LUT of every RetroCam camera type.

```
pip install numpy pillow
python3 tools/lut/generate_luts.py            # all 25 types
python3 tools/lut/generate_luts.py gold_snap  # just one (no contact sheet)
```

Outputs

| File | Purpose |
|---|---|
| `profiles/src/main/assets/luts/<id>.png` | The shipped LUT: 1089x33, 8-bit RGB. Pixel `(x = b*33 + r, y = g)` is the output colour for input `(r, g, b) / 32`. |
| `tools/lut/out/<id>.cube` | Same LUT as an Adobe `.cube` (red fastest), for inspection in Resolve / Photoshop. Gitignored. |
| `tools/lut/out/contact_sheet.png` | Every LUT applied to a synthetic chart (grey ramp, hue sweep, skin / sky / foliage patches, a small scene). Gitignored. |
| `docs/lut-contact-sheet.png` | Half-size copy of the contact sheet, committed so reviewers can eyeball the 25 looks side by side. |

The script is deterministic (no random numbers), so reruns reproduce the PNGs byte for byte. The contact
sheet is rendered from the written PNGs, which also proves the strip layout. It shows the LUT only; the
profile's global exposure / black lift / contrast / saturation, grain, vignette etc. are applied on top by
the shader.

## How a look is described

Each camera type is a `Look` entry in `LOOKS` (PRD table order). Colour is processed on sRGB-encoded
values, in this order:

1. B&W types only: channel mix (`mono=(wr, wg, wb)`, like a coloured filter in front of the lens) then the tone curve, then split-tone, done.
2. `mix`: 3x3 channel crosstalk (rows normalised so greys stay grey), then `gains` (white balance).
3. `curve`: tone curve applied to all channels: gamma, logistic S-curve (`k` contrast, `pivot`), `lift` (black level) and `white` (shoulder). Negative `k` flattens the middle instead.
4. `rc` / `gc` / `bc`: extra per-channel curves. Cross Process uses a hard red S-curve and an inverted blue curve to get yellow-green highlights and cyan-blue shadows.
5. `sat` and `sat_bands`: luma-preserving saturation, globally and per hue band (hue centres in degrees: red 0, orange 30, yellow 60, green 120, cyan 180, blue 240, magenta 300).
6. `hue_shifts`: `(centre, half-width, degrees)` rotations of the chroma vector, gated so neutrals stay neutral. Purple Haze uses a wide +178 degree shift on greens (foliage to purple) and about -48 degrees on blues (sky to teal) while skin hues are left alone.
7. `shadow` / `highlight`: split toning, a colour and an amount weighted by `(1-luma)^2.2` and `luma^2.2`.

To change a look, edit its `Look(...)`, rerun the script, open `tools/lut/out/contact_sheet.png`.
Profile JSON (exposure, grain, vignette, flash...) lives separately in `profiles/src/main/assets/profiles/`.

## Replacing a parametric look with real reference pairs

The parametric description is a stand-in until there are reference photos. The intended upgrade path:

1. Shoot the same scene on a real film camera / scan and on the phone, align the pairs (e.g. SIFT + homography), downscale to a few hundred thousand pixel pairs covering the gamut (colour charts help). Keep both as encoded sRGB floats in 0..1.
2. Fit the LUT: `fit_lut_from_pairs(src, dst)` in `generate_luts.py` solves, per output channel, a regularised least squares problem
   `min |A x - dst|^2 + smooth |Laplacian x|^2 + prior |x - identity|^2`
   with the trilinear interpolation operator `A`, using matrix-free conjugate gradients (numpy only). Unobserved lattice nodes fall back to a smooth extrapolation. It recovers a synthetic look (mean error about 0.005) from 60k random pairs.
3. Feed the result to the same writers used for the parametric looks:
   `u8 = lut_to_u8(lut); write_cube(...); Image.fromarray(lut_to_strip(u8)).save(...)`.
   Nothing else in the app changes: a profile only references `<id>.png`.
4. If you use `colour-science`, it can replace the plain sRGB maths (e.g. do the curves in linear light or in a perceptual space such as Oklab); keep the 8-bit sRGB strip contract.

Tip: blend a fitted LUT with a parametric one (`0.7 * fitted + 0.3 * apply_look(grid(), LOOKS[id])`) to keep
smooth, sane behaviour in colours the reference photos did not cover.
