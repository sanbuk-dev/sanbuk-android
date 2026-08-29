package ir.sanbuk.sdk.core

/**
 * When a view has actually been seen.
 *
 * An impression reported the moment a banner is attached is not an impression:
 * it may be off screen, behind a keyboard, in a list row nobody scrolled to.
 * The MRC bar is the industry's answer — at least half the pixels, for at
 * least one continuous second — and meeting it is what makes the number an
 * advertiser will pay against and a publisher's quality score honest.
 *
 * Pure state machine on purpose: the Android layer feeds it samples from a
 * ViewTreeObserver, the Unity shell from its own loop, and both get identical
 * behaviour. It never reports twice — one drawn ad is one view.
 */
public class Viewability(
    private val minimumVisibleFraction: Double = MRC_FRACTION,
    private val requiredMillis: Long = MRC_MILLIS,
) {

    private var continuousSinceEpochMs: Long? = null
    private var counted = false

    /**
     * Feed one observation.
     *
     * @param visibleFraction 0.0 to 1.0 of the ad's own area currently on screen.
     * @return true exactly once: on the sample that completes the requirement.
     */
    public fun sample(visibleFraction: Double, nowEpochMs: Long): Boolean {
        if (counted) return false

        if (visibleFraction < minimumVisibleFraction) {
            // Interrupted. The clock restarts rather than accumulating: half a
            // second twice is not a second of attention.
            continuousSinceEpochMs = null
            return false
        }

        val since = continuousSinceEpochMs ?: nowEpochMs.also { continuousSinceEpochMs = it }
        if (nowEpochMs - since < requiredMillis) return false

        counted = true
        return true
    }

    public fun hasCounted(): Boolean = counted

    public companion object {
        public const val MRC_FRACTION: Double = 0.5
        public const val MRC_MILLIS: Long = 1_000L
    }
}
