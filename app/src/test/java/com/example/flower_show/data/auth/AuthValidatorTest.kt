package com.example.flower_show.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
    @Test
    fun registrationAcceptsExactCharacterBoundaries() {
        assertValid(username = "a_3")
        assertValid(username = "a".repeat(32))
        assertValid(password = "a".repeat(8))
        assertValid(password = "a".repeat(72))
        assertValid(nickname = "🌻".repeat(80))
        assertValid(bio = "花".repeat(255))
    }

    @Test
    fun registrationRejectsUnsupportedOrOutOfRangeUsernames() {
        listOf(
            "",
            "ab",
            "a".repeat(33),
            "garden user",
            "garden/user",
            "花园用户",
        ).forEach { username ->
            val result = registration(username = username)

            assertFalse("Expected username to be invalid: $username", result.isValid)
            assertTrue(result.fieldErrors.containsKey(AuthField.Username))
        }
    }

    @Test
    fun registrationEnforcesBothPasswordCharacterAndUtf8ByteLimits() {
        assertPasswordInvalid("a".repeat(7))
        assertPasswordInvalid("a".repeat(73))

        // 24 CJK code points occupy exactly 72 UTF-8 bytes; the 25th exceeds the byte limit.
        assertValid(password = "花".repeat(24))
        assertPasswordInvalid("花".repeat(25))

        // Emoji are surrogate pairs on the JVM, but validation counts Unicode code points.
        assertValid(password = "🌻".repeat(18))
        assertPasswordInvalid("🌻".repeat(19))
    }

    @Test
    fun registrationCountsNicknameAndBioByUnicodeCodePoint() {
        assertValid(nickname = "🌺".repeat(80), bio = "🌱".repeat(255))

        val nicknameResult = registration(nickname = "🌺".repeat(81))
        val bioResult = registration(bio = "🌱".repeat(256))

        assertTrue(nicknameResult.fieldErrors.containsKey(AuthField.Nickname))
        assertTrue(bioResult.fieldErrors.containsKey(AuthField.Bio))
    }

    @Test
    fun usernameNormalizationIsTrimmedAndLocaleIndependent() {
        assertEquals("i.garden-user_1", AuthValidator.normalizeUsername("  I.GARDEN-USER_1  "))
    }

    @Test
    fun loginRequiresBothFieldsButDoesNotApplyRegistrationPasswordRules() {
        assertFalse(AuthValidator.validateLogin("   ", "").isValid)
        assertTrue(AuthValidator.validateLogin("garden.user", "short").isValid)
    }

    @Test
    fun optionalTextTrimsValuesAndMapsBlankToNull() {
        assertNull(AuthValidator.optionalText("  \t "))
        assertEquals("阳台种花", AuthValidator.optionalText("  阳台种花  "))
    }

    private fun assertPasswordInvalid(password: String) {
        val result = registration(password = password)
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(AuthField.Password))
    }

    private fun assertValid(
        username: String = "garden.user",
        password: String = "Garden123",
        nickname: String = "花园用户",
        bio: String = "阳台种花",
    ) {
        val result = registration(
            username = username,
            password = password,
            nickname = nickname,
            bio = bio,
        )
        assertTrue(
            result.fieldErrors.toString(),
            result.isValid,
        )
    }

    private fun registration(
        username: String = "garden.user",
        password: String = "Garden123",
        nickname: String = "花园用户",
        bio: String = "阳台种花",
    ): AuthValidationResult = AuthValidator.validateRegistration(
        username = username,
        password = password,
        nickname = nickname,
        bio = bio,
    )
}
