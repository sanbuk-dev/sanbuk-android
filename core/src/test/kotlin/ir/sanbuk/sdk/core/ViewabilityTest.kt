package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewabilityTest {

    @Test
    fun `counts a view that was half on screen for a full second`() {
        val rule = Viewability()

        assertFalse(rule.sample(visibleFraction = 0.6, nowEpochMs = 0))
        assertFalse(rule.sample(visibleFraction = 0.6, nowEpochMs = 500))
        assertTrue(rule.sample(visibleFraction = 0.6, nowEpochMs = 1_000))
    }

    @Test
    fun `a barely visible ad is not a view`() {
        val rule = Viewability()

        assertFalse(rule.sample(visibleFraction = 0.49, nowEpochMs = 0))
        assertFalse(rule.sample(visibleFraction = 0.49, nowEpochMs = 5_000))
    }

    /** Half a second twice is not a second of attention. */
    @Test
    fun `scrolling away restarts the clock`() {
        val rule = Viewability()

        rule.sample(visibleFraction = 1.0, nowEpochMs = 0)
        rule.sample(visibleFraction = 0.0, nowEpochMs = 600)
        rule.sample(visibleFraction = 1.0, nowEpochMs = 700)

        assertFalse(rule.sample(visibleFraction = 1.0, nowEpochMs = 1_400))
        assertTrue(rule.sample(visibleFraction = 1.0, nowEpochMs = 1_700))
    }

    /** One drawn ad is one view, however long the publisher leaves it up. */
    @Test
    fun `never reports the same view twice`() {
        val rule = Viewability()

        rule.sample(visibleFraction = 1.0, nowEpochMs = 0)
        assertTrue(rule.sample(visibleFraction = 1.0, nowEpochMs = 1_000))

        assertFalse(rule.sample(visibleFraction = 1.0, nowEpochMs = 2_000))
        assertFalse(rule.sample(visibleFraction = 1.0, nowEpochMs = 60_000))
        assertTrue(rule.hasCounted())
    }

    @Test
    fun `a publisher who wants a stricter bar can have one`() {
        val rule = Viewability(minimumVisibleFraction = 1.0, requiredMillis = 2_000)

        assertFalse(rule.sample(visibleFraction = 0.9, nowEpochMs = 0))
        rule.sample(visibleFraction = 1.0, nowEpochMs = 0)
        assertFalse(rule.sample(visibleFraction = 1.0, nowEpochMs = 1_999))
        assertTrue(rule.sample(visibleFraction = 1.0, nowEpochMs = 2_000))
    }
}
