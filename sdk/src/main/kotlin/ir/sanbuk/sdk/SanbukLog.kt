package ir.sanbuk.sdk

import android.util.Log

/**
 * Says things out loud only when the publisher asked.
 *
 * A silent SDK is impossible to integrate and a chatty one is a nuisance in
 * somebody else's logcat, so debug mode is the switch and the tag is ours
 * alone. Errors are logged, never thrown — the whole point.
 */
internal object SanbukLog {

    @Volatile
    internal var enabled: Boolean = false

    fun debug(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        if (enabled) Log.w(TAG, message, error)
    }

    private const val TAG = "Sanbuk"
}
