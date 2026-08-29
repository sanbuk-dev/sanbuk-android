package ir.sanbuk.sdk.core

import java.net.URLEncoder

/**
 * Turns the impression URL the server handed us into one this install can
 * safely retry.
 *
 * The `eid` is the whole point. The platform counts impressions into a daily
 * counter with no event identity of its own, which is safe on the web — a
 * browser either sends the pixel or it does not, and nothing retries — and
 * unsafe here, because an offline queue exists precisely to send again. Money
 * hangs off that number: a per-impression publisher commission is paid on it
 * and a new media spends its trial allowance from it.
 *
 * The id is minted when the view HAPPENS, not when it is sent. Two attempts at
 * the same view must carry the same id, or the guard on the server has nothing
 * to recognise.
 */
public object ImpressionUrl {

    public fun build(impressionUrl: String, eventId: String, installId: String): String {
        val separator = if (impressionUrl.contains('?')) '&' else '?'
        return buildString {
            append(impressionUrl)
            append(separator)
            append("eid=").append(encode(eventId))
            append("&install_id=").append(encode(installId))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
