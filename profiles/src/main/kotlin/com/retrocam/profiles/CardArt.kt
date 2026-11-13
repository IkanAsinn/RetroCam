package com.retrocam.profiles

import androidx.annotation.DrawableRes

/** One vector drawable per body; the app tints body/accent. */
@DrawableRes
fun CardBody.drawableRes(): Int = when (this) {
    CardBody.DISPOSABLE -> R.drawable.card_disposable
    CardBody.COMPACT -> R.drawable.card_compact
    CardBody.INSTANT -> R.drawable.card_instant
    CardBody.TOY -> R.drawable.card_toy
}
