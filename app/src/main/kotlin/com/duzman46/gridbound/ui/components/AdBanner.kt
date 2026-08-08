package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.duzman46.gridbound.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

/**
 * The anchored banner, with the one thing a banner needs that is easy to leave out: another go.
 *
 * The request used to be made once, in the factory that built the view, and never again. An
 * auction that answers empty — which is most of them while an app is new and has no history for
 * a bidder to price — therefore left a blank strip for as long as the screen was on, and
 * because the view survives recomposition it stayed blank until the player navigated away and
 * came back. On a home screen that is where players sit, so "the banner never appears" is
 * exactly what one empty answer looks like.
 *
 * The height is reserved whether or not an ad arrives. A strip that appears when the auction
 * settles would shove the menu down under the player's thumb a second after they started
 * reading it, which is worse than a gap.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value.toInt() }.coerceAtLeast(320)
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    val adView = remember(adSize) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(adSize)
        }
    }
    // Counts refusals rather than holding a flag, so each failure is a distinct value and
    // restarts the effect below. A boolean would settle after the first retry and stop.
    var failures by remember(adView) { mutableIntStateOf(0) }
    DisposableEffect(adView) {
        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                failures++
            }
        }
        onDispose { adView.destroy() }
    }
    LaunchedEffect(adView, failures) {
        // Backing off matters: asking again at once for something the auction has just said it
        // does not have is what AdMob's own guidance warns against, and on a handset with no
        // connection a tight loop would spend battery to be told the same thing. Capped at
        // eighty seconds, which is far shorter than anyone sits on one screen.
        if (failures > 0) {
            delay(RETRY_BASE_MILLIS shl (failures - 1).coerceAtMost(MAX_RETRY_BACKOFF_STEPS))
        }
        adView.loadAd(AdRequest.Builder().build())
    }
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth().height(adSize.height.dp),
    )
}

private const val RETRY_BASE_MILLIS = 5_000L
private const val MAX_RETRY_BACKOFF_STEPS = 4
