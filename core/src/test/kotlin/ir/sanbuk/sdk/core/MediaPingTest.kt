package ir.sanbuk.sdk.core

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPingTest {

    private val subject = MediaPing.subject("med456", "ir.charsoo.app")
    private val day = MediaPing.RECHECK_AFTER_MILLIS

    @Test
    fun `goes to the api host path, whatever the base looks like`() {
        assertEquals("https://api.sanbuk.com/api/v1/sdk/ping", MediaPing.url("https://api.sanbuk.com"))
        assertEquals("http://localhost:8000/api/v1/sdk/ping", MediaPing.url("http://localhost:8000/"))
    }

    /** The two fields SdkPingController validates, by those exact names. */
    @Test
    fun `body carries the media code and the package name`() {
        val body = JSONObject(MediaPing.body("med456", "ir.charsoo.app"))

        assertEquals("med456", body.getString("media_code"))
        assertEquals("ir.charsoo.app", body.getString("package_name"))
    }

    @Test
    fun `an unknown package is sent as null, not as the string null`() {
        val body = JSONObject(MediaPing.body("med456", null))

        assertTrue(body.isNull("package_name"))
    }

    @Test
    fun `only an explicit ok counts`() {
        assertTrue(MediaPing.acknowledged("""{"ok":true}"""))
        assertFalse(MediaPing.acknowledged("""{"ok":false}"""))
        assertFalse(MediaPing.acknowledged("""{}"""))
        assertFalse(MediaPing.acknowledged("<html>502 Bad Gateway</html>"))
        assertFalse(MediaPing.acknowledged(""))
    }

    @Test
    fun `a first launch always pings`() {
        assertTrue(MediaPing.isDue(last = null, subject = subject, nowMillis = 1_000))
    }

    @Test
    fun `a fresh acknowledgement keeps the install quiet`() {
        val last = MediaPing.Acknowledgement(subject, atMillis = 1_000)

        assertFalse(MediaPing.isDue(last, subject, nowMillis = 1_000 + day - 1))
    }

    /**
     * Editing an approved media unverifies it on the server. An "ok" that
     * never expired would leave such a media dark until a brand-new install
     * happened to launch.
     */
    @Test
    fun `an acknowledgement expires`() {
        val last = MediaPing.Acknowledgement(subject, atMillis = 1_000)

        assertTrue(MediaPing.isDue(last, subject, nowMillis = 1_000 + day))
    }

    @Test
    fun `an ok for another media code or package says nothing about this one`() {
        val last = MediaPing.Acknowledgement(subject, atMillis = 1_000)

        assertTrue(MediaPing.isDue(last, MediaPing.subject("other1", "ir.charsoo.app"), nowMillis = 2_000))
        assertTrue(MediaPing.isDue(last, MediaPing.subject("med456", "ir.other.app"), nowMillis = 2_000))
    }

    @Test
    fun `a clock moved backwards asks again rather than trusting the future`() {
        val last = MediaPing.Acknowledgement(subject, atMillis = 50_000)

        assertTrue(MediaPing.isDue(last, subject, nowMillis = 10_000))
    }
}
