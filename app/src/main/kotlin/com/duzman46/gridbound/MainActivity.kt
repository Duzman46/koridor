package com.duzman46.gridbound

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duzman46.gridbound.data.SettingsBootstrap
import com.duzman46.gridbound.game.audio.HapticsManager
import com.duzman46.gridbound.game.audio.LocalHapticsManager
import com.duzman46.gridbound.navigation.AppNavigation
import com.duzman46.gridbound.notifications.ComeBackWorker
import com.duzman46.gridbound.notifications.Notifications
import com.duzman46.gridbound.presentation.AppViewModel
import com.duzman46.gridbound.theme.GridboundTheme
import com.duzman46.gridbound.ui.localization.LocaleController
import com.duzman46.gridbound.ui.localization.ProvideAppLocale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var haptics: HapticsManager

    /**
     * Asked for once, when the player turns notifications on and the system has not been asked.
     *
     * The answer is not stored anywhere: the setting is what the player wants and this is what
     * the operating system allows, and the app reads the second directly every time rather than
     * keeping a copy that can go stale the moment somebody changes it in system settings.
     */
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
        Notifications.ensureChannel(this)
        // Stamps "the app was opened" and makes sure the daily check is scheduled. Both live
        // together in the worker's companion, because the timestamp is the only thing the
        // schedule reads.
        lifecycleScope.launch { ComeBackWorker.onAppOpened(this@MainActivity) }
        setContent {
            val viewModel: AppViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val monetization by viewModel.monetization.collectAsStateWithLifecycle()
            val billing by viewModel.billing.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.initializeMonetization(this@MainActivity)
            }
            // Held on the manager rather than passed at every call, so the lobby's long-press
            // copy — which is nowhere near the game state — obeys the same switch as the board.
            LaunchedEffect(settings.hapticsEnabled) {
                haptics.enabled = settings.hapticsEnabled
            }
            // A guest who first launched offline gets a backend identity on the next start.
            LaunchedEffect(session.status) {
                viewModel.recoverBackendIdentity()
            }

            // The locale wraps the theme so a language change also re-lays-out right-to-left
            // scripts, not just the strings inside them.
            ProvideAppLocale(settings.language) {
                GridboundTheme(settings) {
                    CompositionLocalProvider(LocalHapticsManager provides haptics) {
                    AppNavigation(
                        session = session,
                        language = settings.language,
                        onLanguage = { language -> viewModel.setLanguage(this@MainActivity, language) },
                        monetization = monetization,
                        billing = billing,
                        onBuy = { entitlement -> viewModel.buy(this@MainActivity, entitlement) },
                        onDismissBillingMessage = viewModel::dismissBillingMessage,
                        onPrivacyOptions = { viewModel.showPrivacyOptions(this@MainActivity) },
                        // Asked from the settings row and nowhere else. On the first launch of
                        // a new install nobody has been offered anything yet, and a permission
                        // dialog shown then is the one everybody denies — after which Android
                        // allows exactly one more ask, ever.
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onCompletedMatchExit = { onFinished ->
                            viewModel.showInterstitialAfterCompletedMatch(this@MainActivity, onFinished)
                        },
                    )
                    }
                }
            }
        }
    }

}
