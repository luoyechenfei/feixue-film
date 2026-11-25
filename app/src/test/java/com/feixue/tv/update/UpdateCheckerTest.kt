package com.feixue.tv.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `newer version is detected`() {
        assertTrue(UpdateChecker.isNewer("1.2", "1.1"))
        assertTrue(UpdateChecker.isNewer("1.1.1", "1.1"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
    }

    @Test
    fun `same or older version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.1", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.1"))
        assertFalse(UpdateChecker.isNewer("1.1", "1.1.1"))
        assertFalse(UpdateChecker.isNewer("1.9", "1.10"))
    }
}
