package com.duzman46.gridbound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.navigation.AppNavigation
import com.duzman46.gridbound.presentation.AppViewModel
import com.duzman46.gridbound.theme.GridboundTheme
import com.duzman46.gridbound.ui.localization.LocalAppLanguage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            GridboundTheme(settings) {
                CompositionLocalProvider(LocalAppLanguage provides settings.language) {
                    AppNavigation(
                        language = settings.language,
                        onLanguage = viewModel::setLanguage,
                    )
                }
            }
        }
    }
}
