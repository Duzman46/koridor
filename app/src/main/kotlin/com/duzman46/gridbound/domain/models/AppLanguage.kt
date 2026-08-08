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

    /**
     * The language half of [tag], which is all a device reports through
     * `Locale.getLanguage()`.
     *
     * Only [PORTUGUESE_BRAZIL] has a second half, and it is the reason this exists: a phone
     * set to Português (Brasil) answers "pt", never "pt-BR".
     */
    val subtag: String get() = tag.substringBefore('-')

    companion object {
        /**
         * What the picker offers: real languages only.
         *
         * [SYSTEM] is still the stored default and still what a fresh install behaves as —
         * it just isn't an entry any more. "Use the device language" told the player nothing
         * about which language they were about to get, and sat above the list they actually
         * came to read. A Turkish phone now simply shows Türkçe already ticked, which is the
         * same behaviour said in a way that can be checked at a glance.
         */
        val selectable: List<AppLanguage> = entries.filterNot(AppLanguage::followsDevice)

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM

        /**
         * The language [stored] actually produces on a device set to [deviceTag].
         *
         * Only [SYSTEM] needs resolving. A device set to a language the app does not ship
         * falls back to English, which is what the resource system does anyway — so the tick
         * lands on the language the player is really reading.
         *
         * The whole tag is tried first and the language on its own second, because the two
         * places a device tag comes from disagree: Android reports the language alone, so a
         * Brazilian phone says "pt" while the entry that serves it is tagged "pt-BR". Matching
         * only whole tags sent every one of those phones to English — on screen, in a picker
         * sitting above Portuguese text. Falling back on the language is also exactly what the
         * resource system does, which is what makes the tick honest rather than merely kinder.
         */
        fun resolve(stored: AppLanguage, deviceTag: String?): AppLanguage {
            if (!stored.followsDevice) return stored
            val device = deviceTag.orEmpty()
            if (device.isEmpty()) return ENGLISH
            return selectable.firstOrNull { it.tag.equals(device, ignoreCase = true) }
                ?: selectable.firstOrNull {
                    it.subtag.equals(device.substringBefore('-'), ignoreCase = true)
                }
                ?: ENGLISH
        }
    }
}
