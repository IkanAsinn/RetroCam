# :filmengine

The GPU film look, shared by the live preview and the saved photo.

- `FilmShader.kt` - the ONE GLSL ES 3.00 fragment shader. Preview compiles it with
  `samplerExternalOES` (+ `GL_OES_EGL_image_external_essl3`), capture with `sampler2D`; that
  prefix is the only difference.
- `FilmCameraEffect` - CameraX `CameraEffect(PREVIEW)` + `SurfaceProcessor` on its own GL thread.
- `OffscreenFilmRenderer` - the same program on an EGL pbuffer, 1024x1024 tiles, own GL thread.
- `FilmMath` - pure-Kotlin CPU reference of the colour stages (tests, golden-image test).
- `Lut`, `FrameComposer`, `DateStampRenderer` - LUT model, capture-only framing, 7-segment date stamp.

## Shader pipeline (per pixel, in this order)

| # | Stage | Unit / notes |
|---|-------|--------------|
| 1 | Sampling: chromatic aberration, edge softness | CA: px at the corners (radial R/B offset, grows linearly with radius). Edge softness 0..1: 6-tap ring, weighted toward edges, radius 8 px * softness |
| 2 | Exposure | EV; applied as `x^2.2 * 2^EV` then `^(1/2.2)` |
| 3 | Contrast + black lift | `(c-0.5)*contrast+0.5`, then `lift + c*(1-lift)`, clamp |
| 4 | 3D LUT | `sampler3D`, trilinear, coords `(c*(N-1)+0.5)/N` so an identity LUT is exact |
| 5 | Saturation | around Rec.709 luma, after the LUT |
| 6 | colorShift | added after the LUT, clamp |
| 7 | Flash look | radial hotspot (size, falloff), brighten + wash out near the centre, tint from kelvin (relative to 6500 K) and optional gel |
| 8 | Halation | 12 taps on a per-pixel-rotated ring, luma threshold 0.72..1.0, radius 32 px * (0.7+0.6*strength), tinted, screen blend |
| 9 | Flare | analytic: WARM = orange halo + mirrored ghosts + streak; RAINBOW = 5 hue-shifted rings along the line through the centre. Screen blend |
| 10 | Light leak | soft elongated blob entering from the edge at `angle` (radians, y down), shifted along the edge by `offset` (0..1); screen blend |
| 11 | Vignette | `1 - strength * smoothstep(mix(0.85, 0, softness), 1.05, r)`, r = 0 centre .. 1 corner |
| 12 | Grain | hash noise per `grainSize*scale` cell from `seed` + pixel coords; strength `0.25*amount`, falls to 30 % in highlights; monochrome mostly, slight chroma |

Units: all spatial params (`grainSizePx`, `chromaticAberrationPx`, halation radius, edge blur) are
given at `REFERENCE_SHORT_EDGE = 1080` and multiplied by `min(w,h)/1080` in the shader, so a 12 MP
capture matches the preview. UV coordinates (flare centre, `Highlight`) are 0..1 with origin top-left of the
upright output frame. Typical texture taps: 1 (plain) + 12 (halation); worst case with CA, edge
softness and halation: 3 + 6 + 12 + 1 LUT = 22.

Stages 2-6 and 11 are mirrored in `FilmMath`; the golden test (`androidTest`) compares the GPU
capture path to it with all other effects off (max channel delta 3/255).

## Threading and lifecycle

- `FilmCameraEffect.update(params, lut)` is thread-safe and cheap; the LUT is re-uploaded only when the `Lut` *instance* changes (cache and reuse Lut objects).
- Preview grain animates: the effect adds a frame counter to `params.seed`.
- `highlight` updates about every 10 frames from a 32x24 unfiltered read-back (luma > 0.92).
- `OffscreenFilmRenderer.render` is a suspend function that hops to its own GL thread; renders are serialised. Output has the input's size; inputs over `GL_MAX_TEXTURE_SIZE` are downscaled only as a texture.
- Call `release()` on both when done.

## Frame formats (`FrameComposer`)

SQUARE: centre square. INSTANT_SQUARE: square crop on a #F7F5EF card (margins 5.7 % / 8.2 % / 5.7 % / 27.2 % of the
side). INSTANT_MINI: image area 46:62 (portrait source) or 62:46 (landscape) on the same card with
4/5/4/19 unit margins (portrait result is 54:86). HALF_FRAME: left and right halves of the photo, each centre-cropped to
3:4 portrait, side by side with a thin black gutter.
