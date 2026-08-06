package com.duzman46.gridbound.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRulesTest {

    @Test
    fun `accepts ordinary addresses`() {
        assertTrue(EmailRules.isValid("player@example.com"))
        assertTrue(EmailRules.isValid("first.last+tag@mail.example.co.uk"))
        assertTrue(EmailRules.isValid("  spaced@example.com  "))
    }

    @Test
    fun `rejects malformed addresses`() {
        assertFalse(EmailRules.isValid(""))
        assertFalse(EmailRules.isValid("player"))
        assertFalse(EmailRules.isValid("player@"))
        assertFalse(EmailRules.isValid("player@example"))
        assertFalse(EmailRules.isValid("player example@mail.com"))
        assertFalse(EmailRules.isValid("@example.com"))
    }

    @Test
    fun `password must reach the minimum length`() {
        assertEquals(
            AppError.PASSWORD_WEAK,
            PasswordRules.validate("a".repeat(PasswordRules.MIN_LENGTH - 1)).errorOrNull,
        )
        assertTrue(PasswordRules.validate("a".repeat(PasswordRules.MIN_LENGTH)) is Outcome.Success)
    }

    @Test
    fun `mismatched confirmation is reported before weakness`() {
        assertEquals(
            AppError.PASSWORD_MISMATCH,
            PasswordRules.validateMatching("short", "different").errorOrNull,
        )
    }

    @Test
    fun `matching confirmation still enforces strength`() {
        assertEquals(
            AppError.PASSWORD_WEAK,
            PasswordRules.validateMatching("abc", "abc").errorOrNull,
        )
        assertTrue(PasswordRules.validateMatching("longenough1", "longenough1") is Outcome.Success)
    }
}
