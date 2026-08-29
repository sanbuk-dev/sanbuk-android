package ir.sanbuk.sdk.internal

import ir.sanbuk.sdk.SanbukAd
import ir.sanbuk.sdk.SanbukFullscreen
import ir.sanbuk.sdk.SanbukStyle
import ir.sanbuk.sdk.core.FullscreenPolicy
import java.util.concurrent.atomic.AtomicLong

/**
 * Hands a loaded ad to the activity that will show it.
 *
 * An Activity is started with an Intent, and an Intent carries Parcelables —
 * but a [SanbukAd] is a live object with a session behind it, not a bag of
 * strings, and serialising it would mean the shown ad could no longer report
 * its own impression. So the intent carries a token and the object is looked
 * up here.
 *
 * Every entry is removed the moment it is claimed or the activity finishes.
 * A static map holding an ad and a publisher's callbacks is exactly the kind
 * of thing that quietly retains a screen, so nothing is allowed to linger.
 */
internal object FullscreenHost {

    internal class Pending(
        val ad: SanbukAd,
        val style: SanbukStyle,
        val rewarded: Boolean,
        val callbacks: SanbukFullscreen.Callbacks?,
        /** The session's one policy — the screen records its own showing on it. */
        val policy: FullscreenPolicy,
    )

    private val tokens = AtomicLong(0)
    private val pending = HashMap<Long, Pending>()

    @Synchronized
    fun offer(entry: Pending): Long {
        val token = tokens.incrementAndGet()
        pending[token] = entry
        return token
    }

    /** Claimed once, by the activity that was started for it. */
    @Synchronized
    fun claim(token: Long): Pending? = pending.remove(token)

    /** For the case the activity never started at all — nothing is left behind. */
    @Synchronized
    fun discard(token: Long) {
        pending.remove(token)
    }
}
