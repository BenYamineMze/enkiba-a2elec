package com.enkiba.app.security

import com.enkiba.app.ui.lock.PinRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun hash_is_salted_and_verifiable() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.matches("4821", salt, hash))
        assertFalse(PinHasher.matches("4822", salt, hash))
        // Même code, autre sel : hachage différent
        assertNotEquals(hash, PinHasher.hash("4821", PinHasher.newSalt()))
    }

    @Test
    fun five_errors_send_back_to_login() {
        assertEquals(PinResult.Wrong(4), AppLock.evaluate(matches = false, failuresBefore = 0))
        assertEquals(PinResult.Wrong(1), AppLock.evaluate(matches = false, failuresBefore = 3))
        assertEquals(PinResult.TooManyAttempts, AppLock.evaluate(matches = false, failuresBefore = 4))
        assertEquals(PinResult.Ok, AppLock.evaluate(matches = true, failuresBefore = 4))
    }

    @Test
    fun locks_after_two_minutes_in_background() {
        assertFalse(AppLock.shouldLock(backgroundSince = 1_000, now = 1_000 + AppLock.TIMEOUT_MS - 1))
        assertTrue(AppLock.shouldLock(backgroundSince = 1_000, now = 1_000 + AppLock.TIMEOUT_MS))
        // Horloge incohérente : on verrouille par prudence
        assertTrue(AppLock.shouldLock(backgroundSince = 5_000, now = 1_000))
    }

    @Test
    fun too_simple_codes_are_refused() {
        listOf("0000", "1111", "1234", "6789", "9876", "3210").forEach { assertTrue(it, PinRules.isTooSimple(it)) }
        listOf("4821", "1235", "2580", "1122").forEach { assertFalse(it, PinRules.isTooSimple(it)) }
    }
}
