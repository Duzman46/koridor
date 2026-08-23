package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.duzman46.gridbound.R

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

/**
 * The app's two remaining Material `TopAppBar`s, both of them dead ends kept alive by one line
 * each.
 *
 * Every screen that drew a header now draws
 * [com.duzman46.gridbound.ui.components.home.PremiumHeader] — the app's own, and the only one
 * that marks its title as a heading, which is what gives TalkBack's navigate-by-heading gesture
 * anything to land on. The app had five header idioms; this file held two of them.
 *
 * [ScreenTopBar] has **no call sites left at all**. It survives here only because one screen
 * this change does not own still carries a dead `import` of it, and an import of a symbol that
 * does not exist is a compile error where an unused one is merely a warning. Delete the import
 * in `AccountScreen.kt` and this function goes with it.
 *
 * [GateTopBar] still has its one real call site — the username gate, the screen there is
 * genuinely no way back from. It cannot move yet because `PremiumHeader` requires an `onBack`
 * and the whole point of this bar is that there is nothing to pass. When that parameter becomes
 * nullable the gate takes `PremiumHeader(onBack = null)`, and Material's app-bar family leaves
 * the codebase entirely.
 */
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
