package ir.sanbuk.sdk.core

import org.json.JSONObject

/**
 * The ping that lets an app media carry traffic at all.
 *
 * A website proves it hosts our snippet when the crawler fetches the page. An
 * app cannot be crawled, so the server takes first contact from inside the app
 * as the proof: until `POST /api/v1/sdk/ping` arrives with the media code and
 * the app's package name, the media is not verified and every ad request for it
 * comes back empty — however approved it is in the panel.
 *
 * The public spec tells a direct integrator to ping once per launch. We ping
 * less: once an answer says `ok`, this install stays quiet for
 * [RECHECK_AFTER_MILLIS]. Every launch of every install would land on the API
 * host (not the Go edge) for a fact that, once true, rarely changes. It does
 * change, though — editing an approved media's URL or package sends it back to
 * review unverified — so the acknowledgement expires instead of lasting
 * forever. With the installs of a live app expiring at different times, a
 * re-approved media is verified again within minutes, not days.
 *
 * A negative or failed answer is never remembered: the next launch asks again.
 */
public object MediaPing {

    public const val PATH: String = "/api/v1/sdk/ping"

    public const val RECHECK_AFTER_MILLIS: Long = 24L * 60 * 60 * 1000

    public fun url(apiBase: String): String = apiBase.trimEnd('/') + PATH

    public fun body(mediaCode: String, packageName: String?): String =
        JSONObject()
            .put("media_code", mediaCode)
            .put("package_name", packageName ?: JSONObject.NULL)
            .toString()

    /**
     * Whether the server recognised this code and package as an app media.
     * Always answered with 200 by design, so `ok` is the whole verdict; a
     * reply we cannot read counts as no.
     */
    public fun acknowledged(body: String): Boolean =
        runCatching { JSONObject(body).optBoolean("ok", false) }.getOrDefault(false)

    /**
     * What an acknowledgement vouches for. A new media code or package name is
     * a different media to the server, so an old "ok" says nothing about it.
     */
    public fun subject(mediaCode: String, packageName: String?): String =
        "$mediaCode|${packageName.orEmpty()}"

    /** @param nowMillis wall-clock time; it has to compare across launches. */
    public fun isDue(last: Acknowledgement?, subject: String, nowMillis: Long): Boolean {
        if (last == null || last.subject != subject) return true
        val age = nowMillis - last.atMillis
        // A clock moved backwards makes the record look like it is from the
        // future. Asking again is the cheap way out of not knowing.
        return age < 0 || age >= RECHECK_AFTER_MILLIS
    }

    public data class Acknowledgement(val subject: String, val atMillis: Long)
}
