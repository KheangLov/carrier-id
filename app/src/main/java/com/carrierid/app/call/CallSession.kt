package com.carrierid.app.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.carrierid.app.settings.Features

/**
 * Follows one incoming call from ringing → answered → ended, using the
 * system's PHONE_STATE broadcasts. Stored in SharedPreferences because Android
 * may start a fresh process for each broadcast.
 *
 *  - rang and never answered → [AutoReply] (missed or declined)
 *  - answered and ended      → after-call card (note, message, block)
 */
object CallSession {
    private const val FILE = "session"
    private const val NUM = "num"
    private const val RING = "ring"
    private const val ANSWERED = "ans"
    private const val REPLIED = "rep"

    private val main = Handler(Looper.getMainLooper())
    private val endListeners = mutableListOf<() -> Unit>()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, 0)

    fun onRinging(ctx: Context, number: String?) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val ring = p.getLong(RING, 0L)
        // Android sends RINGING twice (with and without the number): keep one session
        if (ring != 0L && now - ring < 2 * 60 * 1000L) {
            if (!number.isNullOrBlank() && p.getString(NUM, null).isNullOrBlank()) {
                p.edit().putString(NUM, number).apply()
            }
            return
        }
        p.edit().clear().putLong(RING, now).putString(NUM, number).apply()
    }

    fun onOffhook(ctx: Context) {
        val p = prefs(ctx)
        if (p.getLong(RING, 0L) != 0L && p.getLong(ANSWERED, 0L) == 0L) {
            p.edit().putLong(ANSWERED, System.currentTimeMillis()).apply()
        }
        CallerOverlay.setInCall()
    }

    fun onIdle(ctx: Context) {
        val p = prefs(ctx)
        val ring = p.getLong(RING, 0L)
        if (ring == 0L) return               // outgoing call, or a duplicate IDLE
        val number = p.getString(NUM, null)
        val answered = p.getLong(ANSWERED, 0L)
        val replied = p.getBoolean(REPLIED, false)
        p.edit().clear().apply()

        main.post { synchronized(endListeners) { endListeners.toList() }.forEach { it() } }
        if (number.isNullOrBlank()) return

        if (answered != 0L) {
            if (Features.afterCallCard.get(ctx)) {
                val seconds = (System.currentTimeMillis() - answered) / 1000
                CallerOverlay.showAfterCall(ctx, number, ring, seconds)
            }
        } else if (!replied) {
            AutoReply.onMissed(ctx, number)
        }
    }

    /** A quick reply was sent for the ringing call, so no auto-reply afterwards. */
    fun markReplied(ctx: Context) = prefs(ctx).edit().putBoolean(REPLIED, true).apply()

    /** Number and ring time of the call in progress, if any. */
    fun current(ctx: Context): Pair<String?, Long>? {
        val p = prefs(ctx)
        val ring = p.getLong(RING, 0L)
        return if (ring == 0L) null else p.getString(NUM, null) to ring
    }

    fun addEndListener(l: () -> Unit) = synchronized(endListeners) { endListeners += l }
    fun removeEndListener(l: () -> Unit) = synchronized(endListeners) { endListeners -= l }
}
