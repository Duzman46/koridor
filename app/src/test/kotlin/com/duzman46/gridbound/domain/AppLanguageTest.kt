package com.duzman46.gridbound.domain

import com.duzman46.gridbound.domain.models.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the language chip, the picker and the settings row say the app is running in.
 *
 * All three ask [AppLanguage.resolve] the same question and pass it the same thing: the
 * device's language as `Locale.getLanguage()` reports it, which is the language subtag alone.
 * The one shipped locale whose tag has a second half is Brazilian Portuguese, so it is the one
 * that has to be checked — and the one that was answered "English" on a phone reading
 * Portuguese.
 */
class AppLanguageTest {

    @Test
    fun `a brazilian phone resolves to portuguese rather than english`() {
        assertEquals(
            AppLanguage.PORTUGUESE_BRAZIL,
            AppLanguage.resolve(AppLanguage.SYSTEM, "pt"),
        )
    }

    @Test
    fun `and so does one that reports the whole tag`() {
        assertEquals(
            AppLanguage.PORTUGUESE_BRAZIL,
            AppLanguage.resolve(AppLanguage.SYSTEM, "pt-BR"),
        )
    }

    @Test
    fun `every shipped language resolves to itself from the subtag a device reports`() {
        // The subtag is what Android hands over, so this is the whole of what `resolve` is
        // ever asked in practice.
        AppLanguage.selectable.forEach { language ->
            assertEquals(language, AppLanguage.resolve(AppLanguage.SYSTEM, language.subtag))
        }
    }

    @Test
    fun `indonesian still resolves through its legacy code`() {
        // Android reports "in" for Indonesian and always has. Matching on the language half
        // must not have quietly started expecting the modern "id".
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.resolve(AppLanguage.SYSTEM, "in"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(AppLanguage.SYSTEM, "id"))
    }

    @Test
    fun `a language the app does not ship falls back to english`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(AppLanguage.SYSTEM, "ja"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(AppLanguage.SYSTEM, ""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(AppLanguage.SYSTEM, null))
    }

    @Test
    fun `a stored choice is never resolved against the device at all`() {
        assertEquals(AppLanguage.TURKISH, AppLanguage.resolve(AppLanguage.TURKISH, "pt"))
    }
}
