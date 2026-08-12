package com.duzman46.gridbound.game.board

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.theme.KoridorGold

/**
 * The board's colours.
 *
 * One board, not a catalogue of them. Alternative board skins were a paid cosmetic; they cost
 * a store, a purchase flow, an entitlement per theme and a settings picker, and none of that
 * made the game better to play. What is left is the scheme that was always the default,
 * following the app's own light and dark colours.
 *
 * The two pawn and goal colours come from [SeatColors], which is the one place they are
 * defined — they are named in the tutorial text and offered by name when a room is created.
 */
fun boardPalette(colors: ColorScheme): BoardPalette = BoardPalette(
    background = colors.surfaceVariant.copy(alpha = 0.58f),
    tile = colors.surface,
    tileAlternate = colors.surfaceVariant.copy(alpha = 0.72f),
    goalOne = SeatColors.goal(PlayerId.PLAYER_ONE),
    goalTwo = SeatColors.goal(PlayerId.PLAYER_TWO),
    valid = Color(0xFF32D583),
    invalid = colors.error,
    wall = colors.secondary,
    // The board's own metal, not the theme's: the surface under these slots is a render
    // and does not change with the theme, so a slot that did would disagree with it.
    slot = KoridorGold,
    playerOne = SeatColors.pawn(PlayerId.PLAYER_ONE),
    playerTwo = SeatColors.pawn(PlayerId.PLAYER_TWO),
    selection = colors.primary,
)
