package ir.sanbuk.sdk

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ir.sanbuk.sdk.core.Ad
import ir.sanbuk.sdk.internal.FullscreenActivity
import ir.sanbuk.sdk.internal.FullscreenHost
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FullscreenActivityTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private var shown = 0
    private var closed = 0
    private var rewarded = 0
    private var refused = mutableListOf<String>()

    private val callbacks = object : SanbukFullscreen.Callbacks {
        override fun onShown() { shown++ }
        override fun onClosed() { closed++ }
        override fun onReward() { rewarded++ }
        override fun onRefused(reason: String) { refused += reason }
    }

    @BeforeTest
    fun setUp() {
        Sanbuk.init(app, SanbukConfig(mediaCode = "TEST-MEDIA"))
    }

    @AfterTest
    fun tearDown() {
        Sanbuk.reset()
    }

    private fun ad() = SanbukAd(
        Ad(
            linkCode = "abc123",
            format = "interstitial",
            campaignId = "cmp-1",
            creativeId = "crv-1",
            headline = "تخفیف پاییزه",
            description = "تا ۵۰ درصد",
            cta = "buy",
            ctaLabel = "خرید",
            brandColor = "#060ADB",
            imageUrl = null,
            logoUrl = null,
            clickUrl = "https://t.sanbuk.com/c/abc123",
            impressionUrl = "https://t.sanbuk.com/i/abc123",
            frequencyWindowHours = 24,
        ),
        requireNotNull(Sanbuk.currentSession()),
    )

    private fun launch(rewardedAd: Boolean = false): ActivityController<FullscreenActivity> {
        val token = FullscreenHost.offer(
            FullscreenHost.Pending(
                ad(), SanbukStyle(), rewardedAd, callbacks,
                requireNotNull(Sanbuk.currentSession()).fullscreenPolicy,
            ),
        )
        val intent = Intent(app, FullscreenActivity::class.java)
            .putExtra(FullscreenActivity.EXTRA_TOKEN, token)
        return Robolectric.buildActivity(FullscreenActivity::class.java, intent).setup()
    }

    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    /** Found by what it says, so the production code needs no test-only hook. */
    private fun closeButton(activity: FullscreenActivity): TextView? =
        find(activity.window.decorView) { it is TextView && it.text == "✕" } as? TextView

    private fun find(view: View, match: (View) -> Boolean): View? {
        if (match(view)) return view
        if (view !is android.view.ViewGroup) return null
        for (index in 0 until view.childCount) {
            find(view.getChildAt(index), match)?.let { return it }
        }
        return null
    }

    /**
     * An ad that can be dismissed in the first half second is an ad nobody was
     * paid for; one that cannot be dismissed at all is a hostage situation.
     */
    @Test
    fun `the close control appears only after the gate`() {
        val activity = launch().get()

        assertEquals(View.GONE, closeButton(activity)?.visibility)

        advance(5_200)

        assertEquals(View.VISIBLE, closeButton(activity)?.visibility)
    }

    @Test
    fun `back does nothing until the ad has been on screen`() {
        val controller = launch()
        val activity = controller.get()

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertFalse(activity.isFinishing, "an ad dismissed instantly was never seen")

        advance(5_200)
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    /** Granted when it is earned, not on close: leaving at second sixteen still watched it. */
    @Test
    fun `a rewarded ad pays out once the person stayed`() {
        launch(rewardedAd = true)

        advance(14_000)
        assertEquals(0, rewarded, "not yet earned")

        advance(2_000)
        assertEquals(1, rewarded)

        advance(30_000)
        assertEquals(1, rewarded, "one ad, one reward")
    }

    @Test
    fun `an interstitial never pays a reward`() {
        launch(rewardedAd = false)

        advance(30_000)

        assertEquals(0, rewarded)
    }

    /** The publisher resumes their game on this, so it has to arrive exactly once. */
    @Test
    fun `closing reports once, however the screen goes away`() {
        val controller = launch()
        advance(5_200)

        closeButton(controller.get())?.performClick()
        controller.pause().stop().destroy()

        assertEquals(1, closed)
        assertEquals(1, shown)
    }

    @Test
    fun `an ad dropped by the system still reports closed`() {
        val controller = launch()

        controller.pause().stop().destroy()

        assertEquals(1, closed, "a publisher waiting to resume must not wait forever")
    }

    /**
     * The process was restarted, or something that is not us started this
     * activity. A blank black screen would be worse than never appearing.
     */
    @Test
    fun `an unknown token closes the screen instead of showing nothing`() {
        val intent = Intent(app, FullscreenActivity::class.java)
            .putExtra(FullscreenActivity.EXTRA_TOKEN, 999_999L)

        val activity = Robolectric.buildActivity(FullscreenActivity::class.java, intent).setup().get()

        assertTrue(activity.isFinishing)
    }

    /**
     * The gate is a statement about a person's afternoon, so it has to outlive
     * any one screen: a policy built per show would count to one forever and
     * enforce nothing at all.
     */
    @Test
    fun `a second interstitial straight after the first is refused`() {
        val session = requireNotNull(Sanbuk.currentSession())
        // Past the launch quiet window, as any real show would be.
        advance(31_000)
        session.fullscreenPolicy.recordShown(android.os.SystemClock.uptimeMillis())

        SanbukFullscreen(ad(), rewarded = false).show(app, SanbukStyle(), callbacks)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("too_soon_after_last"), refused)
        assertEquals(null, shadowOf(app).nextStartedActivity, "no screen should have been started")
    }

    /** Refusing an ad somebody asked for is refusing them their reward. */
    @Test
    fun `a rewarded ad is not subject to the interstitial gate`() {
        val session = requireNotNull(Sanbuk.currentSession())
        advance(31_000)
        session.fullscreenPolicy.recordShown(android.os.SystemClock.uptimeMillis())

        SanbukFullscreen(ad(), rewarded = true).show(app, SanbukStyle(), callbacks)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(refused.isEmpty(), "refused: $refused")
    }

    /** Claimed once: a second start on the same token finds nothing. */
    @Test
    fun `a pending ad is handed over exactly once`() {
        val token = FullscreenHost.offer(
            FullscreenHost.Pending(
                ad(), SanbukStyle(), false, callbacks,
                requireNotNull(Sanbuk.currentSession()).fullscreenPolicy,
            ),
        )

        assertTrue(FullscreenHost.claim(token) != null)
        assertTrue(FullscreenHost.claim(token) == null, "nothing may linger in a static map")
    }
}
