package com.duzman46.gridbound.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.duzman46.gridbound.core.Constants

/**
 * The app's single preference store.
 *
 * Declared once and shared, because DataStore throws if two delegates are created for the
 * same file — which is exactly what would happen if the repository and the startup language
 * read each declared their own.
 */
internal val Context.gridboundDataStore by preferencesDataStore(
    Constants.Data.SETTINGS_FILE_NAME,
)
