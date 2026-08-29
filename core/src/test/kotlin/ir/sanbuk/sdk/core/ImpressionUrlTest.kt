package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpressionUrlTest {

    /**
     * Without the id the server has nothing to recognise a retry by, and the
     * offline queue turns one bad minute of signal into inflated revenue.
     */
    @Test
    fun `carries the event id the server deduplicates on`() {
        val url = ImpressionUrl.build(
            impressionUrl = "https://t.sanbuk.com/i/abc123",
            eventId = "b21f5c9e-1d2a-4c3b-8e7f-0a1b2c3d4e5f",
            installId = "install-1",
        )

        assertEquals(
            "https://t.sanbuk.com/i/abc123?eid=b21f5c9e-1d2a-4c3b-8e7f-0a1b2c3d4e5f&install_id=install-1",
            url,
        )
    }

    /** The server already appends `?v=` for cache busting on the web path. */
    @Test
    fun `appends to a url that already carries a query`() {
        val url = ImpressionUrl.build("https://t.sanbuk.com/i/abc?v=2", "eid-1", "install-1")

        assertTrue(url.contains("?v=2&eid=eid-1"), url)
    }

    @Test
    fun `escapes ids rather than trusting them`() {
        val url = ImpressionUrl.build("https://t.sanbuk.com/i/abc", "a&b=c", "install 1")

        assertTrue(url.contains("eid=a%26b%3Dc"), url)
        assertTrue(url.contains("install_id=install+1"), url)
    }
}
