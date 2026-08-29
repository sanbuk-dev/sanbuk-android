package ir.sanbuk.sdk.core

import java.net.URLEncoder

/** Builds the edge URL for one ad request. Pure string work, no I/O. */
public object ServeUrl {

    /**
     * @param base the tracker origin, e.g. https://t.sanbuk.com — a trailing
     *             slash is tolerated because publishers paste these by hand.
     */
    public fun build(base: String, request: AdRequest): String {
        val origin = base.trimEnd('/')
        val params = buildList {
            add("placement" to request.placementCode)
            add("media" to request.mediaCode)
            add("platform" to request.platform.wire)
            add("install_id" to request.installId)
            add("sdk" to request.sdkVersion)
            add("render" to request.render.wire)
            request.packageName?.let { add("package" to it) }
            request.appVersion?.let { add("app_version" to it) }
            request.osVersion?.let { add("os" to it) }
            if (request.connection != Connection.UNKNOWN) {
                add("conn" to request.connection.wire)
            }
            // An empty counter string is noise on the wire, not information.
            request.frequency?.takeIf { it.isNotEmpty() }?.let { add("fc" to it) }
        }

        return params.joinToString(prefix = "$origin/ad?", separator = "&") { (key, value) ->
            "$key=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
