package com.duzman46.gridbound.core

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsernameRulesTest {

    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        // Turkish is the app's primary market and its casing rules are the classic trap:
        // "I".lowercase() becomes "ı" under a Turkish default locale.
        Locale.setDefault(Locale.forLanguageTag("tr"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `normalization is locale independent`() {
        assertEquals("ilker", UsernameRules.normalize("ILKER"))
        assertEquals("ilker", UsernameRules.normalize("Ilker"))
        assertEquals("ilker", UsernameRules.normalize("  iLkEr  "))
    }

    @Test
    fun `names differing only by case collapse onto the same key`() {
        assertEquals(UsernameRules.normalize("Player_One"), UsernameRules.normalize("PLAYER_ONE"))
    }

    @Test
    fun `accepts letters digits and underscore`() {
        assertTrue(UsernameRules.validate("Koray_42") is Outcome.Success)
        assertTrue(UsernameRules.validate("abc") is Outcome.Success)
        assertTrue(UsernameRules.validate("a".repeat(UsernameRules.MAX_LENGTH)) is Outcome.Success)
    }

    @Test
    fun `rejects blank input`() {
        assertEquals(AppError.USERNAME_BLANK, UsernameRules.validate("   ").errorOrNull)
    }

    @Test
    fun `rejects names outside the length bounds`() {
        assertEquals(AppError.USERNAME_TOO_SHORT, UsernameRules.validate("ab").errorOrNull)
        assertEquals(
            AppError.USERNAME_TOO_LONG,
            UsernameRules.validate("a".repeat(UsernameRules.MAX_LENGTH + 1)).errorOrNull,
        )
    }

    @Test
    fun `rejects spaces and punctuation`() {
        assertEquals(AppError.USERNAME_INVALID_CHARACTERS, UsernameRules.validate("two words").errorOrNull)
        assertEquals(AppError.USERNAME_INVALID_CHARACTERS, UsernameRules.validate("hey!").errorOrNull)
        assertEquals(AppError.USERNAME_INVALID_CHARACTERS, UsernameRules.validate("çğüöşı").errorOrNull)
    }

    @Test
    fun `rejects reserved names regardless of case`() {
        assertEquals(AppError.USERNAME_NOT_ALLOWED, UsernameRules.validate("admin").errorOrNull)
        assertEquals(AppError.USERNAME_NOT_ALLOWED, UsernameRules.validate("ADMIN").errorOrNull)
        assertEquals(AppError.USERNAME_NOT_ALLOWED, UsernameRules.validate("Koridor").errorOrNull)
    }

    @Test
    fun `trims before validating`() {
        assertEquals("Koray", UsernameRules.validate("  Koray  ").successOrNull)
    }

    @Test
    fun `suggestion keeps a usable seed`() {
        assertEquals("Koray42", UsernameRules.suggestFrom("Koray42", "uid123456"))
    }

    @Test
    fun `suggestion falls back when the seed is unusable`() {
        val suggested = UsernameRules.suggestFrom("!!", "abcUID987654")
        assertTrue(UsernameRules.validate(suggested) is Outcome.Success)
        assertTrue(suggested.startsWith("player_"))
    }

    @Test
    fun `suggestion strips characters the policy forbids`() {
        val suggested = UsernameRules.suggestFrom("Ayşe Gül", "uid000001")
        assertTrue(UsernameRules.validate(suggested) is Outcome.Success)
    }

    @Test
    fun `suggestion never exceeds the maximum length`() {
        val suggested = UsernameRules.suggestFrom("a".repeat(50), "uid000001")
        assertTrue(suggested.length <= UsernameRules.MAX_LENGTH)
    }
}
