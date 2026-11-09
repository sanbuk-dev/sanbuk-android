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
    /**
     * The API origin. Used for one thing: the verification ping an app
     * media needs before the server will serve it (see ir.sanbuk.sdk.core.MediaPing).
     * Overridable for the same reason as [trackerUrl].
     */
    public val apiUrl: String = DEFAULT_API_URL,
) {
    /**
     * The three-argument constructor from before [apiUrl] existed, kept
     * explicitly. Shells reach this class over JNI by argument count, where
     * Kotlin's default arguments do not exist: without this overload a Unity
     * build that constructs a config would compile and then crash on device.
     */
    public constructor(mediaCode: String, trackerUrl: String, debug: Boolean) :
        this(mediaCode, trackerUrl, debug, DEFAULT_API_URL)

    public companion object {
        public const val DEFAULT_TRACKER_URL: String = "https://t.sanbuk.com"
        public const val DEFAULT_API_URL: String = "https://api.sanbuk.com"
    }
}
