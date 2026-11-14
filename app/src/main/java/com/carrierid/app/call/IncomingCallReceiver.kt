package com.carrierid.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.carrierid.app.carrier.CarrierLookup
import com.carrierid.app.spam.SpamCheck
import com.carrierid.app.spam.Verdict

/**
 * Follows the phone's call state (system PHONE_STATE broadcast).
 *
 *  - Backup path for showing the call card: on some phones/One UI versions
 *    Android skips the screening service (e.g. for some saved contacts).
 *    CallerOverlay ignores the duplicate when both fire for the same call.
 *  - Drives [CallSession]: in-call card, after-call card, auto-reply.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val app = context.applicationContext

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android sends this broadcast twice; only one copy has the number.
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                CallSession.onRinging(app, number)
                if (!number.isNullOrBlank()) {
                    val pending = goAsync()
                    Thread {
                        try {
                            val verdict = runCatching { SpamCheck.check(app, number) }
                                .getOrDefault(Verdict.SAFE)
                            CallerOverlay.show(app, number, CarrierLookup.lookup(number), verdict)
                        } finally {
                            pending.finish()
                        }
                    }.start()
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallSession.onOffhook(app)
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallerOverlay.onCallEnded()
                CallSession.onIdle(app)
            }
        }
    }
}
