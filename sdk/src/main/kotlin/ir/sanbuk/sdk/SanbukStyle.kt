package ir.sanbuk.sdk

import android.graphics.Color

/**
 * How the built-in renderer should look.
 *
 * The middle road between "draw everything yourself" and "take what you are
 * given". Anything left null falls back to the creative's own brand colour or
 * a Sanbuk default, so an advertiser's identity survives by default while an
 * app that cares can pull the ad towards its own theme.
 *
 * Most publishers stop here and never touch [Sanbuk.loadAd].
 *
 * Colours are plain ARGB ints rather than @ColorInt-annotated ones: the
 * annotation would pull in androidx.annotation, and this SDK ships with no
 * runtime dependencies at all.
 */
// @JvmOverloads so Java — and every shell that reaches this SDK over JNI —
// gets a no-argument constructor. Kotlin default arguments produce none on
// their own, which left Unity unable to build a default style and therefore
// unable to reach any overload that takes one.
public data class SanbukStyle @JvmOverloads constructor(
    public val backgroundColor: Int? = null,
    public val textColor: Int? = null,
    public val secondaryTextColor: Int? = null,
    /** Null means the creative's own brand colour — the advertiser chose it. */
    public val ctaBackgroundColor: Int? = null,
    public val ctaTextColor: Int? = null,
    public val cornerRadiusDp: Float = 8f,
    public val ctaCornerRadiusDp: Float = 6f,
    public val titleSizeSp: Float = 15f,
    public val bodySizeSp: Float = 13f,
    public val showLogo: Boolean = true,
    /** Where the «آگهی» label sits. That it is shown at all is not optional. */
    public val disclosureCorner: DisclosureCorner = DisclosureCorner.TOP_START,
) {

    public enum class DisclosureCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

    public companion object {
        internal const val DEFAULT_BRAND_COLOR: Int = 0xFF060ADB.toInt()
        internal const val DEFAULT_BACKGROUND: Int = Color.WHITE
        internal const val DEFAULT_TEXT: Int = 0xFF1A1A1A.toInt()
        internal const val DEFAULT_SECONDARY_TEXT: Int = 0xFF6B7280.toInt()
    }
}
