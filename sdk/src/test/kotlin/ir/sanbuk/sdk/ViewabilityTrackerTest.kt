package ir.sanbuk.sdk

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import ir.sanbuk.sdk.internal.ViewabilityTracker
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The regression this class exists for.
 *
 * A pre-draw listener reports a change of geometry; it cannot report the
 * passage of time. On a screen nobody touches, Android stops running draw
 * passes — so an ad that simply sits there, fully visible, produced two
 * samples and then silence, never reached its continuous second, and was never
 * reported. The most ordinary case there is, paying nothing.
 */
@RunWith(RobolectricTestRunner::class)
class ViewabilityTrackerTest {

    private var now = 0L
    private var seen = 0

    private fun attachedView(): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = View(activity)
        val root = FrameLayout(activity)
        root.addView(view, 600, 200)
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 600, 200)
        return view
    }

    private fun tracker(view: View) =
        ViewabilityTracker(view, clock = { now }) { seen++ }

    /** Time passes with no draw pass at all — exactly the case that failed. */
    @Test
    fun `an ad that just sits on screen is still counted`() {
        val view = attachedView()
        tracker(view).start()

        advance(1_400)

        assertTrue(seen == 1, "a fully visible, motionless ad must be reported")
    }

    @Test
    fun `it is not counted before the second is up`() {
        val view = attachedView()
        tracker(view).start()

        advance(600)

        assertFalse(seen > 0, "half a second is not a view")
    }

    /** One drawn ad is one impression, however long it stays up. */
    @Test
    fun `the poll stops once the view has counted`() {
        val view = attachedView()
        tracker(view).start()

        advance(5_000)

        assertTrue(seen == 1, "reported $seen times")
    }

    @Test
    fun `stopping ends the sampling`() {
        val view = attachedView()
        val tracker = tracker(view)
        tracker.start()
        advance(400)

        tracker.stop()
        advance(5_000)

        assertFalse(seen > 0, "a stopped tracker must not keep counting")
    }

    /** Advances both the looper and the clock the rule reads. */
    private fun advance(millis: Long) {
        val step = 100L
        repeat((millis / step).toInt()) {
            now += step
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(step))
        }
    }
}
