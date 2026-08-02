package com.duzman46.gridbound.game.board

import androidx.compose.ui.geometry.Offset

object BoardOrientation {
    fun toModelOffset(
        offset: Offset,
        width: Float,
        height: Float,
        flipped: Boolean,
    ): Offset = if (flipped) {
        Offset(width - offset.x, height - offset.y)
    } else {
        offset
    }
}
