package ir.sanbuk.sdk.core

/**
 * How often this install has already seen each campaign.
 *
 * The server distrusts these and re-checks everything — lying only ever costs
 * the visitor some ads — but sending them is what lets a frequency cap work at
 * all without the platform tracking individuals.
 *
 * On the web these counters live in memory and die with the page. An app
 * process lives for days and then is killed without warning, so they have to
 * survive: [encode] and [decode] are for whatever the shell uses for storage.
 * The wire format is narrower than the stored one, because the server only
 * needs the counts, never when they happened.
 */
public class FrequencyCounters private constructor(
    private val entries: MutableMap<String, Entry>,
) {

    private data class Entry(val count: Int, val firstSeenEpochMs: Long)

    public fun record(campaignId: String, nowEpochMs: Long) {
        if (campaignId.isEmpty()) return
        val existing = entries[campaignId]
        entries[campaignId] = if (existing == null) {
            Entry(count = 1, firstSeenEpochMs = nowEpochMs)
        } else {
            existing.copy(count = existing.count + 1)
        }
    }

    /**
     * Forgets what the window no longer covers. Driven by the server's own
     * `fc_hours`, so the operator can retune the cap without shipping an SDK.
     */
    public fun prune(windowHours: Int, nowEpochMs: Long) {
        if (windowHours <= 0) {
            entries.clear()
            return
        }
        val cutoff = nowEpochMs - windowHours * MILLIS_PER_HOUR
        entries.entries.removeAll { it.value.firstSeenEpochMs <= cutoff }
    }

    public fun countFor(campaignId: String): Int = entries[campaignId]?.count ?: 0

    /**
     * The `fc` query parameter: "campaignId:count" pairs.
     *
     * Bounded twice, because the edge bounds it too and a request that gets
     * truncated there is worse than one trimmed here: the busiest campaigns
     * are kept, so the cap that matters most is the one still enforced.
     */
    public fun toWire(): String {
        val builder = StringBuilder()
        entries.entries
            .sortedByDescending { it.value.count }
            .take(MAX_WIRE_PAIRS)
            .forEach { (campaignId, entry) ->
                val pair = "$campaignId:${entry.count}"
                val addedLength = if (builder.isEmpty()) pair.length else pair.length + 1
                if (builder.length + addedLength > MAX_WIRE_CHARS) return@forEach
                if (builder.isNotEmpty()) builder.append(',')
                builder.append(pair)
            }
        return builder.toString()
    }

    /** Storage form — the counts plus when each campaign was first seen. */
    public fun encode(): String = entries.entries
        .joinToString(",") { "${it.key}:${it.value.count}:${it.value.firstSeenEpochMs}" }

    public companion object {
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MAX_WIRE_PAIRS = 100
        private const val MAX_WIRE_CHARS = 2_000

        public fun empty(): FrequencyCounters = FrequencyCounters(mutableMapOf())

        /**
         * Anything malformed is dropped rather than rejected. A corrupt
         * counter file must cost the visitor a repeated ad at worst, never an
         * exception on the way to drawing one.
         */
        public fun decode(stored: String?): FrequencyCounters {
            val entries = mutableMapOf<String, Entry>()
            stored?.split(',')?.forEach { chunk ->
                val parts = chunk.split(':')
                if (parts.size == 3) {
                    val id = parts[0]
                    val count = parts[1].toIntOrNull()
                    val firstSeen = parts[2].toLongOrNull()
                    if (id.isNotEmpty() && count != null && count > 0 && firstSeen != null) {
                        entries[id] = Entry(count, firstSeen)
                    }
                }
            }
            return FrequencyCounters(entries)
        }
    }
}
