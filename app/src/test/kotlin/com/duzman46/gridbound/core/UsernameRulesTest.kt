package com.duzman46.gridbound.core

import java.util.Locale
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a generated name is guest_ and six digits`() {
        assertTrue(GENERATED_SHAPE.matches(UsernameRules.generatedName(Random(1))))
        assertTrue(GENERATED_SHAPE.matches(UsernameRules.generatedName(Random(99))))
    }

    /**
     * The generated name has to survive both gates it will meet: the policy in this file,
     * and the uniqueness index in database.rules.json, which only accepts a key of three to
     * sixteen lower-case letters, digits and underscores.
     */
    @Test
    fun `a generated name satisfies the policy and the database index`() {
        repeat(200) { seed ->
            val name = UsernameRules.generatedName(Random(seed))
            assertTrue(name, UsernameRules.validate(name) is Outcome.Success)
            assertTrue(name, INDEX_KEY.matches(UsernameRules.normalize(name)))
        }
    }

    @Test
    fun `two guests do not get the same name`() {
        val names = (1..500).map { UsernameRules.generatedName() }.toSet()
        // Six digits: a handful of repeats in five hundred draws would still be normal, a
        // generator that keeps handing out one name would not.
        assertTrue(names.size > 400)
    }

    @Test
    fun `the reserved word is guest, not every name built from it`() {
        assertEquals(AppError.USERNAME_NOT_ALLOWED, UsernameRules.validate("guest").errorOrNull)
        assertTrue(UsernameRules.validate("guest_483920") is Outcome.Success)
    }

    @Test
    fun `a generated name is recognised as one, whoever generated it`() {
        assertTrue(UsernameRules.isGenerated(UsernameRules.generatedName(Random(7))))
        // The shape earlier versions handed out, still on live profiles.
        assertTrue(UsernameRules.isGenerated("player_a1B2c3"))
    }

    @Test
    fun `a name a player typed is not mistaken for a generated one`() {
        assertFalse(UsernameRules.isGenerated("Koray"))
        assertFalse(UsernameRules.isGenerated("guest"))
        assertFalse(UsernameRules.isGenerated("guest_of_honour"))
        assertFalse(UsernameRules.isGenerated("theguest_12"))
    }

    private companion object {
        val GENERATED_SHAPE = Regex("^guest_[0-9]{6}$")

        /** Mirrors the `usernames/$normalizedUsername` key rule in database.rules.json. */
        val INDEX_KEY = Regex("^[a-z0-9_]{3,16}$")
    }
}
