package ir.sanbuk.sdk

import android.content.Context
import ir.sanbuk.sdk.internal.Worker

/**
 * The publisher's entry point.
 *
 * ```kotlin
 * Sanbuk.init(context, mediaCode = "YOUR-MEDIA-CODE")
 *
 * Sanbuk.loadAd(context, "HOME-TOP") { ad ->
 *     if (ad != null) drawItYourself(ad) // or use SanbukAdView
 * }
 * ```
 *
 * Nothing here decides anything about which ad appears. Matching, pacing,
 * frequency caps, budgets, trial periods and every publisher rule live on the
 * server and stay there: a version shipped to phones is frozen for months, so
 * a rule hardened into it would outlive several changes of mind.
 */
public object Sanbuk {

    // Lint sees a Context reachable from a static field and calls it a leak.
    // It is the application context — narrowed twice, here and again inside
    // the session — which lives as long as the process does and cannot leak a
    // screen. The alternative, making every caller thread a session handle
    // through their own code, would be a worse API for a false positive.
    @Suppress("StaticFieldLeak")
    @Volatile
    private var session: SanbukSession? = null

    /**
     * Once, at startup, from anywhere. Returns immediately: the session is
     * built here but touches no disk until it is on the worker thread, and
     * this is called from Application.onCreate where every millisecond is
     * charged to the publisher's cold start.
     */
    public fun init(context: Context, config: SanbukConfig) {
        // Two threads racing to initialise would otherwise build two sessions
        // over one queue file. Cheap: contended once per process at most.
        synchronized(this) {
            if (session != null) return
            session = SanbukSession(context.applicationContext, config)
        }
    }

    public fun init(context: Context, mediaCode: String) {
        init(context, SanbukConfig(mediaCode = mediaCode))
    }

    /**
     * The same, with diagnostics — the one option a shell needs and the only
     * one it cannot reach.
     *
     * [SanbukConfig] carries the tracker URL between the media code and this
     * flag, and Kotlin default arguments fill in from the right, so no
     * generated constructor takes a code and a flag without a URL in between.
     * A shell reaching this SDK over JNI cannot use named arguments, which left
     * Unity and React Native choosing between no diagnostics at all and
     * hard-coding our own endpoint into their build — where it would quietly
     * stop matching ours.
     */
    public fun init(context: Context, mediaCode: String, debug: Boolean) {
        init(context, SanbukConfig(mediaCode = mediaCode, debug = debug))
    }

    public val isInitialised: Boolean get() = session != null

    /**
     * Ask for an ad. The callback runs on the main thread, exactly once.
     *
     * A null ad is a normal answer, not a failure: no campaign matched, the
     * budget is spent, the slot is capped for today. Collapse the space or
     * draw your own content — never treat it as an error.
     */
    public fun loadAd(
        context: Context,
        placementCode: String,
        onResult: (SanbukAd?) -> Unit,
    ) {
        // Always CUSTOM from here: whoever calls this draws the ad themselves,
        // and telling the server otherwise would claim our viewability gate
        // decided a view it never saw. SanbukAdView has its own way in.
        loadAd(context, placementCode, RenderStyle.CUSTOM, onResult)
    }

    internal fun loadAd(
        context: Context,
        placementCode: String,
        render: RenderStyle,
        onResult: (SanbukAd?) -> Unit,
    ) {
        val active = session
        if (active == null) {
            // Not initialised is a mistake, but not one worth a crash inside
            // somebody's list adapter. Delivered the same way as every other
            // answer — a callback that sometimes runs on the caller's stack
            // and sometimes later is the kind of thing that only breaks in
            // production.
            SanbukLog.warn("Sanbuk.init() was never called — no ad will be requested")
            Worker.onMain { onResult(null) }
            return
        }
        active.loadAd(context, placementCode, render, onResult)
    }

    /**
     * Which half of the contract drew the ad — and therefore whose judgement
     * decided the view was real. Not a caller's choice: the SDK sets it from
     * the path taken, because the server has no way to check it.
     */
    internal enum class RenderStyle { DEFAULT, CUSTOM }

    internal fun currentSession(): SanbukSession? = session

    /** Test seam: lets a test install a session without a real Context. */
    internal fun reset() {
        session = null
    }
}
