package com.duzman46.gridbound.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.duzman46.gridbound.theme.KoridorGold
import com.duzman46.gridbound.ui.components.home.HomeHero
import com.duzman46.gridbound.ui.components.home.PlayModeCard
import com.duzman46.gridbound.ui.components.home.PremiumIcon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.game.board.SeatColors
import com.duzman46.gridbound.game.models.Difficulty
import com.duzman46.gridbound.game.models.PlayerId
import com.duzman46.gridbound.theme.Dimens
import com.duzman46.gridbound.ui.components.AdBanner
import com.duzman46.gridbound.ui.components.ScreenBackground
import com.duzman46.gridbound.ui.components.ScreenTopBar
import com.duzman46.gridbound.ui.components.home.GlyphKind
import com.duzman46.gridbound.ui.components.home.HomeChoice
import com.duzman46.gridbound.ui.components.home.PlaySlab

/**
 * How you want to play.
 *
 * The single question behind "Play". Playing a stranger, playing a bot and playing the person
 * next to you are the three real answers; everything else — difficulty, room codes, opponents
 * — belongs to whichever of them you picked, not to this screen.
 *
 * Online leads and is the one loud target. It is the mode the game is built around and the
 * only one that needs another person waiting, so it should not be the third thing read.
 */
/**
 * How much of the window the scene takes, on this screen and on the home screen alike.
 *
 * A shared constant rather than two numbers that happen to agree today: the two screens show the
 * same photograph one tap apart, and a picture that changes size between them reads as two
 * designs rather than one.
 */
private const val SCENE_SHARE = 0.34f

@Composable
fun PlayModeScreen(
    onBack: () -> Unit,
    onVsBot: () -> Unit,
    onLocal: () -> Unit,
    onOnline: () -> Unit,
    showAdBanner: Boolean,
) {
    // No banner here any more. This screen now opens on the same scene the home screen does, and
    // a strip of advertising under a photograph is what turns a game into a storefront. The
    // interstitial after a match still runs; that is where the app asks.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // The same share the home screen gives the picture. Weighted against the cards
                // rather than taking what they leave, so one photograph is one size across the
                // two screens a player passes through on the way to every match.
                .weight(SCENE_SHARE),
        ) {
            HomeHero(Modifier.fillMaxSize())
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(onBack)
                Text(
                    text = stringResource(R.string.menu_play),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = KoridorGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Dimens.MenuMaxWidth)
                .align(Alignment.CenterHorizontally)
                .weight(1f - SCENE_SHARE)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(top = Dimens.SpaceMd, bottom = Dimens.SpaceSm)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            // Online leads and wears the gold. It is the mode the game is built around and the
            // only one that needs another person waiting, so it must not be the third thing read.
            PlayModeCard(
                title = stringResource(R.string.menu_online),
                subtitle = stringResource(R.string.play_online_subtitle),
                icon = PremiumIcon.GLOBE,
                onClick = onOnline,
                leading = true,
            )
            PlayModeCard(
                title = stringResource(R.string.play_vs_bot),
                subtitle = stringResource(R.string.play_bot_subtitle),
                icon = PremiumIcon.ROBOT,
                onClick = onVsBot,
            )
            PlayModeCard(
                title = stringResource(R.string.play_local),
                subtitle = stringResource(R.string.play_local_subtitle),
                icon = PremiumIcon.PEOPLE,
                onClick = onLocal,
            )
        }
    }
}

/**
 * The way back, on a screen whose top bar is a photograph.
 *
 * A plain arrow rather than Material's icon button: there is no surface behind it to tint, and a
 * ripple on a picture reads as a smudge. It keeps the 48 dp touch target the platform asks for
 * even though it is drawn at 24.
 */
@Composable
private fun BackArrow(onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val label = stringResource(R.string.action_back)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
        ) {
            val s = size.minDimension
            drawPath(
                Path().apply {
                    moveTo(s * 0.92f, s * 0.5f)
                    lineTo(s * 0.12f, s * 0.5f)
                    moveTo(s * 0.44f, s * 0.18f)
                    lineTo(s * 0.12f, s * 0.5f)
                    lineTo(s * 0.44f, s * 0.82f)
                },
                KoridorGold,
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Which bot.
 *
 * Its own step rather than a row of chips inside a sheet: it is the only decision a player
 * makes before a solo match, so it gets the whole screen.
 *
 * All four carry equal weight. Difficulty is a preference, not a recommendation — filling one
 * of them made the screen look like it was steering the player towards the easy bot.
 */
@Composable
fun DifficultyScreen(onBack: () -> Unit, onSelected: (Difficulty, PlayerId) -> Unit) {
    // Blue every time. It was random, which is right for a match against a stranger — nobody
    // gets the first move by choosing it — and wrong here: there is no opponent to be fair to,
    // and a solo player who never touches this control should get the same board every time
    // rather than one that silently changes who opens.
    var seat by rememberSaveable { mutableStateOf(PlayerId.PLAYER_ONE) }
    ModeColumn(stringResource(R.string.difficulty_title), onBack) {
        SeatPicker(selected = seat, onSelect = { seat = it })
        HomeChoice(stringResource(R.string.difficulty_easy), GlyphKind.QUICK_PLAY, onClick = { onSelected(Difficulty.EASY, seat) })
        HomeChoice(stringResource(R.string.difficulty_medium), GlyphKind.VS_BOT, onClick = { onSelected(Difficulty.MEDIUM, seat) })
        HomeChoice(stringResource(R.string.difficulty_hard), GlyphKind.LEADERBOARD, onClick = { onSelected(Difficulty.HARD, seat) })
        HomeChoice(stringResource(R.string.difficulty_expert), GlyphKind.EXPERT, onClick = { onSelected(Difficulty.EXPERT, seat) })
    }
}

/**
 * Which colour you play, which is to say which seat: blue is seat one and opens.
 *
 * Two swatches rather than a labelled dropdown: the choice *is* a colour, so showing the
 * colour is both the label and the value. Each carries its name as well, because a control
 * that can only be read by hue is one a colour-blind player cannot use.
 */
@Composable
fun SeatPicker(selected: PlayerId, onSelect: (PlayerId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(
            stringResource(R.string.paint_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            SeatSwatch(PlayerId.PLAYER_ONE, selected, onSelect, Modifier.weight(1f))
            SeatSwatch(PlayerId.PLAYER_TWO, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SeatSwatch(
    seat: PlayerId,
    selected: PlayerId,
    onSelect: (PlayerId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = seat == selected
    val swatch = SeatColors.pawn(seat)
    val label = stringResource(
        if (seat == PlayerId.PLAYER_ONE) R.string.game_player_blue else R.string.game_player_red,
    )
    Surface(
        onClick = { onSelect(seat) },
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(Dimens.RadiusSm),
        color = if (chosen) swatch.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(
            width = if (chosen) Dimens.BorderStrong else 1.dp,
            // An unchosen option carries no fill, so this hairline is the whole control: it
            // has to be the outline proper rather than the divider token, which is mixed to
            // be ignored.
            color = if (chosen) swatch else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(swatch))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * The shared frame: a title bar and one column of full-width choices, held in the middle of
 * the screen.
 *
 * Centred rather than stacked under the title bar. Three buttons pinned to the top of a phone
 * screen leave the whole lower two-thirds empty and read as the top of a list that has more
 * below it, which is exactly what this screen does not have.
 *
 * It still scrolls, and that is what the measured viewport is for: at the largest font sizes,
 * or in a small window, the choices are taller than the screen and centring them would put the
 * first one out of reach above the top edge. Giving the column the viewport as a *minimum*
 * height means it centres whenever there is room and grows downward from the top when there
 * is not.
 */
@Composable
private fun ModeColumn(
    title: String,
    onBack: () -> Unit,
    showAdBanner: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(title, onBack) },
        bottomBar = { if (showAdBanner) AdBanner(Modifier.navigationBarsPadding()) },
    ) { padding ->
        ScreenBackground {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                // Read before the scroll modifier below makes the height unbounded, which is
                // the whole reason this is measured rather than asked for with fillMaxHeight.
                val viewport = maxHeight
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = Dimens.MenuMaxWidth)
                            .heightIn(min = viewport)
                            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXl),
                        verticalArrangement = Arrangement.spacedBy(
                            Dimens.SpaceMd,
                            Alignment.CenterVertically,
                        ),
                        content = content,
                    )
                }
            }
        }
    }
}
