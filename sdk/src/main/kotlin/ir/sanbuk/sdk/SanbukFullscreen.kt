package ir.sanbuk.sdk

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import ir.sanbuk.sdk.internal.FullscreenActivity
import ir.sanbuk.sdk.internal.FullscreenHost
import ir.sanbuk.sdk.internal.Worker

/**
 * A full-screen ad — an interstitial, or a rewarded one somebody opted into.
 *
 * Loaded first and shown later, because the moment worth interrupting is
 * rarely the moment you have a network round trip to spare: a game loads
 * between levels and shows at the end of one.
 *
 * ```kotlin
 * SanbukFullscreen.load(context, "LEVEL-END") { ad ->
 *     pending = ad                     // keep it until the level ends
 * }
 *
 * pending?.show(activity, callbacks = object : SanbukFullscreen.Callbacks {
 *     override fun onClosed() = startNextLevel()
 * })
 * ```
 *
 * The interruption rules — nothing during a cold start, nothing back to back,
 * a ceiling per session, a close control after a few seconds — are enforced
 * inside, not left to the integration. They are what separates a format a
 * publisher keeps from one they remove.
 */
public class SanbukFullscreen internal constructor(
    private val ad: SanbukAd,
    private val rewarded: Boolean,
) {

    /** What the publisher's app wants to know. Every method is optional. */
    public interface Callbacks {
        /** On screen. From here the interruption is real, so pause your game. */
        public fun onShown() {}

        /** Dismissed. Resume. Always called, whether or not a reward was earned. */
        public fun onClosed() {}

        /**
         * Earned — rewarded ads only, once, and only after the person stayed
         * long enough. Grant the coins here, not in [onClosed].
         */
        public fun onReward() {}

        /** Could not be shown; [reason] says which rule refused it. */
        public fun onRefused(reason: String) {}
    }

    /**
     * Put it on screen. Silently refuses when the moment is wrong — check
     * [Callbacks.onRefused] rather than assuming a show always happens.
     */
    @JvmOverloads
    public fun show(
        context: Context,
        style: SanbukStyle = SanbukStyle(),
        callbacks: Callbacks? = null,
    ) {
        val session = Sanbuk.currentSession()
        if (session == null) {
            Worker.onMain { callbacks?.onRefused("not_initialised") }
            return
        }

        // Rewarded is exempt on purpose: the person asked for this ad, and
        // refusing it because they already saw two is refusing them the reward
        // they came for.
        if (!rewarded) {
            val refusal = session.fullscreenPolicy.refusalFor(
                nowMillis = SystemClock.uptimeMillis(),
                sessionStartedAtMillis = session.sessionStartedAtMillis,
            )
            if (refusal != null) {
                Worker.onMain { callbacks?.onRefused(refusal.name.lowercase()) }
                return
            }
        }

        val token = FullscreenHost.offer(
            FullscreenHost.Pending(
                ad = ad,
                style = style,
                rewarded = rewarded,
                callbacks = callbacks,
                policy = session.fullscreenPolicy,
            ),
        )

        val started = runCatching {
            context.startActivity(
                Intent(context, FullscreenActivity::class.java)
                    .putExtra(FullscreenActivity.EXTRA_TOKEN, token)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse { false }

        if (!started) {
            // The activity is declared in this library's manifest, so this
            // means the publisher stripped it or the context cannot start one.
            // Better a named refusal than a silent nothing.
            FullscreenHost.discard(token)
            Worker.onMain { callbacks?.onRefused("activity_unavailable") }
        }
    }

    /**
     * The same show, for a caller that wants the callbacks and the default
     * style.
     *
     * @JvmOverloads fills in arguments from the right, so it can produce
     * show(context) and show(context, style) but never show(context,
     * callbacks) — and callbacks are not optional for a rewarded ad, where
     * onReward is the entire point. Unity and React Native reach the SDK by
     * JVM signature and cannot use named arguments, so without this they had
     * to construct a style they did not want in order to learn whether the
     * player earned anything.
     */
    public fun show(context: Context, callbacks: Callbacks) {
        show(context, SanbukStyle(), callbacks)
    }

    public companion object {

        /** An interstitial: shown when the app decides, gated by the rules above. */
        @JvmStatic
        public fun load(context: Context, placementCode: String, onResult: (SanbukFullscreen?) -> Unit) {
            request(context, placementCode, rewarded = false, onResult = onResult)
        }

        /**
         * A rewarded ad: the person asked for it, so the interruption rules do
         * not apply — refusing an ad somebody requested is refusing them their
         * reward. The duration requirement applies instead.
         */
        @JvmStatic
        public fun loadRewarded(context: Context, placementCode: String, onResult: (SanbukFullscreen?) -> Unit) {
            request(context, placementCode, rewarded = true, onResult = onResult)
        }

        private fun request(
            context: Context,
            placementCode: String,
            rewarded: Boolean,
            onResult: (SanbukFullscreen?) -> Unit,
        ) {
            // DEFAULT: we draw it and our own gate decides the view — which is
            // the truth for a full-screen ad and must not be claimed otherwise.
            Sanbuk.loadAd(context, placementCode, Sanbuk.RenderStyle.DEFAULT) { ad ->
                onResult(ad?.let { SanbukFullscreen(it, rewarded) })
            }
        }
    }
}
