package ir.sanbuk.sdk.core

/**
 * The vocabulary the server and every shell agree on.
 *
 * These strings are a published contract (contracts/openapi.yaml, /track/serve):
 * once an SDK carrying them is on phones, they can never be renamed — a build
 * from two years ago still has to be understood. Hence the explicit `wire`
 * value rather than relying on enum names.
 */
public enum class Platform(public val wire: String) {
    ANDROID("android"),
    IOS("ios"),
    WEB("web"),
}

/**
 * Who draws the ad, and therefore who counts the view.
 *
 * DEFAULT: our own renderer draws it and reports the view only once it was
 * really on screen. CUSTOM: the publisher's app draws it with its own UI and
 * calls recordImpression() itself. The server cannot verify the second, which
 * is exactly why it is declared rather than assumed.
 */
public enum class RenderMode(public val wire: String) {
    DEFAULT("default"),
    CUSTOM("custom"),
}

/** Connection class, a matching and anti-fraud signal — never a gate. */
public enum class Connection(public val wire: String) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    UNKNOWN("unknown"),
}
