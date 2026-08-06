package com.duzman46.gridbound.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the context chain to the hosting Activity.
 *
 * Credential Manager and the billing flow both need an Activity, and `LocalContext` inside
 * Compose can be a wrapper rather than the Activity itself.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
