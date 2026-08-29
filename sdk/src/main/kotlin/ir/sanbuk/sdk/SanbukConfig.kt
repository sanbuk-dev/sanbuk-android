package ir.sanbuk.sdk

/**
 * What the SDK needs to know once, at startup.
 *
 * [mediaCode] is what the publisher was given in the panel. It is not a
 * secret — it identifies the media, it does not authenticate it — which is
 * why the server also matches the app's package name against the media when
 * the publisher declared one.
 */
public data class SanbukConfig(
    public val mediaCode: String,
    /** The tracker origin. Overridable only so a staging build can point elsewhere. */
    public val trackerUrl: String = DEFAULT_TRACKER_URL,
    /**
     * Off in a release build. On, the SDK logs what it asked for and what came
     * back — including why a slot stayed empty, when the operator has turned
     * diagnostics on server-side. Answering "why is my slot blank" is
     * otherwise guesswork for everyone involved.
     */
    public val debug: Boolean = false,
) {
    public companion object {
        public const val DEFAULT_TRACKER_URL: String = "https://t.sanbuk.com"
    }
}
