package com.retrocam.ui

import androidx.annotation.DrawableRes
import com.retrocam.profiles.CardBody
import com.retrocam.profiles.drawableRes

/** Three stacked drawables of one body shape; a resource id of 0 means "layer absent". */
data class CardArtLayers(@DrawableRes val body: Int, @DrawableRes val accent: Int, @DrawableRes val detail: Int)

/**
 * TODO(merge): the contract is `CardBody.layers(): CardLayers` (body / accent / detail) in :profiles.
 * This worktree's :profiles stub only has `CardBody.drawableRes()`, so this adapter falls back to a
 * single body layer. After merging replace the function body with:
 *     val l = layers(); return CardArtLayers(l.body, l.accent, l.detail)
 * and delete the `drawableRes` import. Nothing else in :app touches the profile card art.
 */
fun CardBody.artLayers(): CardArtLayers = CardArtLayers(body = drawableRes(), accent = 0, detail = 0)
