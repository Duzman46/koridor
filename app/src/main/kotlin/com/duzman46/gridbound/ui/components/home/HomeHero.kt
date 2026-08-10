package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R

/**
 * The scene the home screen opens on: a Koridor board under one controlled light.
 *
 * This is an image, and that is the decision worth explaining.
 *
 * It was drawn first — tiles projected in a shear, pieces stacked out of ellipses, walls with
 * three lit faces. That version was honest vector work and it was not close. A photograph of a
 * board has materials, a depth of field, a soft shadow under every piece and a specular running
 * along a bevel, and none of that is reachable with the primitives a canvas gives you. Four
 * passes made it better and none of them made it *right*, because the gap was not effort, it
 * was the medium.
 *
 * **What is an image and what is not.** Only the board. The name, the tagline, the play card,
 * the five destinations and the docked bar are all real composables sitting on top of it — a
 * screenshot with invisible buttons over it is not an interface: it is wrong in ten languages,
 * wrong at every font scale, wrong on every screen that is not the one it was cut for, and
 * unreadable to a screen reader. The picture is the room; the interface is still built.
 *
 * Three densities ship. The asset is authored with its bottom quarter faded to transparent, so
 * it has no edge of its own and dissolves into whatever the app paints behind it. That fade is
 * baked into the file rather than layered here, because a gradient drawn over a photograph
 * mixes with it — the fade has to remove the picture, not tint it.
 *
 * Cropped from the top so the board keeps its horizon wherever the box ends up taller or
 * shorter than the asset. Anchoring it to the centre would push the pieces off the bottom on a
 * short screen, and they are what the picture is *of*.
 */
@Composable
fun HomeHero(modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .background(SceneInk)
            // One node, no description. It is scene-setting, and a screen reader announcing
            // "two pawns and three walls" hands the player nothing they can act on.
            .clearAndSetSemantics { },
    ) {
        Image(
            painter = painterResource(R.drawable.home_scene),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // The picture starts below the top bar rather than behind it.
                //
                // The pale piece stands in the brightest corner of the scene, which is also
                // where the two round controls sit, and the two were colliding. Dimming the
                // picture would have cost the light that makes it worth looking at, and moving
                // the controls would have cost the layout. So the picture simply begins lower:
                // the strip the bar occupies is left as plain ground, and because that ground
                // is the same near-black the scene fades into, the reserved space reads as part
                // of the room rather than as a bar of colour above it.
                .statusBarsPadding()
                .padding(top = TopBarReserve)
                // Mirrored under RTL like everything else the app draws, so the lamp stays on
                // the side the reading eye starts from.
                .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
            contentScale = ContentScale.Crop,
            // Anchored to the bottom. The asset carries a strip of empty floor above the
            // composition precisely so this can be done: the pieces and the walls sit in its
            // lower two thirds, and pinning that end keeps them clear of the round controls in
            // the top bar no matter how short the slot gets on a small phone.
            alignment = Alignment.Center,
        )
        // The join. The asset already fades, but a box taller than the asset would show the
        // scene cropped rather than faded, so the last stretch is finished here as well. Costs
        // nothing when the two line up and saves the edge when they do not.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxSize(SCENE_JOIN)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, SceneInk))),
        )
    }
}

/** What the scene sits on, and what it fades into: the dark scheme's own background. */
private val SceneInk = Color(0xFF070A0D)

/**
 * The strip the top bar occupies, which the picture starts below.
 *
 * The bar's controls are 46 dp inside 8 dp of padding, so 60 clears them with a hair to spare.
 * It was 74 and that was too generous: every device-independent pixel reserved here comes off
 * the picture, and the picture was already the thing being squeezed. Kept as a constant rather
 * than read from the bar, because the bar is laid out over this and cannot be measured first.
 */
private val TopBarReserve = 60.dp

/** How much of the box the closing fade covers. */
private const val SCENE_JOIN = 0.22f
