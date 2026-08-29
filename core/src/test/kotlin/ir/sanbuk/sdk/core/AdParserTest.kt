package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AdParserTest {

    @Test
    fun `reads a served ad`() {
        val body = """
            {"ad":{"code":"abc123","format":"native","campaign_id":"cmp-1","creative_id":"crv-1",
            "headline":"تخفیف پاییزه","description":"تا ۵۰ درصد","cta":"buy","cta_label":"خرید کنید",
            "brand_color":"#060ADB","image_url":"https://cdn/i.png","logo_url":null,
            "click_url":"https://t.sanbuk.com/c/abc123","impression_url":"https://t.sanbuk.com/i/abc123",
            "fc_hours":12}}
        """.trimIndent()

        val ad = assertIs<AdResponse.Filled>(AdParser.parse(body)).ad

        assertEquals("abc123", ad.linkCode)
        assertEquals("native", ad.format)
        assertEquals("تخفیف پاییزه", ad.headline)
        assertEquals("خرید کنید", ad.ctaLabel)
        assertEquals("https://t.sanbuk.com/c/abc123", ad.clickUrl)
        assertEquals(12, ad.frequencyWindowHours)
        assertNull(ad.logoUrl, "an explicit JSON null is absence, not the string \"null\"")
    }

    @Test
    fun `an empty slot is a normal answer`() {
        val response = assertIs<AdResponse.Empty>(AdParser.parse("""{"ad":null}"""))

        assertNull(response.reason, "production sends no reason and none is invented")
    }

    /** What a debug build gets once the operator turns diagnostics on. */
    @Test
    fun `an empty slot can name the rule that emptied it`() {
        val response = assertIs<AdResponse.Empty>(
            AdParser.parse("""{"ad":null,"reason":"advertiser_cannot_pay"}"""),
        )

        assertEquals("advertiser_cannot_pay", response.reason)
    }

    @Test
    fun `falls back to the shipped window when the server names none`() {
        val body = """{"ad":{"code":"a","click_url":"https://c","impression_url":"https://i"}}"""

        val ad = assertIs<AdResponse.Filled>(AdParser.parse(body)).ad

        assertEquals(AdParser.DEFAULT_FREQUENCY_WINDOW_HOURS, ad.frequencyWindowHours)
        assertEquals("banner", ad.format, "an unnamed format is the common one, not a crash")
    }

    /**
     * The rule the whole SDK stands on: nothing that arrives over the network
     * may throw into a publisher's app for the sake of an advertisement.
     */
    @Test
    fun `no shape of garbage ever throws`() {
        listOf(
            "",
            "not json at all",
            "{",
            "[]",
            """{"ad":"a string where an object belongs"}""",
            """{"ad":{}}""",
            """{"ad":{"code":"a"}}""",
            """{"ad":{"code":"a","click_url":"https://c"}}""",
            """{"ad":{"code":"a","click_url":"https://c","impression_url":"https://i","fc_hours":"soon"}}""",
        ).forEach { body ->
            val response = AdParser.parse(body)
            if (body.contains("impression_url")) {
                assertIs<AdResponse.Filled>(response, "usable enough to draw: $body")
            } else {
                assertIs<AdResponse.Empty>(response, "expected an empty answer for: $body")
            }
        }
    }

    /**
     * org.json's optString hands back the literal string "null" for an
     * explicit JSON null, so a field the server nulled would have become the
     * four characters n-u-l-l: a link code of "null" is a 404 nobody can
     * explain, and a headline of "null" is a headline shipped to a phone.
     */
    @Test
    fun `an explicit json null is absence, never the word null`() {
        val body = """
            {"ad":{"code":"abc","click_url":"https://c","impression_url":"https://i",
            "format":null,"cta":null,"cta_label":null,"campaign_id":null,
            "creative_id":null,"headline":null,"brand_color":null}}
        """.trimIndent()

        val ad = assertIs<AdResponse.Filled>(AdParser.parse(body)).ad

        assertEquals("banner", ad.format)
        assertEquals("view", ad.cta)
        assertEquals("", ad.ctaLabel)
        assertEquals("", ad.campaignId)
        assertNull(ad.headline)
        assertNull(ad.brandColor)
    }

    @Test
    fun `a nulled link code is not drawable`() {
        assertIs<AdResponse.Empty>(
            AdParser.parse("""{"ad":{"code":null,"click_url":"https://c","impression_url":"https://i"}}"""),
        )
    }

    @Test
    fun `an ad missing its click url is not drawable`() {
        val response = assertIs<AdResponse.Empty>(
            AdParser.parse("""{"ad":{"code":"a","impression_url":"https://i"}}"""),
        )

        assertEquals("malformed_ad", response.reason)
    }
}
