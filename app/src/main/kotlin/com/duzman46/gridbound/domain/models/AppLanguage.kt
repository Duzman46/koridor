package com.duzman46.gridbound.domain.models

/**
 * The languages the app ships.
 *
 * [SYSTEM] is the default: a fresh install follows the device language, and falls back to
 * English when the device is set to a language that is not on this list. Choosing anything
 * else here overrides that, and on Android 13+ the choice is handed to the platform so it
 * also shows in the system per-app language settings.
 *
 * @param tag BCP-47 tag, empty for [SYSTEM]. Must match a values-<qualifier>/ folder.
 * @param endonym the language's own name, never translated — a Russian speaker looking for
 *   their language should see "Русский", not whatever the current language calls it.
 */
enum class AppLanguage(val tag: String, val endonym: String) {
    SYSTEM("", ""),
    TURKISH("tr", "Türkçe"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    RUSSIAN("ru", "Русский"),
    ARABIC("ar", "العربية"),

    /**
     * Indonesian. Android's resource qualifier and Locale language code are the legacy
     * "in" rather than the modern "id"; the two are the same language to the platform.
     */
    INDONESIAN("in", "Bahasa Indonesia"),
    HINDI("hi", "हिन्दी"),
    ;

    val followsDevice: Boolean get() = this == SYSTEM

    companion object {
        val selectable: List<AppLanguage> = entries

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}
