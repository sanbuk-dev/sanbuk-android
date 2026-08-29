package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardPolicyTest {

    @Test
    fun `the reward is earned by staying`() {
        val policy = RewardPolicy()
        val shownAt = 0L

        assertFalse(policy.onProgress(shownAt, nowMillis = 14_999))
        assertTrue(policy.onProgress(shownAt, nowMillis = 15_000))
    }

    /** Leaving early earns nothing — and the person has to be told that first. */
    @Test
    fun `closing early earns nothing`() {
        val policy = RewardPolicy()

        policy.onProgress(shownAtMillis = 0, nowMillis = 3_000)

        assertFalse(policy.hasEarned())
    }

    /** One ad, one reward, however many progress ticks arrive. */
    @Test
    fun `the reward fires exactly once`() {
        val policy = RewardPolicy()

        assertTrue(policy.onProgress(0, 15_000))
        assertFalse(policy.onProgress(0, 16_000))
        assertFalse(policy.onProgress(0, 60_000))
        assertTrue(policy.hasEarned())
    }

    @Test
    fun `it can say how much longer`() {
        val policy = RewardPolicy()

        assertEquals(15_000, policy.millisRemaining(0, 0))
        assertEquals(5_000, policy.millisRemaining(0, 10_000))
        assertEquals(0, policy.millisRemaining(0, 20_000), "never negative")
    }

    @Test
    fun `a shorter ad can ask for less`() {
        val policy = RewardPolicy(requiredMillis = 5_000)

        assertTrue(policy.onProgress(0, 5_000))
    }
}
