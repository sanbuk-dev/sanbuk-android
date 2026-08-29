package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeUrlTest {

    private val request = AdRequest(
        placementCode = "plc123",
        mediaCode = "med456",
        installId = "8f14e45f-ceea-467a-9f1f-2b1c0b2d3e4a",
        sdkVersion = "android-0.1.0",
        packageName = "ir.charsoo.app",
        appVersion = "4.1.3",
        osVersion = "13",
        connection = Connection.WIFI,
    )

    @Test
    fun `carries every field the edge reads`() {
        val url = ServeUrl.build("https://t.sanbuk.com", request)

        assertTrue(url.startsWith("https://t.sanbuk.com/ad?"))
        listOf(
            "placement=plc123",
            "media=med456",
            "platform=android",
            "install_id=8f14e45f-ceea-467a-9f1f-2b1c0b2d3e4a",
            "sdk=android-0.1.0",
            "render=default",
            "package=ir.charsoo.app",
            "app_version=4.1.3",
            "os=13",
            "conn=wifi",
        ).forEach { assertTrue(url.contains(it), "missing $it in $url") }
    }

    /**
     * The one field that decides whether app campaigns reach app inventory at
     * all: the server believes it over the user agent, and a native SDK has no
     * user agent to fall back to.
     */
    @Test
    fun `always names its platform`() {
        assertTrue(ServeUrl.build("https://t.sanbuk.com", request).contains("platform=android"))
    }

    @Test
    fun `leaves out what it does not know`() {
        val bare = AdRequest(
            placementCode = "plc123",
            mediaCode = "med456",
            installId = "install-1",
            sdkVersion = "android-0.1.0",
        )

        val url = ServeUrl.build("https://t.sanbuk.com", bare)

        assertFalse(url.contains("package="))
        assertFalse(url.contains("app_version="))
        assertFalse(url.contains("conn="), "an unknown connection is not a value")
        assertFalse(url.contains("fc="), "an empty counter string is noise, not information")
    }

    @Test
    fun `escapes what publishers actually paste`() {
        val odd = AdRequest(
            placementCode = "plc 123",
            mediaCode = "med&456",
            installId = "install-1",
            sdkVersion = "android-0.1.0",
            appVersion = "4.1.3 (beta)",
        )

        val url = ServeUrl.build("https://t.sanbuk.com", odd)

        assertTrue(url.contains("placement=plc+123"))
        assertTrue(url.contains("media=med%26456"), "an ampersand must not split the query")
        assertTrue(url.contains("app_version=4.1.3+%28beta%29"))
    }

    @Test
    fun `tolerates a trailing slash on the origin`() {
        assertEquals(
            ServeUrl.build("https://t.sanbuk.com", request),
            ServeUrl.build("https://t.sanbuk.com/", request),
        )
    }

    @Test
    fun `sends the frequency counters when there are any`() {
        val withCounts = request.copy(frequency = "cid1:2,cid2:5")

        assertTrue(ServeUrl.build("https://t.sanbuk.com", withCounts).contains("fc=cid1%3A2%2Ccid2%3A5"))
    }
}
