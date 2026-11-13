package com.retrocam.profiles

import com.retrocam.filmengine.FilmParams

/** The ONLY place profile + user toggles + per-shot randomness become shader params. */
object ParamsMapper {
    fun toFilmParams(
        profile: CameraProfile,
        toggles: EffectToggles,
        lens: LensKind,
        flashActive: Boolean,
        seed: Int,
        brightHighlight: Boolean,
    ): FilmParams = TODO("phase 2")
}
