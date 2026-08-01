package com.duzman46.gridbound.game.animation

import com.duzman46.gridbound.core.Constants
import javax.inject.Inject

class AnimationManager @Inject constructor() {
    val pawnDurationMillis: Int = Constants.Animation.PAWN_DURATION_MILLIS
    val wallDurationMillis: Int = Constants.Animation.WALL_DURATION_MILLIS
    val invalidPreviewMillis: Long = Constants.Animation.INVALID_PREVIEW_MILLIS
}

