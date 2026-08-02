package com.duzman46.gridbound.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.duzman46.gridbound.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

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
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth().height(adSize.height.dp),
    )
}
