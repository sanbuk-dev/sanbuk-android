package ir.sanbuk.sdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ir.sanbuk.sdk.internal.ImageLoader
import ir.sanbuk.sdk.internal.ViewabilityTracker
import ir.sanbuk.sdk.internal.Worker

/**
 * The ad, drawn for you.
 *
 * Exists so that a publisher can see revenue before spending an afternoon on
 * UI — not because drawing it yourself is the lesser path. It renders the same
 * data [Sanbuk.loadAd] hands you, and everything it does that you would have
 * to remember is the reason to reach for it first:
 *
 * - the impression is reported only once at least half the ad has been on
 *   screen for a continuous second, so the number an advertiser pays against
 *   is one a person could actually have seen;
 * - the «آگهی» label is always drawn;
 * - the click opens a real browser, which is what keeps attribution — and the
 *   publisher's revenue — intact.
 *
 * Built in code rather than from layout XML: no resources to collide with a
 * publisher's own, nothing to keep in sync across configurations, and a
 * smaller artifact.
 */
public class SanbukAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var style: SanbukStyle = SanbukStyle()
    private var loaded: SanbukAd? = null
    private var tracker: ViewabilityTracker? = null

    /**
     * Which request this view is currently waiting for.
     *
     * A row in a RecyclerView is loaded, recycled and loaded again in the time
     * one request takes. Without this, the first response can land after the
     * second and paint the wrong advertiser into a slot that has already moved
     * on — and then count an impression for it.
     */
    private var generation = 0

    /** Told when the slot came back empty, so the app can collapse the space. */
    public var onEmpty: (() -> Unit)? = null

    /**
     * Ask for an ad and draw it. Safe to call again — a second call replaces
     * whatever is showing, which is what a recycled list row needs.
     */
    @JvmOverloads
    public fun load(placementCode: String, style: SanbukStyle = SanbukStyle()) {
        this.style = style
        clear()

        val requested = ++generation
        Sanbuk.loadAd(context, placementCode, Sanbuk.RenderStyle.DEFAULT) { ad ->
            if (requested != generation) return@loadAd
            if (ad == null) {
                onEmpty?.invoke()
                return@loadAd
            }
            loaded = ad
            render(ad)
        }
    }

    private fun clear() {
        tracker?.stop()
        tracker = null
        loaded = null
        removeAllViews()
        // An empty slot must not keep swallowing taps. render() sets both of
        // these; clearing the children without clearing them leaves an
        // invisible box that eats the touch meant for whatever is underneath.
        setOnClickListener(null)
        isClickable = false
    }

    private fun render(ad: SanbukAd) {
        removeAllViews()

        val brand = ad.brandColor ?: SanbukStyle.DEFAULT_BRAND_COLOR
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(style.backgroundColor ?: SanbukStyle.DEFAULT_BACKGROUND)
                cornerRadius = dp(style.cornerRadiusDp)
            }
            setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(8f).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val thumbnail = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(56f).toInt(), dp(56f).toInt()).apply {
                marginEnd = dp(10f).toInt()
            }
            visibility = View.GONE
        }
        card.addView(thumbnail)

        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        ad.headline?.let { text.addView(label(it, style.titleSizeSp, style.textColor ?: SanbukStyle.DEFAULT_TEXT, bold = true)) }
        ad.body?.let { text.addView(label(it, style.bodySizeSp, style.secondaryTextColor ?: SanbukStyle.DEFAULT_SECONDARY_TEXT, bold = false)) }
        card.addView(text)

        card.addView(callToActionButton(ad, brand))
        addView(card)
        addView(disclosure())

        // The ad is one click target, not a set of them: a person tapping the
        // headline means the same thing as a person tapping the button.
        setOnClickListener { loaded?.click(context) }
        isClickable = true

        loadThumbnail(ad, thumbnail)
        startCounting(ad)
    }

    private fun callToActionButton(ad: SanbukAd, brand: Int): TextView = TextView(context).apply {
        text = ad.callToAction
        setTextColor(style.ctaTextColor ?: Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.bodySizeSp)
        setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
        background = GradientDrawable().apply {
            setColor(style.ctaBackgroundColor ?: brand)
            cornerRadius = dp(style.ctaCornerRadiusDp)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(8f).toInt() }
    }

    /**
     * Not optional and not configurable in its existence — only in where it
     * sits. An ad a reader cannot tell from the app's own content is a problem
     * for the store listing, for the advertiser, and for the reader.
     */
    private fun disclosure(): TextView = TextView(context).apply {
        text = loaded?.disclosureLabel ?: "آگهی"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        setPadding(dp(4f).toInt(), dp(1f).toInt(), dp(4f).toInt(), dp(1f).toInt())
        background = GradientDrawable().apply {
            setColor(0x99000000.toInt())
            cornerRadius = dp(3f)
        }
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = when (style.disclosureCorner) {
                SanbukStyle.DisclosureCorner.TOP_START -> Gravity.TOP or Gravity.START
                SanbukStyle.DisclosureCorner.TOP_END -> Gravity.TOP or Gravity.END
                SanbukStyle.DisclosureCorner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                SanbukStyle.DisclosureCorner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }
            val inset = dp(4f).toInt()
            setMargins(inset, inset, inset, inset)
        }
    }

    private fun label(value: String, sizeSp: Float, colour: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            setTextColor(colour)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            maxLines = if (bold) 1 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun loadThumbnail(ad: SanbukAd, target: ImageView) {
        val url = (ad.imageUrl ?: ad.logoUrl.takeIf { style.showLogo }) ?: return

        ImageLoader.cached(url)?.let { return show(target, it, ad) }

        Worker.background {
            val bitmap = ImageLoader.load(url) ?: return@background
            Worker.onMain {
                // The row may have been recycled onto a different ad while the
                // image was in flight; drawing it then would put one
                // advertiser's picture on another's copy.
                if (loaded === ad) show(target, bitmap, ad)
            }
        }
    }

    private fun show(target: ImageView, bitmap: Bitmap, ad: SanbukAd) {
        if (loaded !== ad) return
        target.setImageBitmap(bitmap)
        target.visibility = View.VISIBLE
    }

    private fun startCounting(ad: SanbukAd) {
        tracker?.stop()
        tracker = ViewabilityTracker(this) { ad.recordImpression() }.also { it.start() }
    }

    override fun onDetachedFromWindow() {
        tracker?.stop()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // A row scrolled off and back on is the same ad, and its second in
        // view starts again from zero — which is the honest reading of "half
        // the pixels for a continuous second". The new tracker carries a fresh
        // rule, so what stops a second report is SanbukAd itself: one drawn ad
        // is one impression, however many times it passes the bar.
        loaded?.let { startCounting(it) }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
