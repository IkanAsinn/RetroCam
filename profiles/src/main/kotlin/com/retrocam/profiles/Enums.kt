package com.retrocam.profiles

import kotlinx.serialization.Serializable

@Serializable enum class CameraFamily { DISPOSABLE, COMPACT, INSTANT, BLACK_AND_WHITE, SLIDE_CINEMA, TOY }

@Serializable enum class LensKind { ULTRA_WIDE, MAIN, TELE, FRONT }

/** Multipliers 0, 0.5, 1, 1.6. */
enum class GrainLevel { OFF, LOW, NORMAL, HIGH }

enum class FlareMode { OFF, AUTO, ALWAYS }

enum class LeakMode { OFF, SUBTLE, STRONG }

@Serializable enum class FlareStyleSpec { NONE, WARM, RAINBOW, RANDOM }

@Serializable enum class CardBody { DISPOSABLE, COMPACT, INSTANT, TOY }
