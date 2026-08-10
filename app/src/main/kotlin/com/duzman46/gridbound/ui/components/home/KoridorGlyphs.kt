package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.duzman46.gridbound.ui.components.drawPawnMark

/** The home screen's destinations, each with a glyph built from board parts. */
enum class GlyphKind {
    QUICK_PLAY,
    CREATE_ROOM,
    JOIN_ROOM,
    MODES,
    LEADERBOARD,
    FRIENDS,
    TUTORIAL,
    STATISTICS,
    SETTINGS,
    VS_BOT,
    EXPERT,
    ONLINE,
    PROFILE,
    MORE,
    REMOVE_ADS,
    HOME,
    QUESTS,
    ACHIEVEMENTS,
    HELP,
    ABOUT,
    UPDATES,
}

/**
 * The icon family, drawn rather than imported.
 *
 * Stock Material glyphs — a mortarboard for the tutorial, a trophy for the leaderboard, a
 * bar chart for statistics — are what make an app look like every other app. These are built
 * from the only three shapes this game has: a board tile, a wall bar, and a pawn. Nine icons
 * end up looking like one set because they are literally made of the same pieces.
 *
 * Monochrome by design: one [tint] per call, no accent parameter. The jade means "wall" on the
 * board and "the way forward" on a control, and a glyph that could reach for it would be
 * spending a colour that already says something somewhere the player is not looking.
 */
@Composable
fun KoridorGlyph(
    kind: GlyphKind,
    modifier: Modifier = Modifier,
    tint: Color,
) {
    val mirror = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f)) {
        val s = size.minDimension
        val muted = tint.copy(alpha = 0.42f)
        when (kind) {
            GlyphKind.QUICK_PLAY -> {
                // A pawn about to pass a wall: forward motion without borrowing a play arrow.
                drawPawnMark(Offset(s * 0.34f, s * 0.54f), s * 0.68f, tint)
                bar(s * 0.70f, s * 0.14f, s * 0.13f, s * 0.72f, muted)
            }

            GlyphKind.CREATE_ROOM -> {
                // Four tiles split by a fresh wall — a room being made.
                quad(s, muted)
                bar(s * 0.455f, s * 0.04f, s * 0.09f, s * 0.92f, tint)
            }

            GlyphKind.JOIN_ROOM -> {
                // Three tiles and a pawn stepping into the empty fourth.
                tile(s * 0.06f, s * 0.06f, s * 0.34f, muted)
                tile(s * 0.60f, s * 0.06f, s * 0.34f, muted)
                tile(s * 0.60f, s * 0.60f, s * 0.34f, muted)
                drawPawnMark(Offset(s * 0.23f, s * 0.79f), s * 0.42f, tint)
            }

            GlyphKind.MODES -> {
                // Three walls of different length: the choices themselves.
                bar(s * 0.08f, s * 0.17f, s * 0.80f, s * 0.115f, tint)
                bar(s * 0.08f, s * 0.44f, s * 0.52f, s * 0.115f, tint)
                bar(s * 0.08f, s * 0.71f, s * 0.66f, s * 0.115f, tint)
            }

            GlyphKind.LEADERBOARD -> {
                // A podium made of board tiles, standing on one baseline.
                column(s * 0.08f, s * 0.46f, s, muted)
                column(s * 0.38f, s * 0.76f, s, tint)
                column(s * 0.68f, s * 0.60f, s, muted)
            }

            GlyphKind.FRIENDS -> {
                // Two pawns sharing a rank. No tiles — that is what tells it apart from
                // JOIN_ROOM at 22 dp.
                drawPawnMark(Offset(s * 0.34f, s * 0.52f), s * 0.60f, tint)
                drawPawnMark(Offset(s * 0.68f, s * 0.52f), s * 0.60f, muted)
                bar(s * 0.14f, s * 0.80f, s * 0.72f, s * 0.10f, tint.copy(alpha = 0.30f))
            }

            GlyphKind.TUTORIAL -> {
                // A pawn below the wall it is being taught about.
                quad(s, muted)
                bar(s * 0.03f, s * 0.475f, s * 0.94f, s * 0.06f, tint)
                drawPawnMark(Offset(s * 0.24f, s * 0.76f), s * 0.42f, tint)
            }

            GlyphKind.STATISTICS -> {
                // The board as a heat map, densest on the trailing column.
                val side = s * 0.28f
                val pitch = s * 0.347f
                repeat(3) { row ->
                    repeat(3) { column ->
                        val alpha = when (column) {
                            0 -> 0.22f
                            1 -> 0.50f
                            else -> 0.95f
                        }
                        tile(
                            s * 0.013f + column * pitch,
                            s * 0.013f + row * pitch,
                            side,
                            tint.copy(alpha = alpha),
                        )
                    }
                }
            }

            GlyphKind.SETTINGS -> {
                // Two wall slots with their pieces set to different depths.
                bar(s * 0.28f, s * 0.10f, s * 0.10f, s * 0.80f, muted)
                tile(s * 0.23f, s * 0.28f, s * 0.20f, tint)
                bar(s * 0.62f, s * 0.10f, s * 0.10f, s * 0.80f, muted)
                tile(s * 0.57f, s * 0.56f, s * 0.20f, tint)
            }

            GlyphKind.VS_BOT -> {
                // A pawn facing a square-headed one: you against the machine.
                drawPawnMark(Offset(s * 0.30f, s * 0.54f), s * 0.62f, tint)
                tile(s * 0.58f, s * 0.22f, s * 0.30f, muted)
                bar(s * 0.64f, s * 0.60f, s * 0.18f, s * 0.09f, muted)
            }

            GlyphKind.EXPERT -> {
                // A pawn walled in on three sides, one gap left. The hardest bot is the one
                // that takes the board away from you, so the icon is what that feels like
                // rather than a star or a flame, which say "difficult" about anything at all.
                bar(s * 0.16f, s * 0.10f, s * 0.68f, s * 0.10f, tint)
                bar(s * 0.12f, s * 0.26f, s * 0.10f, s * 0.54f, muted)
                bar(s * 0.78f, s * 0.26f, s * 0.10f, s * 0.54f, muted)
                drawPawnMark(Offset(s * 0.50f, s * 0.62f), s * 0.50f, tint)
            }

            GlyphKind.ONLINE -> {
                // Two pawns on their own tiles, far apart, with the channel between them
                // open. Read as a dumbbell when it was two plain squares and a bar, so the
                // pawns are what say "two players" rather than "two objects".
                tile(s * 0.02f, s * 0.20f, s * 0.40f, muted)
                drawPawnMark(Offset(s * 0.22f, s * 0.40f), s * 0.40f, tint)
                tile(s * 0.58f, s * 0.20f, s * 0.40f, muted)
                drawPawnMark(Offset(s * 0.78f, s * 0.40f), s * 0.40f, tint)
                bar(s * 0.06f, s * 0.80f, s * 0.88f, s * 0.09f, tint.copy(alpha = 0.30f))
            }

            GlyphKind.PROFILE -> {
                // A single pawn on its own tile.
                tile(s * 0.14f, s * 0.14f, s * 0.72f, muted)
                drawPawnMark(Offset(s * 0.50f, s * 0.52f), s * 0.62f, tint)
            }

            GlyphKind.MORE -> {
                // Three wall pieces stacked, the way spare walls sit beside the board.
                bar(s * 0.12f, s * 0.20f, s * 0.76f, s * 0.11f, tint)
                bar(s * 0.12f, s * 0.445f, s * 0.76f, s * 0.11f, muted)
                bar(s * 0.12f, s * 0.69f, s * 0.76f, s * 0.11f, muted)
            }

            GlyphKind.REMOVE_ADS -> {
                // A tile with the bar lifted off it.
                tile(s * 0.10f, s * 0.34f, s * 0.52f, muted)
                bar(s * 0.34f, s * 0.08f, s * 0.56f, s * 0.11f, tint)
            }

            GlyphKind.HOME -> {
                // Your starting square: the home rank, with your pawn standing on the middle
                // of it. Not a house — this game has no houses, and a roof drawn in a set made
                // of board parts is the one shape that would announce it came from elsewhere.
                bar(s * 0.06f, s * 0.74f, s * 0.88f, s * 0.11f, muted)
                tile(s * 0.06f, s * 0.30f, s * 0.24f, muted)
                tile(s * 0.70f, s * 0.30f, s * 0.24f, muted)
                drawPawnMark(Offset(s * 0.50f, s * 0.46f), s * 0.56f, tint)
            }

            GlyphKind.QUESTS -> {
                // A pawn and the tile it has been asked to reach, with the ground between them
                // stepping up. A task is a destination you have not arrived at yet, which is
                // exactly what a board can say without borrowing a checklist.
                drawPawnMark(Offset(s * 0.22f, s * 0.74f), s * 0.44f, tint)
                tile(s * 0.40f, s * 0.42f, s * 0.22f, muted)
                tile(s * 0.70f, s * 0.10f, s * 0.24f, tint)
            }

            GlyphKind.ACHIEVEMENTS -> {
                // A tile turned on its corner with a smaller one inside it: a medal built from
                // the only square this app owns. The podium already belongs to the leaderboard,
                // so standing on one would have said "ranking" a second time.
                rotate(45f, Offset(s * 0.5f, s * 0.44f)) {
                    tile(s * 0.20f, s * 0.14f, s * 0.60f, muted)
                    tile(s * 0.35f, s * 0.29f, s * 0.30f, tint)
                }
                bar(s * 0.24f, s * 0.82f, s * 0.20f, s * 0.10f, tint)
                bar(s * 0.56f, s * 0.82f, s * 0.20f, s * 0.10f, tint)
            }

            GlyphKind.HELP -> {
                // A pawn under a gap in the wall above it — the shape of not knowing which way
                // to go, rather than a question mark, which is a letterform and not a piece.
                bar(s * 0.06f, s * 0.16f, s * 0.32f, s * 0.11f, muted)
                bar(s * 0.62f, s * 0.16f, s * 0.32f, s * 0.11f, muted)
                tile(s * 0.40f, s * 0.10f, s * 0.20f, tint)
                drawPawnMark(Offset(s * 0.50f, s * 0.70f), s * 0.52f, tint)
            }

            GlyphKind.ABOUT -> {
                // An upright wall with a tile set above it. The information mark, spelled in
                // the two pieces the game is made of.
                tile(s * 0.40f, s * 0.08f, s * 0.20f, tint)
                bar(s * 0.42f, s * 0.36f, s * 0.16f, s * 0.56f, muted)
            }

            GlyphKind.UPDATES -> {
                // Three tiles climbing: what is here now, and what is coming.
                tile(s * 0.06f, s * 0.60f, s * 0.26f, muted)
                tile(s * 0.37f, s * 0.38f, s * 0.26f, muted)
                tile(s * 0.68f, s * 0.16f, s * 0.26f, tint)
            }
        }
    }
}

/** A board tile, at the same corner ratio the real board uses. */
private fun DrawScope.tile(x: Float, y: Float, side: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(side, side),
        cornerRadius = CornerRadius(side * 0.16f),
    )
}

/** A wall, fully rounded on its short axis the way a placed wall is. */
private fun DrawScope.bar(x: Float, y: Float, width: Float, height: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(minOf(width, height) / 2f),
    )
}

/** The four-tile block several glyphs are built on. */
private fun DrawScope.quad(s: Float, color: Color) {
    tile(s * 0.06f, s * 0.06f, s * 0.34f, color)
    tile(s * 0.60f, s * 0.06f, s * 0.34f, color)
    tile(s * 0.06f, s * 0.60f, s * 0.34f, color)
    tile(s * 0.60f, s * 0.60f, s * 0.34f, color)
}

/** A podium column standing on the shared baseline. */
private fun DrawScope.column(x: Float, height: Float, s: Float, color: Color) {
    val width = s * 0.24f
    drawRoundRect(
        color = color,
        topLeft = Offset(x, s * 0.94f - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.16f),
    )
}
