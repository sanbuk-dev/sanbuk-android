package ir.sanbuk.sdk.core

/**
 * One ad, described rather than drawn.
 *
 * The server never sends markup — no HTML, no ready-made banner, no webview.
 * That is the whole product decision behind this SDK: the publisher's app can
 * draw these fields with its own fonts, colours and animation and have the ad
 * look like part of the app, which no network that ships a rendered banner can
 * offer. Our own renderer is a convenience on top of the same data, never a
 * different path.
 */
public data class Ad(
    /** The tracking link behind this decision; every URL below hangs off it. */
    val linkCode: String,
    /** banner | native | interstitial | rewarded — chosen by the publisher when the slot was registered, not by the app. */
    val format: String,
    val campaignId: String,
    val creativeId: String,
    val headline: String?,
    val description: String?,
    val cta: String,
    /** The words on the button, already resolved — a custom CTA is the advertiser's own wording. */
    val ctaLabel: String,
    val brandColor: String?,
    val imageUrl: String?,
    val logoUrl: String?,
    /** Open this, never a destination resolved by following it: the redirect IS the click. */
    val clickUrl: String,
    val impressionUrl: String,
    /** How long the visitor's per-campaign view counters stay relevant. */
    val frequencyWindowHours: Int,
)

/**
 * What came back. An empty answer is a normal outcome, not a failure — the
 * publisher's screen simply shows nothing — so it is a value, not an
 * exception, and it carries the reason when the operator turned diagnostics on.
 */
public sealed interface AdResponse {

    public data class Filled(val ad: Ad) : AdResponse

    /**
     * @param reason the server's or the edge's own word for why, when
     *               diagnostics are on. Null in production, always.
     */
    public data class Empty(val reason: String? = null) : AdResponse
}
