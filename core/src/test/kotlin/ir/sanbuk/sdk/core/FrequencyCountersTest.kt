package ir.sanbuk.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrequencyCountersTest {

    @Test
    fun `counts what this install has seen`() {
        val counters = FrequencyCounters.empty()

        counters.record("cmp-1", nowEpochMs = 1_000)
        counters.record("cmp-1", nowEpochMs = 2_000)
        counters.record("cmp-2", nowEpochMs = 2_000)

        assertEquals(2, counters.countFor("cmp-1"))
        assertEquals(1, counters.countFor("cmp-2"))
        assertEquals(0, counters.countFor("never-seen"))
    }

    /**
     * On the web these die with the page. An app process lives for days and is
     * then killed without warning; without a round trip through storage every
     * cold start resets the cap and the visitor sees one campaign forever.
     */
    @Test
    fun `survives a round trip through storage`() {
        val counters = FrequencyCounters.empty()
        counters.record("cmp-1", nowEpochMs = 1_000)
        counters.record("cmp-1", nowEpochMs = 1_500)

        val restored = FrequencyCounters.decode(counters.encode())

        assertEquals(2, restored.countFor("cmp-1"))
    }

    @Test
    fun `forgets what the window no longer covers`() {
        val counters = FrequencyCounters.empty()
        counters.record("old", nowEpochMs = 0)
        counters.record("recent", nowEpochMs = 23L * 3_600_000)

        counters.prune(windowHours = 24, nowEpochMs = 25L * 3_600_000)

        assertEquals(0, counters.countFor("old"))
        assertEquals(1, counters.countFor("recent"))
    }

    /** The server can retune the cap without an SDK release; zero means forget everything. */
    @Test
    fun `a zero window clears the slate`() {
        val counters = FrequencyCounters.empty()
        counters.record("cmp-1", nowEpochMs = 1_000)

        counters.prune(windowHours = 0, nowEpochMs = 1_000)

        assertEquals(0, counters.countFor("cmp-1"))
    }

    @Test
    fun `writes the pairs the edge parses`() {
        val counters = FrequencyCounters.empty()
        counters.record("cmp-1", nowEpochMs = 1_000)
        counters.record("cmp-1", nowEpochMs = 1_000)
        counters.record("cmp-2", nowEpochMs = 1_000)

        assertEquals("cmp-1:2,cmp-2:1", counters.toWire())
    }

    /**
     * The edge truncates at 2000 characters and 100 pairs. Trimming here — by
     * count, busiest first — means the caps that matter most are the ones
     * still being enforced when the string is cut.
     */
    @Test
    fun `keeps the busiest campaigns when it has to choose`() {
        val counters = FrequencyCounters.empty()
        repeat(150) { index ->
            repeat(index + 1) { counters.record("campaign-$index", nowEpochMs = 1_000) }
        }

        val wire = counters.toWire()

        assertTrue(wire.length <= 2_000, "length was ${wire.length}")
        assertTrue(wire.split(',').size <= 100)
        assertTrue(wire.startsWith("campaign-149:150"), "busiest first, got: ${wire.take(40)}")
    }

    @Test
    fun `a corrupt counter file costs a repeated ad, never a crash`() {
        val counters = FrequencyCounters.decode("garbage,cmp-1:2:1000,cmp-2:notanumber:1,,:::")

        assertEquals(2, counters.countFor("cmp-1"))
        assertEquals(0, counters.countFor("cmp-2"))
    }

    @Test
    fun `nothing stored is simply nothing counted`() {
        assertEquals("", FrequencyCounters.decode(null).toWire())
        assertEquals("", FrequencyCounters.empty().toWire())
    }
}
