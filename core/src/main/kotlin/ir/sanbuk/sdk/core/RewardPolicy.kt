package ir.sanbuk.sdk.core

/**
 * Whether a reward has been earned.
 *
 * The one format where the person chose to see the ad, in exchange for
 * something. That changes two things:
 *
 * - none of [FullscreenPolicy]'s gates apply. Refusing an ad somebody asked
 *   for, because they have already seen two, is refusing them their coins;
 * - the bar is duration, not viewability. A rewarded ad is full screen by
 *   definition, so "was it visible" is not the question; "did they stay" is.
 *
 * Earning is decided here rather than in the publisher's app so that "did this
 * person earn it" has one answer on every platform. Leaving early earns
 * nothing — which has to be said before they start, and that is the
 * integrator's job.
 *
 * Times are elapsed milliseconds from a monotonic source. This one matters
 * more than most: a reward measured on the wall clock is a reward anyone can
 * collect by winding the device forward fifteen seconds.
 */
public class RewardPolicy(private val requiredMillis: Long = REQUIRED_MILLIS) {

    private var earned = false

    /** @return true the first time the requirement is met, false ever after. */
    public fun onProgress(shownAtMillis: Long, nowMillis: Long): Boolean {
        if (earned) return false
        if (nowMillis - shownAtMillis < requiredMillis) return false
        earned = true
        return true
    }

    public fun hasEarned(): Boolean = earned

    public fun millisRemaining(shownAtMillis: Long, nowMillis: Long): Long =
        (requiredMillis - (nowMillis - shownAtMillis)).coerceAtLeast(0)

    public companion object {
        /**
         * Long enough to be worth an advertiser's money, short enough that a
         * player does not abandon it — where the format settled years ago.
         */
        public const val REQUIRED_MILLIS: Long = 15_000
    }
}
