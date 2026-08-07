package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.theme.Dimens

/**
 * The background every screen sits on.
 *
 * Deliberately flat. The board is the only thing in this app that should pull the eye, and a
 * tinted gradient behind every list and form was competing with it while making the ten
 * screens look like ten different apps.
 */
@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Constants.Ui.CONTENT_MAX_WIDTH_DP.dp)
                .padding(Constants.Ui.DEFAULT_PADDING_DP.dp),
        ) {
            content()
        }
    }
}

/**
 * A titled group of related controls.
 *
 * Settings and the online lobby each had their own near-identical card — same idea, different
 * radius and title size, which is exactly how a set of screens stops looking like one app.
 * This is the only card shape in the project.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusLg),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(Dimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
    )
}

/**
 * The same bar for a screen there is no way back from.
 *
 * A back arrow that does nothing is worse than no arrow: it invites the tap and then denies
 * it. Where a screen has to be answered before the app goes on, the absence of the control
 * is the honest statement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateTopBar(title: String) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) })
}
