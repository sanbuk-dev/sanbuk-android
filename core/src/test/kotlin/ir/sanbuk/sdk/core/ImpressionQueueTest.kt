package ir.sanbuk.sdk.core

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImpressionQueueTest {

    private val dir: File = Files.createTempDirectory("sanbuk-queue").toFile()
    private val file = File(dir, "impressions.tsv")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun impression(id: String, at: Long = 1_000L) =
        QueuedImpression(eventId = id, url = "https://t.sanbuk.com/i/abc?eid=$id", createdAtEpochMs = at)

    @Test
    fun `holds what could not be sent and gives it back oldest first`() {
        val queue = ImpressionQueue(file)

        queue.enqueue(impression("a", at = 1_000))
        queue.enqueue(impression("b", at = 2_000))

        assertEquals(listOf("a", "b"), queue.pending(nowEpochMs = 2_500).map { it.eventId })
    }

    /** A queue flushed twice must not become two views of the same ad. */
    @Test
    fun `the same view is only ever held once`() {
        val queue = ImpressionQueue(file)

        queue.enqueue(impression("a"))
        queue.enqueue(impression("a"))

        assertEquals(1, queue.size())
    }

    @Test
    fun `forgets what the server has taken`() {
        val queue = ImpressionQueue(file)
        queue.enqueue(impression("a"))
        queue.enqueue(impression("b"))

        queue.acknowledge(listOf("a"))

        assertEquals(listOf("b"), queue.pending(nowEpochMs = 1_500).map { it.eventId })
    }

    /**
     * A view that lands the next day moves a number in a report someone has
     * already read — worse than losing it.
     */
    @Test
    fun `drops views too old to belong to today`() {
        val queue = ImpressionQueue(file)
        queue.enqueue(impression("stale", at = 0))
        queue.enqueue(impression("fresh", at = ImpressionQueue.MAX_AGE_MILLIS))

        val pending = queue.pending(nowEpochMs = ImpressionQueue.MAX_AGE_MILLIS + 1)

        assertEquals(listOf("fresh"), pending.map { it.eventId })
        assertEquals(1, queue.size(), "the expired entry is forgotten, not just hidden")
    }

    @Test
    fun `a phone offline for a week does not grow the file forever`() {
        val queue = ImpressionQueue(file, maxEntries = 3)

        repeat(10) { queue.enqueue(impression("id-$it", at = it.toLong())) }

        assertEquals(3, queue.size())
        assertEquals(
            listOf("id-7", "id-8", "id-9"),
            queue.pending(nowEpochMs = 100).map { it.eventId },
            "the newest survive — an old impression is worth least",
        )
    }

    @Test
    fun `a corrupt file costs impressions, never an exception`() {
        file.parentFile.mkdirs()
        file.writeText("this is not a queue\n\t\t\nid\tnot-a-number\thttps://x")

        val queue = ImpressionQueue(file)

        assertEquals(emptyList(), queue.pending(nowEpochMs = 1_000))
        queue.enqueue(impression("a"))
        assertEquals(1, queue.size(), "and the queue keeps working afterwards")
    }

    @Test
    fun `an unreadable location is survivable`() {
        val queue = ImpressionQueue(File(dir, "nope/deeper/impressions.tsv"))

        queue.enqueue(impression("a"))

        assertTrue(queue.size() <= 1, "either it wrote or it did not — but it did not throw")
    }
}
