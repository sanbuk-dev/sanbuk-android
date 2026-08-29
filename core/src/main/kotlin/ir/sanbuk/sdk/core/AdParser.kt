package ir.sanbuk.sdk.core

import org.json.JSONObject

/**
 * Reads the edge's answer.
 *
 * Never throws. A malformed body, a field that changed type, a truncated
 * response — all of them become an empty answer, because the alternative is an
 * exception crossing into a publisher's app for the sake of an advertisement.
 * The reason names the parse failure so a debug build can still see it.
 */
public object AdParser {

    public fun parse(body: String): AdResponse = runCatching {
        val root = JSONObject(body)
        val reason = root.optString("reason").takeIf { it.isNotEmpty() }

        // `ad: null` is the documented empty shape; optJSONObject returns null
        // both for an explicit null and for a missing key, which is the same
        // outcome to a caller.
        val ad = root.optJSONObject("ad") ?: return@runCatching AdResponse.Empty(reason)

        // optString would hand back the literal string "null" for an explicit
        // JSON null, and a link code of "null" is a 404 nobody can explain.
        val code = ad.optionalString("code")
            ?: return@runCatching AdResponse.Empty("malformed_ad")
        val clickUrl = ad.optionalString("click_url")
            ?: return@runCatching AdResponse.Empty("malformed_ad")
        val impressionUrl = ad.optionalString("impression_url")
            ?: return@runCatching AdResponse.Empty("malformed_ad")

        AdResponse.Filled(
            Ad(
                linkCode = code,
                format = ad.optionalString("format") ?: "banner",
                campaignId = ad.optionalString("campaign_id").orEmpty(),
                creativeId = ad.optionalString("creative_id").orEmpty(),
                headline = ad.optionalString("headline"),
                description = ad.optionalString("description"),
                cta = ad.optionalString("cta") ?: "view",
                ctaLabel = ad.optionalString("cta_label").orEmpty(),
                brandColor = ad.optionalString("brand_color"),
                imageUrl = ad.optionalString("image_url"),
                logoUrl = ad.optionalString("logo_url"),
                clickUrl = clickUrl,
                impressionUrl = impressionUrl,
                // A missing window is not a reason to stop capping; it is a
                // reason to fall back to the shipped default.
                frequencyWindowHours = ad.optInt("fc_hours", DEFAULT_FREQUENCY_WINDOW_HOURS),
            ),
        )
    }.getOrElse { AdResponse.Empty("unreadable_response") }

    /** JSON null and the string "null" are both absence here, not content. */
    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    public const val DEFAULT_FREQUENCY_WINDOW_HOURS: Int = 24
}
