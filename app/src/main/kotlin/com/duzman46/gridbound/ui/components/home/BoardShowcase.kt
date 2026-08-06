package com.duzman46.gridbound.ui.components.home

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.game.board.BoardGeometry
import com.duzman46.gridbound.game.board.CanvasRenderer
import com.duzman46.gridbound.game.models.WallOrientation
import com.duzman46.gridbound.session.SessionState
import com.duzman46.gridbound.theme.Dimens

/** Wide on a normal phone; wider still on a short one, so the controls below always fit. */
private const val HERO_ASPECT = 1.6f
private const val HERO_ASPECT_SHORT = 2.0f
private const val SHORT_SCREEN_DP = 620

/**
 * The home screen's hero: a real Koridor position, drawn by the same renderer that draws a
 * live match, sunk into a dark well that runs off its own bottom edge.
 *
 * This is the answer to a home screen that could have belonged to any app. The board is the
 * product, so the board is what you see — mid-game, walls placed, one pawn boxed in and a
 * dashed amber line crawling along the long way round it has been forced to take.
 *
 * Two stacked Canvases on purpose: the board layer reads no animated state and is recorded
 * once, while only the three-operation route layer re-records per frame.
 */
@Composable
fun BoardShowcase(
    session: SessionState,
    language: AppLanguage,
    onProfile: () -> Unit,
    onLanguage: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { CanvasRenderer() }
    // The window, not the screen: in split-screen the display is still tall while the app has
    // half of it, and the well has to give the controls below room either way.
    val density = LocalDensity.current
    val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val aspect = if (windowHeightDp < SHORT_SCREEN_DP.dp) HERO_ASPECT_SHORT else HERO_ASPECT
    // Never isSystemInDarkTheme(): Settings can force light or dark independently of the OS.
    val onDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(Dimens.RadiusXl)

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape)
            .then(
                // Against the dark background the well is barely 1.06:1 and would dissolve;
                // in light theme the contrast carries it and a border would look drawn on.
                if (onDarkTheme) Modifier.border(1.dp, HomePalette.WellBorder, shape) else Modifier,
            ),
    ) {
        StaticLayer(renderer, rtl)
        RouteLayer(rtl)
        Overlay(session, language, onProfile, onLanguage, onSettings)
    }
}

@Composable
private fun BoxScope.StaticLayer(renderer: CanvasRenderer, rtl: Boolean) {
    Canvas(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { },
    ) {
        val w = size.width
        val h = size.height

        drawRect(HomePalette.Well)

        // Inner top shadow — this alone is what makes the panel read as recessed into a case
        // rather than pasted on top of one.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.38f),
                1f to Color.Transparent,
                startY = 0f,
                endY = h * 0.16f,
            ),
            size = Size(w, h * 0.16f),
        )

        // Pushed down and shrunk a little from the original framing: the three controls in
        // the top corner were landing on the goal row and the orange pawn, and a settings
        // icon sitting on a piece reads as a mistake rather than as a layer.
        val side = h * 1.06f
        val top = h * 0.20f
        val startX = if (rtl) w - w * 0.055f - side else w * 0.055f
        translate(startX, top) {
            renderer.draw(
                scope = this,
                geometry = BoardGeometry(side),
                state = HomePosition.STATE,
                palette = HomePalette.Board,
                validMoves = emptySet(),
                validWalls = emptySet(),
                wallOrientation = WallOrientation.HORIZONTAL,
                pendingWall = null,
                invalidWall = null,
                recentWall = null,
                recentWallProgress = 1f,
                playerOneRow = 5f,
                playerOneColumn = 3f,
                playerTwoRow = 3f,
                playerTwoColumn = 4f,
                selected = false,
            )
        }

        drawWallRack(w, h, rtl)

        // The wordmark's bed. Two gradients rather than one: the vertical pass sinks the
        // bottom of the board, the horizontal pass sinks the leading corner further, so the
        // letters sit on near-solid ground while the route, the goal disc and the rack on the
        // trailing side stay bright. A single flat scrim strong enough to carry the text
        // would have dimmed the whole board with it.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to HomePalette.Well.copy(alpha = 0.92f),
                startY = h * 0.44f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.44f),
            size = Size(w, h * 0.56f),
        )
        val leading = if (rtl) {
            Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to HomePalette.Well.copy(alpha = 0.86f),
                startX = w * 0.42f,
                endX = w,
            )
        } else {
            Brush.horizontalGradient(
                0f to HomePalette.Well.copy(alpha = 0.86f),
                1f to Color.Transparent,
                startX = 0f,
                endX = w * 0.58f,
            )
        }
        drawRect(
            brush = leading,
            topLeft = Offset(0f, h * 0.58f),
            size = Size(w, h * 0.42f),
        )
    }
}

/**
 * Ten wall slots in the trailing gutter, filled to match what blue has actually spent.
 * HomePositionTest keeps that count honest.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWallRack(
    w: Float,
    h: Float,
    rtl: Boolean,
) {
    val spent = 10 - HomePosition.STATE.player(
        com.duzman46.gridbound.game.models.PlayerId.PLAYER_ONE,
    ).wallsRemaining
    val gutterX = if (rtl) w * 0.0975f else w * 0.9025f
    val barW = w * 0.105f
    val barH = h * 0.026f
    // Clears the crest at every aspect ratio and font scale.
    val rackTop = maxOf(h * 0.40f, 64.dp.toPx())
    val pitch = (h * 0.94f - rackTop - barH) / 9f
    repeat(10) { index ->
        drawRoundRect(
            color = if (index < spent) HomePalette.Amber else Color.White.copy(alpha = 0.07f),
            topLeft = Offset(gutterX - barW / 2f, rackTop + index * pitch),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH / 2f),
        )
    }
}

@Composable
private fun BoxScope.RouteLayer(rtl: Boolean) {
    val context = LocalContext.current
    // Honours "remove animations" in accessibility settings, and stays still in previews.
    val animationsOn = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val animated by rememberInfiniteTransition(label = "route").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "routePhase",
    )
    val phase = if (animationsOn && !LocalInspectionMode.current) animated else 0f

    Canvas(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { },
    ) {
        val w = size.width
        val h = size.height
        val side = h * 1.20f
        val top = h * 0.06f
        val startX = if (rtl) w - w * 0.055f - side else w * 0.055f

        translate(startX, top) {
            val geometry = BoardGeometry(side)
            val cell = geometry.tileSize
            val path = Path()
            HomePosition.ROUTE.forEachIndexed { index, position ->
                val point = geometry.pawnCenter(position.row.toFloat(), position.column.toFloat())
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = HomePalette.Amber.copy(alpha = 0.88f),
                style = Stroke(
                    width = cell * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    // Negative phase walks the dashes forward along the route.
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(cell * 0.30f, cell * 0.26f),
                        phase = -phase * cell * 0.56f,
                    ),
                ),
            )
            val goal = geometry.pawnCenter(
                HomePosition.ROUTE.last().row.toFloat(),
                HomePosition.ROUTE.last().column.toFloat(),
            )
            drawCircle(HomePalette.Amber.copy(alpha = 0.22f), cell * 0.42f, goal)
            drawCircle(HomePalette.Amber, cell * 0.13f, goal)
        }
    }
}

@Composable
private fun BoxScope.Overlay(
    session: SessionState,
    language: AppLanguage,
    onProfile: () -> Unit,
    onLanguage: () -> Unit,
    onSettings: () -> Unit,
) {
    // Settings, language and identity live here as small marks on the panel rather than as
    // entries in the menu below — three things nobody opens often should not take three of
    // the four choices on the home screen.
    Row(
        Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WellIconButton(GlyphKind.SETTINGS, stringResource(R.string.game_settings), onSettings)
        LanguageChip(language, onLanguage)
        ProfileCrest(session, onProfile)
    }

    val density = LocalDensity.current
    // Sized in dp then converted, so the wordmark keeps a fixed cap height and grows by at
    // most a quarter at large font scales. Seven Black glyphs cannot clip at 320 dp.
    val wordmarkSize = with(density) { 30.dp.toSp() } * density.fontScale.coerceAtMost(1.25f)
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 14.dp),
        fontSize = wordmarkSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = HomePalette.OnWell,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
