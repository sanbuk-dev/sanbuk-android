package ir.sanbuk.sdk.internal

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * One background thread for everything this SDK does, and one way back.
 *
 * A single thread rather than a pool: the work is a handful of small requests
 * and some file writes, and serialising them removes every question about two
 * threads touching the queue at once. A pool would buy concurrency nobody
 * asked for and a class of bug nobody wants.
 *
 * An ANR during cold start is the fastest way to be uninstalled, so nothing —
 * not even reading a preference — happens on the caller's thread.
 */
internal object Worker {

    private val main = Handler(Looper.getMainLooper())

    private val executor = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable ->
            Thread(runnable, "sanbuk-sdk").apply {
                isDaemon = true
                // Below the app's own work: an advertisement must never be the
                // reason a publisher's screen janks.
                priority = Thread.MIN_PRIORITY
            }
        },
    )

    /** Runs off the main thread. A throw is swallowed here rather than reaching the app. */
    fun background(block: () -> Unit) {
        runCatching { executor.execute { runCatching(block) } }
    }

    /** Hands a result back to the thread the publisher's UI lives on. */
    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching(block)
        } else {
            main.post { runCatching(block) }
        }
    }
}
