package ir.sanbuk.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import ir.sanbuk.sdk.core.Ad
import ir.sanbuk.sdk.core.AdParser
import ir.sanbuk.sdk.core.AdRequest
import ir.sanbuk.sdk.core.AdResponse
import ir.sanbuk.sdk.core.Connection
import ir.sanbuk.sdk.core.FrequencyCounters
import ir.sanbuk.sdk.core.ImpressionQueue
import ir.sanbuk.sdk.core.ImpressionUrl
import ir.sanbuk.sdk.core.QueuedImpression
import ir.sanbuk.sdk.core.RenderMode
import ir.sanbuk.sdk.internal.ClickLauncher
import ir.sanbuk.sdk.internal.Http
import ir.sanbuk.sdk.internal.Storage
import ir.sanbuk.sdk.internal.Worker
import java.io.File
import java.util.UUID

/**
 * Everything the SDK keeps between calls: who this install is, what it has
 * seen, and what it still owes the server.
 *
 * Held once, created at [Sanbuk.init], and never touched from the caller's
 * thread for anything that reads a file or a preference.
 */
internal class SanbukSession(
    context: Context,
    private val config: SanbukConfig,
) {

    /**
     * Narrowed here rather than trusted from the caller. This object lives for
     * the life of the process; holding an Activity would leak the whole screen
     * behind it, and "the caller passed the right thing" is not a guarantee
     * worth betting a publisher's memory on.
     */
    private val context: Context = context.applicationContext

    /**
     * Both touch the filesystem on first use — getSharedPreferences reads a
     * file, filesDir stats one — and this object is built inside
     * Sanbuk.init(), which publishers are told to call from
     * Application.onCreate. Lazy means the cost lands on the worker thread
     * that warms them below, not on the cold start.
     */
    private val storage by lazy { Storage(context) }
    private val queue by lazy { ImpressionQueue(File(context.filesDir, "sanbuk/impressions.tsv")) }

    /** Resolved once off the main thread, then reused. */
    @Volatile
    private var installId: String? = null

    @Volatile
    private var counters: FrequencyCounters? = null

    init {
        SanbukLog.enabled = config.debug
        // Warming these here means the first ad request does not pay for a
        // preferences read, and the queue left over from the last run goes out
        // before anything else asks for the network.
        Worker.background {
            installId = storage.installId()
            counters = storage.loadCounters()
            flushQueue()
        }
    }

    fun loadAd(
        context: Context,
        placementCode: String,
        render: Sanbuk.RenderStyle,
        onResult: (SanbukAd?) -> Unit,
    ) {
        Worker.background {
            val response = requestAd(placementCode, render)
            val ad = (response as? AdResponse.Filled)?.ad

            if (ad == null) {
                val reason = (response as? AdResponse.Empty)?.reason
                SanbukLog.debug("no ad for $placementCode" + (reason?.let { " ($it)" } ?: ""))
            }

            Worker.onMain { onResult(ad?.let { SanbukAd(it, this) }) }
        }
    }

    private fun requestAd(placementCode: String, render: Sanbuk.RenderStyle): AdResponse {
        val id = installId ?: storage.installId().also { installId = it }
        val counted = counters ?: storage.loadCounters().also { counters = it }

        val request = AdRequest(
            placementCode = placementCode,
            mediaCode = config.mediaCode,
            installId = id,
            sdkVersion = SDK_VERSION,
            packageName = context.packageName,
            appVersion = appVersion(),
            // The Android version a person would recognise ("13"), not the
            // API level — the contract's example is the former.
            osVersion = Build.VERSION.RELEASE,
            connection = connectionClass(),
            render = if (render == Sanbuk.RenderStyle.DEFAULT) RenderMode.DEFAULT else RenderMode.CUSTOM,
            frequency = counted.toWire().takeIf { it.isNotEmpty() },
        )

        val url = ir.sanbuk.sdk.core.ServeUrl.build(config.trackerUrl, request)
        SanbukLog.debug("GET $url")

        val result = Http.get(url)
        if (!result.ok) {
            SanbukLog.warn("ad request answered ${result.status}")
            return AdResponse.Empty("edge_unreachable")
        }

        val response = AdParser.parse(result.body)
        if (response is AdResponse.Filled) {
            // Counted at the decision, not at the draw: the server has already
            // spent this campaign's turn on this install either way, and a
            // banner the visitor scrolls past still burns the rotation.
            counted.record(response.ad.campaignId, System.currentTimeMillis())
            counted.prune(response.ad.frequencyWindowHours, System.currentTimeMillis())
            storage.saveCounters(counted)
        }
        return response
    }

    fun recordImpression(ad: Ad) {
        val eventId = UUID.randomUUID().toString()
        Worker.background {
            val id = installId ?: storage.installId().also { installId = it }
            // Queued first, sent second. A view that happened must survive the
            // request failing — that is the entire reason the queue exists.
            queue.enqueue(
                QueuedImpression(
                    eventId = eventId,
                    url = ImpressionUrl.build(ad.impressionUrl, eventId, id),
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            flushQueue()
        }
    }

    fun openClick(context: Context, ad: Ad, brandColor: Int?) {
        SanbukLog.debug("click ${ad.linkCode}")
        ClickLauncher.open(context, ad.clickUrl, brandColor)
    }

    /**
     * Sends what is waiting. Called after every view and once at startup, so a
     * commute spent offline is delivered on the next screen the app opens.
     */
    private fun flushQueue() {
        val pending = queue.pending(System.currentTimeMillis())
        if (pending.isEmpty()) return

        val delivered = mutableListOf<String>()
        // Bounded per flush. This runs on the one worker thread ad requests
        // also use, so a long backlog on a bad connection must not be able to
        // stall the next ad for a minute; the rest goes with the next view.
        pending.take(MAX_FLUSH_PER_RUN).forEach { impression ->
            val result = Http.get(impression.url)
            // A duplicate answers 200 as well — the server recognises the event
            // id rather than counting it twice — so success means "the server
            // has it", not "the server counted it".
            if (result.ok) delivered += impression.eventId
        }
        queue.acknowledge(delivered)
        SanbukLog.debug("flushed ${delivered.size}/${pending.size} impressions")
    }

    private fun appVersion(): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /**
     * Wi-Fi or cellular, as a matching and anti-fraud signal. Unknown is a
     * perfectly good answer: a missing signal never flags anything, and asking
     * for a permission to sharpen it would cost more than it is worth.
     */
    private fun connectionClass(): Connection = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Connection.UNKNOWN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return Connection.UNKNOWN
            val capabilities = manager.getNetworkCapabilities(network) ?: return Connection.UNKNOWN
            when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> Connection.WIFI
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> Connection.CELLULAR
                else -> Connection.UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            when (manager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> Connection.WIFI
                ConnectivityManager.TYPE_MOBILE -> Connection.CELLULAR
                else -> Connection.UNKNOWN
            }
        }
    }.getOrElse { Connection.UNKNOWN }

    private companion object {
        /**
         * Framework-tagged so one bad shell can be switched off alone: the
         * server reads this to decide whether a build is still allowed to ask.
         */
        const val SDK_VERSION = "android-0.1.0"

        /** A commute's worth of backlog, not a week's, in any single pass. */
        const val MAX_FLUSH_PER_RUN = 25
    }
}
