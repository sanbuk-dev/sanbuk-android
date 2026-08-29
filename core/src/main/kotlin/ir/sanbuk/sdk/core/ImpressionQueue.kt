package ir.sanbuk.sdk.core

import java.io.File

/** One view waiting to be reported. */
public data class QueuedImpression(
    val eventId: String,
    val url: String,
    val createdAtEpochMs: Long,
)

/**
 * Views that could not be delivered yet.
 *
 * An app runs on the metro, in a lift, on two bars of signal. Without this, a
 * publisher loses every impression that happened during a bad minute — real
 * revenue, silently. With it, the same view can be sent twice, which is why
 * every entry carries the event id the server deduplicates on.
 *
 * Three bounds, each for a reason:
 * - a size cap, so a phone that has been offline for a week does not grow a
 *   file forever;
 * - an age cap, because an impression that lands on the wrong day moves a
 *   number in a report someone already read;
 * - dedupe on the id, so a queue flushed twice does not queue twice.
 *
 * Never throws. A corrupt or unreadable file costs some impressions, which is
 * bad; an exception on the way to drawing an ad costs the publisher's app,
 * which is unacceptable.
 */
public class ImpressionQueue(
    private val file: File,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxAgeMillis: Long = MAX_AGE_MILLIS,
) {

    public fun enqueue(impression: QueuedImpression) {
        val kept = read()
            .filterNot { it.eventId == impression.eventId }
            .plus(impression)
            .takeLast(maxEntries)
        write(kept)
    }

    /** What is still worth sending, oldest first. Expired entries are forgotten here. */
    public fun pending(nowEpochMs: Long): List<QueuedImpression> {
        val all = read()
        val fresh = all.filter { nowEpochMs - it.createdAtEpochMs < maxAgeMillis }
        if (fresh.size != all.size) write(fresh)
        return fresh
    }

    /** Called once the server has answered — including for a duplicate, which it accepts. */
    public fun acknowledge(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        val remaining = read().filterNot { it.eventId in eventIds }
        write(remaining)
    }

    public fun size(): Int = read().size

    private fun read(): List<QueuedImpression> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull(::decodeLine)
    }.getOrElse { emptyList() }

    private fun write(entries: List<QueuedImpression>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(entries.joinToString("\n") { encodeLine(it) })
        }
    }

    // A tab separates the fields because none of them can contain one: the id
    // is a uuid and the url is percent-encoded.
    private fun encodeLine(impression: QueuedImpression): String =
        "${impression.eventId}\t${impression.createdAtEpochMs}\t${impression.url}"

    /** A line we cannot read is dropped, never thrown — see the class note. */
    private fun decodeLine(line: String): QueuedImpression? {
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val createdAt = parts[1].toLongOrNull() ?: return null
        if (parts[0].isEmpty() || parts[2].isEmpty()) return null
        return QueuedImpression(eventId = parts[0], url = parts[2], createdAtEpochMs = createdAt)
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 500
        /** Six hours: long enough for a commute, short enough to stay in the right day. */
        public const val MAX_AGE_MILLIS: Long = 6 * 60 * 60 * 1000L
    }
}
