package ir.sanbuk.sdk

import android.content.Context
import ir.sanbuk.sdk.core.Ad

/**
 * One ad, ready to be drawn by whoever wants to draw it.
 *
 * The fields are the whole creative — no markup, no webview, no rendered
 * banner. An app that wants the ad to look like part of itself reads them and
 * draws with its own views; an app that would rather not hands the placement
 * to [SanbukAdView] and gets the same data drawn for it.
 *
 * Two calls carry obligations rather than just behaviour:
 *
 * - [recordImpression] must fire when the ad was really on screen, not when it
 *   was created. Under [SanbukAdView] that judgement is made for you against
 *   the MRC bar; here it is yours, and it cannot be verified from the server.
 * - the «آگهی» disclosure in [disclosureLabel] has to appear somewhere on the
 *   ad. Store policy asks for it, advertisers expect it, and a reader deserves
 *   to know. [SanbukAdView] draws it automatically.
 */
public class SanbukAd internal constructor(
    internal val ad: Ad,
    private val session: SanbukSession,
) {

    public val headline: String? get() = ad.headline
    public val body: String? get() = ad.description
    public val callToAction: String get() = ad.ctaLabel
    public val imageUrl: String? get() = ad.imageUrl
    public val logoUrl: String? get() = ad.logoUrl

    /** ARGB, the advertiser's own colour — null when they did not choose one. */
    public val brandColor: Int? get() = parseColor(ad.brandColor)

    /** banner | native | interstitial | rewarded, as the publisher registered the slot. */
    public val format: String get() = ad.format

    public val campaignId: String get() = ad.campaignId

    /** The word that has to appear on any ad this app draws itself. */
    public val disclosureLabel: String get() = DISCLOSURE

    // Volatile because a publisher drawing the ad themselves may well call
    // this from wherever their rendering happens, and counting one view twice
    // is money.
    @Volatile
    private var impressionRecorded = false

    /**
     * Report that this ad was seen. Safe to call more than once — the second
     * call does nothing, and a retry from the offline queue is recognised by
     * the server rather than counted again.
     */
    public fun recordImpression() {
        if (impressionRecorded) return
        impressionRecorded = true
        session.recordImpression(ad)
    }

    /**
     * Open the destination. Always through the tracker URL and always in a
     * real browser — following the redirect ourselves would spend the click
     * and break the attribution the publisher is paid on.
     */
    public fun click(context: Context) {
        session.openClick(context, ad, brandColor)
    }

    private fun parseColor(value: String?): Int? {
        if (value.isNullOrEmpty()) return null
        return runCatching { android.graphics.Color.parseColor(value) }.getOrNull()
    }

    private companion object {
        const val DISCLOSURE = "آگهی"
    }
}
