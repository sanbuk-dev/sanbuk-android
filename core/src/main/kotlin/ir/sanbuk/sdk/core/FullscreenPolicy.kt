package ir.sanbuk.sdk.core

/**
 * When a full-screen ad may interrupt someone.
 *
 * An interstitial is the most profitable format and the fastest way to lose a
 * publisher. It is natural in a game — between two levels, nobody minds — and
 * an ambush in a utility app. The difference is almost never the creative; it
 * is when it appeared. So the rules live here, in shared code with tests,
 * rather than being left to whoever integrates:
 *
 * - never during the first moments of a cold start. An ad before the app has
 *   drawn its own first screen reads as "this app is an ad", and it is the
 *   most common reason a publisher rips an SDK out;
 * - never back to back. Two in a row is not twice the money, it is an
 *   uninstall;
 * - a ceiling per session, because the tenth is worth less than the first and
 *   costs much more;
 * - the close control appears after a few seconds — long enough to have been
 *   an ad, short enough not to be a hostage situation.
 *
 * Rewarded is deliberately gated by none of this: the person asked for it.
 * See [RewardPolicy].
 *
 * Every time argument here is elapsed milliseconds from a monotonic source,
 * not a date. Callers must not pass wall-clock time: it jumps when the network
 * corrects it and moves whenever someone changes the device clock, and a timer
 * a person can wind forward is a close gate they can skip.
 */
public class FullscreenPolicy(
    private val minimumGapMillis: Long = MIN_GAP_MILLIS,
    private val maxPerSession: Int = MAX_PER_SESSION,
    private val quietAfterLaunchMillis: Long = QUIET_AFTER_LAUNCH_MILLIS,
    private val closeAfterMillis: Long = CLOSE_AFTER_MILLIS,
) {

    private var shownThisSession = 0
    private var lastShownAtMillis: Long? = null

    /** Why a full-screen ad was not shown — the answer an integrator needs. */
    public enum class Refusal {
        TOO_SOON_AFTER_LAUNCH,
        TOO_SOON_AFTER_LAST,
        SESSION_LIMIT,
    }

    /**
     * @param sessionStartedAtMillis when the app process began, not when the
     *        SDK was initialised — a publisher who initialises us late would
     *        otherwise get a quiet window that has already elapsed.
     */
    public fun refusalFor(nowMillis: Long, sessionStartedAtMillis: Long): Refusal? = when {
        nowMillis - sessionStartedAtMillis < quietAfterLaunchMillis -> Refusal.TOO_SOON_AFTER_LAUNCH
        shownThisSession >= maxPerSession -> Refusal.SESSION_LIMIT
        lastShownAtMillis?.let { nowMillis - it < minimumGapMillis } == true -> Refusal.TOO_SOON_AFTER_LAST
        else -> null
    }

    public fun mayShow(nowMillis: Long, sessionStartedAtMillis: Long): Boolean =
        refusalFor(nowMillis, sessionStartedAtMillis) == null

    /** Call when one is actually put on screen, not when it is loaded. */
    public fun recordShown(nowMillis: Long) {
        shownThisSession++
        lastShownAtMillis = nowMillis
    }

    /** Whether the close control has earned its place on screen yet. */
    public fun closeAllowed(shownAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - shownAtMillis >= closeAfterMillis

    public fun millisUntilClose(shownAtMillis: Long, nowMillis: Long): Long =
        (closeAfterMillis - (nowMillis - shownAtMillis)).coerceAtLeast(0)

    public companion object {
        public const val MIN_GAP_MILLIS: Long = 60_000
        public const val MAX_PER_SESSION: Int = 3
        /** A cold start belongs to the app, not to us. */
        public const val QUIET_AFTER_LAUNCH_MILLIS: Long = 30_000
        public const val CLOSE_AFTER_MILLIS: Long = 5_000
    }
}
