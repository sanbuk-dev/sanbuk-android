package ir.sanbuk.sdk.core

/**
 * One ask for an ad, as the edge expects it.
 *
 * Everything a native SDK knows and a browser does not lives here. Two fields
 * carry more weight than their size suggests:
 *
 * - [platform] is what keeps app campaigns in app inventory. The server used
 *   to read the browser user agent to decide whether a visitor could install
 *   an Android app; an SDK has no browser user agent, so without this every
 *   "install our app" campaign is filtered out of the one place it belongs.
 * - [installId] is the rate-limit key. Iranian carriers put tens of thousands
 *   of subscribers behind one CGNAT address, so an IP means very little; the
 *   edge limits a caller that names its install by that name instead.
 */
public data class AdRequest(
    val placementCode: String,
    val mediaCode: String,
    val installId: String,
    /** Framework-tagged, e.g. "android-0.1.0" — so one bad shell can be killed alone. */
    val sdkVersion: String,
    val platform: Platform = Platform.ANDROID,
    val packageName: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
    val connection: Connection = Connection.UNKNOWN,
    val render: RenderMode = RenderMode.DEFAULT,
    /** The visitor's own per-campaign view counts, "id:count" pairs. */
    val frequency: String? = null,
)
