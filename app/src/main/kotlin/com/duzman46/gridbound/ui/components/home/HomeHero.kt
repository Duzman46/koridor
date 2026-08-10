package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
                // Edge to edge, and under the status bar. The picture is the room the interface
                // stands in, so it starts where the screen starts.
                //
                // It was inset below the top bar for a while, to stop the pale piece colliding
                // with the two round controls. That worked and cost far too much: the inset ate
                // a third of the slot, the photograph had to be squeezed into what was left,
                // and the pieces came out half the size they are meant to be. The scene is the
                // first thing anyone sees and it cannot be the thing that gives way.
                //
                // What keeps the piece clear now is the asset itself. It carries unlit floor
                // above the composition — that is what the crop is chosen to include — so the
                // bar sits over empty ground rather than over the piece, and the top fifth
                // fades up into the same near-black the app paints behind it, so there is no
                // edge where the picture begins.
                // Mirrored under RTL like everything else the app draws, so the lamp stays on
                // the side the reading eye starts from.
                .scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f),
            contentScale = ContentScale.Crop,
            // Pinned to the bottom: the pieces stand on the lower edge of the picture, and that
            // is the end that has to survive when the slot is shorter than the asset.
            alignment = Alignment.BottomCenter,
        )
        // A shorter safety net than before. The asset carries its own fade at both ends now, so
        // this only has to cover the case where the box is taller than the picture and the
        // bottom would otherwise be cut rather than faded.
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

/** How much of the box the closing fade covers. */
private const val SCENE_JOIN = 0.14f
