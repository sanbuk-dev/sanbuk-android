package ir.sanbuk.sdk.internal

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ir.sanbuk.sdk.SanbukAd
import ir.sanbuk.sdk.SanbukFullscreen
import ir.sanbuk.sdk.SanbukStyle
import ir.sanbuk.sdk.core.FullscreenPolicy
import ir.sanbuk.sdk.core.RewardPolicy

/**
 * The screen a full-screen ad lives on.
 *
 * Its own activity rather than a dialog over the publisher's: an ad that is
 * part of somebody else's activity inherits their lifecycle, their back
 * handling and their configuration changes, and every one of those is a way to
 * leave a half-dismissed ad on screen. A separate activity is also what makes
 * "the app is paused while this is up" true, which is what a game needs.
 *
 * Built in code, with framework widgets and a framework theme — no appcompat,
 * no layout resources, nothing that can collide with a publisher's own.
 */
internal class FullscreenActivity : Activity() {

    private var ad: SanbukAd? = null
    private var callbacks: SanbukFullscreen.Callbacks? = null
    private var rewarded = false

    /** The session's, not ours: a per-screen policy would count to one forever. */
    private var policy = FullscreenPolicy()
    private val reward = RewardPolicy()

    private var shownAtMillis = 0L
    private var closeButton: TextView? = null
    private var notice: TextView? = null
    private var closed = false

    private val tick = object : Runnable {
        override fun run() {
            if (isFinishing) return
            onTick()
            runCatching { window.decorView.postDelayed(this, TICK_MS) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entry = FullscreenHost.claim(intent.getLongExtra(EXTRA_TOKEN, -1))
        if (entry == null) {
            // Nothing to show — the process was restarted, or this activity was
            // started by something that is not us. Leaving a blank screen up
            // would be worse than not appearing at all.
            finish()
            return
        }

        ad = entry.ad
        callbacks = entry.callbacks
        rewarded = entry.rewarded
        policy = entry.policy
        // Uptime, not wall clock: the same source postDelayed schedules on,
        // and one nobody can wind forward to skip the close gate or collect a
        // reward they did not wait for.
        shownAtMillis = SystemClock.uptimeMillis()

        setContentView(build(entry.ad, entry.style))

        // The whole screen is the ad, so the view gate is about duration only —
        // and it is the same gate the banner uses, so one drawn ad is one
        // impression wherever it was drawn.
        entry.ad.recordImpression()
        callbacks?.onShown()
        policy.recordShown(shownAtMillis)

        runCatching { window.decorView.postDelayed(tick, TICK_MS) }
    }

    private fun build(ad: SanbukAd, style: SanbukStyle): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(style.backgroundColor ?: Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(48f), dp(24f), dp(32f))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            visibility = View.GONE
        }
        column.addView(image)

        ad.headline?.let { column.addView(text(it, 22f, Color.WHITE, bold = true)) }
        ad.body?.let { column.addView(text(it, 15f, 0xFFBFC5D2.toInt(), bold = false)) }

        column.addView(
            TextView(this).apply {
                text = ad.callToAction
                setTextColor(style.ctaTextColor ?: Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                gravity = Gravity.CENTER
                setPadding(dp(24f), dp(14f), dp(24f), dp(14f))
                background = GradientDrawable().apply {
                    setColor(style.ctaBackgroundColor ?: ad.brandColor ?: SanbukStyle.DEFAULT_BRAND_COLOR)
                    cornerRadius = dp(style.ctaCornerRadiusDp).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(20f) }
                setOnClickListener { this@FullscreenActivity.ad?.click(this@FullscreenActivity) }
            },
        )

        root.addView(column)
        root.addView(disclosure())
        root.addView(buildCloseButton())
        root.addView(buildNotice())

        loadImage(ad, image)
        return root
    }

    private fun buildCloseButton(): TextView = TextView(this).apply {
        text = "✕"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(0x99000000.toInt())
            cornerRadius = dp(18f).toFloat()
        }
        layoutParams = FrameLayout.LayoutParams(dp(36f), dp(36f)).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        // Hidden until earned. Present but disabled would just be a button
        // people tap at repeatedly.
        visibility = View.GONE
        setOnClickListener { dismiss() }
        closeButton = this
    }

    /** The countdown, and — for a rewarded ad — what leaving early would cost. */
    private fun buildNotice(): TextView = TextView(this).apply {
        setTextColor(0xFFBFC5D2.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
        layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(12f), dp(16f), dp(12f), dp(12f))
        }
        notice = this
    }

    private fun disclosure(): TextView = TextView(this).apply {
        text = ad?.disclosureLabel ?: "آگهی"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
        background = GradientDrawable().apply {
            setColor(0x99000000.toInt())
            cornerRadius = dp(3f).toFloat()
        }
        layoutParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(dp(12f), dp(12f), dp(12f), dp(12f))
        }
    }

    private fun onTick() {
        val now = SystemClock.uptimeMillis()

        if (rewarded && reward.onProgress(shownAtMillis, now)) {
            // Granted the moment it is earned, not on close: a person who
            // switches apps at second sixteen still watched the ad.
            callbacks?.onReward()
        }

        if (policy.closeAllowed(shownAtMillis, now)) {
            closeButton?.visibility = View.VISIBLE
        }

        notice?.text = noticeText(now)
        notice?.visibility = if (notice?.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun noticeText(now: Long): String = when {
        rewarded && !reward.hasEarned() ->
            "${(reward.millisRemaining(shownAtMillis, now) / 1000) + 1} ثانیه تا دریافت جایزه"
        !policy.closeAllowed(shownAtMillis, now) ->
            "${(policy.millisUntilClose(shownAtMillis, now) / 1000) + 1} ثانیه"
        else -> ""
    }

    /**
     * Back is the close button by another name, so it obeys the same gate.
     * Before that it does nothing at all — an ad that can be dismissed in the
     * first half second is an ad nobody was paid for.
     */
    @Deprecated("onBackPressedDispatcher needs androidx; this SDK ships no dependencies")
    override fun onBackPressed() {
        if (policy.closeAllowed(shownAtMillis, SystemClock.uptimeMillis())) dismiss()
    }

    private fun dismiss() {
        if (closed) return
        closed = true
        runCatching { window.decorView.removeCallbacks(tick) }
        callbacks?.onClosed()
        finish()
    }

    override fun onDestroy() {
        // Whatever route out was taken — the close button, back, or the system
        // reclaiming the activity — the publisher is told exactly once and
        // nothing keeps a reference to their callbacks afterwards.
        runCatching { window.decorView.removeCallbacks(tick) }
        if (!closed) {
            closed = true
            callbacks?.onClosed()
        }
        callbacks = null
        ad = null
        super.onDestroy()
    }

    private fun loadImage(ad: SanbukAd, target: ImageView) {
        val url = ad.imageUrl ?: ad.logoUrl ?: return
        ImageLoader.cached(url)?.let {
            target.setImageBitmap(it)
            target.visibility = View.VISIBLE
            return
        }
        Worker.background {
            val bitmap = ImageLoader.load(url) ?: return@background
            Worker.onMain {
                if (isFinishing || isDestroyed) return@onMain
                target.setImageBitmap(bitmap)
                target.visibility = View.VISIBLE
            }
        }
    }

    private fun text(value: String, sizeSp: Float, colour: Int, bold: Boolean) = TextView(this).apply {
        text = value
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12f) }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    companion object {
        const val EXTRA_TOKEN = "ir.sanbuk.sdk.fullscreen.token"
        private const val TICK_MS = 250L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
