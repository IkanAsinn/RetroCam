package com.retrocam.filmengine

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The single GLSL ES 3.00 film shader used by BOTH the preview ([FilmCameraEffect]) and the
 * capture ([OffscreenFilmRenderer]) paths. The only difference between the paths is the scene
 * sampler type (samplerExternalOES vs sampler2D), switched by a tiny prefix.
 *
 * Conventions
 *  - `vUv` is the output-image UV with the origin at the top-left (y down), 0..1 over the whole
 *    output (also across tiles of a tiled capture).
 *  - Spatial quantities (grain size, CA, halation radius, edge blur) are given at
 *    [FilmParams.REFERENCE_SHORT_EDGE] and scaled by `min(w, h) / 1080` in the shader.
 *  - "r" is the normalised radial distance from the centre: 0 at centre, 1 at the corners.
 */
internal object FilmShader {

    const val VERTEX: String = """#version 300 es
layout(location = 0) in vec2 aPos;   // 0..1 quad corners
uniform vec4 uQuadRect;              // x0, y0, w, h of this draw within the full output (GL-up 0..1)
uniform float uTopDown;              // 1: output row 0 is the image top (capture), 0: GL-up (preview)
out vec2 vUv;
void main() {
    gl_Position = vec4(aPos * 2.0 - 1.0, 0.0, 1.0);
    vec2 st = uQuadRect.xy + aPos * uQuadRect.zw;
    vUv = vec2(st.x, mix(1.0 - st.y, st.y, uTopDown));
}
"""

    private fun prefix(external: Boolean): String = buildString {
        append("#version 300 es\n")
        if (external) {
            append("#extension GL_OES_EGL_image_external_essl3 : require\n")
            append("#define SCENE_SAMPLER samplerExternalOES\n")
        } else {
            append("#define SCENE_SAMPLER sampler2D\n")
        }
        append("precision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler3D;\n")
        if (external) append("precision highp samplerExternalOES;\n")
    }

    fun fragmentSource(external: Boolean): String = prefix(external) + BODY

    /** Plain box-filtered copy of the scene, used for the tiny highlight read-back (preview only). */
    fun passthroughSource(external: Boolean): String = prefix(external) + """
in vec2 vUv;
out vec4 fragColor;
uniform SCENE_SAMPLER uScene;
uniform mat4 uTexMat;
uniform float uTopDown;
uniform vec2 uCell;   // size of one output texel in UV
vec3 fetch(vec2 uv) {
    float t = mix(1.0 - uv.y, uv.y, uTopDown);
    return texture(uScene, (uTexMat * vec4(uv.x, t, 0.0, 1.0)).xy).rgb;
}
void main() {
    vec3 acc = vec3(0.0);
    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            acc += fetch(vUv + (vec2(float(i), float(j)) - 1.5) * 0.25 * uCell);
        }
    }
    fragColor = vec4(acc / 16.0, 1.0);
}
"""

    private fun halationDirs(): String {
        val n = FilmMath.HALATION_TAPS
        return (0 until n).joinToString(", ") {
            val a = it * 2.0 * PI / n
            "vec2(${"%.6f".format(Locale.ROOT, cos(a))}, ${"%.6f".format(Locale.ROOT, sin(a))})"
        }
    }

    private val BODY: String = """
in vec2 vUv;
out vec4 fragColor;

uniform SCENE_SAMPLER uScene;
uniform sampler3D uLut;
uniform mat4 uTexMat;
uniform float uTopDown;
uniform vec2 uRes;            // full output size in px
uniform float uScale;         // min(w,h) / 1080
uniform int uSeed;

uniform float uExposureMul;   // 2^EV
uniform float uBlackLift;
uniform float uContrast;
uniform float uSaturation;
uniform vec3 uColorShift;
uniform float uLutSize;

uniform float uCaPx;
uniform float uEdgeSoft;
uniform vec3 uFlash;          // hotspot size, falloff, strength
uniform vec3 uFlashTint;
uniform float uFlashBg;       // flash background drop (0 = off)
uniform sampler2D uIllum;     // film flash: small blurred ambient-illumination map (gamma encoded RGB, row 0 = top); capture only
uniform vec4 uRelight;        // film flash: effective strength (0 = stage skipped), ambient drop, 0, 0
uniform vec4 uRelightSubj;    // film flash: subject centre (uv, y down), light radius in frame heights, 0
uniform vec3 uRelightTint;    // film flash: flash light colour
uniform float uHalStrength;
uniform vec3 uHalTint;
uniform vec4 uFlare;          // style (0 none, 1 warm, 2 rainbow), strength, centre x, centre y
uniform vec4 uLeak;           // strength, angle, offset, 0
uniform vec3 uLeakTint;
uniform vec2 uVig;            // strength, softness
uniform vec2 uGrain;          // amount, size px at reference
uniform float uStrength;      // 0..1 filter strength (1 = full film look, 0 = untouched input)

const vec3 LUMA = vec3(${FilmMath.LUMA_R}, ${FilmMath.LUMA_G}, ${FilmMath.LUMA_B});
const float GAMMA = ${FilmMath.GAMMA};
const int HAL_TAPS = ${FilmMath.HALATION_TAPS};
const vec2 HAL_DIRS[HAL_TAPS] = vec2[HAL_TAPS](${halationDirs()});

vec3 fetch(vec2 uv) {
    float t = mix(1.0 - uv.y, uv.y, uTopDown);
    return texture(uScene, (uTexMat * vec4(uv.x, t, 0.0, 1.0)).xy).rgb;
}

uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float hash2(vec2 cell, uint seed) {
    uvec2 q = uvec2(ivec2(cell));
    uint h = pcg(q.x ^ pcg(q.y ^ pcg(seed)));
    return float(h >> 8) * (1.0 / 16777216.0);
}
vec3 hueColor(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// ---- film flash relight (CPU reference: RelightMath.apply). s = sRGB-ish scene colour, uv/asp/r = position terms ----
vec3 relight(vec3 s, vec2 uv, vec2 asp, float r) {
    vec3 lin = pow(max(s, 0.0), vec3(GAMMA));
    vec3 ill = pow(texture(uIllum, uv).rgb, vec3(GAMMA));
    float il = dot(ill, LUMA);
    vec3 illc = max(vec3(il) + (ill - il) * ${RelightMath.CHROMA_KEEP}, vec3(${RelightMath.ILLUM_FLOOR}));
    vec3 alb = min(lin / illc, vec3(${RelightMath.ALBEDO_MAX}));
    vec2 dv = (uv - uRelightSubj.xy) * asp;
    float d = length(dv) / uRelightSubj.z;
    float q = 1.0 / (1.0 + d * d);
    float light = (q * q) * (1.0 - ${RelightMath.COVER_DROP} * smoothstep(0.55, 1.1, r));
    vec3 o = lin * (1.0 - uRelight.y * (1.0 - light)) + alb * uRelightTint * (uRelight.x * light);
    float m = max(o.r, max(o.g, o.b));
    if (m > ${RelightMath.SHOULDER_KNEE}) {
        float span = 1.0 - ${RelightMath.SHOULDER_KNEE};
        float m2 = ${RelightMath.SHOULDER_KNEE} + span * (1.0 - exp(-(m - ${RelightMath.SHOULDER_KNEE}) / span));
        o *= m2 / m;
        o = mix(o, vec3(dot(o, LUMA)), ${RelightMath.ROLL_DESAT} * smoothstep(${RelightMath.SHOULDER_KNEE}, 1.5, m));
    }
    return pow(clamp(o, 0.0, 1.0), vec3(1.0 / GAMMA));
}

// ---- flare: analytic ghosts and streak, centre/highlight in UV (y down) ----
vec3 flareLayer(vec2 p, vec2 asp) {
    vec2 f = (uFlare.zw - 0.5) * asp;
    float fl = length(f);
    vec2 fd = fl > 1e-3 ? f / fl : vec2(1.0, 0.0);
    vec3 outc = vec3(0.0);
    if (uFlare.x < 1.5) {
        // WARM: soft orange halo at the highlight, ghosts mirrored through the centre, streak.
        vec3 orange = vec3(1.0, 0.55, 0.2);
        vec2 d0 = p - f;
        float halo = exp(-dot(d0, d0) / 0.0144);
        vec2 d1 = p + f * 0.7;
        float ghost1 = exp(-dot(d1, d1) / 0.0081);
        vec2 d2 = p + f * 1.3;
        float ghost2 = exp(-dot(d2, d2) / 0.0025);
        float along = dot(p, fd);
        float perp = dot(p, vec2(-fd.y, fd.x));
        float streak = exp(-perp * perp / 0.000144) * exp(-(along - fl) * (along - fl) / 0.35);
        outc = orange * (halo * 0.8 + ghost1 * 0.35 + ghost2 * 0.25 + streak * 0.45);
    } else {
        // RAINBOW: ring of hue-shifted ghosts along the line through the centre.
        vec2 d0 = p - f;
        outc = vec3(1.0, 0.95, 0.9) * exp(-dot(d0, d0) / 0.01) * 0.35;
        float reach = max(fl, 0.2);
        for (int i = 0; i < 5; i++) {
            float k = (i == 0) ? -1.25 : (i == 1) ? -0.8 : (i == 2) ? -0.4 : (i == 3) ? 0.35 : 0.75;
            float fi = float(i);
            vec2 gp = fd * (k * reach);
            float rad = 0.055 + 0.016 * fi;
            float d = length(p - gp);
            float rr = (d - rad) / 0.012;
            float ring = exp(-rr * rr);
            float fill = smoothstep(rad, 0.0, d) * 0.12;
            outc += hueColor(fi * 0.21 + 0.05) * (ring * 0.55 + fill);
        }
    }
    return clamp(outc * uFlare.y, 0.0, 1.0);
}

// ---- light leak: soft gradient blob entering from an edge ----
vec3 leakLayer(vec2 p) {
    float a = uLeak.y;
    vec2 dir = vec2(cos(a), sin(a));
    vec2 perp = vec2(-dir.y, dir.x);
    vec2 anchor = dir * 0.6 + perp * (uLeak.z - 0.5) * 0.9;
    vec2 q = p - anchor;
    float d = length(vec2(dot(q, dir) / 0.5, dot(q, perp) / 0.35));
    float m = exp(-d * d);
    vec3 col = mix(uLeakTint, vec3(1.0, 0.85, 0.6), m * m * 0.45);
    return clamp(col * m * uLeak.x, 0.0, 1.0);
}

void main() {
    vec2 uv = vUv;
    float aspect = uRes.x / uRes.y;
    vec2 asp = vec2(aspect, 1.0);
    vec2 pix = uv * uRes;
    vec2 pxToUv = 1.0 / uRes;
    vec2 p = (uv - 0.5) * asp;
    float r = length(p) / (0.5 * length(asp));

    // 1. Sampling: chromatic aberration + edge softness
    vec3 col;
    if (uCaPx > 0.0) {
        vec2 dpx = (uv - 0.5) * uRes;
        vec2 dir = dpx / max(length(dpx), 1e-4);
        vec2 off = dir * (uCaPx * uScale * r * r) * pxToUv;
        col = vec3(fetch(uv + off).r, fetch(uv).g, fetch(uv - off).b);
    } else {
        col = fetch(uv);
    }
    float bw = uEdgeSoft * smoothstep(0.6, 1.1, r);
    if (bw > 0.002) {
        vec2 rad = vec2(5.0 * uScale * bw) * pxToUv;
        vec3 sum = col;
        for (int i = 0; i < 6; i++) {
            float a = float(i) * 1.0471976;
            sum += fetch(uv + vec2(cos(a), sin(a)) * rad);
        }
        col = mix(col, sum / 7.0, min(1.0, bw * 1.5));
    }

    // 1a. Film flash (capture only, uRelight.x = 0 in the preview): light the scene before the film sees it.
    // It replaces the plain background drop of 1b (the ambient drop is part of the relight).
    bool relit = uRelight.x > 0.0;
    if (relit) {
        col = relight(col, uv, asp, r);
    }

    // 1b. Flash background falloff: the flash lights the near subject, the dark far background drops
    // toward black (before exposure/LUT so the film's own black lift and tint still apply).
    if (uFlashBg > 0.0 && !relit) {
        float bl = dot(col, LUMA);
        float drop = uFlashBg * (1.0 - smoothstep(${FilmMath.BG_LUMA_LO}, ${FilmMath.BG_LUMA_HI}, bl)) * mix(${FilmMath.BG_RADIAL_MIN}, 1.0, clamp(r, 0.0, 1.0));
        col *= 1.0 - clamp(drop, 0.0, 1.0);
    }

    // 2. Exposure (gamma-ish linear space)
    vec3 c = col;
    if (uExposureMul != 1.0) {
        c = pow(max(c, 0.0), vec3(GAMMA)) * uExposureMul;
        c = clamp(pow(c, vec3(1.0 / GAMMA)), 0.0, 1.0);
    }
    // 3. Contrast around mid grey, black lift
    c = (c - 0.5) * uContrast + 0.5;
    c = uBlackLift + c * (1.0 - uBlackLift);
    c = clamp(c, 0.0, 1.0);

    // 4. 3D LUT (half-texel corrected so an identity LUT is exact)
    c = texture(uLut, (c * (uLutSize - 1.0) + 0.5) / uLutSize).rgb;

    // 5. Saturation, 6. colour shift
    float l = dot(c, LUMA);
    c = clamp(mix(vec3(l), c, uSaturation), 0.0, 1.0);
    c = clamp(c + uColorShift, 0.0, 1.0);

    // 7. Flash look: the phone's real flash already lit the frame, so there is NO synthetic hotspot.
    // Only a subtle, uniform colour-temperature tint (uFlash.z = strength) remains.
    if (uFlash.z > 0.0) {
        c = clamp(c * mix(vec3(1.0), uFlashTint, ${FilmMath.FLASH_TINT_MIX} * uFlash.z), 0.0, 1.0);
    }

    // 8. Halation: tinted glow from bright areas
    if (uHalStrength > 0.0) {
        float radius = 32.0 * uScale * (0.7 + 0.6 * uHalStrength);
        float ign = fract(52.9829189 * fract(dot(pix, vec2(0.06711056, 0.00583715))));
        float ca = cos(ign * 6.2831853);
        float sa = sin(ign * 6.2831853);
        float acc = 0.0;
        for (int i = 0; i < HAL_TAPS; i++) {
            vec2 d = HAL_DIRS[i];
            vec2 rd = vec2(ca * d.x - sa * d.y, sa * d.x + ca * d.y);
            float rr = radius * (((i & 1) == 0) ? 1.0 : 0.55);
            vec3 s = fetch(uv + rd * rr * pxToUv) * uExposureMul;
            acc += smoothstep(${FilmMath.HAL_LUMA_LO}, 1.0, dot(s, LUMA));
        }
        vec3 glow = uHalTint * (acc / float(HAL_TAPS)) * uHalStrength * ${FilmMath.HAL_GAIN};
        c = 1.0 - (1.0 - c) * (1.0 - clamp(glow, 0.0, 1.0));
    }

    // 9. Flare
    if (uFlare.x > 0.5 && uFlare.y > 0.0) {
        vec3 fl = flareLayer(p, asp);
        c = 1.0 - (1.0 - c) * (1.0 - fl);
    }

    // 10. Light leak
    if (uLeak.x > 0.0) {
        vec3 lk = leakLayer(p);
        c = 1.0 - (1.0 - c) * (1.0 - lk);
    }

    // 11. Vignette
    if (uVig.x > 0.0) {
        float v = smoothstep(mix(0.85, 0.0, uVig.y), 1.05, r);
        c *= 1.0 - uVig.x * v;
    }

    // 12. Grain: procedural, smooth (bilinear value noise), luminance dependent (mid-tones strongest,
    // weak in deep shadows and highlights), mostly monochrome, scaled to the output size.
    if (uGrain.x > 0.0) {
        float size = max(uGrain.y * uScale * ${FilmMath.GRAIN_SIZE_MUL}, 1.0);
        vec2 gp = pix / size;
        vec2 cell = floor(gp);
        vec2 fr = gp - cell;
        fr = fr * fr * (3.0 - 2.0 * fr);
        uint seed = uint(uSeed);
        vec2 ox = vec2(1.0, 0.0);
        vec2 oy = vec2(0.0, 1.0);
        float n00 = hash2(cell, seed) + hash2(cell, seed ^ 2654435769u) - 1.0;
        float n10 = hash2(cell + ox, seed) + hash2(cell + ox, seed ^ 2654435769u) - 1.0;
        float n01 = hash2(cell + oy, seed) + hash2(cell + oy, seed ^ 2654435769u) - 1.0;
        float n11 = hash2(cell + ox + oy, seed) + hash2(cell + ox + oy, seed ^ 2654435769u) - 1.0;
        float n = mix(mix(n00, n10, fr.x), mix(n01, n11, fr.x), fr.y) * ${FilmMath.GRAIN_NOISE_GAIN};
        vec3 chroma = vec3(hash2(cell, seed + 17u), hash2(cell, seed + 31u), hash2(cell, seed + 47u)) - 0.5;
        float lc = dot(c, LUMA);
        float w = smoothstep(0.02, 0.3, lc) * (1.0 - 0.75 * smoothstep(0.6, 1.0, lc));
        c += (vec3(n) + chroma * ${FilmMath.GRAIN_CHROMA}) * (${FilmMath.GRAIN_AMP} * uGrain.x * w);
    }

    c = clamp(c, 0.0, 1.0);
    // 13. Filter strength: blend with the plain input sample (no CA, edge blur, grain or vignette).
    // The branch keeps strength 1 bit-identical to the unblended result.
    if (uStrength < 1.0) {
        c = mix(fetch(uv), c, uStrength);
    }
    fragColor = vec4(c, 1.0);
}
"""
}
