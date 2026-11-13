package com.retrocam.profiles

import androidx.annotation.DrawableRes

/**
 * Card art = three stacked vector drawables of one body shape (same 120x96 viewport).
 * Draw them in order on top of each other:
 *  - [body]: white, tint with [CardSpec.bodyColor]
 *  - [accent]: white, tint with [CardSpec.accentColor]
 *  - [detail]: fixed colours (lens, flash window, shading), never tinted
 */
data class CardLayers(@DrawableRes val body: Int, @DrawableRes val accent: Int, @DrawableRes val detail: Int)

fun CardBody.layers(): CardLayers = when (this) {
    CardBody.DISPOSABLE -> CardLayers(
        R.drawable.card_disposable_body, R.drawable.card_disposable_accent, R.drawable.card_disposable_detail,
    )
    CardBody.COMPACT -> CardLayers(
        R.drawable.card_compact_body, R.drawable.card_compact_accent, R.drawable.card_compact_detail,
    )
    CardBody.INSTANT -> CardLayers(
        R.drawable.card_instant_body, R.drawable.card_instant_accent, R.drawable.card_instant_detail,
    )
    CardBody.TOY -> CardLayers(
        R.drawable.card_toy_body, R.drawable.card_toy_accent, R.drawable.card_toy_detail,
    )
}
