package ir.sanbuk.sdk

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import ir.sanbuk.sdk.internal.ClickLauncher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The single most consequential function in the SDK: an in-app WebView here
 * would break first-party attribution, and in a CPA network broken attribution
 * means the publisher did the work and earns nothing.
 */
@RunWith(RobolectricTestRunner::class)
class ClickLauncherTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `opens the tracker url in a browser, never in the app`() {
        ClickLauncher.open(context, "https://t.sanbuk.com/c/abc123", toolbarColor = null)

        val started = shadowOf(context).nextStartedActivity
        assertNotNull(started, "a click must reach a browser")
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://t.sanbuk.com/c/abc123", started.data.toString())
    }

    /** The session extra is what turns a plain view into a Custom Tab. */
    @Test
    fun `asks for a custom tab rather than a bare browser window`() {
        ClickLauncher.open(context, "https://t.sanbuk.com/c/abc123", toolbarColor = 0xFF060ADB.toInt())

        val started = assertNotNull(shadowOf(context).nextStartedActivity)
        assertTrue(started.extras?.containsKey("android.support.customtabs.extra.SESSION") == true)
        assertEquals(
            0xFF060ADB.toInt(),
            started.getIntExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0),
        )
    }

    /** An application context cannot start an activity without this flag. */
    @Test
    fun `survives being handed an application context`() {
        ClickLauncher.open(context, "https://t.sanbuk.com/c/abc123", toolbarColor = null)

        val started = assertNotNull(shadowOf(context).nextStartedActivity)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a malformed url costs the click, never the app`() {
        ClickLauncher.open(context, "", toolbarColor = null)
        ClickLauncher.open(context, "not a url at all", toolbarColor = null)
    }
}
