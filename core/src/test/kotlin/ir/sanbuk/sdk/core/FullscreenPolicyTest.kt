package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullscreenPolicyTest {

    private val launch = 0L

    /**
     * The rule that saves the integration. An ad before the app has drawn its
     * own first screen reads as "this app is an ad", and it is the most common
     * reason a publisher removes an SDK.
     */
    @Test
    fun `nothing interrupts a cold start`() {
        val policy = FullscreenPolicy()

        assertEquals(
            FullscreenPolicy.Refusal.TOO_SOON_AFTER_LAUNCH,
            policy.refusalFor(nowMillis = 10_000, sessionStartedAtMillis = launch),
        )
        assertNull(policy.refusalFor(nowMillis = 30_000, sessionStartedAtMillis = launch))
    }

    /** Two in a row is not twice the money. */
    @Test
    fun `never back to back`() {
        val policy = FullscreenPolicy()
        policy.recordShown(nowMillis = 60_000)

        assertEquals(
            FullscreenPolicy.Refusal.TOO_SOON_AFTER_LAST,
            policy.refusalFor(nowMillis = 90_000, sessionStartedAtMillis = launch),
        )
        assertTrue(policy.mayShow(nowMillis = 121_000, sessionStartedAtMillis = launch))
    }

    @Test
    fun `a session has a ceiling`() {
        val policy = FullscreenPolicy()
        var now = 60_000L
        repeat(FullscreenPolicy.MAX_PER_SESSION) {
            assertTrue(policy.mayShow(now, launch), "show ${it + 1} should have been allowed")
            policy.recordShown(now)
            now += FullscreenPolicy.MIN_GAP_MILLIS + 1
        }

        assertEquals(
            FullscreenPolicy.Refusal.SESSION_LIMIT,
            policy.refusalFor(now, launch),
            "the fourth is worth less than the first and costs far more",
        )
    }

    /**
     * Long enough to have been an ad, short enough not to be a hostage
     * situation.
     */
    @Test
    fun `the close control appears after a few seconds`() {
        val policy = FullscreenPolicy()
        val shownAt = 100_000L

        assertFalse(policy.closeAllowed(shownAt, nowMillis = shownAt + 4_999))
        assertTrue(policy.closeAllowed(shownAt, nowMillis = shownAt + 5_000))
        assertEquals(5_000, policy.millisUntilClose(shownAt, shownAt))
        assertEquals(0, policy.millisUntilClose(shownAt, shownAt + 9_000), "never negative")
    }

    /** A game between two levels is the case this format is actually for. */
    @Test
    fun `a publisher who knows their app can loosen the gaps`() {
        val policy = FullscreenPolicy(
            minimumGapMillis = 1_000,
            maxPerSession = 10,
            quietAfterLaunchMillis = 0,
        )

        assertTrue(policy.mayShow(nowMillis = 0, sessionStartedAtMillis = 0))
        policy.recordShown(0)
        assertTrue(policy.mayShow(nowMillis = 1_000, sessionStartedAtMillis = 0))
    }

    /** The launch window is measured from the process, not from init(). */
    @Test
    fun `a late init does not reopen the quiet window`() {
        val policy = FullscreenPolicy()

        assertTrue(
            policy.mayShow(nowMillis = 100_000, sessionStartedAtMillis = 40_000),
            "the process started 60s ago; when we were initialised is not the question",
        )
    }
}
