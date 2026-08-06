package com.duzman46.gridbound.game.board

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The board's colours.
 *
 * One board, not a catalogue of them. Alternative board skins were a paid cosmetic; they cost
 * a store, a purchase flow, an entitlement per theme and a settings picker, and none of that
 * made the game better to play. What is left is the scheme that was always the default,
 * following the app's own light and dark colours.
 *
 * The two pawn and goal colours are fixed rather than themed on purpose: blue and orange have
 * to stay distinguishable from each other in both themes, and they are referenced by name in
 * the tutorial's own text.
 */
fun boardPalette(colors: ColorScheme): BoardPalette = BoardPalette(
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
