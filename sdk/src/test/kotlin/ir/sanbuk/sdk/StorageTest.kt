package ir.sanbuk.sdk

import androidx.test.core.app.ApplicationProvider
import ir.sanbuk.sdk.core.FrequencyCounters
import ir.sanbuk.sdk.internal.Storage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StorageTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    /**
     * The install id is the rate-limit key that still means something behind
     * carrier NAT. A fresh one on every launch would put every request back on
     * the per-IP ceiling the edge stopped using for exactly this reason.
     */
    @Test
    fun `the install id is stable across reads`() {
        val storage = Storage(context)

        val first = storage.installId()

        assertEquals(first, storage.installId())
        assertEquals(first, Storage(context).installId(), "and across SDK restarts")
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun `two apps do not share an install id`() {
        val mine = Storage(context).installId()

        assertNotEquals("", mine)
        assertTrue(mine.length >= 32, "a uuid, not something guessable: $mine")
    }

    /**
     * On the web these counters die with the page. An app process lives for
     * days and is then killed without warning; without this round trip every
     * cold start resets the cap and the visitor sees one campaign forever.
     */
    @Test
    fun `frequency counters survive a process restart`() {
        val storage = Storage(context)
        val counters = FrequencyCounters.empty()
        counters.record("cmp-1", nowEpochMs = 1_000)
        counters.record("cmp-1", nowEpochMs = 1_500)
        storage.saveCounters(counters)

        assertEquals(2, Storage(context).loadCounters().countFor("cmp-1"))
    }

    @Test
    fun `nothing stored is simply nothing counted`() {
        assertEquals(0, Storage(context).loadCounters().countFor("never-seen"))
    }
}
