package ir.sanbuk.sdk.internal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Opens the click.
 *
 * This is the single most consequential function in the SDK. Sanbuk pays on
 * conversions, and a conversion happens outside the publisher's app — on the
 * advertiser's site, in the advertiser's browser session. An in-app WebView
 * has its own cookie jar, so the first-party click id does not survive, the
 * visitor is logged out of a shop they are a customer of, and the advertiser's
 * pixel never attributes the sale. The publisher does the work and earns
 * nothing, silently.
 *
 * So: a Custom Tab, which is the real browser wearing an in-app coat. Failing
 * that, the plain browser. Never a WebView.
 *
 * The Custom Tab is launched by intent extras rather than through
 * androidx.browser — the library is a fine piece of code, but this is the only
 * thing we would use it for, and shipping zero dependencies is worth more to a
 * publisher than the few lines it saves us.
 */
internal object ClickLauncher {

    fun open(context: Context, url: String, toolbarColor: Int?): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false

        if (launch(context, customTabIntent(uri, toolbarColor))) return true
        // No browser supports Custom Tabs on this device — a plain view still
        // lands the visitor in a real browser, which is what attribution needs.
        return launch(context, Intent(Intent.ACTION_VIEW, uri))
    }

    private fun customTabIntent(uri: Uri, toolbarColor: Int?): Intent =
        Intent(Intent.ACTION_VIEW, uri).apply {
            // A null session is the documented way to ask for a Custom Tab
            // without holding a connection to the browser.
            putExtras(Bundle().apply { putBinder(EXTRA_SESSION, null) })
            // An early return here would take the title extra with it — the
            // colour is the optional one, not everything after it.
            toolbarColor?.let { putExtra(EXTRA_TOOLBAR_COLOR, it) }
            putExtra(EXTRA_SHOW_TITLE, true)
        }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        // A publisher may hand us an application context, which cannot start
        // an activity without this flag.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrElse {
        // A device with no browser at all is rare but real. It costs this one
        // click, never the app — and the caller falls through to the next
        // intent, which is why this reports failure rather than swallowing it.
        false
    }

    private const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
    private const val EXTRA_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR"
    private const val EXTRA_SHOW_TITLE = "android.support.customtabs.extra.TITLE_VISIBILITY"
}
