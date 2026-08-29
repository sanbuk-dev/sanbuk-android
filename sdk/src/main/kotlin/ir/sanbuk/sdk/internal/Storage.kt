package ir.sanbuk.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import ir.sanbuk.sdk.core.FrequencyCounters
import java.util.UUID

/**
 * The little that has to outlive the process.
 *
 * An app is killed without warning and restarted days later. Two things must
 * survive that or they are worthless: the install identity (the rate-limit key
 * that still means something behind carrier NAT) and the per-campaign view
 * counters (a cap that resets on every cold start is not a cap).
 *
 * Every read and write is wrapped: on a device with a corrupt preferences file
 * we would rather forget a counter than throw on the way to drawing an ad.
 */
internal class Storage(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }.getOrNull()

    /**
     * A random, resettable id — never an advertising identifier. It buys the
     * publisher a working frequency cap and buys us a rate-limit key; it buys
     * nobody a way to recognise a person, and it dies with the app's data.
     */
    fun installId(): String {
        val stored = runCatching { prefs?.getString(KEY_INSTALL_ID, null) }.getOrNull()
        if (!stored.isNullOrEmpty()) return stored

        val minted = UUID.randomUUID().toString()
        runCatching { prefs?.edit()?.putString(KEY_INSTALL_ID, minted)?.apply() }
        return minted
    }

    fun loadCounters(): FrequencyCounters =
        FrequencyCounters.decode(runCatching { prefs?.getString(KEY_COUNTERS, null) }.getOrNull())

    fun saveCounters(counters: FrequencyCounters) {
        runCatching { prefs?.edit()?.putString(KEY_COUNTERS, counters.encode())?.apply() }
    }

    private companion object {
        const val FILE = "ir.sanbuk.sdk"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_COUNTERS = "frequency_counters"
    }
}
