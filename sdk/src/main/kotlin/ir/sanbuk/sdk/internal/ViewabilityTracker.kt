package ir.sanbuk.sdk.internal

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import ir.sanbuk.sdk.core.Viewability

/**
 * Watches one ad view and says when it has actually been seen.
 *
 * The rule itself lives in core so the Unity and Flutter shells get identical
 * behaviour; this only supplies the samples. Android has no callback for "how
 * much of this view is on screen", so this samples from two sources and needs
 * both:
 *
 * - the pre-draw listener catches every change of geometry — a scroll, a
 *   layout, an animation;
 * - a poll catches the passage of time, which no draw pass reports.
 *
 * The second is not belt-and-braces. Half the MRC bar is duration, and a screen
 * nobody touches stops drawing: a banner at the top of an article someone is
 * reading produces two traversals and then silence. With only the listener, the
 * most ordinary case there is — a fully visible ad on a still screen — would
 * never reach its continuous second, and the publisher would be paid nothing
 * for it.
 *
 * Detaching stops everything. An ad in a recycled list row must not keep a
 * listener alive against a view the app has moved on from.
 */
internal class ViewabilityTracker(
    private val view: View,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onSeen: () -> Unit,
) {

    private val rule = Viewability()
    private var listener: ViewTreeObserver.OnPreDrawListener? = null

    /**
     * The clock. `listener` doubles as its stop condition, so there is no
     * second piece of state to keep honest: the moment sampling ends — the ad
     * counted, the view detached, the slot reloaded — this stops re-posting.
     */
    private val poll = object : Runnable {
        override fun run() {
            if (listener == null) return
            sample()
            if (listener != null) runCatching { view.postDelayed(this, POLL_MS) }
        }
    }

    fun start() {
        if (listener != null) return
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            sample()
            true
        }
        listener = preDraw
        runCatching { view.viewTreeObserver.addOnPreDrawListener(preDraw) }
        runCatching { view.postDelayed(poll, POLL_MS) }
    }

    fun stop() {
        val preDraw = listener ?: return
        listener = null
        runCatching { view.removeCallbacks(poll) }
        runCatching {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(preDraw)
        }
    }

    /**
     * Runs inside a draw pass, so it is wrapped like everything else here: an
     * exception thrown from onPreDraw would take down the publisher's frame,
     * not ours.
     */
    private fun sample() {
        runCatching {
            if (rule.hasCounted()) {
                stop()
                return@runCatching
            }
            if (rule.sample(visibleFraction(), clock())) {
                stop()
                onSeen()
            }
        }
    }

    private companion object {
        /**
         * Fine enough that the continuous second lands within a frame or two
         * of a true second, coarse enough to be invisible: the work is one
         * rectangle intersection.
         */
        const val POLL_MS = 200L
    }

    /**
     * How much of the ad a person could actually see right now.
     *
     * getGlobalVisibleRect answers "what part of this view intersects the
     * window", which is the honest question — a row scrolled halfway off the
     * screen returns half. It does not know about a view drawn behind another,
     * and neither does any cheap alternative; the shipped bar accounts for
     * that by asking for a continuous second rather than an instant.
     */
    private fun visibleFraction(): Double = runCatching {
        if (!view.isShown) return 0.0
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return 0.0

        val visible = Rect()
        if (!view.getGlobalVisibleRect(visible)) return 0.0

        val seen = visible.width().toLong() * visible.height().toLong()
        val whole = width.toLong() * height.toLong()
        (seen.toDouble() / whole.toDouble()).coerceIn(0.0, 1.0)
    }.getOrElse { 0.0 }
}
