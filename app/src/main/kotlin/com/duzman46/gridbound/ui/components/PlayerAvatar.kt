package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import java.util.Locale

/**
 * Avatars are drawn rather than shipped as images: twelve id-keyed colours plus the player's
 * initial. That keeps the download free of a dozen bitmaps in five densities, scales to any
 * size, and stays legible in both themes.
 */
object AvatarPalette {
    private val COLORS = listOf(
        Color(0xFF3F82FF), Color(0xFFF0483F), Color(0xFF32D583), Color(0xFFB692F6),
        Color(0xFFF97066), Color(0xFF12B5CB), Color(0xFFFDB022), Color(0xFF7A5AF8),
        Color(0xFF2E90FA), Color(0xFFEE46BC), Color(0xFF66C61C), Color(0xFFEF6820),
    )

    val ids: List<String> = List(Constants.Profile.AVATAR_COUNT) { index ->
        "avatar_%02d".format(Locale.ROOT, index + 1)
    }

    fun colorFor(avatarId: String): Color {
        val index = ids.indexOf(avatarId)
        return COLORS[if (index >= 0) index % COLORS.size else 0]
    }
}

@Composable
fun PlayerAvatar(
    avatarId: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val description = stringResource(R.string.cd_avatar)
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AvatarPalette.colorFor(avatarId))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            modifier = Modifier.clearAndSetSemantics { },
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
