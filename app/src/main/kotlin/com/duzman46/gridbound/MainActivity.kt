package com.duzman46.gridbound

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duzman46.gridbound.data.SettingsBootstrap
import com.duzman46.gridbound.navigation.AppNavigation
import com.duzman46.gridbound.presentation.AppViewModel
import com.duzman46.gridbound.theme.GridboundTheme
import com.duzman46.gridbound.ui.localization.LocaleController
import com.duzman46.gridbound.ui.localization.ProvideAppLocale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Applies the stored language before any resource is read.
     *
     * Android 13+ handles this through the platform locale service, so [LocaleController.wrap]
     * is a no-op there; below that the configuration has to be layered on by hand. The
     * preference is read synchronously because there is nothing to show until it is known.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = SettingsBootstrap.readLanguageBlocking(newBase)
        super.attachBaseContext(LocaleController.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val monetization by viewModel.monetization.collectAsStateWithLifecycle()
            val billing by viewModel.billing.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.initializeMonetization(this@MainActivity)
            }
            // A guest who first launched offline gets a backend identity on the next start.
            LaunchedEffect(session.status) {
                viewModel.recoverBackendIdentity()
            }

            // The locale wraps the theme so a language change also re-lays-out right-to-left
            // scripts, not just the strings inside them.
            ProvideAppLocale(settings.language) {
                GridboundTheme(settings) {
                    AppNavigation(
                        session = session,
                        language = settings.language,
                        onLanguage = { language -> viewModel.setLanguage(this@MainActivity, language) },
                        monetization = monetization,
                        billing = billing,
                        onBuy = { entitlement -> viewModel.buy(this@MainActivity, entitlement) },
                        onRestorePurchases = viewModel::restorePurchases,
                        onDismissBillingMessage = viewModel::dismissBillingMessage,
                        onPrivacyOptions = { viewModel.showPrivacyOptions(this@MainActivity) },
                        onCompletedMatchExit = { onFinished ->
                            viewModel.showInterstitialAfterCompletedMatch(this@MainActivity, onFinished)
                        },
                    )
                }
            }
        }
    }
}
