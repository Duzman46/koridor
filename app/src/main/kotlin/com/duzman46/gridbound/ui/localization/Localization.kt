package com.duzman46.gridbound.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.duzman46.gridbound.domain.models.AppLanguage
import com.duzman46.gridbound.domain.models.LocalizedText

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.TURKISH }

@Composable
fun localized(turkish: String, english: String): String = when (LocalAppLanguage.current) {
    AppLanguage.TURKISH -> turkish
    AppLanguage.ENGLISH -> english
}

@Composable
fun LocalizedText.localized(): String = value(LocalAppLanguage.current)
