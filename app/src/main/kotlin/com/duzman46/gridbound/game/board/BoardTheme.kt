package com.duzman46.gridbound.game.board

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.monetization.domain.Entitlement

/**
 * Board colour schemes.
 *
 * Purely cosmetic: every theme draws the same nine-by-nine board with the same rules. The
 * two paid themes are unlocked by an entitlement, and [CLASSIC] is always available so the
 * game never depends on a purchase.
 */
enum class BoardTheme(val entitlement: Entitlement?) {
    CLASSIC(entitlement = null),
    MIDNIGHT(entitlement = Entitlement.THEME_MIDNIGHT),
    SUNSET(entitlement = Entitlement.THEME_SUNSET),
    ;

    val isFree: Boolean get() = entitlement == null

    /**
     * CLASSIC follows the app's colour scheme so it keeps working with dynamic colour and
     * dark mode; the paid themes are fixed palettes, which is the point of buying them.
     */
    fun palette(colors: ColorScheme): BoardPalette = when (this) {
        CLASSIC -> BoardPalette(
            background = colors.surfaceVariant.copy(alpha = 0.58f),
            tile = colors.surface,
            tileAlternate = colors.surfaceVariant.copy(alpha = 0.72f),
            goalOne = Color(0xFF3F82FF),
            goalTwo = Color(0xFFFF9D3F),
            valid = Color(0xFF32D583),
            invalid = colors.error,
            wall = colors.secondary,
            playerOne = Color(0xFF3F82FF),
            playerTwo = Color(0xFFFF8A34),
            selection = colors.primary,
        )

        MIDNIGHT -> BoardPalette(
            background = Color(0xFF0B1020),
            tile = Color(0xFF161E36),
            tileAlternate = Color(0xFF1E2846),
            goalOne = Color(0xFF6EA8FF),
            goalTwo = Color(0xFFB692F6),
            valid = Color(0xFF5BE7C4),
            invalid = Color(0xFFFF6B6B),
            wall = Color(0xFF9BB4FF),
            playerOne = Color(0xFF6EA8FF),
            playerTwo = Color(0xFFB692F6),
            selection = Color(0xFF5BE7C4),
        )

        SUNSET -> BoardPalette(
            background = Color(0xFF2B1330),
            tile = Color(0xFF3E1B3C),
            tileAlternate = Color(0xFF4C2244),
            goalOne = Color(0xFFFFB25C),
            goalTwo = Color(0xFFFF6F91),
            valid = Color(0xFFFFD166),
            invalid = Color(0xFFFF4D6D),
            wall = Color(0xFFFFC49B),
            playerOne = Color(0xFFFFB25C),
            playerTwo = Color(0xFFFF6F91),
            selection = Color(0xFFFFD166),
        )
    }
}
