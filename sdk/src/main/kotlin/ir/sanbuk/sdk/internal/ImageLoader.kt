package ir.sanbuk.sdk.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Fetches creative images. Deliberately not Glide or Coil.
 *
 * An image loader is the second-heaviest thing an ad SDK can add after a
 * networking library, and every publisher who already ships one would inherit
 * a version to reconcile. What is needed here is narrow: a handful of small
 * images, decoded once, held briefly.
 *
 * The cache exists for one reason — a list that scrolls back and forth must
 * not re-download the same banner on every pass.
 */
internal object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(url: String): Bitmap? = runCatching { cache.get(url) }.getOrNull()

    /** Blocking; call from [Worker.background]. Returns null rather than throwing. */
    fun load(url: String): Bitmap? {
        cached(url)?.let { return it }

        val bytes = runCatching {
            val result = Http.getBytes(url)
            if (result.isEmpty()) null else result
        }.getOrNull() ?: return null

        val bitmap = runCatching { decode(bytes) }.getOrNull() ?: return null

        runCatching { cache.put(url, bitmap) }
        return bitmap
    }

    /**
     * Measured before it is decoded.
     *
     * A creative is supposed to be a banner, but "supposed to" is not a
     * guarantee, and a 4000x4000 PNG decodes to 64 MB of heap — an
     * OutOfMemoryError in a publisher's app, caused by an advertisement. The
     * first pass reads only the header; the second decodes at whatever
     * reduction brings it under a sane size.
     */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        while (
            bounds.outWidth / sample > MAX_DIMENSION_PX ||
            bounds.outHeight / sample > MAX_DIMENSION_PX
        ) {
            sample *= 2
        }

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    /** 4 MiB: a few banners at a time, and nothing a low-end device will miss. */
    private const val CACHE_BYTES = 4 * 1024 * 1024

    /** Comfortably larger than any slot on any phone, and 16x smaller than trouble. */
    private const val MAX_DIMENSION_PX = 1_600
}
